package com.example.demo.storage.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties("storage.s3")
public class S3StorageProperties {
    @NotBlank private String region;
    @NotBlank private String bucket;
    private URI endpoint;
    private URI signingEndpoint;
    private boolean pathStyleAccess;
    private String localAccessKey;
    private String localSecretKey;
    @NotNull private Duration uploadUrlLifetime = Duration.ofSeconds(900);
    @NotNull private Duration downloadUrlLifetime = Duration.ofSeconds(900);
    @NotNull private Duration apiCallTimeout = Duration.ofSeconds(60);
    @NotNull private Duration apiCallAttemptTimeout = Duration.ofSeconds(30);

    public void validate(boolean localCredentialsAllowed) {
        if (region == null || region.isBlank() || bucket == null || bucket.isBlank())
            throw new IllegalArgumentException("Storage region and bucket are required");
        positive(uploadUrlLifetime);
        positive(downloadUrlLifetime);
        positive(apiCallTimeout);
        positive(apiCallAttemptTimeout);
        if (apiCallAttemptTimeout.compareTo(apiCallTimeout) > 0)
            throw new IllegalArgumentException("Attempt timeout exceeds total timeout");
        if (!Duration.ofSeconds(900).equals(uploadUrlLifetime)
                || !Duration.ofSeconds(900).equals(downloadUrlLifetime))
            throw new IllegalArgumentException("Approved capability lifetimes are 900 seconds");
        boolean hasKey = localAccessKey != null && !localAccessKey.isBlank();
        boolean hasSecret = localSecretKey != null && !localSecretKey.isBlank();
        if (hasKey != hasSecret || (hasKey && !localCredentialsAllowed))
            throw new IllegalArgumentException("Local credentials require a local/test profile and a complete pair");
        validateEndpoint(endpoint);
        validateEndpoint(signingEndpoint);
    }

    private static void positive(Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero())
            throw new IllegalArgumentException("Storage durations must be positive");
    }

    private static void validateEndpoint(URI value) {
        if (value != null && (value.getHost() == null || value.getUserInfo() != null
                || !("http".equals(value.getScheme()) || "https".equals(value.getScheme()))
                || value.getQuery() != null || value.getFragment() != null))
            throw new IllegalArgumentException("Invalid storage endpoint");
    }
}
