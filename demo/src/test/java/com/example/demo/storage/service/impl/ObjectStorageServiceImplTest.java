package com.example.demo.storage.service.impl;

import com.example.demo.storage.config.S3StorageConfiguration;
import com.example.demo.storage.config.S3StorageProperties;
import com.example.demo.storage.service.PreparedDownloadSigner;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ObjectStorageServiceImplTest {
    @Test void prepareDownloadSigner_shouldCaptureCredentialsAndReuseApplicationSignerWithoutHeadOrCleanup() {
        S3StorageProperties properties = new S3StorageProperties();
        properties.setRegion("us-east-1");
        properties.setBucket("private-images");
        properties.setEndpoint(URI.create("http://localhost:4566"));
        properties.setPathStyleAccess(true);
        AwsCredentialsProvider credentials = mock(AwsCredentialsProvider.class);
        when(credentials.resolveCredentials()).thenReturn(AwsBasicCredentials.create("captured", "test-secret"));
        S3Client client = mock(S3Client.class);
        // If request credentials are not honored this fails rather than doing network credential lookup.
        try (S3Presigner presigner = new S3StorageConfiguration().createPresigner(properties,
                () -> { throw new IllegalStateException("Default credentials must not resolve during local signing"); })) {
            ObjectStorageServiceImpl storage = new ObjectStorageServiceImpl(client, presigner, properties,
                    Clock.systemUTC(), mock(com.example.demo.storage.service.LocalImageCheckpoint.class), credentials);
            PreparedDownloadSigner prepared = storage.prepareDownloadSigner(Duration.ofSeconds(300));
            verify(credentials).resolveCredentials();
            prepared.close(); // Compatibility close must not close the application signer.
            URI product = URI.create(prepared.sign("images/product"));
            URI avatar = URI.create(prepared.sign("images/avatar"));
            assertThat(product.getPath()).isEqualTo("/private-images/images/product");
            assertThat(avatar.getRawQuery()).contains("X-Amz-Expires=300").contains("captured");
            verify(credentials, times(1)).resolveCredentials();
            verifyNoInteractions(client);
            // A second operation still uses the same live application resource.
            assertThat(storage.prepareDownload("images/review", Duration.ofSeconds(300)))
                    .contains("/private-images/images/review");
        }
    }
}
