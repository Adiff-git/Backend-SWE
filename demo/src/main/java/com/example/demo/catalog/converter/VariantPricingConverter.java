package com.example.demo.catalog.converter;

import com.example.demo.catalog.dto.response.SellingPrice;
import com.example.demo.catalog.dto.response.VariantPricing;
import com.example.demo.catalog.repository.CatalogPricingRow;
import org.springframework.stereotype.Component;
import com.example.demo.catalog.search.ProductCandidatePage.Candidate;
import com.example.demo.catalog.dto.response.ProductPriceRange;

@Component
public class VariantPricingConverter {
    private java.math.BigDecimal percentage(java.math.BigDecimal value) {
        return value==null ? null : value.stripTrailingZeros().setScale(Math.max(0,value.stripTrailingZeros().scale()));
    }
    public VariantPricing toPricing(Candidate candidate) {
        if (candidate.getDisplayAmount() == null || candidate.getPricedAt() == null) return null;
        return new VariantPricing(new SellingPrice(candidate.getOriginalAmount(), "VND"),
                new SellingPrice(candidate.getDisplayAmount(), "VND"), new SellingPrice(candidate.getDirectDiscount(), "VND"),
                percentage(candidate.getSalePercentage()), candidate.getPricedAt(), candidate.getStartsAt(), candidate.getEndsAt());
    }

    public ProductPriceRange toRange(Candidate candidate) {
        VariantPricing minimum = toPricing(candidate);
        if (minimum == null) return new ProductPriceRange(null, null);
        VariantPricing maximum = candidate.getMaximumAmount() == null ? null : new VariantPricing(
                new SellingPrice(candidate.getMaximumOriginalAmount(), "VND"), new SellingPrice(candidate.getMaximumAmount(), "VND"),
                new SellingPrice(candidate.getMaximumDiscount(), "VND"), percentage(candidate.getSalePercentage()), candidate.getPricedAt(),
                candidate.getStartsAt(), candidate.getEndsAt());
        return new ProductPriceRange(new ProductPriceRange.Endpoint(candidate.getRepresentativeVariantId(), minimum),
                maximum == null ? null : new ProductPriceRange.Endpoint(candidate.getMaximumVariantId(), maximum));
    }
    public VariantPricing toPricing(CatalogPricingRow row) {
        if (row == null || row.effective() == null) return null;
        return new VariantPricing(new SellingPrice(row.original().toBigIntegerExact(), "VND"),
                new SellingPrice(row.effective().toBigIntegerExact(), "VND"),
                new SellingPrice(row.discount().toBigIntegerExact(), "VND"), percentage(row.percentage()), row.pricedAt(), row.startsAt(), row.endsAt());
    }
}
