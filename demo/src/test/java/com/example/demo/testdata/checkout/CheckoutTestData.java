package com.example.demo.testdata.checkout;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Fresh values only; no persistence or shared mutable fixture state. */
public final class CheckoutTestData {
    public static final String ADDRESS_ONE_FINGERPRINT = "925cd8f54f806213cc0244d86cde3a09d00f0964d51503fbbb626975737bfd1e";
    private CheckoutTestData() { }

    public static Map<String, Object> receipt(Long orderId, Long itemId, Instant placedAt) {
        return receipt(orderId,itemId,placedAt,100);
    }

    public static Map<String,Object> receipt(Long orderId,Long itemId,Instant placedAt,int historicalAmount){
        Map<String, Object> money = Map.of("amount", historicalAmount, "currency", "VND");
        Map<String, Object> item = Map.of("id", itemId, "productNameSnapshot", "Product",
                "variantSkuSnapshot", "SKU", "variantOptionsSnapshot", Map.of(), "quantity", 1,
                "unitPriceSnapshot", money, "lineTotal", money);
        return Map.of("order", Map.of("id", orderId, "orderNumber", "TEST-" + orderId,
                "placedAt", placedAt.toString(), "status", "PENDING", "recipientNameSnapshot", "Recipient",
                "recipientPhoneSnapshot", "0123456789", "deliveryAddressSnapshot", "Address",
                "items", List.of(item), "merchandiseTotal", money, "payableTotal", money),
                "skippedItems", List.of());
    }
}
