package com.example.demo.order.dto.response;

import com.example.demo.order.entity.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Value;

/** Fully materialized values, never persistence proxies. */
public final class OrderReadSeed {
    private OrderReadSeed() { }

    @Value
    public static class Header {
        Long id;
        String orderNumber;
        Instant placedAt;
        OrderStatus status;
        BigDecimal payableTotal;
        BigDecimal merchandiseTotal;
        String recipientNameSnapshot;
        String recipientPhoneSnapshot;
        String deliveryAddressSnapshot;
        Instant cancelledAt;
        Instant completedAt;
        String cancellationReason;
    }

    @Value
    public static class Item {
        Long id;
        Long orderId;
        UUID productId;
        UUID productVariantId;
        String productNameSnapshot;
        String variantSkuSnapshot;
        Map<String, String> variantOptionsSnapshot;
        int quantity;
        BigDecimal unitPriceSnapshot;
        BigDecimal lineTotal;
        long rowCount;

        public Item(Long id, Long orderId, UUID productId, UUID productVariantId, String name, String sku,
                Map<String, String> options, int quantity, BigDecimal price, BigDecimal lineTotal, long rowCount) {
            this.id = id; this.orderId = orderId; this.productId = productId; this.productVariantId = productVariantId;
            this.productNameSnapshot = name; this.variantSkuSnapshot = sku; this.variantOptionsSnapshot = Map.copyOf(options);
            this.quantity = quantity; this.unitPriceSnapshot = price; this.lineTotal = lineTotal; this.rowCount = rowCount;
        }
    }

    @Value
    public static class Page {
        List<Header> headers;
        List<Item> previews;
        int page;
        int size;
        long totalItems;
        public Page(List<Header> headers, List<Item> previews, int page, int size, long totalItems) {
            this.headers = List.copyOf(headers); this.previews = List.copyOf(previews);
            this.page = page; this.size = size; this.totalItems = totalItems;
        }
    }

    @Value
    public static class Detail {
        Header header;
        List<Item> items;
        public Detail(Header header, List<Item> items) { this.header = header; this.items = List.copyOf(items); }
    }
}
