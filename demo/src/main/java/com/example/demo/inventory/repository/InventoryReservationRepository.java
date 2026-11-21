package com.example.demo.inventory.repository;

import com.example.demo.inventory.entity.InventoryReservation;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface InventoryReservationRepository extends Repository<InventoryReservation, Long> {
    interface AllocationRow {
        Long getId();
        Long getOrderId();
        Long getOrderItemId();
        Long getInventoryBalanceId();
        UUID getOriginalVariantId();
        int getQuantity();
        String getState();
        Instant getReservedAt();
        Instant getResolvedAt();
    }

    String PROJECTION = "SELECT id,order_id AS orderId,order_item_id AS orderItemId,inventory_balance_id AS inventoryBalanceId,original_variant_id AS originalVariantId,quantity,state,reserved_at AS reservedAt,resolved_at AS resolvedAt FROM inventory_reservations WHERE order_id=:orderId ORDER BY original_variant_id";

    @Query(value = PROJECTION, nativeQuery = true)
    List<AllocationRow> findOwnedReservations(Long orderId);

    @Query(value = PROJECTION + " FOR UPDATE", nativeQuery = true)
    List<AllocationRow> lockOwnedReservations(Long orderId);
}
