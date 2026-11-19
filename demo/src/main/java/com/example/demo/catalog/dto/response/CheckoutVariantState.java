package com.example.demo.catalog.dto.response;

import com.example.demo.catalog.entity.ProductStatus;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import lombok.Value;

@Value
public class CheckoutVariantState {
    UUID variantId;
    UUID productId;
    ProductStatus status;
    BigDecimal price;
    String productName;
    String sku;
    Map<String, String> options;

    public CheckoutVariantState(UUID variantId, UUID productId, ProductStatus status, BigDecimal price,
            String productName, String sku, Map<String, String> options) {
        this.variantId = variantId;
        this.productId = productId;
        this.status = status;
        this.price = price;
        this.productName = productName;
        this.sku = sku;
        this.options = options == null ? Map.of() : Map.copyOf(options);
    }
}
