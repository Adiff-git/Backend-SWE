package com.example.demo.cart.dto.response;

import com.example.demo.catalog.dto.response.SellingPrice;
import java.util.Map;
import java.util.UUID;
import lombok.Value;

@Value
@lombok.AllArgsConstructor
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
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
    com.example.demo.inventory.dto.response.InventoryAvailability availability;
    com.example.demo.catalog.dto.response.VariantPricing pricing;
    public CartItemResponse(Long id, UUID productId, UUID variantId, String productName, String sku,
            Map<String,String> options, String image, SellingPrice price, int quantity, boolean selected,
            boolean purchasable, String reason, SellingPrice subtotal,
            com.example.demo.inventory.dto.response.InventoryAvailability availability) {
        this(id,productId,variantId,productName,sku,options,image,price,quantity,selected,purchasable,reason,subtotal,availability,null);
    }
}
