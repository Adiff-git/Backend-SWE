package com.example.demo.behavior;
import java.time.Instant;
import java.util.*;
public record BehaviorOverview(int days, Instant from, Instant to, Map<String,Long> counts,
        List<ProductCount> mostViewedProducts, List<ProductCount> mostClickedProducts,
        List<SearchCount> popularSearchTerms) {
    public record ProductCount(UUID productId, long count) { }
    public record SearchCount(String query, long count) { }
}
