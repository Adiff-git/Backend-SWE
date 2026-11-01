package com.example.demo.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class CatalogEnhancementMigrationIT {
    @Test
    void migration_shouldPreserveLegacyIdentityAndEnforceMetadataConstraints_whenUpgradingV9() throws Exception {
        String url = "jdbc:tc:postgresql:17-alpine:///catalog_enhancement_upgrade_test";
        try (Connection anchor = DriverManager.getConnection(url, "test", "test")) {
            Flyway.configure().dataSource(url, "test", "test").target("9").load().migrate();
            UUID category = UUID.randomUUID();
            UUID product = UUID.randomUUID();
            UUID variant = UUID.randomUUID();
            anchor.setAutoCommit(false);
            try (Statement sql = anchor.createStatement()) {
                sql.execute("INSERT INTO categories(id,name) VALUES ('" + category + "','Laptop')");
                sql.execute("INSERT INTO products(id,category_id,name,status) VALUES ('" + product + "','" + category + "','Legacy','PUBLISHED')");
                sql.execute("INSERT INTO product_variants(id,product_id,sku,option_combination,selling_price) VALUES ('" + variant + "','" + product + "','legacy-sku','{}',1234)");
            }
            anchor.commit();
            anchor.setAutoCommit(true);
            Flyway.configure().dataSource(url, "test", "test").load().migrate();
            try (Statement sql = anchor.createStatement(); java.sql.ResultSet rows = sql.executeQuery(
                    "SELECT p.id,p.category_id,p.status,p.brand_id,v.id,v.sku,v.selling_price,v.option_combination::text,v.technical_specifications::text FROM products p JOIN product_variants v ON v.product_id=p.id")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getObject(1)).isEqualTo(product);
                assertThat(rows.getObject(2)).isEqualTo(category);
                assertThat(rows.getString(3)).isEqualTo("PUBLISHED");
                assertThat(rows.getObject(4)).isNull();
                assertThat(rows.getObject(5)).isEqualTo(variant);
                assertThat(rows.getString(6)).isEqualTo("legacy-sku");
                assertThat(rows.getBigDecimal(7)).isEqualByComparingTo("1234");
                assertThat(rows.getString(8)).isEqualTo("{}");
                assertThat(rows.getString(9)).contains("\"schema_version\": 1", "\"values\": {}");
            }
            UUID brand = UUID.randomUUID();
            try (Statement sql = anchor.createStatement()) {
                sql.execute("INSERT INTO brands(id,code,name) VALUES ('" + brand + "','lenovo','Lenovo')");
                sql.execute("UPDATE products SET brand_id='" + brand + "' WHERE id='" + product + "'");
                sql.execute("UPDATE product_variants SET technical_specifications='{\"schema_version\":1,\"values\":{\"nfc_supported\":false}}' WHERE id='" + variant + "'");
                String hash = "a".repeat(64);
                sql.execute("INSERT INTO catalog_source_links(id,source,source_key,source_url,product_variant_id,source_payload_sha256,normalized_content_sha256,normalization_version,approved_manifest_sha256,updated_at) VALUES ('" + UUID.randomUUID() + "','tgdd','item','https://example.test/item','" + variant + "','" + hash + "','" + hash + "','1','" + hash + "',CURRENT_TIMESTAMP)");
            }
            assertSqlRejected(anchor, "INSERT INTO brands(id,code,name) VALUES ('" + UUID.randomUUID() + "','lenovo','Duplicate')", "23505");
            assertSqlRejected(anchor, "UPDATE product_variants SET technical_specifications='{}' WHERE id='" + variant + "'", "23514");
            assertSqlRejected(anchor, "UPDATE product_variants SET technical_specifications='{\"schema_version\":2,\"values\":{}}' WHERE id='" + variant + "'", "23514");
            assertSqlRejected(anchor, "UPDATE catalog_source_links SET source_payload_sha256='bad'", "23514");
            assertSqlRejected(anchor, "INSERT INTO catalog_source_links SELECT '" + UUID.randomUUID() + "',source,source_key,source_url,source_record_id,product_variant_id,source_payload_sha256,normalized_content_sha256,normalization_version,approved_manifest_sha256,updated_at FROM catalog_source_links", "23505");
            assertSqlRejected(anchor, "DELETE FROM brands WHERE id='" + brand + "'", "23503");
        }
    }
    private void assertSqlRejected(Connection connection, String sql, String state) {
        assertThatThrownBy(() -> {
            try (Statement statement = connection.createStatement()) { statement.execute(sql); }
        }).isInstanceOfSatisfying(SQLException.class, failure -> assertThat(failure.getSQLState()).isEqualTo(state));
    }
}
