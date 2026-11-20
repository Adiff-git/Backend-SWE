package com.example.demo.inventory.service;

import com.example.demo.checkout.dto.request.CheckoutRequest;
import com.example.demo.checkout.dto.response.CheckoutReceiptResponse;
import com.example.demo.checkout.exception.NoEligibleCheckoutItemsException;
import com.example.demo.checkout.service.CheckoutService;
import com.example.demo.config.CheckoutIntegrationSupport;
import com.example.demo.fixture.checkout.CheckoutFixtures;
import com.example.demo.fixture.inventory.InventoryFixtures;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(properties = {"spring.datasource.hikari.maximum-pool-size=6", "checkout.lock-timeout-ms=2000"})
class InventoryConcurrencyIT extends CheckoutIntegrationSupport {
    @Autowired CheckoutService checkout;
    @MockitoSpyBean InventoryService inventory;
    @Autowired javax.sql.DataSource source;
    @Autowired com.example.demo.order.service.OrderService orders;
    @Autowired com.example.demo.order.service.OrderCompletionService completion;
    @MockitoSpyBean com.example.demo.order.repository.OrderRepository orderRows;

    @org.junit.jupiter.api.Test
    void duplicateCancellation_shouldResolveOnlyOnceAfterAnObservedOrderLockWait() throws Exception {
        assertDuplicateTerminal(false);
    }

    @org.junit.jupiter.api.Test
    void duplicateCompletion_shouldConsumeOnlyOnceAfterAnObservedOrderLockWait() throws Exception {
        assertDuplicateTerminal(true);
    }

