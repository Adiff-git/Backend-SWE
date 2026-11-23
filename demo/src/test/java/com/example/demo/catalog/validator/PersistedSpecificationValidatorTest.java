package com.example.demo.catalog.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo.catalog.search.SpecificationFilterRegistry;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PersistedSpecificationValidatorTest {
    private final PersistedSpecificationValidator validator = new PersistedSpecificationValidator(
            new TechnicalSpecificationValidator(), new SpecificationFilterRegistry());

    @Test
    void validate_shouldAcceptEmptyFalseZeroAndGenericFacts_withoutInventingRequiredFacts() {
        assertThatCode(() -> validator.validate(document(Map.of()), Set.of("laptop")))
                .doesNotThrowAnyException();
        Map<String, Object> facts = Map.of("nfc_supported", false, "generic_count", 0,
                "generic_label", "unknown", "generic_quantity", quantity(0, "custom"),
                "physical_ram", quantity(0, "GB"), "display_diagonal", quantity(new BigDecimal("15.6"), "inch"),
                "installed_storage_gb", quantity(512, "GB"), "display_resolution", "1920x1080");
        Map<String, Object> document = document(facts);
        assertThatCode(() -> validator.validate(document, Set.of("laptop"))).doesNotThrowAnyException();
        assertThat(document.get("values")).isEqualTo(facts);
    }

    @ParameterizedTest
    @ValueSource(strings = {"physical_ram", "installed_storage_gb", "display_diagonal"})
    void validate_shouldRejectWrongRegisteredType_whenApplicable(String key) {
        for (Object invalid : List.of("16", 16, false)) {
            assertThatThrownBy(() -> validator.validate(document(Map.of(key, invalid)), Set.of("laptop")))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(key);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"gb", "MB", "TB", "GB "})
    void validate_shouldRejectNonExactUnits_withoutConverting(String unit) {
        assertThatThrownBy(() -> validator.validate(document(Map.of("physical_ram", quantity(16, unit))),
                Set.of("laptop"))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("physical_ram");
    }

    @Test
    void validate_shouldRequireInchAndText_forApplicableDisplayFacts() {
        assertThatThrownBy(() -> validator.validate(document(Map.of("display_diagonal", quantity(10, "cm"))),
                Set.of("tablet"))).isInstanceOf(IllegalArgumentException.class);
        for (Object invalid : List.of(false, 1080, quantity(1080, "px"))) {
            assertThatThrownBy(() -> validator.validate(document(Map.of("display_resolution", invalid)),
                    Set.of("laptop"))).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void validate_shouldKeepNonapplicableFactsGeneric_underTabletOrDisabledBindings() {
        // Empty context represents SQL-resolved missing/ambiguous roots or an ancestry boundary.
        Map<String, Object> genericRam = document(Map.of("physical_ram", "generic text"));
        assertThatCode(() -> validator.validate(genericRam, Set.of("tablet"))).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(genericRam, Set.of())).doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validate(genericRam, Set.of("laptop")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> validator.validate(document(Map.of("display_diagonal", quantity(10, "inch"))),
                Set.of("tablet"))).doesNotThrowAnyException();
    }

    @Test
    void validate_shouldAlwaysEnforceExistingShapeRules_evenWithoutApplicableBindings() {
        List<Map<String, Object>> invalidDocuments = List.of(Map.of(),
                Map.of("schema_version", 2, "values", Map.of()),
                Map.of("schema_version", 1, "values", Map.of(), "extra", true),
                document(Map.of("Bad key", true)), document(Map.of("generic", " ")),
                document(Map.of("generic", Double.NaN)), document(Map.of("generic", Double.POSITIVE_INFINITY)),
                document(Map.of("generic", List.of(1))),
                document(Map.of("generic", Map.of("value", 1, "unit", "GB", "extra", true))),
                document(Map.of("generic", quantity(Double.NEGATIVE_INFINITY, "GB"))),
                document(Map.of("generic", quantity(1, " "))));
        for (Map<String, Object> invalid : invalidDocuments) {
            assertThatThrownBy(() -> validator.validate(invalid, Set.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        Map<String, Object> nullValue = new HashMap<>();
        nullValue.put("generic", null);
        assertThatThrownBy(() -> validator.validate(document(nullValue), Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(null, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validate_shouldRejectMissingOrUnresolvedContext_ratherThanAssumeNotApplicable() {
        assertThatThrownBy(() -> validator.validate(document(Map.of()), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(document(Map.of()), Set.of("Laptop")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(document(Map.of()), Set.of("unknown")))
                .isInstanceOf(IllegalArgumentException.class);
        Set<String> nullFamily = new java.util.HashSet<>();
        nullFamily.add(null);
        assertThatThrownBy(() -> validator.validate(document(Map.of()), nullFamily))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Map<String, Object> document(Map<String, Object> values) {
        return Map.of("schema_version", 1, "values", values);
    }

    private static Map<String, Object> quantity(Number value, String unit) {
        return Map.of("value", value, "unit", unit);
    }
}
