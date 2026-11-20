package com.example.demo.order.dto.response;

import com.example.demo.order.entity.OrderStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Value;

@Value
public class OrderInventorySeed {
    boolean managed;
    Long id;
    Long ownerId;
    OrderStatus status;
    Instant placedAt;
    List<Item> items;

    public OrderInventorySeed(boolean managed, Long id, Long ownerId, OrderStatus status, Instant placedAt, List<Item> items) {
        this.managed = managed;
        this.id = id;
        this.ownerId = ownerId;
        this.status = status;
        this.placedAt = placedAt;
        this.items = List.copyOf(items);
    }

    @Value public static class Item {
        Long id;
        UUID variantId;
        int quantity;
    }
}
