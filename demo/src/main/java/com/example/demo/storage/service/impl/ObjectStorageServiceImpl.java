package com.example.demo.storage.service.impl;

import com.example.demo.storage.config.S3StorageProperties;
import com.example.demo.storage.dto.response.StoredObjectContent;
import com.example.demo.storage.dto.response.StoredObjectMetadata;
import com.example.demo.storage.dto.response.PreparedUpload;
import com.example.demo.storage.exception.StorageUnavailableException;
import com.example.demo.storage.exception.StoredObjectChangedException;
import com.example.demo.storage.service.ObjectStorageService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.MetadataDirective;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
@RequiredArgsConstructor
public class ObjectStorageServiceImpl implements ObjectStorageService {
    private final S3Client client;
    private final S3Presigner presigner;
    private final S3StorageProperties properties;
    private final Clock clock;

    @Override public PreparedUpload prepareUpload(String key, String contentType, Instant deadline) {
        return available(() -> {
            Duration lifetime = Duration.between(clock.instant(), deadline);
            if (lifetime.isNegative() || lifetime.isZero()) throw new StoredObjectChangedException();
            software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest signed =
                    presigner.presignPutObject(PutObjectPresignRequest.builder()
                    .signatureDuration(properties.getUploadUrlLifetime())
                    .putObjectRequest(PutObjectRequest.builder().bucket(properties.getBucket())
                            .key(key).contentType(contentType).build()).build());
            return new PreparedUpload(signed.url().toString(), signed.expiration().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        });
    }

    @Override public Optional<StoredObjectMetadata> inspect(String key) {
        try {
            HeadObjectResponse response = client.headObject(request -> request.bucket(properties.getBucket()).key(key));
            return Optional.of(new StoredObjectMetadata(response.eTag(), response.contentLength(), response.contentType()));
        } catch (S3Exception failure) {
            if (failure.statusCode() == 404) return Optional.empty();
            throw new StorageUnavailableException();
        } catch (SdkException failure) { throw new StorageUnavailableException(); }
    }

    @Override public StoredObjectContent readIfMatch(String key, String etag) {
        Path temporary = null;
        try (ResponseInputStream<GetObjectResponse> input = client.getObject(GetObjectRequest.builder()
                .bucket(properties.getBucket()).key(key).ifMatch(etag).build())) {
            temporary = Files.createTempFile("validated-image-", ".upload");
            Files.copy(input, temporary, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            GetObjectResponse response = input.response();
            return new StoredObjectContent(temporary, new StoredObjectMetadata(
                    response.eTag(), Files.size(temporary), response.contentType()));
        } catch (IOException | SdkException failure) {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); } catch (IOException ignored) { temporary.toFile().deleteOnExit(); }
            }
            if (failure instanceof S3Exception s3 && (s3.statusCode() == 404 || s3.statusCode() == 412))
                throw new StoredObjectChangedException();
            throw new StorageUnavailableException();
        }
    }

    @Override public void copyIfMatch(String source, String etag, String target, String approvedContentType) {
        available(() -> {
            try {
                client.copyObject(CopyObjectRequest.builder().sourceBucket(properties.getBucket()).sourceKey(source)
                        .destinationBucket(properties.getBucket()).destinationKey(target).copySourceIfMatch(etag)
                        .metadataDirective(MetadataDirective.REPLACE).metadata(Map.of())
                        .contentType(approvedContentType).cacheControl("no-store").build());
            } catch (S3Exception failure) {
                if (failure.statusCode() == 404 || failure.statusCode() == 412) throw new StoredObjectChangedException();
                throw failure;
            }
            return null;
        });
    }

    @Override public void verifyFinalExists(String key) {
        if (key == null || key.isBlank() || inspect(key).isEmpty()) throw new StorageUnavailableException();
    }

    @Override public String prepareDownload(String key, Duration lifetime) {
        return available(() -> {
            software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest signed =
                    presigner.presignGetObject(GetObjectPresignRequest.builder().signatureDuration(lifetime)
                            .getObjectRequest(GetObjectRequest.builder().bucket(properties.getBucket()).key(key).build()).build());
            if (!signed.isBrowserExecutable()) throw new StorageUnavailableException();
            return signed.url().toString();
        });
    }

    @Override public void deleteStaging(String key) {
        if (!key.startsWith("staging/")) throw new IllegalArgumentException("Only staging content can be cleaned");
        available(() -> { client.deleteObject(request -> request.bucket(properties.getBucket()).key(key)); return null; });
    }

    private <T> T available(Supplier<T> operation) {
        try { return operation.get(); }
        catch (SdkException failure) { throw new StorageUnavailableException(); }
    }
}
