package com.example.demo.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.catalog.exception.AdminCatalogException;
import java.sql.SQLException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AdminCatalogErrorSupportTest {
    private final AdminCatalogErrorSupport support = new AdminCatalogErrorSupport(new ApiErrorFactory());

    @Test
    void application_shouldPreserveSafeRevisionExtensions_butDropUnapprovedProperties() {
        ApiError error = support.from(new AdminCatalogException(409, "CATALOG_STALE_VERSION", "Reload catalog",
                Map.of("expectedVersion", "7", "currentVersion", "8", "objectKey", "private")), "/api/admin/catalog/products");
        assertThat(error.getStatus()).isEqualTo(409);
        assertThat(error.getProperties()).containsEntry("code", "CATALOG_STALE_VERSION")
                .containsEntry("expectedVersion", "7").doesNotContainKey("objectKey");
    }

    @Test
    void constraints_shouldTranslateOnlyNamedCatalogIntegrity_withoutSqlDisclosure() {
        Exception known = new org.hibernate.exception.ConstraintViolationException("private SQL", new SQLException("secret", "23505"), "uk_brands_name_key");
        ApiError error = support.from(known, "/api/admin/catalog/brands");
        assertThat(error.getStatus()).isEqualTo(409);
        assertThat(error.getProperties()).containsEntry("code", "CATALOG_NAME_CONFLICT");
        Exception unknown = new org.hibernate.exception.ConstraintViolationException("private SQL", new SQLException("secret", "23505"), "unrelated_constraint");
        assertThat(support.from(unknown, "/api/admin/catalog/brands").getStatus()).isEqualTo(500);
        assertThat(error.getDetail()).doesNotContain("secret", "SQL", "uk_brands");
    }

    @Test
    void lockFailures_shouldRequireReload_withoutAutomaticRetry() {
        ApiError error = support.from(new SQLException("private", "40P01"), "/api/admin/catalog/products");
        assertThat(error.getStatus()).isEqualTo(409);
        assertThat(error.getProperties()).containsEntry("code", "CATALOG_CONCURRENT_CONFLICT");
    }
}
