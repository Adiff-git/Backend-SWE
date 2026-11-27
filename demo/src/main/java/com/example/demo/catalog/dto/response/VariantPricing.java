package com.example.demo.catalog.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.Value;

/** Prepared authoritative database projection; never recalculates the direct sale. */
@Value
public class VariantPricing {
    SellingPrice originalPrice;
    SellingPrice effectivePrice;
    SellingPrice directUnitDiscount;
    BigDecimal salePercentage;
    Instant pricedAt;
    @com.fasterxml.jackson.annotation.JsonIgnore
    Instant saleStartsAt;
    @com.fasterxml.jackson.annotation.JsonIgnore
    Instant saleEndsAt;

    public VariantPricing(SellingPrice originalPrice, SellingPrice effectivePrice, SellingPrice directUnitDiscount,
            BigDecimal salePercentage, Instant pricedAt) {
        this(originalPrice, effectivePrice, directUnitDiscount, salePercentage, pricedAt, null, null);
    }

    public VariantPricing(SellingPrice originalPrice, SellingPrice effectivePrice, SellingPrice directUnitDiscount,
            BigDecimal salePercentage, Instant pricedAt, Instant saleStartsAt, Instant saleEndsAt) {
        this.originalPrice = originalPrice;
        this.effectivePrice = effectivePrice;
        this.directUnitDiscount = directUnitDiscount;
        this.salePercentage = salePercentage;
        this.pricedAt = pricedAt;
        this.saleStartsAt = saleStartsAt;
        this.saleEndsAt = saleEndsAt;
    }
}
