package com.example.demo.inventory.converter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryConverterTest {
    private final InventoryConverter converter = new InventoryConverter();

    @Test void availability_shouldDistinguishMissingFromKnownZeroWithoutInventingStock() {
        assertThat(converter.toAvailability(false, 0).getStatus()).isEqualTo("INVENTORY_UNAVAILABLE");
        assertThat(converter.toAvailability(true, 0).getStatus()).isEqualTo("OUT_OF_STOCK");
        assertThat(converter.toAvailability(true, Long.MAX_VALUE).getAvailableQuantity()).isEqualTo(Long.MAX_VALUE);
        assertThat(converter.toAvailability(true, 1).getStatus()).isEqualTo("IN_STOCK");
    }
}
