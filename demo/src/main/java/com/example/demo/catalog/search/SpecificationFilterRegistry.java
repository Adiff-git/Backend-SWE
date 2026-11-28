package com.example.demo.catalog.search;

import com.example.demo.catalog.dto.request.ProductSearchRequest.SpecificationGroup;
import com.example.demo.catalog.dto.request.ProductSearchRequest.NumericValue;
import com.example.demo.catalog.dto.request.ProductSearchRequest.TextValue;
import com.example.demo.catalog.exception.InvalidCatalogRequestException;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.json.JsonMapper;

/** Validated global specification definitions; category and brand are optional search constraints. */
public final class SpecificationFilterRegistry {
    public record Definition(String key, String type, String unit,
            Set<String> operators, boolean searchable, boolean filterable) { }
    private final Map<String, Definition> definitions;
    public SpecificationFilterRegistry() { this(SpecificationFilterRegistry.class.getResourceAsStream("/catalog/specification-filter-registry.json")); }
    SpecificationFilterRegistry(java.io.InputStream document) {
        Map<String, Definition> loaded = new LinkedHashMap<>();
        try (var in = document) {
            if (in == null) { throw new IllegalStateException("Missing specification registry"); }
            var root = JsonMapper.builder().build().readTree(in);
            if (!root.get("documentSchemaVersion").isIntegralNumber() || root.get("documentSchemaVersion").asInt()!=1
                    || root.get("approvalReference").asString().isBlank()
                    || !"specification-registry-v1".equals(root.get("version").asString())) {
                throw new IllegalStateException("Unapproved specification registry version");
            }
            for (var d : root.get("definitions")) {
                String key = d.get("key").asString();
                if (!Set.of("physical_ram", "installed_storage_gb", "display_diagonal", "display_resolution", "cpu_family").contains(key)
                        || !"APPROVED_BY_PROJECT_OWNER".equals(d.get("approval").asString())) {
                    throw new IllegalStateException("Unapproved specification definition");
                }
                var operators = new java.util.HashSet<String>();
                d.get("operators").forEach(v -> operators.add(v.asString()));
                String type = d.get("type").asString();
                String unit = d.get("unit").isNull() ? null : d.get("unit").asString();
                if (!d.get("searchable").asBoolean() || !d.get("filterable").asBoolean()
                        || !Set.of("quantity", "text").contains(type)
                        || Set.of("display_resolution", "cpu_family").contains(key) != type.equals("text")
                        || !java.util.Objects.equals(unit, key.equals("display_diagonal") ? "inch"
                                : Set.of("display_resolution", "cpu_family").contains(key) ? null : "GB")
                        || !operators.equals(type.equals("text") ? Set.of("eq", "prefix", "contains")
                                : Set.of("eq", "gt", "gte", "lt", "lte", "between"))
                        || d.get("identifier").asBoolean()) { throw new IllegalStateException("Invalid registry definition"); }
                var definition = new Definition(key, type, unit, Set.copyOf(operators),
                        d.get("searchable").asBoolean(), d.get("filterable").asBoolean());
                if (loaded.put(key, definition) != null) { throw new IllegalStateException("Duplicate registry key"); }
            }
            if (loaded.size() != 5) { throw new IllegalStateException("Incomplete registry"); }
        } catch (IOException | RuntimeException exception) { throw new IllegalStateException("Invalid specification registry", exception); }
        definitions = Map.copyOf(loaded);
    }
    public Map<String, Definition> definitions() { return definitions; }
    public Definition get(String key) {
        Definition result = definitions.get(key);
        if (result == null) { throw new InvalidCatalogRequestException("Unsupported specification key"); }
        return result;
    }
    public void validate(List<SpecificationGroup> groups, java.util.UUID categoryId) {
        if (groups == null) { return; }
        for (var group : groups) {
            if(group==null || group.getAlternatives()==null || group.getAlternatives().isEmpty()){throw new InvalidCatalogRequestException("Invalid specification group");}
            var d = get(group.getKey());
            for (var operand : group.getAlternatives()) {
                if(operand==null || operand.getOp()==null){throw new InvalidCatalogRequestException("Invalid specification operand");}
                if (!d.filterable() || !d.operators().contains(operand.getOp().name().toLowerCase(java.util.Locale.ROOT))
                        || !java.util.Objects.equals(d.unit(), operand.getUnit())
                        || d.type().equals("quantity") && operand.getOp() != com.example.demo.catalog.dto.request.ProductSearchRequest.SpecificationOperator.BETWEEN
                                && !(operand.getValue() instanceof NumericValue)
                        || d.type().equals("text") && !(operand.getValue() instanceof TextValue)) {
                    throw new InvalidCatalogRequestException("Unsupported specification type,operator or unit");
                }
            }
        }
    }
}
