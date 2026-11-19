package com.example.demo.checkout.converter;

import com.example.demo.fixture.checkout.CheckoutFixtures;
import com.example.demo.testdata.checkout.CheckoutTestData;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class CheckoutConverterTest {
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
}
