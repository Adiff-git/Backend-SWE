package com.example.demo.catalog.service;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface CatalogOrderReadService {
    Map<UUID, String> getPrimaryImageUrls(Collection<UUID> productIds);
}
