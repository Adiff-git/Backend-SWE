package com.example.demo.catalog.storage;

import com.example.demo.catalog.exception.CatalogStorageUnavailableException;
import com.example.demo.catalog.exception.InvalidProductImageException;
import com.example.demo.catalog.storage.ProductImageUploadStorage.ObjectSnapshot;
import com.example.demo.catalog.storage.ProductImageUploadStorage.PreparedPut;
import com.example.demo.catalog.validator.ProductImageContentValidator;
import java.io.IOException;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

@Component
public class S3ProductImageStorage implements ProductImageStorage, ProductImageUploadStorage {

	private static final Duration READ_URL_LIFETIME = Duration.ofMinutes(5);
	private static final long MAX_PUT_SECONDS = Duration.ofMinutes(15).toSeconds();

	private final String bucket;
	private final String region;
	private final String endpoint;
	private final AwsCredentialsProvider credentialsProvider;
	private final S3Client testClient;

	@Autowired
	public S3ProductImageStorage(@Value("${catalog.images.bucket:}") String bucket,
			@Value("${catalog.images.region:}") String region,
			@Value("${catalog.images.endpoint:}") String endpoint) {
		this(bucket, region, endpoint, DefaultCredentialsProvider.create());
	}

	S3ProductImageStorage(String bucket, String region, String endpoint,
			AwsCredentialsProvider credentialsProvider) {
		this(bucket, region, endpoint, credentialsProvider, null);
	}

	S3ProductImageStorage(String bucket, String region, String endpoint,
			AwsCredentialsProvider credentialsProvider, S3Client testClient) {
		this.bucket = bucket;
		this.region = region;
		this.endpoint = endpoint;
		this.credentialsProvider = credentialsProvider;
		this.testClient = testClient;
	}

	@Override
	public PreparedReadSigner prepareReadSigner() {
		if (bucket.isBlank() || region.isBlank()) {
			throw new CatalogStorageUnavailableException();
		}
		try {
			// Credential resolution can use external providers; finish it before the service locks rows.
			AwsCredentials credentials = credentialsProvider.resolveCredentials();
			S3Presigner presigner = newPresigner(credentials);
			return new PreparedReadSigner() {
				@Override
				public String signReadUrl(String privateObjectKey) {
					try {
						GetObjectRequest request = GetObjectRequest.builder()
								.bucket(bucket).key(privateObjectKey).build();
						return presigner.presignGetObject(GetObjectPresignRequest.builder()
								.signatureDuration(READ_URL_LIFETIME)
								.getObjectRequest(request).build()).url().toExternalForm();
					} catch (RuntimeException exception) {
						throw new CatalogStorageUnavailableException();
					}
				}

				@Override
				public void close() {
					presigner.close();
				}
			};
		} catch (RuntimeException exception) {
			throw new CatalogStorageUnavailableException();
		}
	}

	@Override
	public String newStagingKey(UUID uploadId) {
		return newKey("staging", uploadId);
	}

	@Override
	public String newDurableKey(UUID uploadId) {
		return newKey("final", uploadId);
	}

	private String newKey(String root, UUID uploadId) {
		if (uploadId == null) {
			throw new IllegalArgumentException("Upload ID is required");
		}
		return root + "/" + uploadId + "/" + UUID.randomUUID();
	}

	@Override
	public PreparedPut preparePut(String stagingKey, String contentType, long byteSize,
			Instant sessionExpiresAt) {
		if (!isScopedKey(stagingKey, "staging") || !allowedType(contentType)
				|| byteSize < 1 || byteSize > ProductImageContentValidator.MAX_BYTES
				|| sessionExpiresAt == null) {
			throw new IllegalArgumentException("Invalid staging upload request");
		}
		long seconds = Math.min(MAX_PUT_SECONDS, Duration.between(Instant.now(), sessionExpiresAt).getSeconds());
		if (seconds < 1) {
			throw new IllegalArgumentException("Upload session has expired");
		}
		try (S3Presigner presigner = newPresigner(credentialsProvider.resolveCredentials())) {
			// Browsers set Content-Length themselves; bind the declared type and verify
			// the actual byte count from S3 during completion.
			PutObjectRequest put = PutObjectRequest.builder().bucket(bucket).key(stagingKey)
					.contentType(contentType).build();
			PresignedPutObjectRequest signed = presigner.presignPutObject(PutObjectPresignRequest.builder()
					.signatureDuration(Duration.ofSeconds(seconds)).putObjectRequest(put).build());
			Map<String, String> required = new HashMap<>();
			signed.signedHeaders().forEach((name, values) -> {
				if (!"host".equalsIgnoreCase(name)) {
					required.put(name, String.join(",", values));
				}
			});
			return new PreparedPut(signed.url().toExternalForm(), Map.copyOf(required), signed.expiration());
		} catch (RuntimeException failure) {
			throw new CatalogStorageUnavailableException();
		}
	}

