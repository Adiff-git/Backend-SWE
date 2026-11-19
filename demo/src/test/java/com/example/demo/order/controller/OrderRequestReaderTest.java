package com.example.demo.order.controller;

import com.example.demo.order.exception.InvalidOrderRequestException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpMediaTypeNotSupportedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderRequestReaderTest {
    private final ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    private final OrderRequestReader reader = new OrderRequestReader(factory.getValidator());
    @AfterEach void close() { factory.close(); }

    @Test void readCancellation_shouldNormalizeAndCountCodePointsRatherThanUtf16() throws Exception {
        String emoji = new String(Character.toChars(0x1F600));
        assertThat(reader.readCancellation(request("{\"reason\":\"  changed  \"}","application/json")).getReason()).isEqualTo("changed");
        assertThat(reader.readCancellation(request("{\"reason\":\""+emoji.repeat(500)+"\"}","application/json")).getReason()).hasSize(1000);
        assertThatThrownBy(() -> reader.readCancellation(request("{\"reason\":\""+emoji.repeat(501)+"\"}","application/json")))
                .isInstanceOf(InvalidOrderRequestException.class);
        for (String body : new String[]{"","{}","{\"reason\":null}","{\"reason\":\" \\t \"}"}) {
            assertThat(reader.readCancellation(request(body,"application/json")).getReason()).isNull();
        }
    }

    @Test void readCancellation_shouldRejectUnknownDuplicateTrailingAndUnsupportedBodies() {
        for (String body : new String[]{"null","[]","4"," ","{\"reason\":true}","{\"reason\":[],\"id\":2}","{\"reason\":null,\"reason\":null}","{} {}"}) {
            assertThatThrownBy(() -> reader.readCancellation(request(body,"application/json"))).isInstanceOf(InvalidOrderRequestException.class);
        }
        assertThatThrownBy(() -> reader.readCancellation(request("{}",null))).isInstanceOf(HttpMediaTypeNotSupportedException.class);
        assertThatThrownBy(() -> reader.readCancellation(request("{}","text/plain"))).isInstanceOf(HttpMediaTypeNotSupportedException.class);
    }

    private MockHttpServletRequest request(String content, String type) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent(content.getBytes(StandardCharsets.UTF_8));
        request.setContentType(type);
        return request;
    }
}
