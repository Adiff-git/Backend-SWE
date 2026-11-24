package com.example.demo.inventory.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo.catalog.entity.ProductStatus;
import com.example.demo.fixture.inventory.AdminInventoryFixtures;
import com.example.demo.fixture.inventory.InventoryFixtures;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

class AdminInventoryUpgradeIT {
    @Test void syntheticV27UpgradeAndBackupRestorePreserveHistoricalAllocations() throws Exception {
        try (var database = new PostgreSQLContainer<>("postgres:17-alpine")) {
            database.start();
            var source = new DriverManagerDataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword());
            var jdbc = new JdbcTemplate(source);
            var transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
            Flyway.configure().dataSource(source).target("27").load().migrate();
            UUID category = UUID.randomUUID(), product = UUID.randomUUID(), variant = UUID.randomUUID();
            transactions.executeWithoutResult(status -> {
                jdbc.update("INSERT INTO categories(id,name) VALUES (?,?)", category, AdminInventoryFixtures.V27);
                jdbc.update("INSERT INTO products(id,category_id,name,status) VALUES (?,?,?,'DRAFT')", product, category, "Cycle count");
                jdbc.update("INSERT INTO product_variants(id,product_id,sku,option_combination,selling_price) VALUES (?,?,?,'{}',100)",
                        variant, product, "JOURNAL-UPGRADE");
            });
            long actor = jdbc.queryForObject("""
                    INSERT INTO users(username,password,account_status,joined_at) VALUES ('inventory-upgrade','test','ACTIVE',now())
                    RETURNING id
                    """, Long.class);
            transactions.executeWithoutResult(status -> {
                var legacy = com.example.demo.fixture.checkout.CheckoutFixtures.order(jdbc, actor);
                com.example.demo.fixture.checkout.CheckoutFixtures.receipt(jdbc, actor, legacy, "UC037_upgrade_legacy");
            });
            long balance = jdbc.queryForObject("SELECT id FROM inventory_balances WHERE product_variant_id=?", Long.class, variant);
            InventoryFixtures.supplyStock(jdbc, variant, 10);
            long active = transactions.execute(status -> allocate(jdbc, actor, variant, balance));
            long released = transactions.execute(status -> allocate(jdbc, actor, variant, balance));
            long consumed = transactions.execute(status -> allocate(jdbc, actor, variant, balance));
            transactions.executeWithoutResult(status -> {
                var event = java.sql.Timestamp.from(Instant.parse("2026-01-02T00:00:00Z"));
                jdbc.update("UPDATE orders SET status='CANCELLED',cancelled_at=? WHERE id=?", event, released);
                jdbc.update("UPDATE inventory_reservations SET state='RELEASED',resolved_at=? WHERE order_id=?", event, released);
                jdbc.update("UPDATE orders SET status='COMPLETED',completed_at=? WHERE id=?", event, consumed);
                jdbc.update("UPDATE inventory_reservations SET state='CONSUMED',resolved_at=? WHERE order_id=?", event, consumed);
                jdbc.update("UPDATE inventory_balances SET stock=stock-1,reserved=reserved-2 WHERE id=?", balance);
            });
            assertThat(jdbc.queryForObject("SELECT state FROM inventory_reservations WHERE order_id=?", String.class, active)).isEqualTo("ACTIVE");
            var before28 = protectedRows(jdbc);
            Flyway.configure().dataSource(source).target("28").load().migrate();
            assertThat(protectedRows(jdbc)).isEqualTo(before28);
            assertThat(jdbc.queryForObject("SELECT enabled FROM product_variants WHERE id=?", Boolean.class, variant)).isTrue();
            jdbc.update("UPDATE product_variants SET enabled=false WHERE id=?", variant);
            for (ProductStatus lifecycle : ProductStatus.values()) {
                AdminInventoryFixtures.variant(jdbc, transactions, lifecycle, true);
                AdminInventoryFixtures.variant(jdbc, transactions, lifecycle, false);
            }
            var before29 = protectedRows(jdbc);
            var checksums = history(jdbc);

            String user = database.getUsername(), db = database.getDatabaseName();
            assertThat(database.execInContainer("pg_dump", "-U", user, "-Fc", "-f", "/tmp/uc037-v28.dump", db).getExitCode()).isZero();
            assertThat(database.execInContainer("createdb", "-U", user, "uc037_restore").getExitCode()).isZero();
            var restore = database.execInContainer("sh", "-ec", """
                    pg_restore --no-owner --no-acl --file=/tmp/uc037-restore.sql /tmp/uc037-v28.dump
                    old="SELECT pg_catalog.set_config('search_path', '', false);"
                    test "$(grep -Fxc "$old" /tmp/uc037-restore.sql)" -eq 1
                    sed "s/^SELECT pg_catalog.set_config('search_path', '', false);$/SELECT pg_catalog.set_config('search_path', 'pg_catalog,public', false);/" /tmp/uc037-restore.sql > /tmp/uc037-restore-reviewed.sql
                    psql -X -v ON_ERROR_STOP=1 --single-transaction -U "$1" -d uc037_restore -f /tmp/uc037-restore-reviewed.sql
                    """, "uc037-restore", user);
            assertThat(restore.getExitCode()).as(restore.getStderr()).isZero();
            var clone = new DriverManagerDataSource(
                    "jdbc:postgresql://" + database.getHost() + ":" + database.getMappedPort(5432) + "/uc037_restore",
                    user, database.getPassword());
            var restored = new JdbcTemplate(clone);
            assertThat(history(restored)).isEqualTo(checksums);
            assertThat(protectedRows(restored)).isEqualTo(before29);
            var forcedFailure = new org.flywaydb.core.api.callback.Callback() {
                @Override public boolean supports(org.flywaydb.core.api.callback.Event event,
                        org.flywaydb.core.api.callback.Context context) {
                    return event == org.flywaydb.core.api.callback.Event.AFTER_EACH_MIGRATE;
                }
                @Override public boolean canHandleInTransaction(org.flywaydb.core.api.callback.Event event,
                        org.flywaydb.core.api.callback.Context context) { return true; }
                @Override public void handle(org.flywaydb.core.api.callback.Event event,
                        org.flywaydb.core.api.callback.Context context) {
                    throw new org.flywaydb.core.api.FlywayException("Injected V29 rollback");
                }
                @Override public String getCallbackName() { return "uc037-injected-rollback"; }
            };
            assertThatThrownBy(() -> Flyway.configure().dataSource(clone).target("29").callbacks(forcedFailure)
                    .load().migrate()).hasStackTraceContaining("Injected V29 rollback");
            assertThat(restored.queryForObject("SELECT to_regclass('admin_inventory_operations') IS NULL", Boolean.class)).isTrue();
            assertThat(restored.queryForObject("SELECT max(version::int) FROM flyway_schema_history", Integer.class)).isEqualTo(28);
            assertThat(protectedRows(restored)).isEqualTo(before29);
            Flyway.configure().dataSource(clone).target("29").load().migrate();
            assertThat(protectedRows(restored)).isEqualTo(before29);
            assertThat(history(restored)).isEqualTo(checksums);
            assertThat(restored.queryForObject("SELECT count(*) FROM admin_inventory_operations", Long.class)).isZero();
        }
    }

    private long allocate(JdbcTemplate jdbc, long actor, UUID variant, long balance) {
        long order = InventoryFixtures.order(jdbc, actor, true, variant, 1);
        long item = jdbc.queryForObject("SELECT id FROM order_items WHERE order_id=?", Long.class, order);
        InventoryFixtures.activeReservation(jdbc, order, item, balance, variant, 1);
        jdbc.update("UPDATE inventory_balances SET reserved=reserved+1 WHERE id=?", balance);
        return order;
    }

    private List<String> protectedRows(JdbcTemplate jdbc) {
        return List.of(snapshot(jdbc, "inventory_balances"), snapshot(jdbc, "inventory_reservations"),
                snapshot(jdbc, "orders"), snapshot(jdbc, "order_items"), snapshot(jdbc, "checkout_receipts"));
    }

    private String snapshot(JdbcTemplate jdbc, String table) {
        return jdbc.queryForObject("SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY id)::text,'[]') FROM " + table + " t", String.class);
    }

    private List<String> history(JdbcTemplate jdbc) {
        return jdbc.queryForList("""
                SELECT version||':'||checksum FROM flyway_schema_history WHERE version::int <= 28 ORDER BY installed_rank
                """, String.class);
    }
}
