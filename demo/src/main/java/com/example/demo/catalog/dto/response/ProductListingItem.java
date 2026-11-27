package com.example.demo.catalog.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;
import lombok.Getter;

/** Listing-only additive fields; shared summaries, detail and wishlist retain their contracts. */
@Getter
public final class ProductListingItem extends ProductSummary {
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private final SellingPrice displayPrice;
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private final UUID representativeVariantId;
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private final com.example.demo.inventory.dto.response.InventoryAvailability availability;
    private final VariantPricing pricing;
    private final ProductPriceRange priceRange;

    public ProductListingItem(UUID id, String name, CategorySummary category, String primaryImageUrl,
            SellingPrice displayPrice, UUID representativeVariantId, com.example.demo.inventory.dto.response.InventoryAvailability availability) {
        this(id, name, category, primaryImageUrl, displayPrice, representativeVariantId, availability, null,
                new ProductPriceRange(null, null));
    }

    public ProductListingItem(UUID id, String name, CategorySummary category, String primaryImageUrl,
            SellingPrice displayPrice, UUID representativeVariantId,
            com.example.demo.inventory.dto.response.InventoryAvailability availability,
            VariantPricing pricing, ProductPriceRange priceRange) {
        super(id, name, category, primaryImageUrl);
        if ((displayPrice == null) != (representativeVariantId == null)) {
            throw new IllegalArgumentException("Listing price and representative variant must be paired");
        }
        this.displayPrice = displayPrice;
        this.representativeVariantId = representativeVariantId;
        this.availability = representativeVariantId == null ? null : availability;
        this.pricing = pricing;
        this.priceRange = priceRange;
    }
}
