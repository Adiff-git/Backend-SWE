package com.example.demo.catalog.dto.response;

import java.math.BigInteger;
import java.util.Map;
import java.util.UUID;
import lombok.Value;

@Value
public class CartVariantState {
    UUID variantId;
    UUID productId;
    boolean published;
    BigInteger amount;
    String productName;
    String sku;
    Map<String, String> options;
    String primaryImageUrl;
    com.example.demo.inventory.dto.response.InventoryAvailability availability;

    VariantPricing pricing;

    public CartVariantState(UUID variantId, UUID productId, boolean published, BigInteger amount,
            String productName, String sku, Map<String, String> options, String primaryImageUrl,
            com.example.demo.inventory.dto.response.InventoryAvailability availability) {
        this(variantId, productId, published, amount, productName, sku, options, primaryImageUrl, availability, null);
    }

    public CartVariantState(UUID variantId, UUID productId, boolean published, BigInteger amount,
            String productName, String sku, Map<String, String> options, String primaryImageUrl,
            com.example.demo.inventory.dto.response.InventoryAvailability availability, VariantPricing pricing) {
        this.pricing = published ? pricing : null;
        this.variantId = variantId;
        this.productId = productId;
        this.published = published;
        this.amount = amount;
        this.productName = published ? productName : null;
        this.sku = published ? sku : null;
        this.options = published ? Map.copyOf(options) : Map.of();
        this.primaryImageUrl = published ? primaryImageUrl : null;
        this.availability = published ? availability : null;
    }

    public CartVariantState withObservation(boolean visible, String image,
            com.example.demo.inventory.dto.response.InventoryAvailability availability) {
        return new CartVariantState(variantId, productId, published && visible, amount, productName, sku,
                options, image, availability, pricing);
    }

    public boolean isPurchasable() { return published && amount != null; }
}
