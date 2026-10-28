package com.example.demo.catalog.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo.catalog.exception.CatalogStorageUnavailableException;
import com.example.demo.catalog.storage.ProductImageStorage.PreparedReadSigner;
import java.net.URI;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

class S3ProductImageStorageTest {

	@Test
	void signsPrivateLocalStackObjectForExactlyFiveMinutesWithoutStorageRequest() {
		S3ProductImageStorage storage = new S3ProductImageStorage("catalog-images", "us-east-1",
				"http://localhost:4566", StaticCredentialsProvider.create(
						AwsBasicCredentials.create("test-key", "test-secret")));
		try (PreparedReadSigner signer = storage.prepareReadSigner()) {
			URI url = URI.create(signer.signReadUrl("final/private-object"));
			assertThat(url.getHost()).isEqualTo("localhost");
			assertThat(url.getPath()).isEqualTo("/catalog-images/final/private-object");
			assertThat(url.getRawQuery()).contains("X-Amz-Expires=300", "X-Amz-Signature=");
		}
	}

	@Test
	void missingPrivateBucketFailsClosed() {
		S3ProductImageStorage storage = new S3ProductImageStorage("", "us-east-1", "",
				StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")));
		assertThatThrownBy(storage::prepareReadSigner)
				.isInstanceOf(CatalogStorageUnavailableException.class);
	}

}
