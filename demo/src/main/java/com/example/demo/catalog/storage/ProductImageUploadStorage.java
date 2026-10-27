package com.example.demo.catalog.storage;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** S3 operations only; services decide authorization, transactions and publication. */
public interface ProductImageUploadStorage {

	String newStagingKey(UUID uploadId);

	String newDurableKey(UUID uploadId);

	PreparedPut preparePut(String stagingKey, String contentType, long byteSize, Instant sessionExpiresAt);

	ObjectSnapshot inspect(String objectKey);

	void copyVerified(ObjectSnapshot source, String durableKey);

	ObjectSnapshot verifyFinal(String durableKey, byte[] expectedSha256);

	List<String> listUploadKeys(UUID uploadId);

	/** Delete one scoped key only after the recovery service has checked committed image references. */
	void deleteUploadKey(UUID uploadId, String objectKey);

	record PreparedPut(String url, Map<String, String> requiredHeaders, Instant expiresAt) {
	}

	record ObjectSnapshot(String key, String eTag, String versionId, String contentType,
			byte[] bytes, byte[] sha256) {
		public ObjectSnapshot {
			bytes = bytes.clone();
			sha256 = sha256.clone();
		}

		@Override
		public byte[] bytes() {
			return bytes.clone();
		}

		@Override
		public byte[] sha256() {
			return sha256.clone();
		}
	}
}
