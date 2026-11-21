package com.example.demo.inventory.dto.response;

import java.time.Instant;
import java.util.UUID;
import lombok.Value;

@Value
public class InventoryAllocationSeed {
    Long id;
    Long orderId;
    Long orderItemId;
    Long inventoryBalanceId;
    UUID originalVariantId;
    int quantity;
    String state;
    Instant reservedAt;
    Instant resolvedAt;
}
