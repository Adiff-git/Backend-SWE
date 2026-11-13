package com.example.demo.cart.dto.response;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.Value;

@Value
public class CartReadSeed {
    Long id;
    UUID productId;
    UUID variantId;
    int quantity;
    boolean selected;
    Instant addedAt;
    String publicProductName;
    String publicSku;
    Map<String, String> publicOptions;

    public CartReadSeed(Long id, UUID productId, UUID variantId, int quantity, boolean selected,
            Instant addedAt, String publicProductName, String publicSku, Map<String, String> publicOptions) {
        this.id = id;
        this.productId = productId;
        this.variantId = variantId;
        this.quantity = quantity;
        this.selected = selected;
        this.addedAt = addedAt;
        this.publicProductName = publicProductName;
        this.publicSku = publicSku;
        this.publicOptions = Map.copyOf(publicOptions);
    }
}
