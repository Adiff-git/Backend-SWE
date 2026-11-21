package com.example.demo.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Table(name = "inventory_balances") @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryBalance {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "original_variant_id", nullable = false, updatable = false) private UUID originalVariantId;
    @Column(name = "product_variant_id") private UUID productVariantId;
    @Column(nullable = false) private long stock;
    @Column(nullable = false) private long reserved;
}
