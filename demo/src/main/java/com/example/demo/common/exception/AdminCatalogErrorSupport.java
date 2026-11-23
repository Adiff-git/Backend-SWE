package com.example.demo.common.exception;

import com.example.demo.catalog.exception.AdminCatalogException;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Named catalog failures only; unknown constraints/driver text never become public diagnostics. */
@Component
@RequiredArgsConstructor
public class AdminCatalogErrorSupport {
    private static final Set<String> SAFE_PROPERTIES = Set.of("violations", "resourceId", "expectedVersion", "currentVersion",
            "expectedTaxonomyVersion", "currentTaxonomyVersion");
    private static final Map<String, String> CONFLICTS = Map.ofEntries(
            Map.entry("uk_categories_parent_name_key", "CATALOG_NAME_CONFLICT"),
            Map.entry("uk_categories_root_name_key", "CATALOG_NAME_CONFLICT"),
            Map.entry("uk_brands_name_key", "CATALOG_NAME_CONFLICT"), Map.entry("uk_brands_code", "CATALOG_NAME_CONFLICT"),
            Map.entry("uk_product_variants_sku_ci", "CATALOG_SKU_CONFLICT"),
            Map.entry("uk_product_variants_product_id_option_combination", "CATALOG_OPTION_CONFLICT"),
            Map.entry("uk_product_images_product_id_display_order", "CATALOG_GALLERY_CONFLICT"),
            Map.entry("uk_product_images_product_id_primary", "CATALOG_GALLERY_CONFLICT"),
            Map.entry("uk_product_images_source", "CATALOG_GALLERY_CONFLICT"),
            Map.entry("uk_product_images_object_key", "CATALOG_GALLERY_CONFLICT"));
    private static final Set<String> DEPENDENCIES = Set.of("fk_products_category_id", "products_brand_id_fkey",
            "fk_categories_parent_category_id", "fk_product_variants_product_id", "fk_product_images_product_id",
            "fk_product_image_uploads_product_image_id");
    private static final Set<String> VALIDATION = Set.of("ct_product_variants_integrity", "ct_categories_hierarchy_integrity",
            "ck_categories_name_key", "ck_brands_name_key", "ck_product_variants_price_whole_finite",
            "ck_product_variants_technical_specifications", "ck_product_images_active_primary", "ck_product_images_display_order",
            "ck_product_images_content_type", "ck_product_images_byte_size", "ck_product_images_dimensions",
            "ck_product_images_alt_text_nonblank", "ck_product_images_object_key_nonblank");
    private final ApiErrorFactory factory;

    public ApiError from(Exception failure, String path) {
        if (failure instanceof ApplicationException app) {
            ApiError error = coded(app.getStatus(), app.getCode() == null ? defaultCode(app.getStatus()) : app.getCode(), app.getMessage(), path);
            app.getProperties().forEach((key, value) -> { if (SAFE_PROPERTIES.contains(key)) error.setProperty(key, value); });
            return error;
        }
        if (failure instanceof org.springframework.web.HttpMediaTypeNotSupportedException) return coded(415, "CATALOG_INVALID_REQUEST", "JSON media type is required", path);
        if (failure instanceof org.springframework.http.converter.HttpMessageNotReadableException
                || failure instanceof org.springframework.web.bind.MethodArgumentNotValidException
                || failure instanceof org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
                || failure instanceof org.springframework.web.bind.ServletRequestBindingException
                || failure instanceof jakarta.validation.ConstraintViolationException) {
            return coded(400, "CATALOG_INVALID_REQUEST", "Invalid catalog request", path);
        }
        if (failure instanceof org.springframework.dao.OptimisticLockingFailureException) {
            return coded(409, "CATALOG_STALE_VERSION", "Catalog changed; reload before retrying", path);
        }
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            String constraint = null;
            String state = null;
            if (cause instanceof org.hibernate.exception.ConstraintViolationException named) {
                constraint = named.getConstraintName(); state = named.getSQLState();
            } else if (cause instanceof SQLException sql) {
                state = sql.getSQLState(); constraint = factory.postgresConstraint(sql);
            }
            if ("55P03".equals(state) || "40P01".equals(state)) return coded(409, "CATALOG_CONCURRENT_CONFLICT", "Concurrent catalog change; reload before retrying", path);
            if (constraint == null || !Set.of("23503", "23505", "23514").contains(state == null ? "" : state)) continue;
            if (CONFLICTS.containsKey(constraint)) return coded(409, CONFLICTS.get(constraint), "Catalog identity or gallery conflicts", path);
            if (DEPENDENCIES.contains(constraint)) return coded(409, "CATALOG_DEPENDENCY_CONFLICT", "Catalog references prevent this change", path);
            if (Set.of("ct_products_variant_integrity", "ct_product_images_gallery_integrity").contains(constraint)) {
                return coded(422, "CATALOG_PUBLICATION_INVALID", "Product is not publication-ready", path);
            }
            if (VALIDATION.contains(constraint)) return coded(422, "CATALOG_VALIDATION_FAILED", "Invalid catalog data", path);
            if (constraint.equals("ct_catalog_taxonomy_state_integrity") || Set.of("ck_products_version", "ck_categories_version", "ck_brands_version", "ck_catalog_taxonomy_state_version").contains(constraint)) {
                return coded(503, "CATALOG_MAINTENANCE_REQUIRED", "Catalog revision service is unavailable", path);
            }
        }
        return factory.create(500, "An unexpected error occurred", path, Map.of());
    }

    private ApiError coded(int status, String code, String detail, String path) {
        ApiError error = factory.create(status, detail, path, Map.of());
        if (code != null) error.setProperty("code", code);
        return error;
    }

    private static String defaultCode(int status) {
        return switch (status) {
            case 400 -> "CATALOG_INVALID_REQUEST";
            case 404 -> "CATALOG_RESOURCE_NOT_FOUND";
            case 409 -> "CATALOG_DEPENDENCY_CONFLICT";
            case 422 -> "CATALOG_VALIDATION_FAILED";
            case 503 -> "CATALOG_DEPENDENCY_UNAVAILABLE";
            default -> null;
        };
    }
}
