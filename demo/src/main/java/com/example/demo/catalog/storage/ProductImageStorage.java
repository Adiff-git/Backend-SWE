package com.example.demo.catalog.storage;

/** Read-only signing boundary; upload operations use a separate storage interface. */
public interface ProductImageStorage {

	/** Resolve credentials and initialize signing before a Product row lock is taken. */
	PreparedReadSigner prepareReadSigner();


	interface PreparedReadSigner extends AutoCloseable {
		String signReadUrl(String privateObjectKey);

		@Override
		void close();
	}
}
