package com.example.demo.catalog.dto.request;

import com.example.demo.catalog.dto.response.SellingPrice;
import com.example.demo.catalog.exception.AdminCatalogException;
import com.example.demo.catalog.validator.TechnicalSpecificationValidator;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Shared strict transport primitives; endpoint-owning readers supply exact allow/required field sets. */
@Component
public class AdminCatalogRequestReader {
    private final JsonMapper json = JsonMapper.builder(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            // Existing PostgreSQL NUMERIC storage capacity, not a new Admin price ceiling.
            .streamReadConstraints(StreamReadConstraints.builder().maxNumberLength(131073).build()).build())
            .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();

    /** Validate after handler selection so scoped Admin advice can preserve the 415 contract. */
    public void requireJson(String contentType) {
        try {
            if(contentType==null || !org.springframework.http.MediaType.APPLICATION_JSON.isCompatibleWith(org.springframework.http.MediaType.parseMediaType(contentType)))
                throw new AdminCatalogException(415,"CATALOG_INVALID_REQUEST","JSON media type is required");
        } catch(org.springframework.http.InvalidMediaTypeException invalid) {
            throw new AdminCatalogException(415,"CATALOG_INVALID_REQUEST","JSON media type is required");
        }
    }

    public JsonNode object(byte[] bytes, Set<String> allowed, Set<String> required) {
        try {
            JsonNode value = json.readTree(bytes);
            if (value == null || !value.isObject() || !allowed.containsAll(value.propertyNames())
                    || !value.propertyNames().containsAll(required)) throw malformed();
            return value;
        } catch (AdminCatalogException invalid) { throw invalid; }
        catch (RuntimeException invalid) { throw malformed(); }
    }

    public JsonNode patch(byte[] bytes, Set<String> editable) {
        Set<String> allowed = new LinkedHashSet<>(editable);
        allowed.addAll(Set.of("expectedVersion", "expectedTaxonomyVersion", "expectedBrandVersion"));
        JsonNode node = object(bytes, allowed, Set.of("expectedVersion", "expectedTaxonomyVersion"));
        if (node.propertyNames().stream().noneMatch(editable::contains)) throw malformed();
        revision(node.get("expectedVersion"));
        revision(node.get("expectedTaxonomyVersion"));
        return node;
    }

    public long revision(JsonNode value) {
        if (value == null || !value.isString()) throw malformed();
        return revision(value.asString());
    }

    public long revision(String value) {
        if (value == null || !value.matches("0|[1-9][0-9]*")) throw malformed();
        try { return Long.parseLong(value); }
        catch (NumberFormatException invalid) { throw malformed(); }
    }

    public UUID identifier(String value) {
        if (value == null || !value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) throw malformed();
        return UUID.fromString(value);
    }

    public CatalogField<String> text(JsonNode object, String name, boolean nullable, boolean nonblank) {
        if (!object.has(name)) return CatalogField.absent();
        JsonNode value = object.get(name);
        if (value.isNull() && nullable) return CatalogField.supplied(null);
        if (!value.isString() || nonblank && value.asString().isBlank()) throw malformed();
        return CatalogField.supplied(value.asString());
    }

    public CatalogField<UUID> identifier(JsonNode object, String name, boolean nullable) {
        CatalogField<String> value = text(object, name, nullable, true);
        return value.isSupplied() ? CatalogField.supplied(value.getValue() == null ? null : identifier(value.getValue())) : CatalogField.absent();
    }

    public boolean bool(JsonNode value) {
        if (value == null || !value.isBoolean()) throw malformed();
        return value.asBoolean();
    }

    public SellingPrice price(JsonNode value) {
        if (value == null || !value.isObject() || !value.propertyNames().equals(Set.of("amount", "currency"))) throw malformed();
        JsonNode currency = value.get("currency");
        JsonNode amount = value.get("amount");
        if (!currency.isString() || amount.isNull() || !amount.isNumber()) throw malformed();
        if (!currency.asString().equals("VND") || !amount.isIntegralNumber()) {
            throw new AdminCatalogException(422, "CATALOG_VALIDATION_FAILED", "Use an exact whole-VND price");
        }
        return new SellingPrice(amount.bigIntegerValue(), "VND");
    }

    public Map<String, Object> specifications(JsonNode value) {
        if (value == null || value.isNull() || !value.isObject()) throw malformed();
        Map<String, Object> result = json.convertValue(value, new TypeReference<Map<String, Object>>() { });
        try { new TechnicalSpecificationValidator().validate(result); }
        catch (IllegalArgumentException invalid) { throw new AdminCatalogException(422, "CATALOG_VALIDATION_FAILED", "Invalid technical specifications"); }
        return result;
    }

    public void brandPrecondition(JsonNode value, boolean assigningNewReference) {
        boolean selected = value.has("brandId") && !value.get("brandId").isNull();
        if (!selected && value.has("expectedBrandVersion") || selected && assigningNewReference && !value.has("expectedBrandVersion")) throw malformed();
        if (value.has("expectedBrandVersion")) revision(value.get("expectedBrandVersion"));
    }

    public Map<String, String> options(JsonNode value) {
        if (value == null || !value.isObject()) throw malformed();
        Map<String, String> result = new LinkedHashMap<>();
        for (String key : value.propertyNames()) {
            JsonNode option = value.get(key);
            if (key.isBlank() || !option.isString() || option.asString().isBlank()) {
                throw new AdminCatalogException(422, "CATALOG_VALIDATION_FAILED", "Use nonblank option names and values");
            }
            result.put(key, option.asString());
        }
        return Map.copyOf(result);
    }

    public List<UUID> identifiers(JsonNode value) {
        if (value == null || !value.isArray()) throw malformed();
        java.util.ArrayList<UUID> result = new java.util.ArrayList<>();
        for (JsonNode element : value) {
            if (!element.isString()) throw malformed();
            result.add(identifier(element.asString()));
        }
        return List.copyOf(result); // Preserve duplicate/order intent; service checks exact permutation.
    }

    public Map<String, List<String>> query(Map<String, String[]> parameters, Set<String> allowed, Set<String> repeated) {
        if (!allowed.containsAll(parameters.keySet())) throw malformed();
        Map<String, List<String>> result = new LinkedHashMap<>();
        parameters.forEach((key, values) -> {
            if (values == null || values.length == 0 || !repeated.contains(key) && values.length != 1
                    || Arrays.stream(values).anyMatch(java.util.Objects::isNull)) throw malformed();
            result.put(key, List.copyOf(new LinkedHashSet<>(Arrays.asList(values))));
        });
        return Map.copyOf(result);
    }

    public org.springframework.data.domain.Pageable page(Map<String, List<String>> query) {
        int page = integer(query.getOrDefault("page", List.of("0")).getFirst());
        int size = integer(query.getOrDefault("size", List.of("20")).getFirst());
        if (size < 1 || size > 100) throw malformed();
        return org.springframework.data.domain.PageRequest.of(page, size);
    }

    private static int integer(String value) {
        if (value == null || !value.matches("[0-9]+")) throw malformed();
        try { return Integer.parseInt(value); }
        catch (NumberFormatException invalid) { throw malformed(); }
    }

    private static AdminCatalogException malformed() {
        return new AdminCatalogException(400, "CATALOG_INVALID_REQUEST", "Invalid catalog request");
    }
}
