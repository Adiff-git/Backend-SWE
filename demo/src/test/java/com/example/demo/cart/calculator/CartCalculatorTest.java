package com.example.demo.cart.calculator;

import com.example.demo.cart.dto.response.CartReadSeed;
import com.example.demo.cart.dto.response.PreparedCart;
import com.example.demo.catalog.dto.response.CartVariantState;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CartCalculatorTest {
    @Test void calculate_shouldExcludeTheWholeShortageRowWithoutChangingStoredSelection() {
        UUID variant = UUID.randomUUID();
        UUID product = UUID.randomUUID();
        CartReadSeed shortage = new CartReadSeed(1L, product, variant, 5, true, Instant.EPOCH, "P", "S", Map.of());
        CartVariantState state = new CartVariantState(variant, product, true, BigInteger.TEN, "P", "S", Map.of(), null,
                new com.example.demo.inventory.dto.response.InventoryAvailability(3, "IN_STOCK"));
        PreparedCart prepared = new CartCalculator().calculate(List.of(shortage), Map.of(variant, state), Map.of(1L, "INSUFFICIENT_STOCK"));
        assertThat(prepared.getSelectedSubtotal()).isZero();
        assertThat(prepared.getSelectedItemCount()).isZero();
        assertThat(prepared.getSelectedTotalQuantity()).isZero();
        assertThat(prepared.getSubtotals()).isEmpty();
        assertThat(prepared.getItems().getFirst().getQuantity()).isEqualTo(5);
        assertThat(prepared.getItems().getFirst().isSelected()).isTrue();
    }
    @Test void calculate_shouldDistinguishRowsUnitsAndUnavailableSelectedItems() {
        UUID product = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        CartReadSeed first = new CartReadSeed(1L, product, a, 2, true, Instant.EPOCH, "P", "A", Map.of());
        CartReadSeed second = new CartReadSeed(2L, product, b, 3, true, Instant.EPOCH, "P", "B", Map.of());
        CartVariantState stateA = new CartVariantState(a, product, true, BigInteger.valueOf(100), "P", "A", Map.of(), null, new com.example.demo.inventory.dto.response.InventoryAvailability(999, "IN_STOCK"));
        CartVariantState stateB = new CartVariantState(b, product, true, BigInteger.valueOf(50), "P", "B", Map.of(), null, new com.example.demo.inventory.dto.response.InventoryAvailability(999, "IN_STOCK"));
        CartCalculator calculator = new CartCalculator();
        PreparedCart all = calculator.calculate(List.of(first, second), Map.of(a, stateA, b, stateB), java.util.Map.of());
        assertThat(all.getSelectedSubtotal()).isEqualTo(350);
        assertThat(all.getSelectedItemCount()).isEqualTo(2);
        assertThat(all.getSelectedTotalQuantity()).isEqualTo(5);
        CartVariantState hidden = new CartVariantState(a, product, false, BigInteger.valueOf(100), null, null, Map.of(), null, new com.example.demo.inventory.dto.response.InventoryAvailability(999, "IN_STOCK"));
        PreparedCart eligible = calculator.calculate(List.of(first, second), Map.of(a, hidden, b, stateB), java.util.Map.of(1L, "PRODUCT_UNAVAILABLE"));
        assertThat(eligible.getSelectedSubtotal()).isEqualTo(150);
        assertThat(eligible.getSubtotals()).doesNotContainKey(1L);
        assertThat(eligible.getItems().getFirst().isSelected()).isTrue();
    }

    @Test void calculate_shouldKeepArbitraryPrecisionAndQuantityBeyondOneRowLimit() {
        UUID variant = UUID.randomUUID();
        UUID product = UUID.randomUUID();
        BigInteger price = BigInteger.TEN.pow(40);
        CartReadSeed row = new CartReadSeed(1L, product, variant, 999, true, Instant.EPOCH, "P", "S", Map.of());
        CartReadSeed second = new CartReadSeed(2L, product, variant, 999, true, Instant.EPOCH, "P", "S", Map.of());
        PreparedCart result = new CartCalculator().calculate(List.of(row, second),
                Map.of(variant, new CartVariantState(variant, product, true, price, "P", "S", Map.of(), null, new com.example.demo.inventory.dto.response.InventoryAvailability(999, "IN_STOCK"))), java.util.Map.of());
        assertThat(result.getSelectedSubtotal()).isEqualTo(price.multiply(BigInteger.valueOf(1998)));
        assertThat(result.getSelectedTotalQuantity()).isEqualTo(1998);
        assertThat(new CartCalculator().calculate(List.of(), Map.of(), java.util.Map.of()).getSelectedSubtotal()).isZero();
    }
}
