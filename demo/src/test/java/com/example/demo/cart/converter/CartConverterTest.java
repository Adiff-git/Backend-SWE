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
    @Test void toResponse_shouldUsePriorPublicSnapshotForHiddenRowsAndNeverChargeThem() {
        UUID variant = UUID.randomUUID();
        UUID product = UUID.randomUUID();
        CartReadSeed seed = new CartReadSeed(1L, product, variant, 2, true, Instant.EPOCH, "Prior name", "Prior SKU", Map.of("size","M"));
        CartVariantState hidden = new CartVariantState(variant, product, false, BigInteger.TEN, "Hidden new name", "Hidden SKU", Map.of("private","value"), "private-url");
        CartResponse response = new CartConverter().toResponse(new CartCalculator().calculate(List.of(seed), Map.of(variant,hidden)));
        assertThat(response.getItems().getFirst().getProductName()).isEqualTo("Prior name");
        assertThat(response.getItems().getFirst().getOptions()).containsOnlyKeys("size");
        assertThat(response.getItems().getFirst().getSubtotal()).isNull();
        assertThat(response.getItems().getFirst().getPrimaryImageUrl()).isNull();
        assertThat(response.getFinalTotal().getAmount()).isZero();
    }
}
