package com.example.demo.order.service;

import com.example.demo.checkout.dto.request.CheckoutRequest;
import com.example.demo.checkout.service.CheckoutService;
import com.example.demo.config.CheckoutIntegrationSupport;
import com.example.demo.config.MutableClock;
import com.example.demo.fixture.checkout.CheckoutFixtures;
import com.example.demo.fixture.inventory.InventoryFixtures;
import com.example.demo.fixture.order.OrderFixtures;
import com.example.demo.order.entity.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderCompletionServiceIT extends CheckoutIntegrationSupport {
    @Autowired CheckoutService checkout;
    @Autowired OrderCompletionService completion;
    @Autowired MutableClock clock;
    @Autowired com.example.demo.inventory.service.InventoryService inventory;

    @TestConfiguration(proxyBeanMethods = false) static class EventClock {
        @Bean @Primary MutableClock completionTestClock() { return new MutableClock(); }
    }

    @BeforeEach void setEvent() { clock.set(CheckoutFixtures.PLACED_AT.plusSeconds(60)); }

    private long shippingOrder() {
        InventoryFixtures.supplyStock(jdbc, fixture.variant(), 2);
        long id = checkout.checkout(fixture.owner(), "Completion_key_123", new CheckoutRequest(fixture.address())).getOrder().getId();
        jdbc.update("UPDATE orders SET status='SHIPPING' WHERE id=?", id); // Authorized future progression is a fixture precondition only.
        return id;
    }

    @Test void completion_shouldConsumeAtAvailableZeroAndKeepTheOriginalReceiptAndFirstEvent() {
        long id = shippingOrder();
        var receipt = checkout.checkout(fixture.owner(), "Completion_key_123", new CheckoutRequest(fixture.address()));
        var item = jdbc.queryForMap("SELECT * FROM order_items WHERE order_id=?", id);
        clock.set(clock.instant().plusSeconds(1).plusNanos(789));
        completion.completeOrder(fixture.owner(), id);
        assertThat(jdbc.queryForMap("SELECT stock,reserved FROM inventory_balances WHERE original_variant_id=?", fixture.variant()))
                .containsEntry("stock", 0L).containsEntry("reserved", 0L);
        assertThat(jdbc.queryForObject("SELECT state FROM inventory_reservations WHERE order_id=?", String.class, id)).isEqualTo("CONSUMED");
        assertThat(jdbc.queryForObject("SELECT completed_at=(SELECT resolved_at FROM inventory_reservations WHERE order_id=orders.id) FROM orders WHERE id=?", Boolean.class, id)).isTrue();
        assertThat(jdbc.queryForMap("SELECT * FROM order_items WHERE order_id=?", id)).isEqualTo(item);
        var terminal = jdbc.queryForMap("SELECT * FROM orders WHERE id=?", id);
        clock.set(CheckoutFixtures.PLACED_AT.minusSeconds(1));
        completion.completeOrder(fixture.owner(), id);
        assertThat(jdbc.queryForMap("SELECT * FROM orders WHERE id=?", id)).isEqualTo(terminal);
        assertThat(checkout.checkout(fixture.owner(), "Completion_key_123", new CheckoutRequest(fixture.address()))).usingRecursiveComparison().isEqualTo(receipt);
    }

    @Test void legacyCompletion_shouldChangeNoStockAndForeignOwnerShouldRemainNotFound() {
        long id = transactions.execute(s -> OrderFixtures.persist(entities, fixture.owner(), OrderStatus.SHIPPING, 1).getId());
        var balances = jdbc.queryForList("SELECT * FROM inventory_balances ORDER BY id");
        assertThatThrownBy(() -> completion.completeOrder(fixture.foreign(), id)).isInstanceOf(com.example.demo.order.exception.OrderNotFoundException.class);
        completion.completeOrder(fixture.owner(), id);
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, id)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForList("SELECT * FROM inventory_balances ORDER BY id")).isEqualTo(balances);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_reservations", Long.class)).isZero();
    }

    @Test void completion_shouldRollbackCountersAndStatusWhenTimeOrFinalCommitFails() {
        jdbc.update("UPDATE cart_items SET selected=true WHERE id=?", fixture.unselected());
        long id = shippingOrder();
        var before = jdbc.queryForMap("SELECT * FROM orders WHERE id=?", id);
        var beforeBalances = jdbc.queryForList("SELECT * FROM inventory_balances ORDER BY id");
        clock.set(CheckoutFixtures.PLACED_AT);
        assertThatThrownBy(() -> completion.completeOrder(fixture.owner(), id)).isInstanceOf(com.example.demo.order.exception.OrderConflictException.class);
        clock.set(CheckoutFixtures.PLACED_AT.plusSeconds(61));
        jdbc.execute("CREATE FUNCTION inventory_test_reject_completion() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN IF NEW.status='COMPLETED' THEN RAISE EXCEPTION 'Injected completion commit failure' USING ERRCODE='23514'; END IF; RETURN NULL; END $$");
        jdbc.execute("CREATE CONSTRAINT TRIGGER ct_inventory_test_completion AFTER UPDATE ON orders DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION inventory_test_reject_completion()");
        try {
            assertThatThrownBy(() -> completion.completeOrder(fixture.owner(), id)).hasStackTraceContaining("Injected completion commit failure");
            assertThat(jdbc.queryForMap("SELECT * FROM orders WHERE id=?", id)).isEqualTo(before);
            assertThat(jdbc.queryForMap("SELECT stock,reserved FROM inventory_balances WHERE original_variant_id=?", fixture.variant()))
                    .containsEntry("stock", 2L).containsEntry("reserved", 2L);
            assertThat(jdbc.queryForList("SELECT state FROM inventory_reservations WHERE order_id=?", String.class, id)).containsExactly("ACTIVE", "ACTIVE");
            assertThat(jdbc.queryForList("SELECT * FROM inventory_balances ORDER BY id")).isEqualTo(beforeBalances);
        } finally {
            jdbc.execute("DROP TRIGGER ct_inventory_test_completion ON orders");
            jdbc.execute("DROP FUNCTION inventory_test_reject_completion()");
        }
    }

    @Test void completion_shouldConsumeOwnThreeAndPreserveTheOtherOrdersFour() {
        InventoryFixtures.supplyStock(jdbc, fixture.variant(), 10);
        long[] ids = transactions.execute(s -> {
            long[] orders = new long[2];
            for (int index = 0; index < 2; index++) {
                int quantity = index == 0 ? 3 : 4;
                orders[index] = InventoryFixtures.order(jdbc, fixture.owner(), true, fixture.variant(), quantity);
                long item = jdbc.queryForObject("SELECT id FROM order_items WHERE order_id=?", Long.class, orders[index]);
                inventory.reserveReservations(new com.example.demo.inventory.dto.request.ReservationCommand(orders[index],
                        java.time.Instant.parse("2026-01-01T00:00:00Z"), java.util.List.of(new com.example.demo.inventory.dto.request.ReservationLine(item, fixture.variant(), quantity))));
            }
            jdbc.update("UPDATE orders SET status='SHIPPING' WHERE id=?", orders[0]);
            return orders;
        });
        completion.completeOrder(fixture.owner(), ids[0]);
        assertThat(jdbc.queryForMap("SELECT stock,reserved FROM inventory_balances WHERE original_variant_id=?", fixture.variant()))
                .containsEntry("stock", 7L).containsEntry("reserved", 4L);
        assertThat(jdbc.queryForObject("SELECT state FROM inventory_reservations WHERE order_id=?", String.class, ids[1])).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT quantity FROM inventory_reservations WHERE order_id=?", Integer.class, ids[1])).isEqualTo(4);
    }
}
