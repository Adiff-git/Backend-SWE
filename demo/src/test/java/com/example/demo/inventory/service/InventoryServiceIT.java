package com.example.demo.inventory.service;

import com.example.demo.catalog.CatalogFixtures;
import com.example.demo.catalog.entity.ProductStatus;
import com.example.demo.fixture.checkout.CheckoutFixtures;
import com.example.demo.fixture.inventory.InventoryFixtures;
import com.example.demo.inventory.dto.request.ReservationCommand;
import com.example.demo.inventory.dto.request.ReservationLine;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest @ActiveProfiles("test")
@org.springframework.context.annotation.Import(com.example.demo.config.SqlCaptureConfiguration.class)
class InventoryServiceIT {
    @Autowired InventoryService inventory;
    @Autowired EntityManager entities;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transactions;
    private long owner;
    private UUID variant;
    private static final Instant PLACED = Instant.parse("2026-01-01T00:00:00Z");

    @Test void retainedBalanceWithoutLiveLink_shouldRemainUnavailableToNewReservation() {
        jdbc.update("UPDATE inventory_balances SET product_variant_id=NULL WHERE original_variant_id=?", variant);
        assertThat(inventory.getAvailability(List.of(variant)).get(variant).getStatus()).isEqualTo("INVENTORY_UNAVAILABLE");
        assertThat(inventory.lockAvailability(List.of(variant)).get(variant).getStatus()).isEqualTo("INVENTORY_UNAVAILABLE");
        assertThatThrownBy(() -> transactions.executeWithoutResult(s -> inventory.reserveReservations(createCommand(variant, 3, true))))
                .isInstanceOf(com.example.demo.inventory.exception.InventoryConflictException.class);
        assertNoPurchase();
    }

    @Test void availability_shouldKeepTheFullLongRangeAndIssueNoSqlForEmptyInputs() {
        com.example.demo.config.SqlCapture.begin();
        java.util.List<String> emptySql;
        try { assertThat(inventory.getAvailability(List.of())).isEmpty(); }
        finally { emptySql = com.example.demo.config.SqlCapture.end(); }
        assertThat(emptySql).isEmpty();
        InventoryFixtures.supplyStock(jdbc, variant, Long.MAX_VALUE);
        assertThat(inventory.getAvailability(List.of(variant)).get(variant).getAvailableQuantity()).isEqualTo(Long.MAX_VALUE);
        transactions.executeWithoutResult(s -> inventory.reserveReservations(createCommand(variant, 999, true)));
        assertThat(inventory.getAvailability(List.of(variant)).get(variant).getAvailableQuantity()).isEqualTo(Long.MAX_VALUE-999);
        assertThat(jdbc.queryForObject("SELECT stock FROM inventory_balances WHERE original_variant_id=?", Long.class, variant)).isEqualTo(Long.MAX_VALUE);
        assertThat(jdbc.queryForObject("SELECT pg_typeof(SUM(quantity::NUMERIC))::TEXT FROM inventory_reservations", String.class)).isEqualTo("numeric");
    }

    @BeforeEach void prepare() {
        InventoryFixtures.cleanRetainedInventory(jdbc);
        transactions.executeWithoutResult(s -> {
            owner = CheckoutFixtures.customer(jdbc);
            variant = CatalogFixtures.persistProduct(entities, ProductStatus.DRAFT).variant().getId();
            entities.flush();
            InventoryFixtures.supplyStock(jdbc, variant, 10);
        });
    }
    @AfterEach void clean() { InventoryFixtures.cleanRetainedInventory(jdbc); }

    private ReservationCommand createCommand(UUID id, int quantity, boolean managed) {
        long order = InventoryFixtures.order(jdbc, owner, managed, id, quantity);
        long item = jdbc.queryForObject("SELECT id FROM order_items WHERE order_id=?", Long.class, order);
        return new ReservationCommand(order, PLACED, List.of(new ReservationLine(item, id, quantity)));
    }

