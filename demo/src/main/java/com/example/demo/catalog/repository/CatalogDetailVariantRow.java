package com.example.demo.catalog.repository;
import java.util.Map;
/** Detail metadata and prices materialized by the same PostgreSQL statement. */
public record CatalogDetailVariantRow(CatalogPricingRow pricing, Map<String,Object> specifications,
        Map<String,Map<String,String>> canonicalLabels) {
    public CatalogDetailVariantRow {
        specifications=Map.copyOf(specifications);
        canonicalLabels=canonicalLabels.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey,entry -> Map.copyOf(entry.getValue())));
    }
}
