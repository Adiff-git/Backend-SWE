package com.example.demo.catalog.audit;

import com.example.demo.catalog.exception.InvalidProductImageException;
import com.example.demo.catalog.validator.ProductImageContentValidator;
import com.example.demo.catalog.validator.ProductImageContentValidator.ValidatedImage;
import com.example.demo.storage.config.S3StorageProperties;
import com.example.demo.storage.dto.response.StoredObjectContent;
import com.example.demo.storage.dto.response.StoredObjectMetadata;
import com.example.demo.storage.exception.StoredObjectChangedException;
import com.example.demo.storage.service.ObjectStorageService;
import com.example.demo.storage.service.impl.ObjectStorageServiceImpl;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

/** Read-only adapter: only existing HEAD and bounded If-Match GET operations are called. */
final class AuditImageReader implements AutoCloseable {
    private final Map<String, String> environment;
    private final ProductImageContentValidator validator = new ProductImageContentValidator();
    private ObjectStorageService storage;
    private S3Client client;
    private DefaultCredentialsProvider credentials;

    AuditImageReader(Map<String, String> environment) { this.environment = Map.copyOf(environment); }
    AuditImageReader(ObjectStorageService storage) { this.environment = Map.of(); this.storage = storage; }

    String inspect(CatalogCompatibilityAudit.Image image) throws Exception {
        if (image.key() == null || image.key().isBlank() || image.bytes() < 1
                || image.bytes() > ProductImageContentValidator.MAX_BYTES) return null;
        if (storage == null) initialize(); // Empty gallery does not require storage configuration/credentials.
        Optional<StoredObjectMetadata> inspected = storage.inspect(image.key());
        if (inspected.isEmpty()) return null;
        StoredObjectMetadata metadata = inspected.get();
        if (metadata.getEtag() == null || metadata.getEtag().isBlank()) throw new IllegalStateException();
        if (metadata.getLength() != image.bytes() || !Objects.equals(metadata.getContentType(), image.contentType())) return null;
        try (StoredObjectContent content = storage.readBoundedIfMatch(image.key(), metadata.getEtag(), ProductImageContentValidator.MAX_BYTES);
                InputStream input = Files.newInputStream(content.getPath())) {
            byte[] bytes = input.readNBytes(ProductImageContentValidator.MAX_BYTES + 1);
            if (content.getMetadata() == null || !Objects.equals(metadata.getEtag(), content.getMetadata().getEtag())
                    || content.getMetadata().getLength() != metadata.getLength()
                    || !Objects.equals(content.getMetadata().getContentType(), metadata.getContentType())) {
                throw new StoredObjectChangedException();
            }
            ValidatedImage decoded = validator.validate(bytes, image.contentType(), image.bytes(), image.altText());
            if (decoded.width() != image.width() || decoded.height() != image.height()) return null;
            return HexFormat.of().formatHex(decoded.sha256());
        } catch (InvalidProductImageException invalid) {
            return null;
        }
        // Missing/changed objects during GET, permissions, timeouts, decoding I/O and unavailable
        // credentials propagate as INCOMPLETE. They cannot certify a stable object snapshot.
    }

    private void initialize() {
        String bucket = required("AUDIT_S3_BUCKET");
        String region = required("AUDIT_S3_REGION");
        S3StorageProperties properties = new S3StorageProperties();
        properties.setBucket(bucket);
        properties.setRegion(region);
        credentials = DefaultCredentialsProvider.builder().build();
        S3ClientBuilder builder = S3Client.builder().region(Region.of(region)).credentialsProvider(credentials)
                .httpClientBuilder(UrlConnectionHttpClient.builder().connectionTimeout(Duration.ofSeconds(10))
                        .socketTimeout(Duration.ofSeconds(30)))
                .overrideConfiguration(configuration -> configuration.apiCallTimeout(Duration.ofSeconds(60))
                        .apiCallAttemptTimeout(Duration.ofSeconds(30)));
        String endpoint = environment.get("AUDIT_S3_ENDPOINT");
        if (endpoint != null && !endpoint.isBlank()) {
            URI uri = URI.create(endpoint);
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || !(uri.getScheme().equals("https") || uri.getScheme().equals("http"))) throw new IllegalArgumentException();
            builder.endpointOverride(uri).forcePathStyle(true);
        }
        client = builder.build();
        // These two existing read methods depend only on client/properties. Deliberately absent
        // signer/checkpoint dependencies prevent any uploader/checkpoint initialization. No other
        // ObjectStorageService method is reachable through this adapter's CLI interface.
        storage = new ObjectStorageServiceImpl(client, null, properties, Clock.systemUTC(), null, credentials);
    }

    private String required(String key) {
        String value = environment.get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException();
        return value;
    }

    @Override public void close() {
        try {
            if (client != null) client.close();
        } finally {
            if (credentials != null) credentials.close();
        }
    }
}
