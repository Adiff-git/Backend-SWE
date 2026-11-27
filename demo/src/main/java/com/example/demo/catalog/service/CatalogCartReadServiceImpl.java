package com.example.demo.catalog.service;

import com.example.demo.catalog.dto.response.CartVariantState;
import com.example.demo.catalog.entity.ProductStatus;
import com.example.demo.catalog.exception.CatalogStorageUnavailableException;
import com.example.demo.catalog.repository.ProductImageRepository;
import com.example.demo.catalog.repository.ProductRepository;
import com.example.demo.catalog.repository.ProductVariantRepository;
import com.example.demo.storage.service.PreparedImageUrls;
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
@RequiredArgsConstructor(onConstructor_ = @org.springframework.beans.factory.annotation.Autowired)
public class CatalogCartReadServiceImpl implements CatalogCartReadService {
    private static final int BATCH_SIZE = 100;
    private final ProductVariantRepository variants;
    private final ProductRepository products;
    private final ProductImageRepository images;
    private final com.example.demo.inventory.service.InventoryService inventory;

    private final java.time.Clock clock;
    private final com.example.demo.catalog.converter.VariantPricingConverter pricingConverter;
    public CatalogCartReadServiceImpl(ProductVariantRepository variants, ProductRepository products,
            ProductImageRepository images, com.example.demo.inventory.service.InventoryService inventory) {
        this(variants,products,images,inventory,java.time.Clock.systemUTC(),new com.example.demo.catalog.converter.VariantPricingConverter());
    }

    @Override @Transactional(readOnly = true)
    public Map<UUID, CartVariantState> getVariantStates(Collection<UUID> requested) {
        return catalogStates(requested);
    }

    @Override @Transactional(readOnly = true)
    public com.example.demo.catalog.dto.response.PreparedOperationPricing preparePricing(Collection<UUID> requested, java.time.Instant at) {
        return preparePrices(requested,at);
    }

    @Override @Transactional(readOnly=true)
    public Map<UUID,CartVariantState> getVariantStates(com.example.demo.catalog.dto.response.PreparedOperationPricing pricing) {
        return withAvailability(pricing.getStates());
    }

    private com.example.demo.catalog.dto.response.PreparedOperationPricing preparePrices(Collection<UUID> requested, java.time.Instant at) {
        Map<UUID, CartVariantState> states = new HashMap<>();
        var converter = pricingConverter;
        if (!requested.isEmpty()) for (var row : variants.findPricedVariants(requested, at)) {
            var price = converter.toPricing(row);
            states.put(row.variantId(), new CartVariantState(row.variantId(), row.productId(),
                    row.status() == ProductStatus.PUBLISHED, row.enabled(), price == null ? null : price.getEffectivePrice().getAmount(),
                    row.productName(), row.sku(), row.options(), null, null, price));
        }
        return new com.example.demo.catalog.dto.response.PreparedOperationPricing(at, states);
    }

    private Map<UUID, CartVariantState> catalogStates(Collection<UUID> requested) {
        return getVariantStates(preparePricing(requested, clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS)));
    }

    @Override @Transactional(readOnly = true)
    public boolean hasImageCandidates(Collection<UUID> variantIds) {
        // Signer preparation needs only existence, not two complete catalog/image projections.
        // Current state is still reloaded under ordered Product locks before presentation.
        List<UUID> ids = variantIds.stream().distinct().sorted(Comparator.comparing(UUID::toString)).toList();
        for (List<UUID> chunk : chunks(ids)) {
            if (images.hasCartImageCandidates(chunk)) return true;
        }
        return false;
    }

    @Override @Transactional
    public Map<UUID, CartVariantState> getCartPresentation(Collection<UUID> variantIds, PreparedImageUrls urls) {
        return getCartPresentation(preparePrices(variantIds, clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS)), urls);
    }

    @Override @Transactional
    public Map<UUID, CartVariantState> getCartPresentation(com.example.demo.catalog.dto.response.PreparedOperationPricing pricing, PreparedImageUrls urls) {
        // Parent discovery is scalar. Lock every parent, including imageless/unavailable rows,
        // then reload current variant eligibility and prices after potentially slow signer preparation.
        Map<UUID, CartVariantState> discovered = pricing.getStates();
        List<UUID> parents = discovered.values().stream().map(CartVariantState::getProductId)
                .distinct().sorted(Comparator.comparing(UUID::toString)).toList();
        Map<UUID, Boolean> publication = new HashMap<>();
        for (List<UUID> chunk : chunks(parents)) {
            for (Object[] row : products.lockStatusesForShare(chunk)) {
                publication.put((UUID) row[0], "PUBLISHED".equals(row[1]));
            }
        }
        Map<UUID, CartVariantState> states = new HashMap<>(preparePrices(discovered.keySet(), pricing.getPricingInstant()).getStates());
        states.replaceAll((id, state) -> {
            CartVariantState before = discovered.get(id);
            if (before == null || !before.getProductId().equals(state.getProductId())) {
                throw new com.example.demo.catalog.exception.CatalogConflictException();
            }
            return state.withObservation(publication.getOrDefault(state.getProductId(), false), null, null);
        });
        List<UUID> ids = states.values().stream().filter(CartVariantState::isPurchasable)
                .map(CartVariantState::getProductId).distinct().sorted(Comparator.comparing(UUID::toString)).toList();
        Map<UUID, String> signed = new HashMap<>();
        for (Map.Entry<UUID, String> key : imageKeys(ids).entrySet()) {
            if (urls == null) throw new CatalogStorageUnavailableException();
            try { signed.put(key.getKey(), urls.resolve(key.getValue())); }
            catch (RuntimeException failure) { throw new CatalogStorageUnavailableException(); }
        }
        states.replaceAll((id, state) -> state.withObservation(
                publication.getOrDefault(state.getProductId(), false), signed.get(state.getProductId()), null));
        return withAvailability(states);
    }

    private Map<UUID, CartVariantState> withAvailability(Map<UUID, CartVariantState> states) {
        List<UUID> ids = states.values().stream().filter(CartVariantState::isPurchasable).map(CartVariantState::getVariantId)
                .distinct().sorted(Comparator.comparing(UUID::toString)).toList();
        Map<UUID, com.example.demo.inventory.dto.response.InventoryAvailability> availability = ids.isEmpty() ? Map.of() : inventory.getAvailability(ids);
        Map<UUID, CartVariantState> prepared = new HashMap<>();
        states.forEach((id, state) -> prepared.put(id, state.withObservation(true, state.getPrimaryImageUrl(), state.isPurchasable() ? availability.get(id) : null)));
        return Map.copyOf(prepared);
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


}
