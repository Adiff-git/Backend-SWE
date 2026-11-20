package com.example.demo.inventory.repository;

import com.example.demo.catalog.CatalogFixtures;
import com.example.demo.catalog.entity.ProductStatus;
import com.example.demo.fixture.inventory.InventoryFixtures;
import com.example.demo.fixture.order.OrderFixtures;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class InventoryReservationRepositoryIT {
    @Autowired EntityManager entities;
    @Autowired InventoryReservationRepository reservations;
    @Autowired JdbcTemplate jdbc;
    @Autowired InventoryBalanceRepository balances;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"RELEASED", "CONSUMED"})
    void ownedProjections_shouldRetainExactOriginalAttributionWithoutForeignRows(String resolution) {
        long owner = OrderFixtures.customer(entities).getId();
        UUID variant = CatalogFixtures.persistProduct(entities, ProductStatus.DRAFT).variant().getId();
        entities.flush();
        InventoryFixtures.supplyStock(jdbc, variant, 10);
        long order = InventoryFixtures.order(jdbc, owner, true, variant, 3);
        long item = jdbc.queryForObject("SELECT id FROM order_items WHERE order_id=?", Long.class, order);
        long balance = jdbc.queryForObject("SELECT id FROM inventory_balances WHERE original_variant_id=?", Long.class, variant);
        assertThat(balances.reserveUnits(balance, 3)).isEqualTo(1);
        assertThat(balances.insertReservation(order, item, balance, variant, 3, java.time.Instant.parse("2026-01-01T00:00:00Z"))).isEqualTo(1);
        var rows = reservations.findOwnedReservations(order);
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().getOrderItemId()).isEqualTo(item);
        assertThat(rows.getFirst().getOriginalVariantId()).isEqualTo(variant);
        assertThat(rows.getFirst().getQuantity()).isEqualTo(3);
        assertThat(rows.getFirst().getState()).isEqualTo("ACTIVE");
        assertThat(reservations.findOwnedReservations(Long.MAX_VALUE)).isEmpty();
        assertThat(reservations.lockOwnedReservations(order)).extracting(r -> r.getId()).containsExactly(rows.getFirst().getId());
        assertThat(balances.reserveUnits(balance, 8)).isZero();
        assertThat(balances.reserveUnits(balance, -1)).isZero();
        assertThat(balances.releaseUnits(balance, 4)).isZero();
        assertThat(balances.consumeUnits(balance, 4)).isZero();
        assertThat(balances.resolveReservation(rows.getFirst().getId(), "RELEASED", java.time.Instant.parse("2025-01-01T00:00:00Z"))).isZero();
        assertThat(resolution.equals("CONSUMED") ? balances.consumeUnits(balance, 3) : balances.releaseUnits(balance, 3)).isEqualTo(1);
        assertThat(balances.resolveReservation(rows.getFirst().getId(), resolution, java.time.Instant.parse("2026-01-01T00:00:01Z"))).isEqualTo(1);
        assertThat(balances.resolveReservation(rows.getFirst().getId(), resolution.equals("CONSUMED") ? "RELEASED" : "CONSUMED", java.time.Instant.parse("2026-01-01T00:00:02Z"))).isZero();
        assertThat(balances.consumeUnits(balance, 3)).isZero();
        assertThat(balances.releaseUnits(balance, 3)).isZero();
        assertThat(jdbc.queryForObject("SELECT stock FROM inventory_balances WHERE id=?", Long.class, balance)).isEqualTo(resolution.equals("CONSUMED") ? 7 : 10);
        if (resolution.equals("CONSUMED")) jdbc.update("UPDATE orders SET status='COMPLETED',completed_at=placed_at+INTERVAL '1 second' WHERE id=?", order);
        else jdbc.update("UPDATE orders SET status='CANCELLED',cancelled_at=placed_at+INTERVAL '1 second' WHERE id=?", order);
        jdbc.execute("SET CONSTRAINTS ALL IMMEDIATE");
    }
}
