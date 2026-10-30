package com.example.demo.catalog.validator;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Validates typed document shape. Registry applicability is checked by the normalizer. */
@Component
public class TechnicalSpecificationValidator {
    public void validate(Map<String, Object> document) {
        if (document == null || !document.keySet().equals(Set.of("schema_version", "values"))
                || !(document.get("schema_version") instanceof Number version)
                || !new BigDecimal(version.toString()).equals(BigDecimal.ONE)
                || !(document.get("values") instanceof Map<?, ?> values)) {
            throw new IllegalArgumentException("Invalid technical specification document or version");
        }
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            if (!(entry.getKey() instanceof String key) || !key.matches("[a-z][a-z0-9_]*")) {
                throw new IllegalArgumentException("Invalid canonical specification key");
            }
            validateValue(entry.getValue());
        }
    }

    private void validateValue(Object value) {
        if (value instanceof Boolean) {
            return;
        }
        if (value instanceof String text && !text.isBlank()) {
            return;
        }
        if (value instanceof Number number && finite(number)) {
            return;
        }
        if (value instanceof Map<?, ?> quantity && quantity.keySet().equals(Set.of("value", "unit"))
                && quantity.get("value") instanceof Number number && finite(number)
                && quantity.get("unit") instanceof String unit && !unit.isBlank()) {
            return;
        }
        throw new IllegalArgumentException("Unsupported technical specification value");
    }

    private boolean finite(Number number) {
        try {
            new BigDecimal(number.toString());
            return true;
        } catch (NumberFormatException invalid) {
            return false;
        }
    }
}
