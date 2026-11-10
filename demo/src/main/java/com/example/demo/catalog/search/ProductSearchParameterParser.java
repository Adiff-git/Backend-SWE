package com.example.demo.catalog.search;

import com.example.demo.catalog.dto.request.ProductSearchRequest;
import com.example.demo.catalog.dto.request.ProductSearchRequest.BooleanValue;
import com.example.demo.catalog.dto.request.ProductSearchRequest.NumericValue;
import com.example.demo.catalog.dto.request.ProductSearchRequest.SpecificationAlternative;
import com.example.demo.catalog.dto.request.ProductSearchRequest.SpecificationGroup;
import com.example.demo.catalog.dto.request.ProductSearchRequest.SpecificationOperator;
import com.example.demo.catalog.dto.request.ProductSearchRequest.TextValue;
import com.example.demo.catalog.exception.InvalidCatalogRequestException;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.cfg.JsonNodeFeature;
import tools.jackson.databind.json.JsonMapper;

/** Decoded transport syntax only. Does not authorize registry keys or connect to an endpoint. */
public final class ProductSearchParameterParser {
    private static final Set<String> PARAMETERS = Set.of(
            "q", "page", "size", "categoryId", "brandId", "minPrice", "maxPrice", "sort", "specFilters");
    private final Validator validator;
    private final ProductSearchNormalizer normalizer = new ProductSearchNormalizer();
    private final JsonMapper json = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(JsonNodeFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .disable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES)
            .build();

    public ProductSearchParameterParser(Validator validator) {
        this.validator = java.util.Objects.requireNonNull(validator);
    }

    /** Values are already URL-decoded exactly once by the HTTP transport. */
    public ProductSearchRequest parse(Map<String, ? extends List<String>> parameters) {
        if (parameters == null) { throw invalid("parameters"); }
        for (Map.Entry<String, ? extends List<String>> entry : parameters.entrySet()) {
            String name = entry.getKey();
            if (name == null || !PARAMETERS.contains(name) || entry.getValue() == null
                    || entry.getValue().isEmpty() || entry.getValue().stream().anyMatch(java.util.Objects::isNull)
                    || !name.equals("brandId") && entry.getValue().size() != 1) {
                throw invalid("query parameters");
            }
        }
        ProductSearchRequest request = new ProductSearchRequest();
        if (parameters.containsKey("q")) { request.setQ(first(parameters, "q")); }
        if (parameters.containsKey("page")) { request.setPage(integer(first(parameters, "page"), "page")); }
        if (parameters.containsKey("size")) { request.setSize(integer(first(parameters, "size"), "size")); }
        if (parameters.containsKey("categoryId")) { request.setCategoryId(uuid(first(parameters, "categoryId"))); }
        if (parameters.containsKey("brandId")) {
            List<String> raw = parameters.get("brandId");
            if (raw.size() > 20) { throw invalid("brandId"); }
            List<String> brands = raw.stream().map(this::uuid).toList();
            request.setBrandIds(List.copyOf(new LinkedHashSet<>(brands)));
        }
        if (parameters.containsKey("minPrice")) { request.setMinPrice(price(first(parameters, "minPrice"))); }
        if (parameters.containsKey("maxPrice")) { request.setMaxPrice(price(first(parameters, "maxPrice"))); }
        if (request.getMinPrice() != null && request.getMaxPrice() != null
                && request.getMinPrice().compareTo(request.getMaxPrice()) > 0) { throw invalid("price range"); }
        if (parameters.containsKey("sort")) {
            String value = first(parameters, "sort");
            ProductSearchSort sort = null;
            for (ProductSearchSort candidate : ProductSearchSort.values()) {
                if (candidate.getParameterValue().equals(value)) { sort = candidate; }
            }
            if (sort == null || sort == ProductSearchSort.RELEVANCE
                    && normalizer.normalizeQuery(request.getQ()).getTokens().isEmpty()) { throw invalid("sort"); }
            request.setSort(sort);
        }
        if (parameters.containsKey("specFilters")) {
            String raw = first(parameters, "specFilters");
            if (raw.codePointCount(0, raw.length()) > 4096 || request.getCategoryId() == null) {
                throw invalid("specFilters/categoryId");
            }
            request.setSpecFilters(raw);
            request.setSpecificationGroups(groups(raw));
        }
        if (!validator.validate(request).isEmpty()) { throw invalid("search request"); }
        return request;
    }

