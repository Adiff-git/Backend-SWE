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

    public CartVariantState(UUID variantId, UUID productId, boolean published, BigInteger amount,
            String productName, String sku, Map<String, String> options, String primaryImageUrl) {
        this.variantId = variantId;
        this.productId = productId;
        this.published = published;
        this.amount = amount;
        this.productName = published ? productName : null;
        this.sku = published ? sku : null;
        this.options = published ? Map.copyOf(options) : Map.of();
        this.primaryImageUrl = published ? primaryImageUrl : null;
    }

    public boolean isPurchasable() { return published && amount != null; }
}
