package com.example.demo.checkout.dto.response;

import lombok.Value;

@Value
public class SkippedCheckoutItem {
    Long cartItemId;
    Reason reason;

    public enum Reason {
        VARIANT_UNAVAILABLE, PRODUCT_UNAVAILABLE, PRODUCT_NOT_FOR_SALE, INVALID_QUANTITY, INVALID_PRICE,
        INVENTORY_UNAVAILABLE, INSUFFICIENT_STOCK
    }
}
