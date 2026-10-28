package com.example.demo.storage.config;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class S3EnvironmentConfigurationTest {
    @Test void validate_shouldRejectCredentialBearingEndpointAndPartialLocalCredentials() {
        S3StorageProperties properties = new S3StorageProperties();
        properties.setBucket("test-images");
        properties.setRegion("us-east-1");
        properties.setEndpoint(URI.create("http://user:password@localhost:4566"));
        assertThatThrownBy(() -> properties.validate(true)).isInstanceOf(IllegalArgumentException.class);
        properties.setEndpoint(URI.create("http://localhost:4566"));
        properties.setLocalAccessKey("test");
        assertThatThrownBy(() -> properties.validate(true)).isInstanceOf(IllegalArgumentException.class);
        properties.setLocalSecretKey("test");
        properties.setApiCallAttemptTimeout(Duration.ofSeconds(61));
        assertThatThrownBy(() -> properties.validate(true)).isInstanceOf(IllegalArgumentException.class);
    }
}
