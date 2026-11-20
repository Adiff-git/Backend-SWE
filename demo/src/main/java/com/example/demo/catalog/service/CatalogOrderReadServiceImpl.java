package com.example.demo.catalog.service;

import com.example.demo.catalog.exception.CatalogStorageUnavailableException;
import com.example.demo.catalog.repository.ProductImageRepository;
import com.example.demo.catalog.repository.ProductRepository;
import com.example.demo.catalog.storage.ProductImageStorage;
import jakarta.annotation.PostConstruct;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class CatalogOrderReadServiceImpl implements CatalogOrderReadService {
    private static final int BATCH_SIZE = 100;
    private final ProductRepository products;
    private final ProductImageRepository images;
    private final ProductImageStorage storage;
    @Value("${order.lock-timeout-ms:5000}")
    private long lockTimeoutMs = 5000;

    @PostConstruct void validateConfiguration() {
        if (lockTimeoutMs <= 0) throw new IllegalArgumentException("Order lock timeout must be positive");
    }

    @Override @Transactional
    public Map<UUID, String> getPrimaryImageUrls(Collection<UUID> requested) {
        List<UUID> ids = requested.stream().filter(Objects::nonNull).distinct()
                .sorted(Comparator.comparing(UUID::toString)).toList();
        if (ids.isEmpty()) return Map.of();
        List<UUID> candidates = keys(ids).keySet().stream().sorted(Comparator.comparing(UUID::toString)).toList();
        if (candidates.isEmpty()) return Map.of();
        ProductImageStorage.PreparedReadSigner signer;
        try { signer = storage.prepareReadSigner(); }
        catch (CatalogStorageUnavailableException unavailable) { return Map.of(); }
        Map<UUID, String> result = new HashMap<>();
        try {
            images.setOrderReadLockTimeout(lockTimeoutMs + "ms");
            Map<UUID, Boolean> visible = new HashMap<>();
            for (int start = 0; start < candidates.size(); start += BATCH_SIZE) {
                for (Object[] row : products.lockStatusesForShare(candidates.subList(start, Math.min(start + BATCH_SIZE, candidates.size())))) {
                    visible.put((UUID) row[0], "PUBLISHED".equals(row[1]));
                }
            }
            List<UUID> published = candidates.stream().filter(id -> visible.getOrDefault(id, false)).toList();
            for (Map.Entry<UUID, String> key : keys(published).entrySet()) {
                try {
                    String url = signer.signReadUrl(key.getValue());
                    if (url != null) result.put(key.getKey(), url);
                } catch (CatalogStorageUnavailableException unavailable) {
                    // Historical orders remain readable without this optional image.
                }
            }
        } finally {
            try { signer.close(); }
            catch (CatalogStorageUnavailableException unavailable) { result.clear(); }
        }
        return Map.copyOf(result);
    }

    private Map<UUID, String> keys(List<UUID> ids) {
        Map<UUID, String> result = new HashMap<>();
        for (int start = 0; start < ids.size(); start += BATCH_SIZE) {
            for (Object[] row : images.findOrderPrimaryImages(ids.subList(start, Math.min(start + BATCH_SIZE, ids.size())))) {
                result.put((UUID) row[0], (String) row[1]);
            }
        }
        return result;
    }
}
