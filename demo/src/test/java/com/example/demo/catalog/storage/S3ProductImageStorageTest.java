package com.example.demo.catalog.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.catalog.exception.CatalogStorageUnavailableException;
import com.example.demo.catalog.exception.InvalidProductImageException;
import com.example.demo.catalog.storage.ProductImageStorage.PreparedReadSigner;
import com.example.demo.catalog.storage.ProductImageUploadStorage.ObjectSnapshot;
import com.example.demo.catalog.storage.ProductImageUploadStorage.PreparedPut;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

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

	@Test
	void createsDistinctUploadScopedKeysAndSignsPutNoLongerThanSession() {
		S3ProductImageStorage storage = storage(mock(S3Client.class));
		UUID uploadId = UUID.randomUUID();
		String staging = storage.newStagingKey(uploadId);
		String durable = storage.newDurableKey(uploadId);
		assertThat(staging).startsWith("staging/" + uploadId + "/");
		assertThat(durable).startsWith("final/" + uploadId + "/");
		assertThat(storage.newDurableKey(uploadId)).isNotEqualTo(durable);
		Instant deadline = Instant.now().plusSeconds(60);
		PreparedPut put = storage.preparePut(staging, "image/png", 123, deadline);
		assertThat(put.url()).contains("X-Amz-Signature=", "X-Amz-Expires=");
		assertThat(put.url()).contains("/catalog-images/" + staging);
		assertThat(put.expiresAt()).isBeforeOrEqualTo(deadline);
		assertThat(put.requiredHeaders()).containsEntry("content-type", "image/png")
				.doesNotContainKey("content-length");
		assertThat(storage.preparePut(staging, "image/png", 123,
				Instant.now().plusSeconds(1200)).url()).contains("X-Amz-Expires=900");
		assertThatThrownBy(() -> storage.preparePut(staging, "image/png", 1,
				Instant.now().minusSeconds(1))).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> storage.preparePut(staging, "image/gif", 1,
				Instant.now().plusSeconds(60))).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> storage.preparePut(staging, "image/png", 10_485_761,
				Instant.now().plusSeconds(60))).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void copiesOnlyTheInspectedSourceAndChecksFinalDigest() throws Exception {
		S3Client client = mock(S3Client.class);
		S3ProductImageStorage storage = storage(client);
		UUID uploadId = UUID.randomUUID();
		String staging = storage.newStagingKey(uploadId);
		String durable = storage.newDurableKey(uploadId);
		byte[] bytes = "validated bytes".getBytes(StandardCharsets.UTF_8);
		when(client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder()
				.contentLength((long) bytes.length).contentType("image/png")
				.eTag("\"source-etag\"").versionId("source-version").build());
		when(client.getObject(any(GetObjectRequest.class))).thenAnswer(invocation ->
				new ResponseInputStream<>(GetObjectResponse.builder().build(), new ByteArrayInputStream(bytes)));
		ObjectSnapshot source = storage.inspect(staging);
		storage.copyVerified(source, durable);
		ArgumentCaptor<CopyObjectRequest> copy = ArgumentCaptor.forClass(CopyObjectRequest.class);
		verify(client).copyObject(copy.capture());
		assertThat(copy.getValue().sourceKey()).isEqualTo(staging);
		assertThat(copy.getValue().destinationKey()).isEqualTo(durable);
		assertThat(copy.getValue().copySourceIfMatch()).isEqualTo("\"source-etag\"");
		assertThat(copy.getValue().sourceVersionId()).isEqualTo("source-version");
		assertThat(storage.verifyFinal(durable, MessageDigest.getInstance("SHA-256").digest(bytes))
				.bytes()).isEqualTo(bytes);
		assertThatThrownBy(() -> storage.verifyFinal(durable, new byte[32]))
				.isInstanceOf(InvalidProductImageException.class);
		assertThatThrownBy(() -> storage.copyVerified(source, storage.newDurableKey(UUID.randomUUID())))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsOversizedHeadBeforeDownloadingAndFailsClosedOnCopyErrors() {
		S3Client client = mock(S3Client.class);
		S3ProductImageStorage storage = storage(client);
		String key = storage.newStagingKey(UUID.randomUUID());
		when(client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder()
				.contentLength(10_485_761L).eTag("etag").build());
		assertThatThrownBy(() -> storage.inspect(key)).isInstanceOf(InvalidProductImageException.class);
		ObjectSnapshot snapshot = new ObjectSnapshot(key, "etag", null, "image/png",
				new byte[] {1}, new byte[32]);
		when(client.copyObject(any(CopyObjectRequest.class))).thenThrow(new RuntimeException("S3 failed"));
		assertThatThrownBy(() -> storage.copyVerified(snapshot,
				"final/" + key.split("/")[1] + "/attempt"))
				.isInstanceOf(CatalogStorageUnavailableException.class);
	}

	@Test
	void paginatesOnlyUploadScopedKeysAndRejectsCrossUploadDeletion() {
		S3Client client = mock(S3Client.class);
		S3ProductImageStorage storage = storage(client);
		UUID uploadId = UUID.randomUUID();
		String staging = "staging/" + uploadId + "/old";
		String durable = "final/" + uploadId + "/old";
		when(client.listObjectsV2(any(ListObjectsV2Request.class))).thenAnswer(invocation -> {
			ListObjectsV2Request request = invocation.getArgument(0);
			if (request.prefix().startsWith("staging") && request.continuationToken() == null) {
				return ListObjectsV2Response.builder().contents(S3Object.builder().key(staging).build())
						.nextContinuationToken("next").build();
			}
			if (request.prefix().startsWith("staging")) {
				return ListObjectsV2Response.builder().contents(S3Object.builder()
						.key("staging/" + UUID.randomUUID() + "/other").build()).build();
			}
			return ListObjectsV2Response.builder().contents(S3Object.builder().key(durable).build()).build();
		});
		assertThat(storage.listUploadKeys(uploadId)).containsExactly(staging, durable);
		storage.deleteUploadKey(uploadId, staging);
		verify(client).deleteObject(any(DeleteObjectRequest.class));
		assertThatThrownBy(() -> storage.deleteUploadKey(UUID.randomUUID(), durable))
				.isInstanceOf(IllegalArgumentException.class);
	}

	private S3ProductImageStorage storage(S3Client client) {
		return new S3ProductImageStorage("catalog-images", "us-east-1", "http://localhost:4566",
				StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")), client);
	}
}