    private List<SpecificationGroup> groups(String raw) {
        JsonNode root;
        try { root = json.readTree(raw); }
        catch (JacksonException exception) { throw invalid("specFilters JSON"); }
        if (root == null || !root.isArray() || root.size() < 1 || root.size() > 8) {
            throw invalid("specFilters groups");
        }
        Set<String> keys = new HashSet<>();
        List<SpecificationGroup> groups = new ArrayList<>();
        for (JsonNode group : root) {
            object(group, Set.of("key", "alternatives"));
            String key = string(required(group, "key"));
            if (!keys.add(key)) { throw invalid("duplicate specification group"); }
            JsonNode alternatives = required(group, "alternatives");
            if (!alternatives.isArray() || alternatives.size() < 1 || alternatives.size() > 4) {
                throw invalid("specFilters alternatives");
            }
            List<SpecificationAlternative> values = new ArrayList<>();
            for (JsonNode alternative : alternatives) { values.add(alternative(alternative)); }
            SpecificationGroup parsed = new SpecificationGroup();
            parsed.setKey(key);
            parsed.setAlternatives(List.copyOf(values));
            groups.add(parsed);
        }
        return List.copyOf(groups);
    }

    private SpecificationAlternative alternative(JsonNode node) {
        String operation = string(required(node, "op"));
        SpecificationOperator operator;
        try {
            operator = SpecificationOperator.valueOf(operation.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) { throw invalid("specification operator"); }
        if (!operation.equals(operation.toLowerCase(Locale.ROOT))) { throw invalid("specification operator"); }
        boolean between = operator == SpecificationOperator.BETWEEN;
        object(node, between ? Set.of("op", "min", "max", "unit") : Set.of("op", "value", "unit"));
        SpecificationAlternative parsed = new SpecificationAlternative();
        parsed.setOp(operator);
        if (between) {
            BigDecimal min = number(required(node, "min"));
            BigDecimal max = number(required(node, "max"));
            if (min.compareTo(max) > 0) { throw invalid("specification range"); }
            parsed.setMin(min);
            parsed.setMax(max);
        } else {
            JsonNode value = required(node, "value");
            if (value.isNumber()) {
                if (operator == SpecificationOperator.PREFIX || operator == SpecificationOperator.CONTAINS) {
                    throw invalid("numeric operator");
                }
                parsed.setValue(new NumericValue(number(value)));
            } else if (value.isString()) {
                if (operator != SpecificationOperator.EQ && operator != SpecificationOperator.PREFIX
                        && operator != SpecificationOperator.CONTAINS || node.has("unit")) {
                    throw invalid("text operator/unit");
                }
                parsed.setValue(new TextValue(string(value)));
            } else if (value.isBoolean()) {
                if (operator != SpecificationOperator.EQ || node.has("unit")) { throw invalid("boolean operator/unit"); }
                parsed.setValue(new BooleanValue(value.asBoolean()));
            } else { throw invalid("specification operand"); }
        }
        if (node.has("unit")) { parsed.setUnit(string(required(node, "unit"))); }
        return parsed;
    }

    private void object(JsonNode node, Set<String> allowed) {
        if (node == null || !node.isObject()) { throw invalid("specFilters object"); }
        for (Map.Entry<String, JsonNode> entry : node.properties()) {
            if (!allowed.contains(entry.getKey()) || entry.getValue().isNull()) { throw invalid("specFilters member"); }
        }
    }

    private JsonNode required(JsonNode node, String field) {
        if (node == null || !node.isObject() || !node.has(field) || node.get(field).isNull()) {
            throw invalid("specFilters " + field);
        }
        return node.get(field);
    }

    private String string(JsonNode node) {
        if (!node.isString()) { throw invalid("specFilters string"); }
        return node.asString();
    }

    private BigDecimal number(JsonNode node) {
        if (!node.isNumber()) { throw invalid("specFilters number"); }
        BigDecimal value = node.decimalValue();
        if (value.precision() > 30 || value.scale() > 12) { throw invalid("specFilters precision"); }
        return value;
    }

    private String first(Map<String, ? extends List<String>> parameters, String name) {
        return parameters.get(name).getFirst();
    }

    private Integer integer(String raw, String parameter) {
        if (!raw.matches(ProductSearchRequest.CANONICAL_UNSIGNED_INTEGER)) { throw invalid(parameter); }
        try { return Integer.valueOf(raw); }
        catch (NumberFormatException exception) { throw invalid(parameter); }
    }

    private BigInteger price(String raw) {
        if (!raw.matches(ProductSearchRequest.CANONICAL_PRICE)) { throw invalid("price"); }
        return new BigInteger(raw);
    }

    private String uuid(String raw) {
        if (!raw.matches(ProductSearchRequest.CANONICAL_UUID)) { throw invalid("UUID parameter"); }
        return raw;
    }

    private InvalidCatalogRequestException invalid(String parameter) {
        return new InvalidCatalogRequestException("Invalid " + parameter);
    }
}
