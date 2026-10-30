package com.example.demo.common.exception;
import java.sql.SQLException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class ApiErrorTest {
    @Test void create_shouldAlignStandardAndCustomFields() {
        ApiError e=new ApiErrorFactory().create(400,"Invalid input","/api/me",Map.of());
        assertThat(e.getStatus()).isEqualTo(400);
        assertThat(e.getProperties()).containsEntry("message",e.getDetail()).containsEntry("path",e.getInstance().toString()).containsEntry("fieldErrors",Map.of());
    }
    @Test void from_shouldMapKnownLockFailureWithoutSqlDisclosure() {
        ApiError e=new ApiErrorFactory().from(new SQLException("secret SQL","55P03"),"/api/me");
        assertThat(e.getStatus()).isEqualTo(409);
        assertThat(e.getDetail()).contains("retry").doesNotContain("secret");
    }
    @Test void from_shouldNotMisclassifyUnknownIntegrityFailure() {
        assertThat(new ApiErrorFactory().from(new SQLException("secret","23503"),"/api/me").getStatus()).isEqualTo(500);
    }
}
