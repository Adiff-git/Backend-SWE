package com.example.demo.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class WishlistPersistenceErrorTest {
    @ParameterizedTest
    @CsvSource({"fk_wishlist_items_user_id,23503,401", "fk_wishlist_items_product_id,23503,404",
            "unrelated_fk,23503,500", "uk_wishlist_items_user_id_product_id,23505,500"})
    void from_shouldTranslateOnlyKnownFailures_whenTransactionHasFailed(String constraint, String state, int status) {
        ConstraintViolationException failure = new ConstraintViolationException("private database details",
                new SQLException("private SQL", state), constraint);
        ApiError error = new ApiErrorFactory().from(new RuntimeException(failure), "/api/me/wishlist");
        assertThat(error.getStatus()).isEqualTo(status);
        assertThat(error.getDetail()).doesNotContain("private", constraint);
    }
}
