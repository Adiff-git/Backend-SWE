package com.example.demo.catalog.search;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.Value;

/** ID-first projections only; relation loading, publication rechecks and DTO mapping remain upstream. */
@Value
public class ProductCandidatePage {
    List<Candidate> items;
    long totalElements;

    public ProductCandidatePage(List<Candidate> items, long totalElements) {
        this.items = List.copyOf(items);
        this.totalElements = totalElements;
    }

    @Value
    public static class Candidate {
        UUID productId;
        UUID representativeVariantId;
        BigInteger displayAmount;

        public Candidate(UUID productId, UUID representativeVariantId, BigInteger displayAmount) {
            this.productId = Objects.requireNonNull(productId);
            if ((representativeVariantId == null) != (displayAmount == null)) {
                throw new IllegalArgumentException("Candidate variant and amount must be paired");
            }
            this.representativeVariantId = representativeVariantId;
            this.displayAmount = displayAmount;
        }
    }
}
