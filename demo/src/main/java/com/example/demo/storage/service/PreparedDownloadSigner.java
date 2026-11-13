package com.example.demo.storage.service;

/** Captured credentials over the application-managed signer; owns no request resources. */
public interface PreparedDownloadSigner extends AutoCloseable {
    String sign(String key);
    /** Compatibility with existing callers. Application shutdown owns signer cleanup. */
    @Override default void close() { }
}
