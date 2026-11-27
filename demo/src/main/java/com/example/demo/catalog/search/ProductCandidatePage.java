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
        BigInteger originalAmount;
        BigInteger directDiscount;
        java.math.BigDecimal salePercentage;
        java.time.Instant startsAt;
        java.time.Instant endsAt;
        java.time.Instant pricedAt;
        UUID maximumVariantId;
        BigInteger maximumAmount;
        BigInteger maximumOriginalAmount;
        BigInteger maximumDiscount;

        public Candidate(UUID productId, UUID representativeVariantId, BigInteger displayAmount) {
            this(productId, representativeVariantId, displayAmount, displayAmount,
                    displayAmount == null ? null : BigInteger.ZERO, null, null, null, null,
                    representativeVariantId, displayAmount, displayAmount, displayAmount == null ? null : BigInteger.ZERO);
        }

        public Candidate(UUID productId, UUID representativeVariantId, BigInteger displayAmount,
                BigInteger originalAmount, BigInteger directDiscount, java.math.BigDecimal salePercentage,
                java.time.Instant startsAt, java.time.Instant endsAt, java.time.Instant pricedAt,
                UUID maximumVariantId, BigInteger maximumAmount, BigInteger maximumOriginalAmount, BigInteger maximumDiscount) {
            this.productId = Objects.requireNonNull(productId);
            if ((representativeVariantId == null) != (displayAmount == null)) {
                throw new IllegalArgumentException("Candidate variant and amount must be paired");
            }
            this.representativeVariantId = representativeVariantId;
            this.displayAmount = displayAmount;
            this.originalAmount = originalAmount;
            this.directDiscount = directDiscount;
            this.salePercentage = salePercentage;
            this.startsAt = startsAt;
            this.endsAt = endsAt;
            this.pricedAt = pricedAt;
            this.maximumVariantId = maximumVariantId;
            this.maximumAmount = maximumAmount;
            this.maximumOriginalAmount = maximumOriginalAmount;
            this.maximumDiscount = maximumDiscount;
        }
    }
}
