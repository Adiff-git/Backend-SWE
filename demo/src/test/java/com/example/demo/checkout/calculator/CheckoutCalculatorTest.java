package com.example.demo.checkout.calculator;

import com.example.demo.cart.dto.response.CheckoutCartSeed;
import com.example.demo.catalog.dto.response.CheckoutVariantState;
import com.example.demo.catalog.entity.ProductStatus;
import com.example.demo.checkout.exception.CheckoutAmountLimitException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CheckoutCalculatorTest {
    private final CheckoutCalculator calculator = new CheckoutCalculator();

    @Test void calculate_shouldPreserveAmountsAboveLongWithoutRounding() {
        UUID product = UUID.randomUUID();
        UUID variant = UUID.randomUUID();
        BigInteger price = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);
        CheckoutCartSeed.Row row = new CheckoutCartSeed.Row(1L,product,variant,999,true);
        CheckoutVariantState state = new CheckoutVariantState(variant,product,ProductStatus.PUBLISHED,new BigDecimal(price),"Product","SKU",Map.of());
        assertThat(calculator.calculate(List.of(row),Map.of(variant,state)).getTotal()).isEqualTo(price.multiply(BigInteger.valueOf(999)));
    }

    @Test void validateAmount_shouldAcceptStorageBoundaryAndRejectOverflow() {
        calculator.validateAmount(BigInteger.TEN.pow(131071));
        assertThatThrownBy(() -> calculator.validateAmount(BigInteger.TEN.pow(131072))).isInstanceOf(CheckoutAmountLimitException.class);
    }

    @Test void calculate_shouldRejectLineAndAggregateOverflowBeforePersistence() {
        UUID product = UUID.randomUUID();
        UUID variant = UUID.randomUUID();
        BigInteger price = BigInteger.TEN.pow(131071);
        CheckoutVariantState state = new CheckoutVariantState(variant,product,ProductStatus.PUBLISHED,new BigDecimal(price),"Product","SKU",Map.of());
        assertThatThrownBy(() -> calculator.calculate(List.of(new CheckoutCartSeed.Row(1L,product,variant,999,true)),Map.of(variant,state)))
                .isInstanceOf(CheckoutAmountLimitException.class);
        assertThatThrownBy(() -> calculator.calculate(List.of(new CheckoutCartSeed.Row(1L,product,variant,5,true),
                new CheckoutCartSeed.Row(2L,product,variant,5,true)),Map.of(variant,state)))
                .isInstanceOf(CheckoutAmountLimitException.class);
    }
}
