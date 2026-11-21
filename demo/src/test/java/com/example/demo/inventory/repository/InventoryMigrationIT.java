package com.example.demo.inventory.repository;

import com.example.demo.fixture.inventory.InventoryFixtures;
import com.example.demo.fixture.checkout.CheckoutFixtures;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Final-commit assertions on a disposable database, never a rollback-only test context. */
class InventoryMigrationIT {
    private static final PostgreSQLContainer<?> DATABASE = new PostgreSQLContainer<>("postgres:17-alpine");
    private JdbcTemplate jdbc;
    private TransactionTemplate transactions;
    private DriverManagerDataSource source;
    private long owner;
    private UUID variant;

    @BeforeAll static void startDatabase() { DATABASE.start(); }
    @AfterAll static void stopDatabase() { DATABASE.stop(); }

    @BeforeEach void prepareV24() {
        source = new DriverManagerDataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
        jdbc = new JdbcTemplate(source);
        jdbc.execute("DROP SCHEMA public CASCADE");
        jdbc.execute("CREATE SCHEMA public");
        Flyway.configure().dataSource(source).target("24").load().migrate();
        transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
        owner = jdbc.queryForObject("INSERT INTO users(username,password,account_status,joined_at) VALUES ('inventory-migration','test','ACTIVE',CURRENT_TIMESTAMP) RETURNING id", Long.class);
        variant = catalogVariant();
    }

    private UUID catalogVariant() {
        UUID category = UUID.randomUUID();
        UUID product = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        transactions.executeWithoutResult(s -> {
            jdbc.update("INSERT INTO categories(id,name) VALUES (?,'Inventory category')", category);
            jdbc.update("INSERT INTO products(id,category_id,name,status) VALUES (?,?,'Inventory product','DRAFT')", product, category);
            jdbc.update("INSERT INTO product_variants(id,product_id,sku,option_combination,selling_price) VALUES (?,?,?,'{\"choice\":\"original\"}',100)", id, product, "INV-" + id);
        });
        return id;
    }

    private void migrateInventory() {
        Flyway.configure().dataSource(source).load().migrate();
        assertThat(jdbc.queryForObject("SELECT MAX(version::INTEGER) FROM flyway_schema_history WHERE success", Integer.class)).isEqualTo(27);
    }

    private long balance() {
        return jdbc.queryForObject("SELECT id FROM inventory_balances WHERE original_variant_id=?", Long.class, variant);
    }

    private void addSiblingVariant() {
        UUID sibling = UUID.randomUUID();
        jdbc.update("INSERT INTO product_variants(id,product_id,sku,option_combination,selling_price) SELECT ?,product_id,?,'{\"replacement\":\"yes\"}',100 FROM product_variants WHERE id=?", sibling, "INV-" + sibling, variant);
    }

    private long managedOrder(int quantity) {
        long order = InventoryFixtures.order(jdbc, owner, true, variant, quantity);
        long item = jdbc.queryForObject("SELECT id FROM order_items WHERE order_id=?", Long.class, order);
        InventoryFixtures.activeReservation(jdbc, order, item, balance(), variant, quantity);
        jdbc.update("UPDATE inventory_balances SET reserved=reserved+? WHERE id=?", quantity, balance());
        return order;
    }

    @Test void upgradeAndProvision_shouldPreserveHistoricalDataAndCreateOnlyZeroBalances() {
        long historical = transactions.execute(s -> {
            Long order = jdbc.queryForObject("""
                    INSERT INTO orders(customer_id,order_number,placed_at,status,recipient_name_snapshot,recipient_phone_snapshot,
                    delivery_address_snapshot,merchandise_total,payable_total)
                    VALUES (?,'HISTORICAL',CURRENT_TIMESTAMP,'PENDING','Name','0123456789','Address',100,100) RETURNING id
                    """, Long.class, owner);
            jdbc.update("INSERT INTO order_items(order_id,product_variant_id,product_name_snapshot,variant_sku_snapshot,variant_options_snapshot,quantity,unit_price_snapshot,line_total) VALUES (?,?,'Old','Old','{}',1,100,100)", order, variant);
            return order;
        });
        CheckoutFixtures.HistoricalOrder receiptOrder = transactions.execute(s -> CheckoutFixtures.preInventoryOrder(jdbc, owner));
        transactions.executeWithoutResult(s -> CheckoutFixtures.receipt(jdbc, owner, receiptOrder, "Inventory_upgrade_key"));
        var receiptsBefore = jdbc.queryForList("SELECT * FROM checkout_receipts");
        jdbc.update("INSERT INTO carts(user_id) VALUES (?)", owner);
        var cartsBefore = jdbc.queryForList("SELECT id,user_id FROM carts");
        var before = jdbc.queryForMap("SELECT * FROM order_items WHERE order_id=?", historical);
        migrateInventory();
        assertThat(jdbc.queryForList("SELECT * FROM checkout_receipts")).isEqualTo(receiptsBefore);
        assertThat(jdbc.queryForList("SELECT id,user_id FROM carts")).isEqualTo(cartsBefore);
        assertThat(jdbc.queryForMap("SELECT * FROM order_items WHERE order_id=?", historical)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT inventory_managed FROM orders WHERE id=?", Boolean.class, historical)).isFalse();
        assertThat(jdbc.queryForObject("SELECT stock+reserved FROM inventory_balances WHERE original_variant_id=?", Long.class, variant)).isZero();
        UUID next = catalogVariant();
        assertThat(jdbc.queryForObject("SELECT stock+reserved FROM inventory_balances WHERE original_variant_id=?", Long.class, next)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_reservations", Long.class)).isZero();
        assertConstraint(() -> jdbc.update("UPDATE orders SET inventory_managed=true WHERE id=?", historical), "ct_orders_inventory_participation");
        jdbc.update("UPDATE orders SET status='FAILED' WHERE id=?", historical);
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, historical)).isEqualTo("FAILED");
    }

