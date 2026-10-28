package com.example.demo.storage.config;

import java.net.URI;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class S3StorageConfigurationTest {
    private final S3StorageConfiguration configuration = new S3StorageConfiguration();

    @Test void prepareUpload_shouldExposeExactSignedDeadlineAndFull900SecondLifetime() {
        S3StorageProperties properties = properties();
        properties.setEndpoint(URI.create("http://localhost:4566"));
        properties.setPathStyleAccess(true);
        properties.setLocalAccessKey("test");
        properties.setLocalSecretKey("test");
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        Instant now = Instant.now();
        try (S3Presigner presigner = configuration.s3Presigner(properties, configuration.storageCredentials(properties, environment))) {
            com.example.demo.storage.service.impl.ObjectStorageServiceImpl storage =
                    new com.example.demo.storage.service.impl.ObjectStorageServiceImpl(
                            org.mockito.Mockito.mock(software.amazon.awssdk.services.s3.S3Client.class), presigner,
                            properties, Clock.fixed(now, ZoneOffset.UTC));
            com.example.demo.storage.dto.response.PreparedUpload signed = storage.prepareUpload("staging/key", "image/png", now.plusSeconds(900));
            java.util.Map<String, String> query = java.util.Arrays.stream(URI.create(signed.getUrl()).getRawQuery().split("&"))
                    .map(value -> value.split("=", 2)).collect(java.util.stream.Collectors.toMap(value -> value[0], value -> value[1]));
            assertThat(query.get("X-Amz-Expires")).isEqualTo("900");
            String download = storage.prepareDownload("images/final", properties.getDownloadUrlLifetime());
            assertThat(URI.create(download).getRawQuery()).contains("X-Amz-Expires=300&");
            Instant signingTime = java.time.LocalDateTime.parse(query.get("X-Amz-Date"),
                    java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")).toInstant(ZoneOffset.UTC);
            assertThat(signed.getExpiresAt()).isEqualTo(signingTime.plusSeconds(900));
        }
    }

    private S3StorageProperties properties() {
        S3StorageProperties result = new S3StorageProperties();
        result.setRegion("us-east-1");
        result.setBucket("test-images");
        return result;
    }

    @Test void validate_shouldRejectMissingBucketAndInvalidDurations() {
        S3StorageProperties properties = properties();
        properties.setBucket(" ");
        assertThatThrownBy(() -> properties.validate(false)).isInstanceOf(IllegalArgumentException.class);
        properties.setBucket("test-images");
        properties.setApiCallTimeout(Duration.ZERO);
        assertThatThrownBy(() -> properties.validate(false)).isInstanceOf(IllegalArgumentException.class);
        properties.setApiCallTimeout(Duration.ofSeconds(60));
        properties.setUploadUrlLifetime(Duration.ofSeconds(901));
        assertThatThrownBy(() -> properties.validate(false)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void credentials_shouldSelectDefaultChainWithoutExplicitSecrets() {
        AwsCredentialsProvider provider = configuration.storageCredentials(properties(), new MockEnvironment());
        assertThat(provider).isInstanceOf(DefaultCredentialsProvider.class);
        ((DefaultCredentialsProvider) provider).close();
    }

    @Test void credentials_shouldRejectDummyCredentialsInProductionEvenWhenLocalProfileIsAlsoActive() {
        S3StorageProperties properties = properties();
        properties.setLocalAccessKey("test");
        properties.setLocalSecretKey("test");
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod", "local");
        assertThatThrownBy(() -> configuration.storageCredentials(properties, environment))
                .isInstanceOf(IllegalArgumentException.class);
        environment.setActiveProfiles("local");
        assertThat(configuration.storageCredentials(properties, environment))
                .isInstanceOf(StaticCredentialsProvider.class);
    }

    @Test void presign_shouldUseBrowserEndpointAndBindContentTypeWithoutNetworkAccess() {
        S3StorageProperties properties = properties();
        properties.setEndpoint(URI.create("http://localstack:4566"));
        properties.setSigningEndpoint(URI.create("http://localhost:4566"));
        properties.setPathStyleAccess(true);
        properties.setLocalAccessKey("test");
        properties.setLocalSecretKey("test");
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        try (S3Presigner presigner = configuration.s3Presigner(properties,
                configuration.storageCredentials(properties, environment))) {
            software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest signed =
                    presigner.presignPutObject(PutObjectPresignRequest.builder()
                            .signatureDuration(Duration.ofSeconds(900))
                            .putObjectRequest(request -> request.bucket(properties.getBucket())
                                    .key("staging/test").contentType("image/png")).build());
            assertThat(signed.url().getHost()).isEqualTo("localhost");
            assertThat(signed.url().getPath()).isEqualTo("/test-images/staging/test");
            assertThat(signed.signedHeaders()).containsKey("content-type");
        }
    }
}
