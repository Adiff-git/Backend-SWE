package com.example.demo.catalog.repository;

import com.example.demo.catalog.entity.ProductStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Internal scalar projection from one PostgreSQL statement snapshot. */
public record CatalogPricingRow(UUID variantId, UUID productId, ProductStatus status, String productName,
        String sku, Map<String, String> options, BigDecimal original, BigDecimal effective,
        BigDecimal discount, BigDecimal percentage, Instant startsAt, Instant endsAt, Instant pricedAt, boolean enabled) {
    public CatalogPricingRow(UUID variantId, UUID productId, ProductStatus status, String productName,
            String sku, Map<String, String> options, BigDecimal original, BigDecimal effective,
            BigDecimal discount, BigDecimal percentage, Instant startsAt, Instant endsAt, Instant pricedAt) {
        this(variantId, productId, status, productName, sku, options, original, effective, discount,
                percentage, startsAt, endsAt, pricedAt, true);
    }

    public CatalogPricingRow {
        options = Map.copyOf(options);
    }
}
