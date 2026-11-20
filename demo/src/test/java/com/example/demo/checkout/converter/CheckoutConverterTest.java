package com.example.demo.checkout.converter;

import com.example.demo.fixture.checkout.CheckoutFixtures;
import com.example.demo.testdata.checkout.CheckoutTestData;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class CheckoutConverterTest {
    @Test void stockSkipReasons_shouldRoundTripWithoutAddingLiveAvailabilityToReceipt() {
        CheckoutConverter converter = new CheckoutConverter();
        String body = JsonMapper.builder().build().writeValueAsString(CheckoutTestData.receipt(5L, 7L, CheckoutFixtures.PLACED_AT));
        var original = converter.decode(body);
        for (var reason : java.util.List.of(com.example.demo.checkout.dto.response.SkippedCheckoutItem.Reason.INVENTORY_UNAVAILABLE,
                com.example.demo.checkout.dto.response.SkippedCheckoutItem.Reason.INSUFFICIENT_STOCK)) {
            var response = new com.example.demo.checkout.dto.response.CheckoutReceiptResponse(original.getOrder(),
                    java.util.List.of(new com.example.demo.checkout.dto.response.SkippedCheckoutItem(9L, reason)));
            String encoded = converter.encode(response);
            assertThat(converter.decode(encoded)).usingRecursiveComparison().isEqualTo(response);
            assertThat(encoded).doesNotContain("availability", "reservedQuantity", "inventoryBalanceId");
        }
    }
    @Test void decode_shouldPreserveFrozenReceiptWithoutCurrentDomainAccess() {
        String body = JsonMapper.builder().build().writeValueAsString(CheckoutTestData.receipt(5L,7L,CheckoutFixtures.PLACED_AT));
        CheckoutConverter converter = new CheckoutConverter();
        assertThat(converter.decode(body).getOrder().getId()).isEqualTo(5L);
        assertThat(converter.decode(body).getOrder().getStatus().name()).isEqualTo("PENDING");
        assertThat(JsonMapper.builder().build().readTree(converter.encode(converter.decode(body))))
                .isEqualTo(JsonMapper.builder().build().readTree(body));
    }

    @Test void decode_shouldPreserveWideNumbersWithinReceiptOnlyParser() {
        String amount = "1"+"0".repeat(131071);
        String body = JsonMapper.builder().build().writeValueAsString(CheckoutTestData.receipt(5L,7L,CheckoutFixtures.PLACED_AT))
                .replace("\"amount\":100","\"amount\":"+amount);
        CheckoutConverter converter = new CheckoutConverter();
        assertThat(converter.decode(body).getOrder().getPayableTotal().getAmount()).isEqualTo(new java.math.BigInteger(amount));
        assertThat(converter.decode(converter.encode(converter.decode(body))).getOrder().getPayableTotal().getAmount())
                .isEqualTo(new java.math.BigInteger(amount));
    }
    @Test void versionTwo_shouldPreserveNewZeroDiscountFieldsWithoutAddingThemToLegacyReplay() {
        CheckoutConverter converter = new CheckoutConverter();
        var json = JsonMapper.builder().build();
        String old = json.writeValueAsString(CheckoutTestData.receipt(5L, 7L, CheckoutFixtures.PLACED_AT));
        var root = json.readTree(old);
        var order = (tools.jackson.databind.node.ObjectNode) root.get("order");
        order.set("voucherDiscount", json.readTree("{\"amount\":0,\"currency\":\"VND\"}"));
        order.putNull("voucher");
        String modern = json.writeValueAsString(root);
        assertThat(json.readTree(converter.encode(converter.decode(modern, 2)))).isEqualTo(root);
        assertThat(json.readTree(converter.encode(converter.decode(old, 1))).get("order").has("voucherDiscount")).isFalse();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> converter.decode(modern, 99)).isInstanceOf(IllegalStateException.class);
    }
}
