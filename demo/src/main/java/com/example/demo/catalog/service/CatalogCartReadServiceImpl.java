package com.example.demo.catalog.service;

import com.example.demo.catalog.dto.response.CartVariantState;
import com.example.demo.catalog.entity.ProductStatus;
import com.example.demo.catalog.exception.CatalogStorageUnavailableException;
import com.example.demo.catalog.repository.CartVariantView;
import com.example.demo.catalog.repository.ProductImageRepository;
import com.example.demo.catalog.repository.ProductRepository;
import com.example.demo.catalog.repository.ProductVariantRepository;
import com.example.demo.storage.service.PreparedImageUrls;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CatalogCartReadServiceImpl implements CatalogCartReadService {
    private static final int BATCH_SIZE = 100;
    private final ProductVariantRepository variants;
    private final ProductRepository products;
    private final ProductImageRepository images;

    @Override @Transactional(readOnly = true)
    public Map<UUID, CartVariantState> getVariantStates(Collection<UUID> requested) {
        Map<UUID, CartVariantState> result = new HashMap<>();
        List<UUID> ids = requested.stream().distinct().sorted().toList();
        for (List<UUID> chunk : chunks(ids)) {
            for (CartVariantView row : variants.findCartStates(chunk)) {
                boolean published = row.getStatus() == ProductStatus.PUBLISHED;
                result.put(row.getVariantId(), new CartVariantState(row.getVariantId(), row.getProductId(),
                        published, validAmount(row.getPrice()), row.getProductName(), row.getSku(),
                        published ? row.getOptions() : Map.of(), null));
            }
        }
        return Map.copyOf(result);
    }

    @Override @Transactional(readOnly = true)
    public boolean hasImageCandidates(Collection<UUID> variantIds) {
        List<UUID> ids = getVariantStates(variantIds).values().stream().filter(CartVariantState::isPublished)
                .map(CartVariantState::getProductId).distinct().sorted().toList();
        return !imageKeys(ids).isEmpty();
    }

    @Override @Transactional
    public Map<UUID, CartVariantState> getCartPresentation(Collection<UUID> variantIds, PreparedImageUrls urls) {
        Map<UUID, CartVariantState> states = new HashMap<>(getVariantStates(variantIds));
        List<UUID> ids = states.values().stream().filter(CartVariantState::isPublished)
                .map(CartVariantState::getProductId).distinct().sorted().toList();
        Map<UUID, String> keys = imageKeys(ids);
        Map<UUID, Boolean> publication = new HashMap<>();
        // PostgreSQL UUID order is unsigned byte order, unlike UUID.compareTo's signed longs.
        for (List<UUID> chunk : chunks(keys.keySet().stream().sorted(Comparator.comparing(UUID::toString)).toList())) {
            for (Object[] row : products.lockStatusesForShare(chunk)) {
                publication.put((UUID) row[0], "PUBLISHED".equals(row[1]));
            }
        }
        // Re-read gallery after publication locks; never sign stale hidden gallery data.
        Map<UUID, String> currentKeys = imageKeys(publication.entrySet().stream()
                .filter(Map.Entry::getValue).map(Map.Entry::getKey).sorted().toList());
        Map<UUID, String> signed = new HashMap<>();
        if (urls != null) {
            for (Map.Entry<UUID, String> key : currentKeys.entrySet()) {
                try { signed.put(key.getKey(), urls.resolve(key.getValue())); }
                catch (RuntimeException failure) { throw new CatalogStorageUnavailableException(); }
            }
        }
        states.replaceAll((id, state) -> new CartVariantState(id, state.getProductId(),
                state.isPublished() && publication.getOrDefault(state.getProductId(), !keys.containsKey(state.getProductId())),
                state.getAmount(), state.getProductName(), state.getSku(), state.getOptions(), signed.get(state.getProductId())));
        return Map.copyOf(states);
    }

    private Map<UUID, String> imageKeys(List<UUID> productIds) {
        Map<UUID, String> result = new HashMap<>();
        for (List<UUID> chunk : chunks(productIds)) {
            for (Object[] row : images.findCartPrimaryImages(chunk)) result.put((UUID) row[0], (String) row[1]);
        }
        return result;
    }

    private List<List<UUID>> chunks(List<UUID> ids) {
        List<List<UUID>> result = new ArrayList<>();
        for (int start = 0; start < ids.size(); start += BATCH_SIZE) {
            result.add(ids.subList(start, Math.min(start + BATCH_SIZE, ids.size())));
        }
        return result;
    }

    private BigInteger validAmount(BigDecimal price) {
        if (price == null || price.signum() < 0) return null;
        try { return price.toBigIntegerExact(); }
        catch (ArithmeticException invalidPrice) { return null; }
    }
}
