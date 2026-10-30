package com.example.demo.storage.service;

public interface PreparedDownloadSigner extends AutoCloseable {
    String sign(String key);
    @Override void close();
}
