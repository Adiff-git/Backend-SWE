package com.example.demo.catalog.validator;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class TechnicalSpecificationValidatorTest {
    private final TechnicalSpecificationValidator validator = new TechnicalSpecificationValidator();
    @Test
    void validate_shouldAcceptKnownFalseAndTypedQuantities() {
        assertThatCode(() -> validator.validate(Map.of("schema_version", 1, "values", Map.of(
                "nfc_supported", false, "cpu_model", "10110U", "physical_ram", Map.of("value", 8, "unit", "GB")))))
                .doesNotThrowAnyException();
    }
    @Test
    void validate_shouldRejectUnknownVersionMissingEnvelopeAndInvalidValues() {
        for (Map<String, Object> invalid : java.util.List.<Map<String, Object>>of(
                Map.of(), Map.of("schema_version", 2, "values", Map.of()),
                Map.of("schema_version", 1, "values", Map.of("ram", Double.NaN)),
                Map.of("schema_version", 1, "values", Map.of("ram", "")),
                Map.of("schema_version", 1, "values", Map.of("ram", Map.of("value", 8))),
                Map.of("schema_version", 1, "values", Map.of("Bad key", true)))) {
            assertThatThrownBy(() -> validator.validate(invalid)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
