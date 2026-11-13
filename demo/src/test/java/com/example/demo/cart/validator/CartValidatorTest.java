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
    CartValidator validator = new CartValidator();

    @ParameterizedTest @NullSource @ValueSource(ints = {-1, 0, 1001, Integer.MAX_VALUE})
    void quantity_shouldRejectInvalidInput(Integer quantity) {
        assertThatThrownBy(() -> validator.quantity(quantity)).isInstanceOf(InvalidCartRequestException.class);
    }

    @Test void addedQuantity_shouldCheckResultWithoutWrappingOrCapping() {
        assertThat(validator.addedQuantity(999, 1)).isEqualTo(1000);
        assertThatThrownBy(() -> validator.addedQuantity(1000, 1)).isInstanceOf(CartQuantityLimitException.class);
    }

    @Test void sameProduct_shouldRejectCrossProductSwitch() {
        assertThatThrownBy(() -> validator.sameProduct(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(CartVariantSwitchException.class);
    }

    @Test void selectable_shouldRejectAlreadySelectedUnavailableItem() {
        CartVariantState hidden = new CartVariantState(UUID.randomUUID(), UUID.randomUUID(), false,
                BigInteger.TEN, null, null, Map.of(), null);
        assertThatThrownBy(() -> validator.selectable(hidden)).isInstanceOf(CartItemUnavailableException.class);
    }
}
