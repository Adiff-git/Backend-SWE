package com.example.demo.catalog.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CatalogPricingRepository {
    List<CatalogPricingRow> findPricingForProducts(Collection<UUID> ids, Instant pricingInstant);
    java.util.List<CatalogDetailVariantRow> findProductPricedVariants(java.util.UUID product,java.time.Instant at);
    List<CatalogPricingRow> findPricedVariants(Collection<UUID> ids, Instant pricingInstant);
}