    @Test void cleanApplyAndVariantRollback_shouldLeaveNoPositiveSeedOrOrphanProvisioning() {
        jdbc.execute("DROP SCHEMA public CASCADE");
        jdbc.execute("CREATE SCHEMA public");
        migrateInventory();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_balances", Long.class)).isZero();
        UUID rolledBack = transactions.execute(status -> {
            UUID id = catalogVariant();
            assertThat(jdbc.queryForObject("SELECT stock+reserved FROM inventory_balances WHERE original_variant_id=?", Long.class, id)).isZero();
            status.setRollbackOnly();
            return id;
        });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_variants WHERE id=?", Long.class, rolledBack)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_balances WHERE original_variant_id=?", Long.class, rolledBack)).isZero();
        assertThat(jdbc.queryForList("SELECT conname FROM pg_constraint WHERE connamespace='public'::regnamespace AND octet_length(conname)>63")).isEmpty();
    }

    @Test void managedCompletenessAndReconciliation_shouldRejectAtCommitAndRollbackEverything() {
        migrateInventory();
        InventoryFixtures.supplyStock(jdbc, variant, 10);
        assertConstraint(() -> transactions.executeWithoutResult(s -> InventoryFixtures.order(jdbc, owner, true, variant, 3)), "ct_orders_inventory_integrity");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders", Long.class)).isZero();
        assertConstraint(() -> transactions.executeWithoutResult(s -> jdbc.update("UPDATE inventory_balances SET reserved=3 WHERE id=?", balance())), "ct_inventory_balances_allocation_integrity");
        long order = transactions.execute(s -> managedOrder(3));
        assertThat(jdbc.queryForObject("SELECT reserved FROM inventory_balances WHERE id=?", Long.class, balance())).isEqualTo(3);
        assertThatThrownBy(() -> transactions.executeWithoutResult(s -> jdbc.update("UPDATE order_items SET quantity=4,line_total=400 WHERE order_id=?", order))).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT quantity FROM inventory_reservations WHERE order_id=?", Integer.class, order)).isEqualTo(3);
    }

    @Test void localBoundsAndRetention_shouldRejectOverflowCorruptionAndActiveDeletion() {
        migrateInventory();
        for (String mutation : new String[]{"stock=-1", "reserved=-1", "reserved=1", "stock=9223372036854775808"}) {
            assertThatThrownBy(() -> jdbc.update("UPDATE inventory_balances SET " + mutation + " WHERE id=?", balance())).isInstanceOf(RuntimeException.class);
        }
        InventoryFixtures.supplyStock(jdbc, variant, Long.MAX_VALUE);
        assertThat(jdbc.queryForObject("SELECT stock FROM inventory_balances WHERE id=?", Long.class, balance())).isEqualTo(Long.MAX_VALUE);
        assertConstraint(() -> jdbc.update("DELETE FROM inventory_balances WHERE id=?", balance()), "ct_inventory_balance_retention");
        transactions.execute(s -> managedOrder(3));
        addSiblingVariant();
        assertConstraint(() -> jdbc.update("DELETE FROM product_variants WHERE id=?", variant), "ct_inventory_balance_retention");
        assertThat(jdbc.queryForObject("SELECT product_variant_id FROM inventory_balances WHERE id=?", UUID.class, balance())).isEqualTo(variant);
        UUID missing = catalogVariant();
        InventoryFixtures.removeUnusedBalance(jdbc, missing);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_balances WHERE original_variant_id=?", Long.class, missing)).isZero();
    }

    @Test void resolution_shouldKeepTerminalAttributionStockAndFirstTimestamp() {
        migrateInventory();
        InventoryFixtures.supplyStock(jdbc, variant, 10);
        long order = transactions.execute(s -> managedOrder(3));
        transactions.executeWithoutResult(s -> {
            jdbc.update("UPDATE orders SET status='CANCELLED',cancelled_at=placed_at+INTERVAL '1 second' WHERE id=?", order);
            jdbc.update("UPDATE inventory_reservations SET state='RELEASED',resolved_at=reserved_at+INTERVAL '1 second' WHERE order_id=?", order);
            jdbc.update("UPDATE inventory_balances SET reserved=reserved-3 WHERE id=?", balance());
        });
        var terminal = jdbc.queryForMap("SELECT * FROM inventory_reservations WHERE order_id=?", order);
        for (String mutation : new String[]{"state='ACTIVE',resolved_at=NULL", "quantity=2", "resolved_at=resolved_at+INTERVAL '1 second'"}) {
            assertConstraint(() -> jdbc.update("UPDATE inventory_reservations SET " + mutation + " WHERE order_id=?", order), "ct_inventory_reservations_immutable");
        }
        assertThatThrownBy(() -> jdbc.update("DELETE FROM inventory_reservations WHERE order_id=?", order)).isInstanceOf(RuntimeException.class);
        addSiblingVariant();
        jdbc.update("DELETE FROM product_variants WHERE id=?", variant);
        assertThat(jdbc.queryForObject("SELECT product_variant_id FROM inventory_balances WHERE id=?", UUID.class, balance())).isNull();
        assertThat(jdbc.queryForObject("SELECT stock FROM inventory_balances WHERE id=?", Long.class, balance())).isEqualTo(10);
        assertThat(jdbc.queryForMap("SELECT * FROM inventory_reservations WHERE order_id=?", order)).isEqualTo(terminal);
    }

    @Test void attribution_shouldRejectLegacyAllocationsForeignItemsAndTerminalBootstrap() {
        migrateInventory();
        InventoryFixtures.supplyStock(jdbc, variant, 10);
        long legacy = transactions.execute(s -> InventoryFixtures.order(jdbc, owner, false, variant, 1));
        long item = jdbc.queryForObject("SELECT id FROM order_items WHERE order_id=?", Long.class, legacy);
        assertThatThrownBy(() -> transactions.executeWithoutResult(s -> InventoryFixtures.activeReservation(jdbc, legacy, item, balance(), variant, 1)))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> transactions.executeWithoutResult(s -> {
            long order = InventoryFixtures.order(jdbc, owner, true, variant, 1);
            InventoryFixtures.activeReservation(jdbc, order, item, balance(), variant, 1);
        })).isInstanceOf(RuntimeException.class);
        assertConstraint(() -> transactions.executeWithoutResult(s -> {
            long order = InventoryFixtures.order(jdbc, owner, true, variant, 1);
            long ownItem = jdbc.queryForObject("SELECT id FROM order_items WHERE order_id=?", Long.class, order);
            jdbc.update("INSERT INTO inventory_reservations(order_id,order_item_id,inventory_balance_id,original_variant_id,quantity,state,reserved_at,resolved_at) VALUES (?,?,?,?,1,'CONSUMED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", order, ownItem, balance(), variant);
        }), "ct_inventory_reservations_immutable");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_reservations", Long.class)).isZero();
    }

    @Test void concurrentLiveDetachment_shouldRejectAStaleDirectAllocationAtActualCommit() throws Exception {
        migrateInventory();
        InventoryFixtures.supplyStock(jdbc, variant, 10);
        try (var held = source.getConnection(); var worker = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            held.setAutoCommit(false);
            try (var detach = held.prepareStatement("UPDATE inventory_balances SET product_variant_id=NULL WHERE original_variant_id=?")) {
                detach.setObject(1, variant);
                assertThat(detach.executeUpdate()).isEqualTo(1);
            }
            var allocated = worker.submit(() -> transactions.execute(s -> {
                jdbc.execute("SET LOCAL lock_timeout='5s'");
                return managedOrder(3); // Noncooperative SQL may see the old live link before its FK/counter lock wait.
            }));
            try {
                boolean blocked = false;
                long deadline = System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
                while (!blocked && System.nanoTime()<deadline) {
                    blocked = Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock' AND query ILIKE '%inventory_%' AND pid<>pg_backend_pid())", Boolean.class));
                    Thread.onSpinWait();
                }
                assertThat(blocked).as("stale allocation actually overlaps the detached balance lock").isTrue();
                held.commit();
                assertThatThrownBy(() -> allocated.get(15, java.util.concurrent.TimeUnit.SECONDS)).satisfies(failure ->
                        assertThat(new com.example.demo.common.exception.ApiErrorFactory().from(failure.getCause(), "/api/me/cart/checkout").getStatus()).isEqualTo(409));
            } finally { held.rollback(); }
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_reservations", Long.class)).isZero();
        assertThat(jdbc.queryForMap("SELECT stock,reserved FROM inventory_balances WHERE original_variant_id=?", variant))
                .containsEntry("stock", 10L).containsEntry("reserved", 0L);
    }

    private static void assertConstraint(Runnable action, String constraint) {
        assertThatThrownBy(action::run).rootCause().isInstanceOfSatisfying(org.postgresql.util.PSQLException.class, failure -> {
            assertThat(failure.getSQLState()).isEqualTo("23514");
            assertThat(failure.getServerErrorMessage().getConstraint()).isEqualTo(constraint);
        });
    }
}
