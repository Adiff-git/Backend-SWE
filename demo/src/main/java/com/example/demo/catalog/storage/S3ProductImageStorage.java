package com.example.demo.catalog.storage;

import com.example.demo.catalog.exception.CatalogStorageUnavailableException;
import com.example.demo.storage.config.S3StorageProperties;
import com.example.demo.storage.service.ObjectStorageService;
import com.example.demo.storage.service.PreparedDownloadSigner;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Catalog adapter to the server's shared private image storage. */
@Component
@RequiredArgsConstructor
public class S3ProductImageStorage implements ProductImageStorage {
    private final ObjectStorageService storage;
    private final S3StorageProperties properties;

    @Override public PreparedReadSigner prepareReadSigner() {
        try {
            PreparedDownloadSigner signer = storage.prepareDownloadSigner(properties.getDownloadUrlLifetime());
            return new PreparedReadSigner() {
                @Override public String signReadUrl(String key) {
                    try { return signer.sign(key); }
                    catch (RuntimeException failure) { throw new CatalogStorageUnavailableException(); }
                }
                @Override public void close() { signer.close(); }
            };
        } catch (RuntimeException failure) { throw new CatalogStorageUnavailableException(); }
    }
}
