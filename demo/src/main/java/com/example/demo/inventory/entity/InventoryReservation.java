package com.example.demo.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Table(name = "inventory_reservations") @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryReservation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "order_id", nullable = false, updatable = false) private Long orderId;
    @Column(name = "order_item_id", nullable = false, updatable = false) private Long orderItemId;
    @Column(name = "inventory_balance_id", nullable = false, updatable = false) private Long inventoryBalanceId;
    @Column(name = "original_variant_id", nullable = false, updatable = false) private UUID originalVariantId;
    @Column(nullable = false, updatable = false) private int quantity;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private ReservationState state;
    @Column(name = "reserved_at", nullable = false, updatable = false) private Instant reservedAt;
    @Column(name = "resolved_at") private Instant resolvedAt;
}
