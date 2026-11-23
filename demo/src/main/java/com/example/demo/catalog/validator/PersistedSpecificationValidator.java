package com.example.demo.catalog.validator;

import com.example.demo.catalog.search.SpecificationFilterRegistry;
import com.example.demo.catalog.search.SpecificationFilterRegistry.Definition;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates persisted facts against an already resolved category context.
 * No entities, database connection or application context are required. The caller obtains
 * normalized family names from CatalogApplicabilitySql in its own consistent read snapshot.
 */
public final class PersistedSpecificationValidator {
    private final TechnicalSpecificationValidator shapeValidator;
    private final SpecificationFilterRegistry registry;
    private final Set<String> supportedFamilies;

    public PersistedSpecificationValidator(TechnicalSpecificationValidator shapeValidator,
            SpecificationFilterRegistry registry) {
        this.shapeValidator = Objects.requireNonNull(shapeValidator);
        this.registry = Objects.requireNonNull(registry);
        this.supportedFamilies = registry.definitions().values().stream()
                .flatMap(definition -> definition.categoryRoots().stream())
                .map(family -> family.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
    }

    /** Empty families means a resolved disabled/nonapplicable binding, never an unresolved lookup. */
    public void validate(Map<String, Object> document, Set<String> applicableFamilies) {
        shapeValidator.validate(document);
        if (applicableFamilies == null || applicableFamilies.stream()
                .anyMatch(family -> family == null || !supportedFamilies.contains(family))) {
            throw new IllegalArgumentException("Resolved canonical specification families are required");
        }
        Map<?, ?> values = (Map<?, ?>) document.get("values");
        for (Definition definition : registry.definitions().values()) {
            boolean applicable = definition.categoryRoots().stream()
                    .map(family -> family.toLowerCase(Locale.ROOT)).anyMatch(applicableFamilies::contains);
            if (!applicable || !values.containsKey(definition.key())) {
                continue;
            }
            Object value = values.get(definition.key());
            // The shared shape validator has already checked finite numbers, exact quantity
            // members, canonical keys and nonblank text. Only the applicable registry rule remains.
            boolean valid = switch (definition.type()) {
                case "text" -> value instanceof String;
                case "quantity" -> value instanceof Map<?, ?> quantity
                        && Objects.equals(definition.unit(), quantity.get("unit"));
                default -> throw new IllegalStateException("Unsupported approved specification type");
            };
            if (!valid) {
                throw new IllegalArgumentException("Invalid applicable specification type or unit: " + definition.key());
            }
        }
    }
}
