package com.example.demo.cart.controller;

import com.example.demo.cart.dto.request.AddCartItemRequest;
import com.example.demo.cart.dto.request.CartSelectionRequest;
import com.example.demo.cart.exception.InvalidCartRequestException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;

@Component
public class CartRequestReader {
    private final JsonFactory json = JsonFactory.builder().build();

    public void validate(byte[] body, Class<?> type) {
        Set<String> fields = new HashSet<>();
        boolean selection = type == CartSelectionRequest.class;
        try (JsonParser parser = json.createParser(body)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) throw new InvalidCartRequestException();
            JsonToken token;
            while ((token = parser.nextToken()) != JsonToken.END_OBJECT) {
                if (token != JsonToken.PROPERTY_NAME) throw new InvalidCartRequestException();
                String field = parser.currentName();
                if (!fields.add(field)) throw new InvalidCartRequestException();
                JsonToken value = parser.nextToken();
                if (selection && "selected".equals(field)) {
                    if (value != JsonToken.VALUE_TRUE && value != JsonToken.VALUE_FALSE) throw new InvalidCartRequestException();
                } else if (!selection && "quantity".equals(field)) {
                    if (value != JsonToken.VALUE_NUMBER_INT || !parser.getText().matches("[0-9]+")) throw new InvalidCartRequestException();
                    int quantity = Integer.parseInt(parser.getText());
                    if (quantity < 1 || quantity > 1000) throw new InvalidCartRequestException();
                } else if (!selection && "variantId".equals(field)) {
                    if (value != JsonToken.VALUE_STRING || !parser.getString().matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
                        throw new InvalidCartRequestException();
                } else { throw new InvalidCartRequestException(); }
            }
            if (parser.nextToken() != null || !fields.contains(selection ? "selected" : "quantity")
                    || type == AddCartItemRequest.class && !fields.contains("variantId")) throw new InvalidCartRequestException();
        } catch (InvalidCartRequestException invalid) { throw invalid; }
        catch (RuntimeException malformed) { throw new InvalidCartRequestException(); }
    }

    public void noQuery(HttpServletRequest request) {
        if (request.getQueryString() != null && !request.getQueryString().isEmpty()) throw new InvalidCartRequestException();
    }

    public void noBody(HttpServletRequest request) {
        noQuery(request);
        try { if (request.getInputStream().readAllBytes().length != 0) throw new InvalidCartRequestException(); }
        catch (IOException failure) { throw new InvalidCartRequestException(); }
    }
}
