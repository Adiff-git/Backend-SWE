package com.example.demo.storage.service.impl;

import com.example.demo.storage.config.S3StorageProperties;
import com.example.demo.storage.exception.StorageUnavailableException;
import com.example.demo.storage.service.ImageUrlResolver;
import com.example.demo.storage.service.ObjectStorageService;
import com.example.demo.storage.service.PreparedDownloadSigner;
import com.example.demo.storage.service.PreparedImageUrls;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Replace this distribution policy to support a CDN without changing domain records. */
@Service
@RequiredArgsConstructor
public class PresignedImageUrlResolver implements ImageUrlResolver {
    private final ObjectStorageService storage;
    private final S3StorageProperties properties;

    @Override
    public String resolve(String objectKey) {
        requireKey(objectKey);
        return storage.prepareDownload(objectKey, properties.getDownloadUrlLifetime());
    }

    @Override
    public PreparedImageUrls prepare() {
        PreparedDownloadSigner signer = storage.prepareDownloadSigner(properties.getDownloadUrlLifetime());
        return objectKey -> {
            requireKey(objectKey);
            return signer.sign(objectKey);
        };
    }

    private static void requireKey(String key) {
        if (key == null || key.isBlank()) {
            throw new StorageUnavailableException();
        }
    }
}
