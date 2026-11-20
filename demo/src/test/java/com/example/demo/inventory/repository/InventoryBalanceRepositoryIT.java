package com.example.demo.inventory.repository;

import com.example.demo.catalog.CatalogFixtures;
import com.example.demo.catalog.entity.ProductStatus;
import com.example.demo.fixture.inventory.InventoryFixtures;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class InventoryBalanceRepositoryIT {
    @Autowired EntityManager entities;
    @Autowired InventoryBalanceRepository balances;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    @Test void observations_shouldReturnExactScalarsAndFreshOrderedLockedRows() {
        UUID first = CatalogFixtures.persistProduct(entities, ProductStatus.DRAFT).variant().getId();
        UUID second = CatalogFixtures.persistProduct(entities, ProductStatus.DRAFT).variant().getId();
        entities.flush();
        InventoryFixtures.supplyStock(jdbc, first, Long.MAX_VALUE);
        var rows = balances.findBalances(List.of(first, second));
        assertThat(rows).hasSize(2);
        assertThat(rows.stream().filter(r -> r.getOriginalVariantId().equals(first)).findFirst().orElseThrow().getStock()).isEqualTo(Long.MAX_VALUE);
        InventoryFixtures.supplyStock(jdbc, first, 3);
        var locked = balances.lockBalances(List.of(first, second));
        assertThat(locked).extracting(r -> r.getOriginalVariantId().toString()).isSorted();
        assertThat(locked.stream().filter(r -> r.getOriginalVariantId().equals(first)).findFirst().orElseThrow().getStock()).isEqualTo(3);
        InventoryFixtures.removeUnusedBalance(jdbc, second);
        assertThat(balances.findBalances(List.of(second))).isEmpty();
        jdbc.update("UPDATE inventory_balances SET product_variant_id=NULL WHERE original_variant_id=?", first);
        assertThat(balances.lockLiveBalances(List.of(first))).isEmpty();
        assertThat(balances.lockBalances(List.of(first))).hasSize(1);
    }

    @Test @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void lockedObservation_shouldWaitIndependentlyAndSeeTheCommittedCounter() throws Exception {
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        UUID variant = transactions.execute(s -> {
            UUID id = CatalogFixtures.persistProduct(entities, ProductStatus.DRAFT).variant().getId();
            entities.flush();
            InventoryFixtures.supplyStock(jdbc, id, 10);
            return id;
        });
        AtomicInteger workerPid = new AtomicInteger();
        CountDownLatch workerReady = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var worker = transactions.execute(s -> {
                balances.lockBalances(List.of(variant));
                var pending = executor.submit(() -> transactions.execute(inner -> {
                    jdbc.execute("SET LOCAL lock_timeout='5s'");
                    workerPid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                    workerReady.countDown();
                    return balances.lockBalances(List.of(variant)).getFirst().getStock();
                }));
                try {
                    assertThat(workerReady.await(5, TimeUnit.SECONDS)).isTrue();
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
                    boolean observed = false;
                    while (System.nanoTime() < deadline && !observed) {
                        jdbc.execute("SELECT pg_stat_clear_snapshot()");
                        observed = Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE pid=? AND wait_event_type='Lock')", Boolean.class, workerPid.get()));
                        Thread.onSpinWait();
                    }
                    assertThat(observed).as("independent worker actually waits on the held balance").isTrue();
                    assertThat(pending.isDone()).isFalse();
                    InventoryFixtures.supplyStock(jdbc, variant, 3);
                    return pending;
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                }
            });
            assertThat(worker.get(10, TimeUnit.SECONDS)).isEqualTo(3L);
        } finally {
            InventoryFixtures.cleanRetainedInventory(jdbc);
        }
    }
}