    private void assertDuplicateTerminal(boolean completing) throws Exception {
        if (completing) InventoryFixtures.supplyStock(jdbc, fixture.variant(), 2);
        long order = checkout.checkout(fixture.owner(), "Cancel_race_key_123", new CheckoutRequest(fixture.address())).getOrder().getId();
        if (completing) jdbc.update("UPDATE orders SET status='SHIPPING' WHERE id=?", order);
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean first = new AtomicBoolean(true);
        org.mockito.stubbing.Answer<?> orderDelegate = org.mockito.Mockito.mockingDetails(orderRows).getMockCreationSettings().getDefaultAnswer();
        doAnswer(call -> {
            Object result = orderDelegate.answer(call);
            if (first.compareAndSet(true, false)) {
                held.countDown();
                if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Cancellation release timed out");
            }
            return result;
        }).when(orderRows).findOwnedForUpdate(fixture.owner(), order);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var a = workers.submit(() -> terminal(order, "first", completing));
            assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();
            var b = workers.submit(() -> terminal(order, "second", completing));
            try { awaitLock("orders"); } finally { release.countDown(); }
            a.get(15, TimeUnit.SECONDS);
            b.get(15, TimeUnit.SECONDS);
        } finally { release.countDown(); }
        if (completing) org.mockito.Mockito.verify(inventory).consumeReservations(org.mockito.ArgumentMatchers.any());
        else {
            org.mockito.Mockito.verify(inventory).releaseReservations(org.mockito.ArgumentMatchers.any());
            assertThat(jdbc.queryForObject("SELECT cancellation_reason FROM orders WHERE id=?", String.class, order)).isEqualTo("first");
        }
        assertThat(jdbc.queryForObject("SELECT state FROM inventory_reservations WHERE order_id=?", String.class, order)).isEqualTo(completing ? "CONSUMED" : "RELEASED");
        assertThat(jdbc.queryForMap("SELECT stock,reserved FROM inventory_balances WHERE original_variant_id=?", fixture.variant()))
                .containsEntry("stock", completing ? 0L : 1000L).containsEntry("reserved", 0L);
    }

    private void terminal(long id, String reason, boolean completing) {
        if (completing) completion.completeOrder(fixture.owner(), id);
        else orders.cancelOrder(fixture.owner(), id, new com.example.demo.order.dto.request.CancelOrderRequest(reason));
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void opposingTerminalAction_shouldRejectWithoutBroadeningStateEligibility(boolean completing) throws Exception {
        InventoryFixtures.supplyStock(jdbc, fixture.variant(), 2);
        long id = checkout.checkout(fixture.owner(), "Opposing_action_123", new CheckoutRequest(fixture.address())).getOrder().getId();
        if (completing) jdbc.update("UPDATE orders SET status='SHIPPING' WHERE id=?", id);
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean first = new AtomicBoolean(true);
        org.mockito.stubbing.Answer<?> delegate = org.mockito.Mockito.mockingDetails(orderRows).getMockCreationSettings().getDefaultAnswer();
        doAnswer(call -> {
            Object result = delegate.answer(call);
            if (first.compareAndSet(true, false)) {
                held.countDown();
                if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Opposing terminal release timed out");
            }
            return result;
        }).when(orderRows).findOwnedForUpdate(fixture.owner(), id);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var winner = workers.submit(() -> terminal(id, "winner", completing));
            assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();
            var rejected = workers.submit(() -> terminal(id, "loser", !completing));
            try { awaitLock("orders"); } finally { release.countDown(); }
            winner.get(15, TimeUnit.SECONDS);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> rejected.get(15, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(com.example.demo.order.exception.OrderConflictException.class);
        } finally { release.countDown(); }
        assertThat(jdbc.queryForMap("SELECT stock,reserved FROM inventory_balances WHERE original_variant_id=?", fixture.variant()))
                .containsEntry("stock", completing ? 0L : 2L).containsEntry("reserved", 0L);
        assertThat(jdbc.queryForObject("SELECT state FROM inventory_reservations WHERE order_id=?", String.class, id)).isEqualTo(completing ? "CONSUMED" : "RELEASED");
    }

    @org.junit.jupiter.api.Test
    void anotherCustomersCheckout_shouldPreserveTheAvailabilityAcrossOverlappingConsumption() throws Exception {
        InventoryFixtures.supplyStock(jdbc, fixture.variant(), 4);
        long original = checkout.checkout(fixture.owner(), "Consume_overlap_123", new CheckoutRequest(fixture.address())).getOrder().getId();
        jdbc.update("UPDATE orders SET status='SHIPPING' WHERE id=?", original);
        CheckoutFixtures.Scenario other = transactions.execute(s -> CheckoutFixtures.scenario(entities));
        jdbc.update("UPDATE cart_items SET product_id=?,product_variant_id=? WHERE id=?", fixture.product(), fixture.variant(), other.item());
        CountDownLatch consumed = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        doAnswer(call -> {
            Object result = call.callRealMethod();
            consumed.countDown();
            if (!commit.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Consumption commit timed out");
            return result;
        }).when(inventory).consumeReservations(org.mockito.ArgumentMatchers.any());
        try (var workers = Executors.newFixedThreadPool(2)) {
            var completed = workers.submit(() -> completion.completeOrder(fixture.owner(), original));
            assertThat(consumed.await(10, TimeUnit.SECONDS)).isTrue();
            var purchased = workers.submit(() -> checkout.checkout(other.owner(), "Other_consume_1234", new CheckoutRequest(other.address())));
            try { awaitLock("inventory_balances"); } finally { commit.countDown(); }
            completed.get(15, TimeUnit.SECONDS);
            assertThat(purchased.get(15, TimeUnit.SECONDS).getOrder().getId()).isNotEqualTo(original);
        } finally { commit.countDown(); }
        assertThat(jdbc.queryForMap("SELECT stock,reserved FROM inventory_balances WHERE original_variant_id=?", fixture.variant()))
                .containsEntry("stock", 2L).containsEntry("reserved", 2L);
        assertThat(jdbc.queryForList("SELECT state FROM inventory_reservations ORDER BY id", String.class)).containsExactly("CONSUMED", "ACTIVE");
    }

    @org.junit.jupiter.api.Test
    void anotherCustomersCheckout_shouldReserveTheUnitsReleasedByAnOverlappingCancellation() throws Exception {
        InventoryFixtures.supplyStock(jdbc, fixture.variant(), 2);
        long original = checkout.checkout(fixture.owner(), "Release_overlap_123", new CheckoutRequest(fixture.address())).getOrder().getId();
        CheckoutFixtures.Scenario other = transactions.execute(s -> CheckoutFixtures.scenario(entities));
        jdbc.update("UPDATE cart_items SET product_id=?,product_variant_id=? WHERE id=?", fixture.product(), fixture.variant(), other.item());
        CountDownLatch released = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        doAnswer(call -> {
            Object result = call.callRealMethod();
            released.countDown();
            if (!commit.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Release commit timed out");
            return result;
        }).when(inventory).releaseReservations(org.mockito.ArgumentMatchers.any());
        try (var workers = Executors.newFixedThreadPool(2)) {
            var cancellation = workers.submit(() -> orders.cancelOrder(fixture.owner(), original, new com.example.demo.order.dto.request.CancelOrderRequest(null)));
            assertThat(released.await(10, TimeUnit.SECONDS)).isTrue();
            var purchase = workers.submit(() -> checkout.checkout(other.owner(), "After_release_1234", new CheckoutRequest(other.address())));
            try { awaitLock("inventory_balances"); } finally { commit.countDown(); }
            cancellation.get(15, TimeUnit.SECONDS);
            assertThat(purchase.get(15, TimeUnit.SECONDS).getOrder().getId()).isNotEqualTo(original);
        } finally { commit.countDown(); }
        assertThat(jdbc.queryForMap("SELECT stock,reserved FROM inventory_balances WHERE original_variant_id=?", fixture.variant()))
                .containsEntry("stock", 2L).containsEntry("reserved", 2L);
        assertThat(jdbc.queryForList("SELECT state FROM inventory_reservations ORDER BY id", String.class)).containsExactly("RELEASED", "ACTIVE");
    }

    private void awaitLock(String table) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (System.nanoTime() < deadline) {
            if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE wait_event_type='Lock' AND query ILIKE ? AND pid<>pg_backend_pid())", Boolean.class, "%"+table+"%"))) return;
            Thread.onSpinWait();
        }
        throw new IllegalStateException("No actual lock wait on " + table);
    }

    @org.junit.jupiter.api.Test
    void heldInventoryLock_shouldRollBackAndAllowExplicitSameKeyRetry() throws Exception {
        try (var connection = source.getConnection()) {
            connection.setAutoCommit(false);
            try (var lock = connection.prepareStatement("SELECT id FROM inventory_balances WHERE original_variant_id=? FOR UPDATE")) {
                lock.setObject(1, fixture.variant());
                try (var locked = lock.executeQuery()) { assertThat(locked.next()).isTrue(); }
            }
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> checkout.checkout(fixture.owner(), "Stock_timeout_1234", new CheckoutRequest(fixture.address())))
                    .isInstanceOfSatisfying(RuntimeException.class, failure -> assertThat(new com.example.demo.common.exception.ApiErrorFactory()
                            .from(failure, "/api/me/cart/checkout").getStatus()).isEqualTo(409));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders", Long.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_reservations", Long.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM checkout_receipts", Long.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT reserved FROM inventory_balances WHERE original_variant_id=?", Long.class, fixture.variant())).isZero();
            connection.rollback();
        }
        assertThat(checkout.checkout(fixture.owner(), "Stock_timeout_1234", new CheckoutRequest(fixture.address())).getOrder().getItems()).hasSize(1);
    }

    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> stockCases() {
        return java.util.stream.Stream.of(org.junit.jupiter.params.provider.Arguments.of(1, false),
                org.junit.jupiter.params.provider.Arguments.of(2, false),
                org.junit.jupiter.params.provider.Arguments.of(1, true));
    }

    @ParameterizedTest @MethodSource("stockCases")
    void differentCustomers_shouldCompeteForLastFullQuantityWithoutOverselling(int quantity, boolean reverseMultipleVariants) throws Exception {
        CheckoutFixtures.Scenario other = transactions.execute(s -> CheckoutFixtures.scenario(entities));
        jdbc.update("UPDATE cart_items SET product_id=?,product_variant_id=?,quantity=? WHERE id=?", fixture.product(), fixture.variant(), quantity, other.item());
        jdbc.update("UPDATE cart_items SET quantity=? WHERE id=?", quantity, fixture.item());
        InventoryFixtures.supplyStock(jdbc, fixture.variant(), quantity);
        if (reverseMultipleVariants) {
            java.util.UUID secondVariant = jdbc.queryForObject("SELECT product_variant_id FROM cart_items WHERE id=?", java.util.UUID.class, fixture.unselected());
            java.util.UUID secondProduct = jdbc.queryForObject("SELECT product_id FROM cart_items WHERE id=?", java.util.UUID.class, fixture.unselected());
            jdbc.update("UPDATE cart_items SET selected=true,quantity=? WHERE id=?", quantity, fixture.unselected());
            transactions.executeWithoutResult(s -> {
                // Swap both live targets atomically while preserving each cart's uniqueness.
                jdbc.update("DELETE FROM cart_items WHERE id=?", other.unselected());
                jdbc.update("UPDATE cart_items SET product_id=?,product_variant_id=? WHERE id=?", secondProduct, secondVariant, other.item());
                jdbc.update("INSERT INTO cart_items(cart_id,product_id,product_variant_id,quantity,selected,added_at,public_product_name,public_sku,public_options) SELECT cart_id,?,?,?,true,added_at,public_product_name,public_sku,public_options FROM cart_items WHERE id=?", fixture.product(), fixture.variant(), quantity, other.item());
            });
            InventoryFixtures.supplyStock(jdbc, secondVariant, quantity);
        }
        CyclicBarrier together = new CyclicBarrier(2);
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean first = new AtomicBoolean(true);
        doAnswer(call -> {
            together.await(10, TimeUnit.SECONDS);
            Object result = call.callRealMethod();
            if (first.compareAndSet(true, false)) {
                firstLocked.countDown();
                if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Inventory race release timed out");
            }
            return result;
        }).when(inventory).lockAvailability(anyCollection());
        try (var workers = Executors.newFixedThreadPool(2)) {
            var a = workers.submit(() -> attempt(fixture.owner(), fixture.address(), "Customer_a_key_123"));
            var b = workers.submit(() -> attempt(other.owner(), other.address(), "Customer_b_key_123"));
            try {
                assertThat(firstLocked.await(10, TimeUnit.SECONDS)).isTrue();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
                boolean waited = false;
                while (!waited && System.nanoTime() < deadline) {
                    waited = Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE wait_event_type='Lock' AND query LIKE '%inventory_balances%' AND pid<>pg_backend_pid())", Boolean.class));
                    Thread.onSpinWait();
                }
                assertThat(waited).as("different customers actually overlap on shared stock").isTrue();
            } finally { release.countDown(); }
            Object firstResult = a.get(20, TimeUnit.SECONDS);
            Object secondResult = b.get(20, TimeUnit.SECONDS);
            assertThat(java.util.List.of(firstResult, secondResult).stream().filter(CheckoutReceiptResponse.class::isInstance)).hasSize(1);
            assertThat(java.util.List.of(firstResult, secondResult).stream().filter(NoEligibleCheckoutItemsException.class::isInstance)).hasSize(1);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM checkout_receipts", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_reservations", Long.class)).isEqualTo(reverseMultipleVariants ? 2 : 1);
        assertThat(jdbc.queryForMap("SELECT stock,reserved FROM inventory_balances WHERE original_variant_id=?", fixture.variant()))
                .containsEntry("stock", (long) quantity).containsEntry("reserved", (long) quantity);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cart_items WHERE selected", Long.class)).isEqualTo(reverseMultipleVariants ? 2 : 1);
    }

    private Object attempt(long owner, long address, String key) {
        try { return checkout.checkout(owner, key, new CheckoutRequest(address)); }
        catch (NoEligibleCheckoutItemsException shortage) { return shortage; }
    }
}
