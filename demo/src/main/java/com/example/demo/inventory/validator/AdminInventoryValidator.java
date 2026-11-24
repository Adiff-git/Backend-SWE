package com.example.demo.inventory.validator;

import com.example.demo.inventory.dto.request.AdminInventoryCommand;
import com.example.demo.inventory.dto.request.AdminInventoryCommand.OperationType;
import com.example.demo.inventory.exception.AdminInventoryException;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class AdminInventoryValidator {
    private static final Set<String> FIELDS = Set.of("operationId", "delta", "reason");
    private static final Pattern INTEGER = Pattern.compile("0|-?[1-9][0-9]*");
    private static final Pattern UUID_FORMAT = Pattern.compile("[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}");
    private static final Pattern EDGE_WHITESPACE = Pattern.compile("\\A\\p{IsWhite_Space}+|\\p{IsWhite_Space}+\\z");
    private final JsonMapper json = JsonMapper.builder(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    public AdminInventoryCommand command(String variant, OperationType type, byte[] body) {
        JsonNode node;
        try {
            node = json.readTree(body);
        } catch (RuntimeException invalid) {
            throw invalidRequest();
        }
        if (node == null || !node.isObject() || !FIELDS.containsAll(node.propertyNames())
                || !node.has("operationId") || !node.has("delta")
                || type == OperationType.CORRECTION && !node.has("reason")) {
            throw invalidRequest();
        }
        UUID variantId = identifier(variant);
        UUID operationId = identifier(string(node.get("operationId")));
        String quantity = string(node.get("delta"));
        String reason = node.has("reason") ? string(node.get("reason")) : null;
        long delta = delta(quantity, type);
        if (reason != null) {
            reason = EDGE_WHITESPACE.matcher(reason).replaceAll("");
            if (reason.isEmpty()) reason = null;
        }
        if ((reason == null && type == OperationType.CORRECTION)
                || (reason != null && (reason.codePointCount(0, reason.length()) > 500
                || reason.codePoints().anyMatch(cp -> cp == 0 || cp >= 0xD800 && cp <= 0xDFFF)))) {
            throw new AdminInventoryException(422, "INVALID_REASON", "Invalid inventory reason");
        }
        return new AdminInventoryCommand(operationId, variantId, type, delta, reason);
    }

    private long delta(String text, OperationType type) {
        if (text.length() > 20 || !INTEGER.matcher(text).matches()) throw invalidQuantity();
        try {
            long delta = Long.parseLong(text);
            if (delta == 0 || type == OperationType.REPLENISHMENT && delta < 0) throw invalidQuantity();
            return delta;
        } catch (NumberFormatException invalid) {
            throw invalidQuantity();
        }
    }

    public UUID identifier(String text) {
        if (!UUID_FORMAT.matcher(text).matches()) throw invalidRequest();
        return UUID.fromString(text);
    }

    private String string(JsonNode node) {
        if (!node.isString()) throw invalidRequest();
        return node.asString();
    }

    public static AdminInventoryException invalidRequest() {
        return new AdminInventoryException(400, "INVALID_REQUEST", "Invalid inventory request");
    }

    public static AdminInventoryException invalidQuantity() {
        return new AdminInventoryException(422, "INVALID_QUANTITY", "Invalid inventory quantity");
    }
}
