package com.example.demo.media.integration;

import com.example.demo.user.integration.PostgresTestSupport;
import java.util.UUID;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import org.testcontainers.containers.Container;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;

/** A separate disposable S3 endpoint; never falls back to developer infrastructure. */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public abstract class MediaInfrastructureTestSupport extends PostgresTestSupport {
    protected static final LocalStackContainer STORAGE = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:4.14.0"))
            .withServices(LocalStackContainer.Service.S3)
            .withEnv("S3_SKIP_SIGNATURE_VALIDATION", "0");
    @Autowired private S3Client storageClient;
    @Autowired private JdbcTemplate jdbc;
    @Value("${storage.s3.bucket}") protected String bucket;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry properties) {
        STORAGE.start();
        String testBucket = "media-test-" + UUID.randomUUID();
        try {
            Container.ExecResult result = STORAGE.execInContainer("awslocal", "s3api", "create-bucket", "--bucket", testBucket);
            if (result.getExitCode() != 0) throw new IllegalStateException("Bucket initialization failed");
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot initialize disposable storage", failure);
        }
        properties.add("storage.s3.bucket", () -> testBucket);
        properties.add("storage.s3.region", STORAGE::getRegion);
        properties.add("storage.s3.endpoint", STORAGE::getEndpoint);
        properties.add("storage.s3.signing-endpoint", STORAGE::getEndpoint);
        properties.add("storage.s3.local-access-key", STORAGE::getAccessKey);
        properties.add("storage.s3.local-secret-key", STORAGE::getSecretKey);
        properties.add("storage.s3.path-style-access", () -> true);
    }

    @AfterEach
    void removeDisposableBucket() {
        ListObjectsV2Response objects;
        do {
            objects = storageClient.listObjectsV2(request -> request.bucket(bucket));
            objects.contents().forEach(object -> storageClient.deleteObject(
                    request -> request.bucket(bucket).key(object.key())));
        } while (Boolean.TRUE.equals(objects.isTruncated()));
        storageClient.deleteBucket(request -> request.bucket(bucket));
        jdbc.execute("TRUNCATE TABLE users RESTART IDENTITY CASCADE");
    }
}
