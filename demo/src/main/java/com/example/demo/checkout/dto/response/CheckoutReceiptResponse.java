package com.example.demo.checkout.dto.response;

import java.util.List;
import lombok.Value;

@Value
public class CheckoutReceiptResponse {
    CheckoutOrderReceipt order;
    List<SkippedCheckoutItem> skippedItems;

    public CheckoutReceiptResponse(CheckoutOrderReceipt order, List<SkippedCheckoutItem> skippedItems) {
        this.order = order;
        this.skippedItems = List.copyOf(skippedItems);
    }
}
