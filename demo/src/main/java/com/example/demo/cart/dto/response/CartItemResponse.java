package com.example.demo.cart.dto.response;

import com.example.demo.catalog.dto.response.SellingPrice;
import java.util.Map;
import java.util.UUID;
import lombok.Value;

@Value
public class CartItemResponse {
    Long id;
    UUID productId;
    UUID variantId;
    String productName;
    String sku;
    Map<String, String> options;
    String primaryImageUrl;
    SellingPrice unitPrice;
    int quantity;
    boolean selected;
    boolean purchasable;
    String availabilityReason;
    SellingPrice subtotal;
}
