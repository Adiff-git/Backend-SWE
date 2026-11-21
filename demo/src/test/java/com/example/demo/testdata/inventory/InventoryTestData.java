package com.example.demo.testdata.inventory;

import java.time.Instant;
import java.util.UUID;

/** Fresh immutable input values; no persistence or implicit positive stock. */
public final class InventoryTestData {
    public static final Instant PLACED = Instant.parse("2026-01-01T00:00:00Z");

    private InventoryTestData() { }

    public static Balance zeroBalance() {
        return new Balance(UUID.randomUUID(), 0, 0);
    }

    public static Balance balance(long stock, long reserved) {
        return new Balance(UUID.randomUUID(), stock, reserved);
    }

    public record Balance(UUID originalVariantId, long stock, long reserved) { }
}
