package com.example.demo.common.exception;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import static org.assertj.core.api.Assertions.assertThat;

class ApiErrorFactoryTest {
    ApiErrorFactory factory = new ApiErrorFactory();

    @Test void from_shouldMapOnlyActualLockFailuresToConflict() {
        assertThat(factory.from(new DataAccessResourceFailureException("safe", new SQLException("private", "55P03")), "/api/me/cart").getStatus()).isEqualTo(409);
        assertThat(factory.from(new DataAccessResourceFailureException("safe", new SQLException("private", "40P01")), "/api/me/cart").getStatus()).isEqualTo(409);
        assertThat(factory.from(new DataAccessResourceFailureException("safe", new SQLException("private", "23514")), "/api/me/cart").getStatus()).isEqualTo(500);
        assertThat(factory.from(new IllegalStateException("private detail"), "/api/me/cart").getDetail()).doesNotContain("private detail");
        assertThat(factory.from(new SQLException("private", "55P03"), "/api/me/cart/items/1").getProperties()).containsEntry("code", "CART_CONFLICT");
        assertThat(factory.from(new SQLException("private", "55P03"), "/api/me/profile").getProperties()).doesNotContainKey("code");
    }
}
