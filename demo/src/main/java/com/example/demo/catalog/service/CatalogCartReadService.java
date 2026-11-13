package com.example.demo.catalog.service;

import com.example.demo.catalog.dto.response.CartVariantState;
import com.example.demo.storage.service.PreparedImageUrls;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface CatalogCartReadService {
    Map<UUID, CartVariantState> getVariantStates(Collection<UUID> variantIds);
    boolean hasImageCandidates(Collection<UUID> variantIds);
    Map<UUID, CartVariantState> getCartPresentation(Collection<UUID> variantIds, PreparedImageUrls imageUrls);
}
