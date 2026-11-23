package com.example.demo.catalog.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Source safety checks only. PostgreSQL syntax/upgrade/rollback proof belongs to T014. */
class AdminCatalogMigrationSourceTest {
    @Test
    void finalGuards_shouldCoverProductStatusAndChildren_andValidateExistingRows() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V28__admin_catalog_management.sql"));
        String original = Files.readString(Path.of("src/main/resources/db/migration/V3__enforce_catalog_relationships.sql"));
        assertThat(sql).contains("CREATE OR REPLACE FUNCTION check_catalog_variant_invariants",
                "CREATE OR REPLACE FUNCTION check_product_gallery_invariants", "FOR UPDATE", "ORDER BY id",
                "enabled_count = 0", "selling_price <= 0", "AND active", "lifecycle = 'PUBLISHED' AND image_count = 0",
                "PERFORM validate_admin_catalog_hierarchy()", "PERFORM validate_admin_catalog_product(product_id)",
                "DEFERRABLE INITIALLY DEFERRED", "WHERE NOT a.cycle");
        assertThat(original).contains("AFTER INSERT OR UPDATE ON products", "DEFERRABLE INITIALLY DEFERRED");
        assertThat(sql).doesNotContain("ALTER TABLE inventory", "DROP TRIGGER ct_product_variants_inventory_initialize");
    }

    @Test
    void migration_shouldKeepExistingBusinessRecordsAndIdentityConstraints() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V28__admin_catalog_management.sql"));
        assertThat(sql).contains("DEFAULT TRUE", "UNPUBLISHED", "version BIGINT NOT NULL DEFAULT 0",
                "last_restored_at TIMESTAMPTZ", "uk_categories_root_name_key", "uk_categories_parent_name_key", "uk_brands_name_key");
        assertThat(sql).doesNotContain("UPDATE products SET", "UPDATE product_variants SET", "DELETE FROM",
                "TRUNCATE", "DROP TABLE", "DROP COLUMN", "DROP INDEX uk_product_variants_sku_ci",
                "DROP CONSTRAINT fk_product_image_uploads_product_image_id", "DISABLE TRIGGER", "CONCURRENTLY");
        assertThat(sql).contains("CREATE UNIQUE INDEX uk_product_images_product_id_display_order",
                "WHERE active", "WHERE active AND is_primary", "CHECK (active OR NOT is_primary)");
    }

    @Test
    void nameNormalizer_shouldUseApprovedOuterWhitespaceWithoutRewritingNames() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V28__admin_catalog_management.sql"));
        String preflight = Files.readString(Path.of("../specs/013-admin-catalog-management/contracts/data-preflight.sql"));
        String whitespace = "U&'\\0009\\000A\\000B\\000C\\000D\\0020\\0085\\00A0\\1680\\2000\\2001\\2002\\2003\\2004\\2005\\2006\\2007\\2008\\2009\\200A\\2028\\2029\\202F\\205F\\3000'";
        assertThat(sql).contains(whitespace, "LOWER(public.catalog_name_trim(value))");
        assertThat(preflight).contains(whitespace);
        assertThat(sql).doesNotContain("UPDATE brands", "UPDATE categories", "UNACCENT");
    }
}
