package com.example.demo.inventory.validator;

import static com.example.demo.inventory.dto.request.AdminInventoryCommand.OperationType.CORRECTION;
import static com.example.demo.inventory.dto.request.AdminInventoryCommand.OperationType.REPLENISHMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo.inventory.exception.AdminInventoryException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AdminInventoryValidatorTest {
    private final AdminInventoryValidator validator = new AdminInventoryValidator();
    private final String id = UUID.randomUUID().toString();

    private byte[] body(String delta, String extra) {
        return ("{\"operationId\":\"" + id + "\",\"delta\":" + delta + extra + "}").getBytes(StandardCharsets.UTF_8);
    }

    @Test void acceptsSignedBoundsAndNormalizesUnicodeReason() {
        assertThat(validator.command(id, CORRECTION, body("\"-9223372036854775808\"", ",\"reason\":\"  x 　\"")).delta())
                .isEqualTo(Long.MIN_VALUE);
        assertThat(validator.command(id, REPLENISHMENT, body("\"9223372036854775807\"", ",\"reason\":\" 　\"")).reason()).isNull();
        assertThat(validator.command(id, CORRECTION, body("\"-1\"", ",\"reason\":\" " + "🦊".repeat(500) + " \"")).reason())
                .isEqualTo("🦊".repeat(500));
    }

    @ParameterizedTest @ValueSource(strings = {"0", "-1", "01", "-0", "1.0", " 1", "", "9223372036854775808", "-9223372036854775809"})
    void semanticQuantityErrors(String delta) {
        assertThatThrownBy(() -> validator.command(id, REPLENISHMENT, body("\"" + delta + "\"", "")))
                .isInstanceOfSatisfying(AdminInventoryException.class, e -> assertThat(e.getStatus()).isEqualTo(422));
    }

    @Test void rejectsWrongShapeAndExcessiveReason() {
        for (byte[] request : new byte[][] {body("1", ""), body("1.5", ""), body("\"1\"", ",\"delta\":\"2\""),
                body("\"1\"", ",\"reason\":null"), body("\"1\"", ",\"stock\":\"2\"")}) {
            assertThatThrownBy(() -> validator.command(id, REPLENISHMENT, request))
                    .isInstanceOfSatisfying(AdminInventoryException.class, e -> assertThat(e.getStatus()).isEqualTo(400));
        }
        assertThatThrownBy(() -> validator.command(id, CORRECTION, body("\"-1\"", ",\"reason\":\"" + "a".repeat(501) + "\"")))
                .isInstanceOfSatisfying(AdminInventoryException.class, e -> assertThat(e.getCode()).isEqualTo("INVENTORY_INVALID_REASON"));
    }
}
