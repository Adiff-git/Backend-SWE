package com.example.demo.cart.converter;

import com.example.demo.cart.calculator.CartCalculator;
import com.example.demo.cart.dto.response.CartReadSeed;
import com.example.demo.cart.dto.response.CartResponse;
import com.example.demo.catalog.dto.response.CartVariantState;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CartConverterTest {
    @Test void shortageResponse_shouldKeepValidCurrentUnitPriceAndPreparedReasonWithNullSubtotal() {
        UUID variant = UUID.randomUUID();
        UUID product = UUID.randomUUID();
        CartReadSeed seed = new CartReadSeed(1L, product, variant, 5, true, Instant.EPOCH, "P", "S", Map.of());
        CartVariantState state = new CartVariantState(variant, product, true, BigInteger.TEN, "P", "S", Map.of(), null,
                new com.example.demo.inventory.dto.response.InventoryAvailability(3, "IN_STOCK"));
        var prepared = new CartCalculator().calculate(List.of(seed), Map.of(variant, state), Map.of(1L, "INSUFFICIENT_STOCK"));
        var row = new CartConverter().toResponse(prepared).getItems().getFirst();
        assertThat(row.getUnitPrice().getAmount()).isEqualTo(BigInteger.TEN);
        assertThat(row.getSubtotal()).isNull();
        assertThat(row.isPurchasable()).isFalse();
        assertThat(row.getAvailabilityReason()).isEqualTo("INSUFFICIENT_STOCK");
        assertThat(row.getAvailability().getAvailableQuantity()).isEqualTo(3);
        assertThat(row.isSelected()).isTrue();
    }
    @Test void toResponse_shouldUsePriorPublicSnapshotForHiddenRowsAndNeverChargeThem() {
        UUID variant = UUID.randomUUID();
        UUID product = UUID.randomUUID();
        CartReadSeed seed = new CartReadSeed(1L, product, variant, 2, true, Instant.EPOCH, "Prior name", "Prior SKU", Map.of("size","M"));
        CartVariantState hidden = new CartVariantState(variant, product, false, BigInteger.TEN, "Hidden new name", "Hidden SKU", Map.of("private","value"), "private-url", new com.example.demo.inventory.dto.response.InventoryAvailability(999, "IN_STOCK"));
        CartResponse response = new CartConverter().toResponse(new CartCalculator().calculate(List.of(seed), Map.of(variant,hidden), java.util.Map.of(1L, "PRODUCT_UNAVAILABLE")));
        assertThat(response.getItems().getFirst().getProductName()).isEqualTo("Prior name");
        assertThat(response.getItems().getFirst().getOptions()).containsOnlyKeys("size");
        assertThat(response.getItems().getFirst().getSubtotal()).isNull();
        assertThat(response.getItems().getFirst().getPrimaryImageUrl()).isNull();
        assertThat(response.getItems().getFirst().getUnitPrice()).isNull();
        assertThat(response.getItems().getFirst().getAvailability()).isNull();
        assertThat(response.getFinalTotal().getAmount()).isZero();
    }
}
