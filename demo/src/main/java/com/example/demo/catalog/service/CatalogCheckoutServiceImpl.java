package com.example.demo.catalog.service;

import com.example.demo.catalog.dto.response.CheckoutVariantState;
import com.example.demo.catalog.exception.CatalogConflictException;
import com.example.demo.catalog.repository.CartVariantView;
import com.example.demo.catalog.repository.ProductRepository;
import com.example.demo.catalog.repository.ProductVariantRepository;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CatalogCheckoutServiceImpl implements CatalogCheckoutService {
    private static final int BATCH_SIZE = 100;
    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final com.example.demo.catalog.converter.VariantPricingConverter pricingConverter;
    private final java.time.Clock clock;

    @Override
    @Transactional
    public Map<UUID, CheckoutVariantState> getCheckoutVariantStates(Collection<UUID> requested) {
        return getCheckoutCatalogView(requested).getStates();
    }

    @Override
    @Transactional
    public com.example.demo.catalog.dto.response.CheckoutCatalogView getCheckoutCatalogView(Collection<UUID> requested) {
        List<UUID> ids = requested.stream().distinct().sorted(Comparator.comparing(UUID::toString)).toList();
        Map<UUID, UUID> candidateParents = new HashMap<>();
        for (List<UUID> batch : batches(ids)) {
            for (Object[] row : variants.findCheckoutParents(batch)) candidateParents.put((UUID)row[0],(UUID)row[1]);
        }
        List<UUID> parents = candidateParents.values().stream().distinct().sorted(Comparator.comparing(UUID::toString)).toList();
        for (List<UUID> batch : batches(parents)) products.lockStatusesForShare(batch);
        Set<UUID> parentSet = Set.copyOf(parents);
        List<UUID> candidates = candidateParents.keySet().stream().sorted(Comparator.comparing(UUID::toString)).toList();
        for (List<UUID> batch : batches(candidates)) variants.lockCheckoutVariants(batch);
        java.time.Instant at = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        Map<UUID, CheckoutVariantState> result = new HashMap<>();
        for (com.example.demo.catalog.repository.CatalogPricingRow row : variants.findPricedVariants(candidates, at)) {
            if (!parentSet.contains(row.productId()) || !row.productId().equals(candidateParents.get(row.variantId()))) {
                throw new CatalogConflictException();
            }
            result.put(row.variantId(), new CheckoutVariantState(row.variantId(), row.productId(), row.status(),
                    row.effective(), row.productName(), row.sku(), row.options(), pricingConverter.toPricing(row)));
        }
        return new com.example.demo.catalog.dto.response.CheckoutCatalogView(at, result);
    }

    private List<List<UUID>> batches(List<UUID> ids) {
        return java.util.stream.IntStream.iterate(0,i -> i < ids.size(),i -> i+BATCH_SIZE)
                .mapToObj(i -> ids.subList(i,Math.min(i+BATCH_SIZE,ids.size()))).toList();
    }
}
