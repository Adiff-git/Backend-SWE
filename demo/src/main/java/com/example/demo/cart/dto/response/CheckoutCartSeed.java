package com.example.demo.cart.dto.response;

import java.util.List;
import java.util.UUID;
import lombok.Value;

@Value
public class CheckoutCartSeed {
    Long ownerId;
    Long cartId;
    List<Row> rows;
    Long selectedEntitlementId;
    long selectionRevision;

    public CheckoutCartSeed(Long ownerId, Long cartId, List<Row> rows) {
        this(ownerId, cartId, rows, null, 0);
    }
    public CheckoutCartSeed(Long ownerId, Long cartId, List<Row> rows, Long selectedEntitlementId, long selectionRevision) {
        this.selectedEntitlementId=selectedEntitlementId;
        this.selectionRevision=selectionRevision;
        this.ownerId = ownerId;
        this.cartId = cartId;
        this.rows = List.copyOf(rows);
    }

    @Value
    public static class Row {
        Long id;
        UUID productId;
        UUID variantId;
        int quantity;
        boolean selected;
    }
}
