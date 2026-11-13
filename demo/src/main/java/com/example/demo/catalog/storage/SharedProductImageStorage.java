package com.example.demo.catalog.storage;

import com.example.demo.catalog.exception.CatalogStorageUnavailableException;
import com.example.demo.storage.service.ImageUrlResolver;
import com.example.demo.storage.service.PreparedImageUrls;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Compatibility adapter; catalog owns publication checks, storage owns URL distribution. */
@Component
@RequiredArgsConstructor
public class SharedProductImageStorage implements ProductImageStorage {
    private final ImageUrlResolver imageUrls;

    @Override public PreparedReadSigner prepareReadSigner() {
        try {
            PreparedImageUrls urls = imageUrls.prepare();
            return new PreparedReadSigner() {
                @Override public String signReadUrl(String key) {
                    try { return urls.resolve(key); }
                    catch (RuntimeException failure) { throw new CatalogStorageUnavailableException(); }
                }
                @Override public void close() { /* No per-request resources. */ }
            };
        } catch (RuntimeException failure) { throw new CatalogStorageUnavailableException(); }
    }
}
