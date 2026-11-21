package com.example.demo.inventory.repository;

import java.time.Instant;
import java.util.UUID;

public interface InventoryMutationRepository {
    int reserveUnits(long balanceId, int quantity);
    int releaseUnits(long balanceId, int quantity);
    int consumeUnits(long balanceId, int quantity);
    int insertReservation(long orderId, long itemId, long balanceId, UUID variantId, int quantity, Instant reservedAt);
    int resolveReservation(long reservationId, String state, Instant resolvedAt);
}
