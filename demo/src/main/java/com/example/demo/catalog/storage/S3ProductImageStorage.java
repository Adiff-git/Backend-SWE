package com.example.demo.catalog.storage;

import com.example.demo.catalog.exception.CatalogStorageUnavailableException;
import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Component
public class S3ProductImageStorage implements ProductImageStorage {

	private static final Duration READ_URL_LIFETIME = Duration.ofMinutes(5);

	private final String bucket;
	private final String region;
	private final String endpoint;
	private final AwsCredentialsProvider credentialsProvider;

	@Autowired
	public S3ProductImageStorage(@Value("${catalog.images.bucket:}") String bucket,
			@Value("${catalog.images.region:}") String region,
			@Value("${catalog.images.endpoint:}") String endpoint) {
		this(bucket, region, endpoint, DefaultCredentialsProvider.create());
	}

	S3ProductImageStorage(String bucket, String region, String endpoint,
			AwsCredentialsProvider credentialsProvider) {
		this.bucket = bucket;
		this.region = region;
		this.endpoint = endpoint;
		this.credentialsProvider = credentialsProvider;
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

}
