package com.example.demo.testdata.order;

import com.example.demo.order.entity.Order;
import com.example.demo.order.entity.OrderItem;
import com.example.demo.order.entity.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class OrderTestData {
    public static final Instant PLACED = Instant.parse("2026-01-01T00:00:00Z");
    private OrderTestData() { }

    public static Order.OrderBuilder order(Long owner, OrderStatus status) {
        return Order.builder().customerId(owner).orderNumber("ORDER-" + UUID.randomUUID()).placedAt(PLACED)
                .status(status).recipientNameSnapshot("Historical recipient").recipientPhoneSnapshot("0123456789")
                .deliveryAddressSnapshot("Historical address").merchandiseTotal(BigDecimal.valueOf(100))
                .payableTotal(BigDecimal.valueOf(90))
                .cancelledAt(status == OrderStatus.CANCELLED ? PLACED.plusSeconds(1) : null)
                .completedAt(status == OrderStatus.COMPLETED ? PLACED.plusSeconds(1) : null);
    }

    public static OrderItem.OrderItemBuilder item(Order order) {
        return OrderItem.builder().order(order).productNameSnapshot("Historical product")
                .variantSkuSnapshot("OLD-SKU").variantOptionsSnapshot(Map.of("color", "Blue"))
                .quantity(2).unitPriceSnapshot(BigDecimal.valueOf(50)).lineTotal(BigDecimal.valueOf(100));
    }
}