	@Override
	public ObjectSnapshot inspect(String objectKey) {
		requirePrivateKey(objectKey);
		try (S3Client client = newClient()) {
			HeadObjectResponse head = client.headObject(HeadObjectRequest.builder()
					.bucket(bucket).key(objectKey).build());
			if (head.contentLength() == null || head.contentLength() < 1
					|| head.contentLength() > ProductImageContentValidator.MAX_BYTES
					|| head.eTag() == null || head.eTag().isBlank()) {
				throw new InvalidProductImageException();
			}
			GetObjectRequest.Builder get = GetObjectRequest.builder().bucket(bucket).key(objectKey)
					.ifMatch(head.eTag());
			if (head.versionId() != null) {
				get.versionId(head.versionId());
			}
			try (ResponseInputStream<GetObjectResponse> input = client.getObject(get.build())) {
				byte[] bytes = input.readNBytes(ProductImageContentValidator.MAX_BYTES + 1);
				if (bytes.length != head.contentLength() || bytes.length > ProductImageContentValidator.MAX_BYTES) {
					throw new InvalidProductImageException();
				}
				return new ObjectSnapshot(objectKey, head.eTag(), head.versionId(),
						head.contentType(), bytes, sha256(bytes));
			}
		} catch (IOException | RuntimeException failure) {
			if (failure instanceof InvalidProductImageException invalid) {
				throw invalid;
			}
			throw new CatalogStorageUnavailableException();
		}
	}

	@Override
	public void copyVerified(ObjectSnapshot source, String durableKey) {
		if (source == null || !isScopedKey(source.key(), "staging")
				|| !isScopedKey(durableKey, "final") || !uploadScope(source.key()).equals(uploadScope(durableKey))
				|| source.eTag() == null || source.eTag().isBlank()) {
			throw new IllegalArgumentException("Copy must stay within one upload");
		}
		try (S3Client client = newClient()) {
			CopyObjectRequest.Builder copy = CopyObjectRequest.builder()
					.sourceBucket(bucket).sourceKey(source.key()).destinationBucket(bucket)
					.destinationKey(durableKey).copySourceIfMatch(source.eTag());
			if (source.versionId() != null) {
				copy.sourceVersionId(source.versionId());
			}
			client.copyObject(copy.build());
		} catch (RuntimeException failure) {
			throw new CatalogStorageUnavailableException();
		}
	}

	@Override
	public ObjectSnapshot verifyFinal(String durableKey, byte[] expectedSha256) {
		if (!isScopedKey(durableKey, "final") || expectedSha256 == null || expectedSha256.length != 32) {
			throw new IllegalArgumentException("Final verification requires a digest and scoped key");
		}
		ObjectSnapshot actual = inspect(durableKey);
		if (!MessageDigest.isEqual(actual.sha256(), expectedSha256)) {
			throw new InvalidProductImageException();
		}
		return actual;
	}

	@Override
	public List<String> listUploadKeys(UUID uploadId) {
		if (uploadId == null) {
			throw new IllegalArgumentException("Upload ID is required");
		}
		try (S3Client client = newClient()) {
			List<String> keys = new ArrayList<>();
			for (String root : List.of("staging", "final")) {
				String prefix = root + "/" + uploadId + "/";
				String token = null;
				do {
					ListObjectsV2Request request = ListObjectsV2Request.builder().bucket(bucket)
							.prefix(prefix).continuationToken(token).build();
					ListObjectsV2Response page = client.listObjectsV2(request);
					page.contents().forEach(object -> {
						if (object.key().startsWith(prefix)) {
							keys.add(object.key());
						}
					});
					token = page.nextContinuationToken();
				} while (token != null);
			}
			return List.copyOf(keys);
		} catch (RuntimeException failure) {
			throw new CatalogStorageUnavailableException();
		}
	}

	@Override
	public void deleteUploadKey(UUID uploadId, String objectKey) {
		if (uploadId == null || objectKey == null || !objectKey.startsWith("staging/" + uploadId + "/")
				&& !objectKey.startsWith("final/" + uploadId + "/")) {
			throw new IllegalArgumentException("Deletion is limited to this upload");
		}
		try (S3Client client = newClient()) {
			client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(objectKey).build());
		} catch (RuntimeException failure) {
			throw new CatalogStorageUnavailableException();
		}
	}

	private S3Client newClient() {
		if (bucket.isBlank() || region.isBlank()) {
			throw new CatalogStorageUnavailableException();
		}
		if (testClient != null) {
			return testClient;
		}
		S3ClientBuilder builder = S3Client.builder().region(Region.of(region))
				.credentialsProvider(credentialsProvider);
		if (!endpoint.isBlank()) {
			builder.endpointOverride(URI.create(endpoint))
					.serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
		}
		return builder.build();
	}

	private S3Presigner newPresigner(AwsCredentials credentials) {
		if (bucket.isBlank() || region.isBlank()) {
			throw new CatalogStorageUnavailableException();
		}
		S3Presigner.Builder builder = S3Presigner.builder().region(Region.of(region))
				.credentialsProvider(StaticCredentialsProvider.create(credentials));
		if (!endpoint.isBlank()) {
			builder.endpointOverride(URI.create(endpoint))
					.serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
		}
		return builder.build();
	}

	private void requirePrivateKey(String key) {
		if (!isScopedKey(key, "staging") && !isScopedKey(key, "final")) {
			throw new IllegalArgumentException("Object key is outside upload storage");
		}
	}

	private boolean isScopedKey(String key, String root) {
		if (key == null || !key.startsWith(root + "/")) {
			return false;
		}
		String[] parts = key.split("/", -1);
		if (parts.length != 3 || parts[2].isBlank()) {
			return false;
		}
		try {
			UUID.fromString(parts[1]);
			return true;
		} catch (IllegalArgumentException invalid) {
			return false;
		}
	}

	private String uploadScope(String key) {
		return key.split("/")[1];
	}

	private boolean allowedType(String type) {
		return "image/jpeg".equals(type) || "image/png".equals(type) || "image/webp".equals(type);
	}

	private byte[] sha256(byte[] bytes) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(bytes);
		} catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException("SHA-256 is unavailable", impossible);
		}
	}
}
