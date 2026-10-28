package com.example.demo.storage.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.retries.StandardRetryStrategy;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@EnableConfigurationProperties(S3StorageProperties.class)
public class S3StorageConfiguration {
    @Bean
    public AwsCredentialsProvider storageCredentials(S3StorageProperties properties, Environment environment) {
        properties.validate(!environment.acceptsProfiles(Profiles.of("prod"))
                && environment.acceptsProfiles(Profiles.of("local", "test")));
        if (properties.getLocalAccessKey() != null && !properties.getLocalAccessKey().isBlank())
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(
                    properties.getLocalAccessKey(), properties.getLocalSecretKey()));
        return DefaultCredentialsProvider.builder().build();
    }

    @Bean(destroyMethod = "close")
    public S3Client s3Client(S3StorageProperties properties, AwsCredentialsProvider storageCredentials) {
        S3ClientBuilder builder = S3Client.builder().region(Region.of(properties.getRegion()))
                .credentialsProvider(storageCredentials)
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(properties.isPathStyleAccess()).build())
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(properties.getApiCallTimeout())
                        .apiCallAttemptTimeout(properties.getApiCallAttemptTimeout())
                        .retryStrategy(StandardRetryStrategy.builder().maxAttempts(1).build()).build());
        if (properties.getEndpoint() != null) builder.endpointOverride(properties.getEndpoint());
        return builder.build();
    }

    @Bean(destroyMethod = "close")
    public S3Presigner s3Presigner(S3StorageProperties properties, AwsCredentialsProvider storageCredentials) {
        return createPresigner(properties, storageCredentials);
    }

    public S3Presigner createPresigner(S3StorageProperties properties, AwsCredentialsProvider storageCredentials) {
        S3Presigner.Builder builder = S3Presigner.builder().region(Region.of(properties.getRegion()))
                .credentialsProvider(storageCredentials)
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(properties.isPathStyleAccess())
                        .checksumValidationEnabled(false).build());
        if (properties.getSigningEndpoint() != null) builder.endpointOverride(properties.getSigningEndpoint());
        else if (properties.getEndpoint() != null) builder.endpointOverride(properties.getEndpoint());
        return builder.build();
    }
}
