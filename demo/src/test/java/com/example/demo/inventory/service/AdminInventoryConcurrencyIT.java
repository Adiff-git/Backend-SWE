package com.example.demo.inventory.service;

import com.example.demo.inventory.dto.request.AdminInventoryCommand;
import com.example.demo.inventory.dto.response.AdminInventoryOperation;
import com.example.demo.inventory.dto.request.AdminInventoryCommand.OperationType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo.inventory.exception.AdminInventoryException;
import com.example.demo.user.integration.PostgresTestSupport;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:17-alpine:///admin_inventory_concurrency",
        "spring.datasource.hikari.maximum-pool-size=6"
})
class AdminInventoryConcurrencyIT extends PostgresTestSupport {
    @Autowired AdminInventoryMutationService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transactions;
    @Autowired jakarta.persistence.EntityManager entities;
    @Autowired com.example.demo.checkout.service.CheckoutService checkout;
    @Autowired com.example.demo.order.service.OrderService orders;
    @Autowired com.example.demo.order.service.OrderCompletionService completion;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    com.example.demo.inventory.repository.AdminInventoryOperationRepository journal;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"checkout", "cancel", "complete", "archive", "disable", "enable"})
    void adminWaitsForCommerceAndCatalogCommit(String competitor) throws Exception {
        var fixture = transactions.execute(status -> com.example.demo.fixture.checkout.CheckoutFixtures.scenario(entities));
        UUID variant = fixture.variant();
        com.example.demo.fixture.inventory.InventoryFixtures.supplyStock(jdbc, variant, 10);
        if (competitor.equals("enable")) {
            jdbc.update("UPDATE products SET status='UNPUBLISHED' WHERE id=?", fixture.product());
            jdbc.update("UPDATE product_variants SET enabled=false WHERE id=?", variant);
        }
        long order = switch (competitor) {
            case "cancel", "complete" -> checkout.checkout(fixture.owner(), "Before_admin_concurrency",
                    new com.example.demo.checkout.dto.request.CheckoutRequest(fixture.address())).getOrder().getId();
            default -> 0L;
        };
        if (competitor.equals("complete")) jdbc.update("UPDATE orders SET status='SHIPPING' WHERE id=?", order);
        CountDownLatch held = new CountDownLatch(1), release = new CountDownLatch(1);
        AdminInventoryCommand command = new AdminInventoryCommand(UUID.randomUUID(), variant, OperationType.CORRECTION, -1, "Cycle count");
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> transactions.executeWithoutResult(status -> {
                compete(competitor, fixture, order);
                held.countDown();
                await(release);
            }));
            try {
                assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();
                var second = pool.submit(() -> service.execute(1, command));
                try {
                    awaitDatabaseLock();
                } finally {
                    release.countDown();
                }
                first.get(15, TimeUnit.SECONDS);
                if (competitor.equals("archive")) {
                    assertThatThrownBy(() -> second.get(15, TimeUnit.SECONDS))
                            .hasCauseInstanceOf(AdminInventoryException.class).hasStackTraceContaining("Archived");
                } else {
                    assertThat(second.get(15, TimeUnit.SECONDS).delta()).isEqualTo("-1");
                }
            } finally {
                release.countDown();
            }
        }
        var counters = jdbc.queryForMap("SELECT stock,reserved FROM inventory_balances WHERE product_variant_id=?", variant);
        assertThat(counters.get("stock")).isEqualTo(switch (competitor) {
            case "archive" -> 10L;
            case "complete" -> 7L;
            default -> 9L;
        });
        assertThat(counters.get("reserved")).isEqualTo(competitor.equals("checkout") ? 2L : 0L);
        assertReservationIntegrity(variant, competitor, order);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM admin_inventory_operations WHERE operation_id=?", Long.class,
                command.operationId())).isEqualTo(competitor.equals("archive") ? 0L : 1L);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"checkout", "cancel", "complete", "archive", "disable", "enable"})
    void commerceAndCatalogWaitForAdminCommit(String competitor) throws Exception {
        var fixture = transactions.execute(status -> com.example.demo.fixture.checkout.CheckoutFixtures.scenario(entities));
        UUID variant = fixture.variant();
        com.example.demo.fixture.inventory.InventoryFixtures.supplyStock(jdbc, variant, 10);
        if (competitor.equals("enable")) {
            jdbc.update("UPDATE products SET status='UNPUBLISHED' WHERE id=?", fixture.product());
            jdbc.update("UPDATE product_variants SET enabled=false WHERE id=?", variant);
        }
        long order = switch (competitor) {
            case "cancel", "complete" -> checkout.checkout(fixture.owner(), "Before_admin_reverse",
                    new com.example.demo.checkout.dto.request.CheckoutRequest(fixture.address())).getOrder().getId();
            default -> 0L;
        };
        if (competitor.equals("complete")) jdbc.update("UPDATE orders SET status='SHIPPING' WHERE id=?", order);
        var held = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var command = new AdminInventoryCommand(UUID.randomUUID(), variant, OperationType.CORRECTION, -1, "Cycle count");
        var delegate = org.mockito.Mockito.mockingDetails(journal).getMockCreationSettings().getDefaultAnswer();
        org.mockito.Mockito.doAnswer(invocation -> {
            var result = delegate.answer(invocation);
            held.countDown();
            await(release);
            return result;
        }).when(journal).insert(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(command),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> service.execute(1, command));
            try {
                assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();
                var second = pool.submit(() -> transactions.executeWithoutResult(status -> compete(competitor, fixture, order)));
                try { awaitDatabaseLock(); } finally { release.countDown(); }
                assertThat(first.get(15, TimeUnit.SECONDS).onHandAfter()).isEqualTo("9");
                second.get(15, TimeUnit.SECONDS);
            } finally { release.countDown(); }
        }
        assertThat(stock(variant)).isEqualTo(competitor.equals("complete") ? 7L : 9L);
        assertThat(jdbc.queryForObject("SELECT reserved FROM inventory_balances WHERE product_variant_id=?", Long.class, variant))
                .isEqualTo(competitor.equals("checkout") ? 2L : 0L);
        assertReservationIntegrity(variant, competitor, order);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM admin_inventory_operations WHERE operation_id=?",
                Long.class, command.operationId())).isOne();
    }

    private void assertReservationIntegrity(UUID variant, String action, long order) {
        assertThat(jdbc.queryForObject("""
                SELECT b.reserved=COALESCE((SELECT SUM(r.quantity) FROM inventory_reservations r
                    WHERE r.inventory_balance_id=b.id AND r.state='ACTIVE'),0)
                FROM inventory_balances b WHERE b.original_variant_id=?
                """, Boolean.class, variant)).isTrue();
        if (order != 0) {
            assertThat(jdbc.queryForObject("SELECT state FROM inventory_reservations WHERE order_id=?", String.class, order))
                    .isEqualTo(action.equals("cancel") ? "RELEASED" : "CONSUMED");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM checkout_receipts WHERE order_id=?", Long.class, order)).isOne();
        }
    }

    private void compete(String action, com.example.demo.fixture.checkout.CheckoutFixtures.Scenario fixture, long order) {
        switch (action) {
            case "checkout" -> checkout.checkout(fixture.owner(), "Admin_checkout_concurrency",
                    new com.example.demo.checkout.dto.request.CheckoutRequest(fixture.address()));
            case "cancel" -> orders.cancelOrder(fixture.owner(), order,
                    new com.example.demo.order.dto.request.CancelOrderRequest("Test"));
            case "complete" -> completion.completeOrder(fixture.owner(), order);
            case "archive" -> jdbc.update("UPDATE products SET status='ARCHIVED' WHERE id=?", fixture.product());
            case "disable" -> {
                jdbc.update("UPDATE products SET status='UNPUBLISHED' WHERE id=?", fixture.product());
                jdbc.update("UPDATE product_variants SET enabled=false WHERE id=?", fixture.variant());
            }
            case "enable" -> {
                // Match Catalog's Product-first claim before the deferred Variant invariants run.
                jdbc.queryForObject("SELECT id FROM products WHERE id=? FOR UPDATE", UUID.class, fixture.product());
                jdbc.update("UPDATE product_variants SET enabled=true WHERE id=?", fixture.variant());
            }
            default -> throw new IllegalArgumentException(action);
        }
    }

    @Test void replenishmentStoresBigintMaximumAndRollsBackOverflow() {
        UUID variant = variant();
        assertThat(service.execute(1, command(variant, Long.MAX_VALUE)).onHandAfter()).isEqualTo("9223372036854775807");
        assertThatThrownBy(() -> service.execute(1, command(variant, 1)))
                .isInstanceOfSatisfying(AdminInventoryException.class,
                        e -> assertThat(e.getCode()).isEqualTo("INVENTORY_INVALID_QUANTITY"));
        assertThat(stock(variant)).isEqualTo(Long.MAX_VALUE);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(com.example.demo.catalog.entity.ProductStatus.class)
    void correctionUsesParentLifecycleIndependentlyOfVariantEnabledState(
            com.example.demo.catalog.entity.ProductStatus lifecycle) {
        for (boolean enabled : new boolean[]{true, false}) {
            UUID variant = com.example.demo.fixture.inventory.AdminInventoryFixtures.variant(jdbc, transactions, lifecycle, enabled);
            AdminInventoryCommand correction = new AdminInventoryCommand(UUID.randomUUID(), variant, OperationType.CORRECTION, 1, "Count");
            if (lifecycle == com.example.demo.catalog.entity.ProductStatus.ARCHIVED) {
                assertThatThrownBy(() -> service.execute(1, correction))
                        .isInstanceOfSatisfying(AdminInventoryException.class,
                                e -> assertThat(e.getCode()).isEqualTo("INVENTORY_ARCHIVED"));
                assertThat(stock(variant)).isZero();
            } else {
                assertThat(service.execute(1, correction).onHandAfter()).isEqualTo("1");
            }
            assertThat(jdbc.queryForObject("SELECT enabled FROM product_variants WHERE id=?", Boolean.class, variant))
                    .isEqualTo(enabled);
        }
    }

    @Test void realActiveReservationDefinesTheCorrectionFloor() {
        var fixture = transactions.execute(status -> com.example.demo.fixture.checkout.CheckoutFixtures.scenario(entities));
        UUID variant = fixture.variant();
        long order = checkout.checkout(fixture.owner(), "Reservation_floor_test",
                new com.example.demo.checkout.dto.request.CheckoutRequest(fixture.address())).getOrder().getId();
        assertThat(service.execute(1, new AdminInventoryCommand(UUID.randomUUID(), variant, OperationType.CORRECTION,
                -998, "Physical count")).availableAfter()).isEqualTo("0");
        assertThatThrownBy(() -> service.execute(1, new AdminInventoryCommand(UUID.randomUUID(), variant,
                OperationType.CORRECTION, -1, "Would invade allocation")))
                .isInstanceOfSatisfying(AdminInventoryException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("INVENTORY_RESERVATION_FLOOR");
                    assertThat(e.getProperties()).containsEntry("reserved", "2").containsEntry("available", "0");
                });
        assertThat(stock(variant)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT state FROM inventory_reservations WHERE order_id=?", String.class, order))
                .isEqualTo("ACTIVE");
    }

    @Test void journalUniqueConflictRollsBackBeforeFreshReplay() throws Exception {
        UUID variant = variant();
        AdminInventoryCommand command = command(variant, 3);
        var inserted = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var imported = pool.submit(() -> transactions.executeWithoutResult(status -> {
                jdbc.update("UPDATE inventory_balances SET stock=3 WHERE original_variant_id=?", variant);
                jdbc.update("""
                        INSERT INTO admin_inventory_operations
                            (admin_id,operation_id,inventory_balance_id,original_variant_id,operation_type,
                             delta,on_hand_before,on_hand_after,reserved_observed,occurred_at)
                        SELECT 1,?,id,original_variant_id,'REPLENISHMENT',3,0,3,0,now()
                        FROM inventory_balances WHERE original_variant_id=?
                        """, command.operationId(), variant);
                inserted.countDown();
                await(release);
            }));
            try {
                assertThat(inserted.await(10, TimeUnit.SECONDS)).isTrue();
                var pending = pool.submit(() -> service.execute(1, command));
                try { awaitDatabaseLock(); } finally { release.countDown(); }
                imported.get(15, TimeUnit.SECONDS);
                assertThat(pending.get(15, TimeUnit.SECONDS).onHandAfter()).isEqualTo("3");
            } finally { release.countDown(); }
        }
        assertThat(stock(variant)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM admin_inventory_operations WHERE operation_id=?",
                Long.class, command.operationId())).isOne();
    }

    private void awaitDatabaseLock() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            if (Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT EXISTS(SELECT 1 FROM pg_stat_activity
                      WHERE datname=current_database() AND wait_event_type='Lock'
                        AND (query LIKE '%inventory_balances%' OR query LIKE '%FOR SHARE%' OR query LIKE '%FOR UPDATE%' OR query LIKE '%UPDATE products%' OR query LIKE '%UPDATE product_variants%'))
                    """, Boolean.class))) return;
            Thread.sleep(20);
        }
        throw new AssertionError("Expected competing inventory transaction to wait on a PostgreSQL lock");
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Test lock release timed out");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    @Test void committedWritersPreserveTotalsAndSameKeyCommitsOnce() throws Exception {
        UUID variant = variant();
        race(() -> service.execute(1, command(variant, 2)), () -> service.execute(2, command(variant, 3)));
        assertThat(stock(variant)).isEqualTo(5);
        AdminInventoryCommand same = command(variant, 4);
        race(() -> attempt(1, same), () -> attempt(1, same));
        assertThat(service.execute(1, same).delta()).isEqualTo("4");
        assertThat(stock(variant)).isEqualTo(9);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM admin_inventory_operations WHERE original_variant_id=?", Long.class, variant)).isEqualTo(3);
        assertThatThrownBy(() -> service.execute(1, new AdminInventoryCommand(same.operationId(), variant, same.type(), 8, null)))
                .isInstanceOfSatisfying(AdminInventoryException.class,
                        e -> assertThat(e.getCode()).isEqualTo("INVENTORY_OPERATION_CONFLICT"));
    }

    @Test void concurrentConflictingPayloadsConvergeAndKeysAreActorScoped() throws Exception {
        UUID variant = variant(), other = variant();
        UUID id = UUID.randomUUID();
        var left = new AdminInventoryCommand(id, variant, OperationType.REPLENISHMENT, 2, null);
        var right = new AdminInventoryCommand(id, other, OperationType.CORRECTION, 3, "Count");
        race(() -> competingPayload(left), () -> competingPayload(right));
        assertThat(stock(variant) + stock(other)).isIn(2L, 3L);
        var winner = stock(variant) == 2 ? left : right;
        var loser = winner.equals(left) ? right : left;
        var replay = service.execute(1, winner);
        assertThat(replay.onHandAfter()).isEqualTo(Long.toString(winner.delta()));
        requirePayloadConflict(loser);
        requirePayloadConflict(new AdminInventoryCommand(id, winner.variantId(), winner.type(), winner.delta(), "Different reason"));
        assertThat(service.execute(2, loser).adminId()).isEqualTo("2");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM admin_inventory_operations WHERE operation_id=?", Long.class, id))
                .isEqualTo(2);
    }

    private AdminInventoryOperation competingPayload(AdminInventoryCommand command) {
        try { return service.execute(1, command); }
        catch (AdminInventoryException rejected) {
            assertThat(rejected.getCode()).isIn("INVENTORY_CONCURRENT_CONFLICT", "INVENTORY_OPERATION_CONFLICT");
            return null;
        }
    }

    private void requirePayloadConflict(AdminInventoryCommand command) {
        assertThatThrownBy(() -> service.execute(1, command))
                .isInstanceOfSatisfying(AdminInventoryException.class,
                        e -> assertThat(e.getCode()).isEqualTo("INVENTORY_OPERATION_CONFLICT"));
    }

    @Test void rollbackAfterStockChangeLeavesNoCommittedReplayAndCanRetry() {
        UUID variant = variant();
        AdminInventoryCommand command = command(variant, 4);
        String table = "admin_inventory_operations";
        jdbc.execute("ALTER TABLE " + table + " ADD CONSTRAINT zz_test_journal_failure CHECK (false) NOT VALID");
        try {
            assertThatThrownBy(() -> service.execute(1, command)).isInstanceOf(RuntimeException.class);
            assertThat(stock(variant)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM admin_inventory_operations WHERE operation_id=?", Long.class,
                    command.operationId())).isZero();
        } finally {
            jdbc.execute("ALTER TABLE " + table + " DROP CONSTRAINT zz_test_journal_failure");
        }
        assertThat(service.execute(1, command).onHandAfter()).isEqualTo("4");
    }

    @Test void deferredReservationReconciliationRollsBackAdminBalanceAndJournal() {
        UUID variant = variant();
        AdminInventoryCommand command = command(variant, 4);
        jdbc.execute("""
                CREATE FUNCTION inventory_test_break_reconciliation() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    UPDATE inventory_balances SET reserved=1 WHERE id=NEW.inventory_balance_id;
                    RETURN NEW;
                END;
                $$
                """);
        jdbc.execute("""
                CREATE TRIGGER inventory_test_deferred_failure AFTER INSERT ON admin_inventory_operations
                FOR EACH ROW EXECUTE FUNCTION inventory_test_break_reconciliation()
                """);
        try {
            assertThatThrownBy(() -> service.execute(1, command))
                    .hasStackTraceContaining("Inventory counter differs from active allocations");
            assertThat(stock(variant)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM admin_inventory_operations WHERE operation_id=?",
                    Long.class, command.operationId())).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER inventory_test_deferred_failure ON admin_inventory_operations");
            jdbc.execute("DROP FUNCTION inventory_test_break_reconciliation()");
        }
        assertThat(service.execute(1, command).onHandAfter()).isEqualTo("4");
    }

    private AdminInventoryOperation attempt(long actor, AdminInventoryCommand command) {
        try { return service.execute(actor, command); }
        catch (AdminInventoryException e) {
            assertThat(e.getCode()).isEqualTo("INVENTORY_CONCURRENT_CONFLICT");
            return null;
        }
    }

    private void race(Callable<AdminInventoryOperation> first, Callable<AdminInventoryOperation> second) throws Exception {
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> { start.await(); return first.call(); });
            var b = pool.submit(() -> { start.await(); return second.call(); });
            start.countDown();
            assertThat(java.util.Arrays.asList(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS)))
                    .anyMatch(java.util.Objects::nonNull);
        }
    }

    private UUID variant() {
        UUID category = UUID.randomUUID(), product = UUID.randomUUID(), variant = UUID.randomUUID();
        jdbc.update("INSERT INTO categories(id,name) VALUES (?,?)", category, "Inventory " + category);
        jdbc.update("INSERT INTO products(id,category_id,name,status) VALUES (?,?,?,'DRAFT')", product, category, "Concurrency");
        jdbc.update("INSERT INTO product_variants(id,product_id,sku,option_combination,selling_price) VALUES (?,?,?,'{}',100)", variant, product, "INV_" + variant);
        return variant;
    }

    private AdminInventoryCommand command(UUID variant, long delta) {
        return new AdminInventoryCommand(UUID.randomUUID(), variant, OperationType.REPLENISHMENT, delta, null);
    }

    private long stock(UUID variant) {
        return jdbc.queryForObject("SELECT stock FROM inventory_balances WHERE product_variant_id=?", Long.class, variant);
    }
}
