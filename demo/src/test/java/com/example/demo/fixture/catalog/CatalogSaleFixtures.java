package com.example.demo.fixture.catalog;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Fresh explicit inputs for direct-sale and sparse option acceptance fixtures. */
public final class CatalogSaleFixtures {
    public static final Instant START = Instant.parse("2026-11-26T00:00:00Z");
    public static final Instant END = Instant.parse("2026-11-27T00:00:00Z");

    private CatalogSaleFixtures() {}

    public static List<Map<String, String>> sparseOptions() {
        return List.of(Map.of("Color", "Red", "Size", "M"),
                Map.of("Color", "Blue", "Size", "M"));
    }

    public static List<SaleCase> saleCases() {
        return List.of(new SaleCase("absent", null, false, null, null, START, 1000000),
                new SaleCase("active", new BigDecimal("20"), true, START, END, START, 800000),
                new SaleCase("disabled", new BigDecimal("20"), false, START, END, START, 1000000),
                new SaleCase("future", new BigDecimal("20"), true, START, END, START.minusSeconds(1), 1000000),
                new SaleCase("expired", new BigDecimal("20"), true, START, END, END, 1000000));
    }

    public record SaleCase(String name, BigDecimal percentage, boolean enabled, Instant start,
            Instant end, Instant at, long effectiveAmount) {}
}
