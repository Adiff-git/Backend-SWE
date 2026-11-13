package com.example.demo.catalog.repository;

import com.example.demo.catalog.search.ProductSearchNormalizer;
import java.util.Map;
import java.util.Objects;
import lombok.Value;

/**
 * Trusted Catalog SPI for T023/T030, not an active registry or public SQL interface.
 * A future approval-backed bean may supply a predicate over fixed p/v aliases for ONE variant.
 * SQL must come from trusted implementation constants; all operands use namespaced bindings.
 * The current legacy baseline never invokes this interface or searches technical facts.
 */
public interface SpecificationKeywordContributor {
    Predicate forToken(ProductSearchNormalizer.Token token, String parameterNamespace);

    @Value
    class Predicate {
        String sql;
        Map<String, Object> bindings;

        public Predicate(String sql, Map<String, Object> bindings) {
            this.sql = Objects.requireNonNull(sql);
            this.bindings = Map.copyOf(bindings);
        }
    }
}
