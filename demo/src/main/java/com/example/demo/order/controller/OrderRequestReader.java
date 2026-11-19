package com.example.demo.order.controller;

import com.example.demo.order.dto.request.OrderListRequest;
import com.example.demo.order.dto.request.CancelOrderRequest;
import com.example.demo.order.entity.OrderStatus;
import com.example.demo.order.exception.InvalidOrderRequestException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Validator;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.http.MediaType;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.json.JsonFactory;

@Component @RequiredArgsConstructor
public class OrderRequestReader {
    private static final Set<String> LIST_PARAMETERS = Set.of("page","size","status","sort");
    private final Validator validator;
    private final JsonFactory json = JsonFactory.builder().build();

    public OrderListRequest readList(HttpServletRequest request) {
        noBody(request);
        Map<String, String[]> parameters = request.getParameterMap();
        for (Map.Entry<String, String[]> entry : parameters.entrySet()) {
            if (!LIST_PARAMETERS.contains(entry.getKey()) || entry.getValue().length != 1) throw new InvalidOrderRequestException();
        }
        try {
            String status = request.getParameter("status");
            return new OrderListRequest(number(request.getParameter("page"),0),number(request.getParameter("size"),20),
                    status == null ? null : OrderStatus.valueOf(status),
                    request.getParameter("sort") == null ? "placedAt,desc" : request.getParameter("sort"));
        } catch (IllegalArgumentException invalid) { throw new InvalidOrderRequestException(); }
    }

    private int number(String value, int fallback) {
        if (value == null) return fallback;
        if (!value.matches("0|[1-9][0-9]*")) throw new InvalidOrderRequestException();
        return Integer.parseInt(value);
    }

    public Long readId(String value) {
        if (!value.matches("[1-9][0-9]*")) throw new InvalidOrderRequestException();
        try { return Long.valueOf(value); }
        catch (NumberFormatException invalid) { throw new InvalidOrderRequestException(); }
    }

    public CancelOrderRequest readCancellation(HttpServletRequest request) throws HttpMediaTypeNotSupportedException {
        noQuery(request);
        byte[] body;
        try { body = request.getInputStream().readAllBytes(); }
        catch (IOException invalid) { throw new InvalidOrderRequestException(); }
        if (body.length == 0) return new CancelOrderRequest(null);
        requireJson(request.getContentType());
        String reason = null;
        boolean seen = false;
        try (JsonParser parser = json.createParser(body)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) throw new InvalidOrderRequestException();
            JsonToken token;
            while ((token = parser.nextToken()) != JsonToken.END_OBJECT) {
                if (token != JsonToken.PROPERTY_NAME || !"reason".equals(parser.currentName()) || seen) throw new InvalidOrderRequestException();
                seen = true;
                token = parser.nextToken();
                if (token == JsonToken.VALUE_STRING) reason = parser.getString();
                else if (token != JsonToken.VALUE_NULL) throw new InvalidOrderRequestException();
            }
            if (parser.nextToken() != null) throw new InvalidOrderRequestException();
        } catch (RuntimeException malformed) { throw new InvalidOrderRequestException(); }
        CancelOrderRequest result = new CancelOrderRequest(reason);
        if (!validator.validate(result).isEmpty()) throw new InvalidOrderRequestException();
        return result;
    }

    private void requireJson(String value) throws HttpMediaTypeNotSupportedException {
        if (value != null) {
            try {
                MediaType type = MediaType.parseMediaType(value);
                if ("application".equals(type.getType()) && !type.isWildcardSubtype()
                        && ("json".equals(type.getSubtype()) || type.getSubtype().endsWith("+json"))) return;
            } catch (IllegalArgumentException invalid) {
                // Return the same safe media-type outcome for malformed or unsupported headers.
            }
        }
        throw new HttpMediaTypeNotSupportedException("JSON media type is required for a nonempty cancellation body");
    }

    public void noQuery(HttpServletRequest request) {
        if (request.getQueryString() != null && !request.getQueryString().isEmpty()) throw new InvalidOrderRequestException();
        if (!request.getParameterMap().isEmpty()) throw new InvalidOrderRequestException();
    }

    public void noBody(HttpServletRequest request) {
        try { if (request.getInputStream().readAllBytes().length != 0) throw new InvalidOrderRequestException(); }
        catch (IOException invalid) { throw new InvalidOrderRequestException(); }
    }
}
