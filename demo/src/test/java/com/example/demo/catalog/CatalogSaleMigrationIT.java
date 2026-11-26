package com.example.demo.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import com.example.demo.fixture.checkout.CheckoutFixtures;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class CatalogSaleMigrationIT {
    @Test
    void upgrade_shouldPreserveVariantIdentityOptionsAndPayablePrice() throws Exception {
        String url = databaseUrl();
        try (Connection anchor = DriverManager.getConnection(url, "test", "test")) {
            migrate(url, "27");
            UUID variant = insertLegacyVariant(anchor, "123456");
            DriverManagerDataSource source = new DriverManagerDataSource(url, "test", "test");
            JdbcTemplate jdbc = new JdbcTemplate(source);
            TransactionTemplate transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
            Long owner = CheckoutFixtures.customer(jdbc);
            CheckoutFixtures.HistoricalOrder historical = transactions.execute(status -> {
                CheckoutFixtures.HistoricalOrder created = CheckoutFixtures.order(jdbc, owner);
                CheckoutFixtures.receipt(jdbc, owner, created, "Sale_upgrade_key_1234");
                return created;
            });
            String originalItem = jdbc.queryForObject("SELECT TO_JSONB(i)::TEXT FROM order_items i WHERE id=?",
                    String.class, historical.itemId());
            String originalReceipt = jdbc.queryForObject("SELECT receipt_body::TEXT FROM checkout_receipts WHERE order_id=?",
                    String.class, historical.id());
            Long balance = jdbc.queryForObject("SELECT id FROM inventory_balances WHERE original_variant_id=?", Long.class, variant);
            migrate(url, "28");
            assertThat(jdbc.queryForObject("SELECT TO_JSONB(i)::TEXT FROM order_items i WHERE id=?",
                    String.class, historical.itemId())).isEqualTo(originalItem);
            assertThat(jdbc.queryForObject("SELECT receipt_body::TEXT FROM checkout_receipts WHERE order_id=?",
                    String.class, historical.id())).isEqualTo(originalReceipt);
            assertThat(jdbc.queryForObject("SELECT id FROM inventory_balances WHERE product_variant_id=?",
                    Long.class, variant)).isEqualTo(balance);
            try (PreparedStatement query = anchor.prepareStatement(
                    "SELECT v.base_price,v.selling_price,v.option_combination::TEXT,p.sale_percentage,p.sale_enabled "
                    + "FROM product_variants v JOIN products p ON p.id=v.product_id WHERE v.id=?")) {
                query.setObject(1, variant);
                try (ResultSet row = query.executeQuery()) {
                    assertThat(row.next()).isTrue();
                    assertThat(row.getBigDecimal(1)).isEqualByComparingTo("123456");
                    assertThat(row.getBigDecimal(2)).isEqualByComparingTo("123456");
                    assertThat(row.getString(3)).isEqualTo("{}");
                    assertThat(row.getBigDecimal(4)).isNull();
                    assertThat(row.getBoolean(5)).isFalse();
                }
            }
            assertRejected(anchor, "UPDATE product_variants SET selling_price=1 WHERE id='" + variant + "'");
            execute(anchor, "UPDATE product_variants SET base_price=654321 WHERE id='" + variant + "'");
            try (Statement query = anchor.createStatement(); ResultSet row = query.executeQuery(
                    "SELECT base_price=selling_price FROM product_variants WHERE id='" + variant + "'")) {
                assertThat(row.next()).isTrue();
                assertThat(row.getBoolean(1)).isTrue();
            }
        }
    }

    @Test
    void upgrade_shouldRejectInvalidLegacyPriceWithoutCoercion() throws Exception {
        String url = databaseUrl();
        try (Connection anchor = DriverManager.getConnection(url, "test", "test")) {
            migrate(url, "27");
            UUID variant = insertLegacyVariant(anchor, "-1");
            assertThatThrownBy(() -> migrate(url, "28")).hasStackTraceContaining("legacy price");
            try (Statement query = anchor.createStatement(); ResultSet row = query.executeQuery(
                    "SELECT selling_price FROM product_variants WHERE id='" + variant + "'")) {
                assertThat(row.next()).isTrue();
                assertThat(row.getBigDecimal(1)).isEqualByComparingTo("-1");
            }
        }
    }

    @Test
    void saleConfiguration_shouldRejectOutOfRangePrecisionAndInvalidBounds() throws Exception {
        String url = databaseUrl();
        try (Connection anchor = DriverManager.getConnection(url, "test", "test")) {
            migrate(url, "27");
            insertLegacyVariant(anchor, "100");
            migrate(url, "28");
            for (String invalid : new String[] {"0", "-1", "100.01", "1.001", "'NaN'", "'Infinity'"}) {
                assertRejected(anchor, "UPDATE products SET sale_percentage=" + invalid);
            }
            assertRejected(anchor, "UPDATE products SET sale_enabled=TRUE");
            assertRejected(anchor, "UPDATE products SET sale_percentage=20,sale_starts_at='2026-11-27T00:00:00Z',sale_ends_at='2026-11-26T00:00:00Z'");
            execute(anchor, "UPDATE products SET sale_percentage=100,sale_enabled=TRUE");
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false,true})
    void optionUpgrade_shouldRejectExistingCollisionsOrDimensionMismatchWithoutMutation(boolean mismatch) throws Exception {
        String url = databaseUrl();
        try (Connection anchor = DriverManager.getConnection(url,"test","test")) {
            migrate(url,"27");
            UUID first = insertLegacyVariant(anchor,"100");
            migrate(url,"28");
            JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(url,"test","test"));
            UUID parent = jdbc.queryForObject("SELECT product_id FROM product_variants WHERE id=?",UUID.class,first);
            jdbc.update("UPDATE product_variants SET option_combination=CAST(? AS JSONB) WHERE id=?",
                    "{\" Color \":\" RED \"}",first);
            jdbc.update("INSERT INTO product_variants(id,product_id,sku,option_combination,base_price) VALUES (?,?,?,CAST(? AS JSONB),100)",
                    UUID.randomUUID(),parent,"AUDIT-"+UUID.randomUUID(),mismatch ? "{\"size\":\"M\"}" : "{\"color\":\"red\"}");
            var before = jdbc.queryForList("SELECT TO_JSONB(v)::TEXT FROM product_variants v ORDER BY id",String.class);
            assertThatThrownBy(() -> migrate(url,"29")).hasStackTraceContaining(mismatch ? "dimension" : "canonical");
            assertThat(jdbc.queryForList("SELECT TO_JSONB(v)::TEXT FROM product_variants v ORDER BY id",String.class)).isEqualTo(before);
            assertThat(jdbc.queryForObject("SELECT MAX(version::INTEGER) FROM flyway_schema_history WHERE success",Integer.class)).isEqualTo(28);
        }
    }

    private String databaseUrl() {
        return "jdbc:tc:postgresql:17-alpine:///sale_upgrade_" + UUID.randomUUID().toString().replace("-", "");
    }

    private void migrate(String url, String version) {
        Flyway.configure().dataSource(url, "test", "test").locations("classpath:db/migration")
                .target(version).load().migrate();
    }

    private UUID insertLegacyVariant(Connection connection, String price) throws SQLException {
        UUID category = UUID.randomUUID();
        UUID product = UUID.randomUUID();
        UUID variant = UUID.randomUUID();
        connection.setAutoCommit(false);
        try {
            execute(connection, "INSERT INTO categories(id,name) VALUES ('" + category + "','Sale upgrade')");
            execute(connection, "INSERT INTO products(id,category_id,name,status) VALUES ('" + product + "','" + category + "','Legacy','PUBLISHED')");
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO product_variants(id,product_id,sku,option_combination,selling_price) VALUES (?,?,?,'{}',?)")) {
                insert.setObject(1, variant);
                insert.setObject(2, product);
                insert.setString(3, "SALE-" + variant);
                insert.setBigDecimal(4, new BigDecimal(price));
                insert.executeUpdate();
            }
            connection.commit();
        } finally {
            connection.setAutoCommit(true);
        }
        return variant;
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) { statement.execute(sql); }
    }

    private void assertRejected(Connection connection, String sql) {
        assertThatThrownBy(() -> execute(connection, sql)).isInstanceOf(SQLException.class);
    }
}
