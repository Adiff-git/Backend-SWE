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
    boolean enabled;
    BigInteger amount;
    String productName;
    String sku;
    Map<String, String> options;
    String primaryImageUrl;
    com.example.demo.inventory.dto.response.InventoryAvailability availability;

    public CartVariantState(UUID variantId, UUID productId, boolean published, BigInteger amount,
            String productName, String sku, Map<String, String> options, String primaryImageUrl,
            com.example.demo.inventory.dto.response.InventoryAvailability availability) {
        this(variantId, productId, published, true, amount, productName, sku, options, primaryImageUrl, availability);
    }

    public CartVariantState(UUID variantId, UUID productId, boolean published, boolean enabled, BigInteger amount,
            String productName, String sku, Map<String, String> options, String primaryImageUrl,
            com.example.demo.inventory.dto.response.InventoryAvailability availability) {
        boolean visible = published && enabled;
        this.variantId = variantId;
        this.productId = productId;
        this.published = published;
        this.enabled = enabled;
        this.amount = amount;
        this.productName = visible ? productName : null;
        this.sku = visible ? sku : null;
        this.options = visible ? Map.copyOf(options) : Map.of();
        this.primaryImageUrl = visible ? primaryImageUrl : null;
        this.availability = visible ? availability : null;
    }

    public boolean isPurchasable() { return published && enabled && amount != null && amount.signum() > 0; }
}
