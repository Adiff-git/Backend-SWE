package com.example.demo.common.exception;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import static org.assertj.core.api.Assertions.assertThat;

class ApiErrorFactoryTest {
    @Test void from_shouldReadTheExactStructuredPostgresConstraintForJdbcAndCommitFailures() {
        var failure = new org.postgresql.util.PSQLException(new org.postgresql.util.ServerErrorMessage(
                "SERROR\0C23514\0Mprivate inventory integrity failure\0nct_orders_inventory_integrity\0\0"));
        assertThat(factory.from(failure, "/api/me/orders/1").getStatus()).isEqualTo(409);
        assertThat(factory.from(failure, "/api/me/orders/1").getProperties()).containsEntry("code", "INVENTORY_CONFLICT");
        var unrelated = new org.postgresql.util.PSQLException(new org.postgresql.util.ServerErrorMessage(
                "SERROR\0C23514\0Mprivate integrity failure\0nck_orders_merchandise\0\0"));
        assertThat(factory.from(unrelated, "/api/me/orders/1").getStatus()).isEqualTo(500);
        assertThat(factory.from(new SQLException("ct_orders_inventory_integrity", "23514"), "/api/me/orders/1").getStatus()).isEqualTo(500);
    }
    @Test void from_shouldRecognizeOnlyStructuredVoucherConstraintsAndKeepCheckoutCode() {
        var named = new org.hibernate.exception.ConstraintViolationException("private SQL", new SQLException("private", "23514"), "ct_voucher_usage_integrity");
        assertThat(factory.from(named, "/api/me/cart/voucher").getStatus()).isEqualTo(409);
        assertThat(factory.from(named, "/api/me/cart/voucher").getProperties()).containsEntry("code", "VOUCHER_CONFLICT");
        assertThat(factory.from(named, "/api/me/cart/checkout").getProperties()).containsEntry("code", "CHECKOUT_CONFLICT");
        assertThat(factory.from(new SQLException("ct_voucher_usage_integrity", "23514"), "/api/me/cart/voucher").getStatus()).isEqualTo(500);
        var structured = new org.postgresql.util.PSQLException(new org.postgresql.util.ServerErrorMessage(
                "SERROR\0C23514\0Mprivate voucher failure\0nct_voucher_usage_integrity\0\0"));
        assertThat(factory.from(structured, "/api/me/cart/voucher").getStatus()).isEqualTo(409);
    }
    ApiErrorFactory factory = new ApiErrorFactory();

    @Test void from_shouldRecognizeOnlyNamedInventoryIntegrityAndKeepCheckoutAdaptation() {
        RuntimeException named = new org.hibernate.exception.ConstraintViolationException("private SQL", new SQLException("private", "23514"), "ct_orders_inventory_integrity");
        assertThat(factory.from(named, "/api/me/orders/1").getStatus()).isEqualTo(409);
        assertThat(factory.from(named, "/api/me/orders/1").getProperties()).containsEntry("code", "INVENTORY_CONFLICT");
        assertThat(factory.from(named, "/api/me/cart/checkout").getProperties()).containsEntry("code", "CHECKOUT_CONFLICT");
        assertThat(factory.from(named, "/api/me/orders/1").getDetail()).doesNotContain("private");
        RuntimeException unrelated = new org.hibernate.exception.ConstraintViolationException("private SQL", new SQLException("private", "23514"), "ck_orders_merchandise");
        assertThat(factory.from(unrelated, "/api/me/orders/1").getStatus()).isEqualTo(500);
    }

    @Test void from_shouldMapOnlyActualLockFailuresToConflict() {
        assertThat(factory.from(new DataAccessResourceFailureException("safe", new SQLException("private", "55P03")), "/api/me/cart").getStatus()).isEqualTo(409);
        assertThat(factory.from(new DataAccessResourceFailureException("safe", new SQLException("private", "40P01")), "/api/me/cart").getStatus()).isEqualTo(409);
        assertThat(factory.from(new DataAccessResourceFailureException("safe", new SQLException("private", "23514")), "/api/me/cart").getStatus()).isEqualTo(500);
        assertThat(factory.from(new IllegalStateException("private detail"), "/api/me/cart").getDetail()).doesNotContain("private detail");
        assertThat(factory.from(new SQLException("private", "55P03"), "/api/me/cart/items/1").getProperties()).containsEntry("code", "CART_CONFLICT");
        assertThat(factory.from(new SQLException("private", "55P03"), "/api/me/profile").getProperties()).doesNotContainKey("code");
    }
}