    @Test void reserve_shouldCommitExactOwnedAllocationAndLeavePhysicalStockUntouched() {
        ReservationCommand command = transactions.execute(s -> {
            ReservationCommand prepared = createCommand(variant, 3, true);
            inventory.reserveReservations(prepared);
            return prepared;
        });
        assertThat(jdbc.queryForMap("SELECT stock,reserved FROM inventory_balances WHERE original_variant_id=?", variant))
                .containsEntry("stock", 10L).containsEntry("reserved", 3L);
        var allocation = jdbc.queryForMap("SELECT * FROM inventory_reservations WHERE order_id=?", command.getOrderId());
        assertThat(allocation).containsEntry("order_item_id", command.getLines().getFirst().getOrderItemId())
                .containsEntry("quantity", 3).containsEntry("state", "ACTIVE").containsEntry("original_variant_id", variant)
                .containsEntry("resolved_at", null);
        assertThatThrownBy(() -> transactions.executeWithoutResult(s -> inventory.reserveReservations(command))).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT reserved FROM inventory_balances WHERE original_variant_id=?", Long.class, variant)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_reservations", Long.class)).isEqualTo(1);
    }

    @Test void failedReserveOrCommit_shouldRollBackCountersOrderAndEveryAllocation() {
        assertThatThrownBy(() -> transactions.executeWithoutResult(s -> inventory.reserveReservations(createCommand(variant, 11, true))))
                .isInstanceOf(RuntimeException.class);
        assertNoPurchase();
        assertThatThrownBy(() -> transactions.executeWithoutResult(s -> inventory.reserveReservations(createCommand(variant, 3, false))))
                .isInstanceOf(RuntimeException.class);
        assertNoPurchase();
        assertThatThrownBy(() -> transactions.executeWithoutResult(s -> {
            ReservationCommand command = createCommand(variant, 3, true);
            inventory.reserveReservations(new ReservationCommand(command.getOrderId(), PLACED,
                    List.of(new ReservationLine(command.getLines().getFirst().getOrderItemId(), variant, 2))));
        })).isInstanceOf(RuntimeException.class);
        assertNoPurchase();
    }

    @Test void lateMultiVariantShortage_shouldRollBackTheEarlierSuccessfulReservation() {
        UUID another = transactions.execute(s -> {
            UUID id = CatalogFixtures.persistProduct(entities, ProductStatus.DRAFT).variant().getId();
            entities.flush();
            return id;
        });
        List<UUID> sorted = java.util.stream.Stream.of(variant, another).sorted(java.util.Comparator.comparing(UUID::toString)).toList();
        UUID sufficient = sorted.getFirst();
        UUID shortage = sorted.getLast();
        InventoryFixtures.supplyStock(jdbc, sufficient, 10);
        InventoryFixtures.supplyStock(jdbc, shortage, 0);
        assertThatThrownBy(() -> transactions.executeWithoutResult(s -> {
            ReservationCommand first = createCommand(sufficient, 3, true);
            long item = jdbc.queryForObject("INSERT INTO order_items(order_id,product_variant_id,product_name_snapshot,variant_sku_snapshot,variant_options_snapshot,quantity,unit_price_snapshot,line_total) VALUES (?,?,'Other','Other','{}',1,100,100) RETURNING id", Long.class, first.getOrderId(), shortage);
            jdbc.update("UPDATE orders SET merchandise_total=400,payable_total=400 WHERE id=?", first.getOrderId());
            inventory.reserveReservations(new ReservationCommand(first.getOrderId(), PLACED,
                    List.of(first.getLines().getFirst(), new ReservationLine(item, shortage, 1))));
        })).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_reservations", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT SUM(reserved) FROM inventory_balances", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT stock FROM inventory_balances WHERE original_variant_id=?", Long.class, sufficient)).isEqualTo(10);
    }

    @Test void release_shouldRequireExactOwnedActiveSetAndRollbackADeferredTerminalMismatch() {
        ReservationCommand command = transactions.execute(s -> {
            ReservationCommand created = createCommand(variant, 3, true);
            inventory.reserveReservations(created);
            return created;
        });
        Instant terminal = PLACED.plusSeconds(1);
        var wrong = new com.example.demo.inventory.dto.request.ResolutionCommand(command.getOrderId(), terminal,
                List.of(new ReservationLine(command.getLines().getFirst().getOrderItemId(), variant, 2)));
        assertThatThrownBy(() -> transactions.executeWithoutResult(s -> inventory.releaseReservations(wrong))).isInstanceOf(RuntimeException.class);
        var resolution = new com.example.demo.inventory.dto.request.ResolutionCommand(command.getOrderId(), terminal, command.getLines());
        assertThatThrownBy(() -> transactions.executeWithoutResult(s -> {
            inventory.releaseReservations(resolution);
            jdbc.update("UPDATE orders SET status='CANCELLED',cancelled_at=placed_at+INTERVAL '2 seconds' WHERE id=?", command.getOrderId());
        })).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT reserved FROM inventory_balances WHERE original_variant_id=?", Long.class, variant)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT state FROM inventory_reservations WHERE order_id=?", String.class, command.getOrderId())).isEqualTo("ACTIVE");
        transactions.executeWithoutResult(s -> {
            inventory.releaseReservations(resolution);
            jdbc.update("UPDATE orders SET status='CANCELLED',cancelled_at=placed_at+INTERVAL '1 second' WHERE id=?", command.getOrderId());
        });
        assertThat(jdbc.queryForObject("SELECT stock FROM inventory_balances WHERE original_variant_id=?", Long.class, variant)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT reserved FROM inventory_balances WHERE original_variant_id=?", Long.class, variant)).isZero();
        assertThatThrownBy(() -> transactions.executeWithoutResult(s -> inventory.releaseReservations(resolution))).isInstanceOf(RuntimeException.class);
    }

    @Test void consume_shouldRequireTheExactActiveOwnerAndNeverReleaseOrConsumeTerminalUnitsAgain() {
        ReservationCommand command = transactions.execute(s -> {
            ReservationCommand created = createCommand(variant, 3, true);
            inventory.reserveReservations(created);
            jdbc.update("UPDATE orders SET status='SHIPPING' WHERE id=?", created.getOrderId());
            return created;
        });
        InventoryFixtures.supplyStock(jdbc, variant, 3); // available=0 is valid for consumption.
        Instant terminal = PLACED.plusSeconds(1);
        for (ReservationLine wrong : List.of(new ReservationLine(command.getLines().getFirst().getOrderItemId(), variant, 2),
                new ReservationLine(Long.MAX_VALUE, variant, 3),
                new ReservationLine(command.getLines().getFirst().getOrderItemId(), UUID.randomUUID(), 3))) {
            assertThatThrownBy(() -> transactions.executeWithoutResult(s -> inventory.consumeReservations(
                    new com.example.demo.inventory.dto.request.ResolutionCommand(command.getOrderId(), terminal, List.of(wrong)))))
                    .isInstanceOf(RuntimeException.class);
            assertThat(jdbc.queryForObject("SELECT reserved FROM inventory_balances WHERE original_variant_id=?", Long.class, variant)).isEqualTo(3);
        }
        var resolution = new com.example.demo.inventory.dto.request.ResolutionCommand(command.getOrderId(), terminal, command.getLines());
        transactions.executeWithoutResult(s -> {
            inventory.consumeReservations(resolution);
            jdbc.update("UPDATE orders SET status='COMPLETED',completed_at=placed_at+INTERVAL '1 second' WHERE id=?", command.getOrderId());
        });
        assertThat(jdbc.queryForMap("SELECT stock,reserved FROM inventory_balances WHERE original_variant_id=?", variant))
                .containsEntry("stock", 0L).containsEntry("reserved", 0L);
        assertThatThrownBy(() -> transactions.executeWithoutResult(s -> inventory.consumeReservations(resolution))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> transactions.executeWithoutResult(s -> inventory.releaseReservations(resolution))).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT state FROM inventory_reservations WHERE order_id=?", String.class, command.getOrderId())).isEqualTo("CONSUMED");
    }

    private void assertNoPurchase() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_reservations", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT reserved FROM inventory_balances WHERE original_variant_id=?", Long.class, variant)).isZero();
        assertThat(jdbc.queryForObject("SELECT stock FROM inventory_balances WHERE original_variant_id=?", Long.class, variant)).isEqualTo(10);
    }
}
