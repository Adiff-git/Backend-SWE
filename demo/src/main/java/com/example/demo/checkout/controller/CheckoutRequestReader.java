package com.example.demo.checkout.controller;

import com.example.demo.checkout.dto.request.CheckoutRequest;
import com.example.demo.checkout.exception.InvalidCheckoutRequestException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Validator;
import java.io.IOException;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.json.JsonFactory;

@Component
@RequiredArgsConstructor
public class CheckoutRequestReader {
    private final Validator validator;
    private final JsonFactory json = JsonFactory.builder().build();

    public String readKey(HttpServletRequest request) {
        if (request.getQueryString() != null && !request.getQueryString().isEmpty() || !request.getParameterMap().isEmpty())
            throw new InvalidCheckoutRequestException();
        java.util.List<String> keys = Collections.list(request.getHeaders("Idempotency-Key"));
        if (keys.size() != 1 || !keys.getFirst().matches("[A-Za-z0-9_-]{16,128}")) throw new InvalidCheckoutRequestException();
        return keys.getFirst();
    }

    public CheckoutRequest readRequest(HttpServletRequest request) throws HttpMediaTypeNotSupportedException {
        byte[] body;
        try { body = request.getInputStream().readAllBytes(); }
        catch (IOException invalid) { throw new InvalidCheckoutRequestException(); }
        if (body.length == 0) throw new InvalidCheckoutRequestException();
        requireJson(request.getContentType());
        Long address;
        try (JsonParser parser = json.createParser(body)) {
            if (parser.nextToken() != JsonToken.START_OBJECT || parser.nextToken() != JsonToken.PROPERTY_NAME
                    || !"addressId".equals(parser.currentName()) || parser.nextToken() != JsonToken.VALUE_NUMBER_INT)
                throw new InvalidCheckoutRequestException();
            String value = parser.getString();
            if (!value.matches("[1-9][0-9]*")) throw new InvalidCheckoutRequestException();
            address = Long.valueOf(value);
            if (parser.nextToken() != JsonToken.END_OBJECT || parser.nextToken() != null) throw new InvalidCheckoutRequestException();
        } catch (RuntimeException invalid) { throw new InvalidCheckoutRequestException(); }
        CheckoutRequest result = new CheckoutRequest(address);
        if (!validator.validate(result).isEmpty()) throw new InvalidCheckoutRequestException();
        return result;
    }

    private void requireJson(String value) throws HttpMediaTypeNotSupportedException {
        if (value != null) {
            try {
                MediaType type = MediaType.parseMediaType(value);
                if ("application".equals(type.getType()) && !type.isWildcardSubtype()
                        && ("json".equals(type.getSubtype()) || type.getSubtype().endsWith("+json"))) return;
            } catch (IllegalArgumentException invalid) { /* Same safe415 for malformed media. */ }
        }
        throw new HttpMediaTypeNotSupportedException("Checkout requires JSON media type");
    }
}
