package com.example.demo.cart.validator;

import com.example.demo.cart.exception.CartItemUnavailableException;
import com.example.demo.cart.exception.CartQuantityLimitException;
import com.example.demo.cart.exception.CartVariantSwitchException;
import com.example.demo.cart.exception.InvalidCartRequestException;
import com.example.demo.catalog.dto.response.CartVariantState;
import java.math.BigInteger;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CartValidatorTest {
    @Test void stockEligibility_shouldUseFullResultAndPreserveCatalogReasonPrecedence() {
        CartVariantState state = new CartVariantState(UUID.randomUUID(), UUID.randomUUID(), true,
                BigInteger.TEN, "P", "S", Map.of(), null,
                new com.example.demo.inventory.dto.response.InventoryAvailability(3, "IN_STOCK"));
        validator.stockEligible(state, 3);
        assertThatThrownBy(() -> validator.stockEligible(state, 4)).isInstanceOf(com.example.demo.cart.exception.CartStockConflictException.class);
        assertThat(validator.ineligibleReason(state, 4)).isEqualTo("INSUFFICIENT_STOCK");
        assertThat(validator.ineligibleReason(state, 3)).isNull();
        assertThat(validator.ineligibleReason(null, 4)).isEqualTo("PRODUCT_UNAVAILABLE");
        CartVariantState invalidPrice = new CartVariantState(state.getVariantId(), state.getProductId(), true,
                null, "P", "S", Map.of(), null, new com.example.demo.inventory.dto.response.InventoryAvailability(0, "INVENTORY_UNAVAILABLE"));
        assertThat(validator.ineligibleReason(invalidPrice, 1)).isEqualTo("PRICE_UNAVAILABLE");
    }
    CartValidator validator = new CartValidator();

    @ParameterizedTest @NullSource @ValueSource(ints = {-1, 0, 1000, 1001, Integer.MAX_VALUE})
    void quantity_shouldRejectInvalidInput(Integer quantity) {
        assertThatThrownBy(() -> validator.quantity(quantity)).isInstanceOf(InvalidCartRequestException.class);
    }

    @Test void addedQuantity_shouldCheckResultWithoutWrappingOrCapping() {
        assertThat(validator.addedQuantity(998, 1)).isEqualTo(999);
        assertThatThrownBy(() -> validator.addedQuantity(998, 2)).isInstanceOf(CartQuantityLimitException.class);
    }

    @Test void sameProduct_shouldRejectCrossProductSwitch() {
        assertThatThrownBy(() -> validator.sameProduct(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(CartVariantSwitchException.class);
    }

    @Test void selectable_shouldRejectAlreadySelectedUnavailableItem() {
        CartVariantState hidden = new CartVariantState(UUID.randomUUID(), UUID.randomUUID(), false,
                BigInteger.TEN, null, null, Map.of(), null, new com.example.demo.inventory.dto.response.InventoryAvailability(999, "IN_STOCK"));
        assertThatThrownBy(() -> validator.selectable(hidden)).isInstanceOf(CartItemUnavailableException.class);
    }
}
