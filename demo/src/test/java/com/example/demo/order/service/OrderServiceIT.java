package com.example.demo.order.service;

import com.example.demo.fixture.order.OrderFixtures;
import com.example.demo.catalog.CatalogFixtures;
import com.example.demo.catalog.entity.ProductStatus;
import com.example.demo.catalog.storage.ProductImageStorage;
import com.example.demo.catalog.exception.CatalogStorageUnavailableException;
import com.example.demo.testdata.order.OrderTestData;
import com.example.demo.order.entity.Order;
import com.example.demo.order.dto.request.CancelOrderRequest;
import com.example.demo.config.MutableClock;
import com.example.demo.order.dto.request.OrderListRequest;
import com.example.demo.order.entity.OrderStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties={"spring.datasource.hikari.maximum-pool-size=4","order.lock-timeout-ms=5000","spring.jpa.properties.hibernate.generate_statistics=true","logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"})
@ActiveProfiles("test")
class OrderServiceIT {
    @Autowired OrderService service;
    @Autowired EntityManager entities;
    @Autowired EntityManagerFactory factory;
    @Autowired TransactionTemplate transactions;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;
    @Autowired DataSource source;
    @Autowired com.example.demo.common.exception.ApiErrorFactory errors;
    @MockitoBean ProductImageStorage storage;
    Long owner;
    @Autowired com.example.demo.inventory.service.InventoryService inventory;

    @Test void cancelOrder_shouldReleaseOwnAllocationAndPreserveOtherOrderAndTerminalRepeat() {
        long[] orders = transactions.execute(s -> {
            var product = CatalogFixtures.persistProduct(entities, ProductStatus.DRAFT);
            entities.flush();
            com.example.demo.fixture.inventory.InventoryFixtures.supplyStock(jdbc, product.variant().getId(), 10);
            long[] ids = new long[2];
            for (int index = 0; index < 2; index++) {
                int quantity = index == 0 ? 3 : 4;
                ids[index] = com.example.demo.fixture.inventory.InventoryFixtures.order(jdbc, owner, true, product.variant().getId(), quantity);
                long item = jdbc.queryForObject("SELECT id FROM order_items WHERE order_id=?", Long.class, ids[index]);
                inventory.reserveReservations(new com.example.demo.inventory.dto.request.ReservationCommand(ids[index], OrderTestData.PLACED,
                        java.util.List.of(new com.example.demo.inventory.dto.request.ReservationLine(item, product.variant().getId(), quantity))));
            }
            return ids;
        });
        service.cancelOrder(owner, orders[0], new CancelOrderRequest("first"));
        assertThat(jdbc.queryForObject("SELECT SUM(stock) FROM inventory_balances", Long.class)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT SUM(reserved) FROM inventory_balances", Long.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT state FROM inventory_reservations WHERE order_id=?", String.class, orders[1])).isEqualTo("ACTIVE");
        var terminal = jdbc.queryForMap("SELECT * FROM orders WHERE id=?", orders[0]);
        clock.set(OrderTestData.PLACED.minusSeconds(1));
        service.cancelOrder(owner, orders[0], new CancelOrderRequest("second"));
        assertThat(jdbc.queryForMap("SELECT * FROM orders WHERE id=?", orders[0])).isEqualTo(terminal);
        assertThat(jdbc.queryForObject("SELECT SUM(reserved) FROM inventory_balances", Long.class)).isEqualTo(4);
    }

    @BeforeEach void prepare() {
        cleanup();
        clock.set(OrderTestData.PLACED.plusSeconds(60));
        owner = transactions.execute(s -> OrderFixtures.customer(entities).getId());
    }
    @AfterEach void cleanup() { com.example.demo.fixture.inventory.InventoryFixtures.cleanRetainedInventory(jdbc); }

    @Test void listOrders_shouldCountOnlyForEmptyAndLargeBeyondLastPage() {
        Statistics statistics = factory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        assertThat(service.listOrders(owner,new OrderListRequest(Integer.MAX_VALUE,100,null,"placedAt,desc")).getTotalItems()).isZero();
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        transactions.executeWithoutResult(s -> OrderFixtures.persist(entities,owner,OrderStatus.CONFIRMED,5));
        statistics.clear();
        assertThat(service.listOrders(owner,new OrderListRequest(Integer.MAX_VALUE,100,null,"placedAt,desc")).getTotalItems()).isEqualTo(1);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test void listOrders_shouldBoundQueriesAndPreviewRowsAcrossPageSizes() {
        transactions.executeWithoutResult(s -> OrderFixtures.corpus(entities,owner,125));
        for (int size : new int[]{1,20,100}) {
            Statistics statistics = factory.unwrap(SessionFactory.class).getStatistics();
            statistics.clear();
            com.example.demo.order.dto.response.OrderPage page = service.listOrders(owner,new OrderListRequest(0,size,null,"placedAt,desc"));
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);
            assertThat(page.getTotalItems()).isEqualTo(125);
            assertThat(page.getItems()).hasSize(size).allSatisfy(order -> {
                assertThat(order.getItemCount()).isEqualTo(5);
                assertThat(order.getItemPreview()).hasSize(3);
                assertThat(order.getRemainingItemCount()).isEqualTo(2);
                assertThat(order.getItemPreview()).allSatisfy(item -> assertThat(item.getPrimaryImageUrl()).isNull());
            });
        }
    }

    @Test void getOrder_shouldPreserveHistoryAfterProfileCatalogAndDeletionChanges() {
        Long id = transactions.execute(s -> {
            CatalogFixtures.ProductFixture product = CatalogFixtures.persistProduct(entities,ProductStatus.PUBLISHED);
            entities.persist(CatalogFixtures.image(product.product(),0,true));
            Order order = OrderTestData.order(owner,OrderStatus.PENDING).build();
            entities.persist(order);
            OrderFixtures.referencedItem(entities,order,product.product().getId(),product.variant().getId());
            return order.getId();
        });
        org.mockito.Mockito.when(storage.prepareReadSigner()).thenThrow(new CatalogStorageUnavailableException());
        com.example.demo.order.dto.response.OrderDetailResponse before = service.getOrder(owner,id);
        assertThat(before.getItems().getFirst().getPrimaryImageUrl()).isNull();
        assertThat(service.listOrders(owner,new OrderListRequest(0,20,null,"placedAt,desc")).getItems()).hasSize(1);
        java.util.UUID product = before.getItems().getFirst().getProductId();
        java.util.UUID variant = before.getItems().getFirst().getProductVariantId();
        jdbc.update("INSERT INTO addresses(user_id,recipient_name,recipient_phone,address_text,is_default) VALUES (?,'New recipient','0123456789','Changed address',TRUE)",owner);
        transactions.executeWithoutResult(s -> {
            jdbc.update("UPDATE products SET name='Changed product',status='ARCHIVED' WHERE id=?",product);
            jdbc.update("UPDATE product_variants SET sku='Changed SKU',option_combination='{\"color\":\"Red\"}',selling_price=999 WHERE id=?",variant);
        });
        assertThat(service.getOrder(owner,id)).usingRecursiveComparison().isEqualTo(before);
        transactions.executeWithoutResult(s -> {
            jdbc.update("DELETE FROM product_images WHERE product_id=?",product);
            jdbc.update("DELETE FROM product_variants WHERE product_id=?",product);
            jdbc.update("DELETE FROM products WHERE id=?",product);
        });
        Statistics statistics = factory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        com.example.demo.order.dto.response.OrderDetailResponse after = service.getOrder(owner,id);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
        assertThat(after.getItems().getFirst().getProductId()).isNull();
        assertThat(after.getItems().getFirst().getProductVariantId()).isNull();
        assertThat(after).usingRecursiveComparison().ignoringFields("items.productId","items.productVariantId").isEqualTo(before);
    }

    @Test void getOrder_shouldReturnAllRowsWhenMoreThanOneImageBatchIsNeeded() {
        Long id = transactions.execute(s -> {
            Order order = OrderTestData.order(owner,OrderStatus.COMPLETED).merchandiseTotal(java.math.BigDecimal.valueOf(10500)).build();
            entities.persist(order);
            for (int i=0;i<105;i++) {
                CatalogFixtures.ProductFixture product = CatalogFixtures.persistProduct(entities,ProductStatus.PUBLISHED);
                OrderFixtures.referencedItem(entities,order,product.product().getId(),product.variant().getId());
            }
            return order.getId();
        });
        assertThat(service.getOrder(owner,id).getItems()).hasSize(105)
                .allSatisfy(item -> assertThat(item.getPrimaryImageUrl()).isNull());
    }

    @Test void cancelOrder_shouldCommitOnlyAllowedTransitionsAndPreserveEveryRejectedOrRepeatedField() {
        for (OrderStatus status : OrderStatus.values()) {
            Long id = transactions.execute(s -> OrderFixtures.persist(entities,owner,status,2).getId());
            java.util.Map<String,Object> before = jdbc.queryForMap("SELECT * FROM orders WHERE id=?",id);
            if (status == OrderStatus.SHIPPING || status == OrderStatus.COMPLETED || status == OrderStatus.FAILED) {
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.cancelOrder(owner,id,new CancelOrderRequest("reason")))
                        .isInstanceOf(com.example.demo.order.exception.OrderConflictException.class);
                assertThat(jdbc.queryForMap("SELECT * FROM orders WHERE id=?",id)).isEqualTo(before);
            } else {
                service.cancelOrder(owner,id,new CancelOrderRequest(" first "));
                java.util.Map<String,Object> winning = jdbc.queryForMap("SELECT * FROM orders WHERE id=?",id);
                service.cancelOrder(owner,id,new CancelOrderRequest("second"));
                assertThat(jdbc.queryForMap("SELECT * FROM orders WHERE id=?",id)).isEqualTo(winning);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_items WHERE order_id=?",Long.class,id)).isEqualTo(2);
            }
        }
    }

    @Test void cancelOrder_shouldRejectPrePlacementClockButKeepRepeatIdempotentAfterRollbackOfClock() {
        Long id = transactions.execute(s -> OrderFixtures.persist(entities,owner,OrderStatus.PENDING,1).getId());
        clock.set(OrderTestData.PLACED.minusSeconds(1));
        java.util.Map<String,Object> before = jdbc.queryForMap("SELECT * FROM orders WHERE id=?",id);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.cancelOrder(owner,id,new CancelOrderRequest(null)))
                .isInstanceOf(com.example.demo.order.exception.OrderConflictException.class);
        assertThat(jdbc.queryForMap("SELECT * FROM orders WHERE id=?",id)).isEqualTo(before);
        clock.set(OrderTestData.PLACED);
        service.cancelOrder(owner,id,new CancelOrderRequest("winner"));
        java.util.Map<String,Object> winner = jdbc.queryForMap("SELECT * FROM orders WHERE id=?",id);
        clock.set(OrderTestData.PLACED.minusSeconds(1));
        service.cancelOrder(owner,id,new CancelOrderRequest(null));
        assertThat(jdbc.queryForMap("SELECT * FROM orders WHERE id=?",id)).isEqualTo(winner);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.cancelOrder(owner+10000,id,new CancelOrderRequest(null)))
                .isInstanceOf(com.example.demo.order.exception.OrderNotFoundException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.cancelOrder(owner,Long.MAX_VALUE,new CancelOrderRequest(null)))
                .isInstanceOf(com.example.demo.order.exception.OrderNotFoundException.class);
    }

    @TestConfiguration
    static class TimeConfiguration {
        @Bean @Primary MutableClock orderTestClock() { return new MutableClock(); }
    }

    @Test void cancelOrder_shouldSerializeObservedCompetingTransactionsAndWriteExactlyOnce() throws Exception {
        for (OrderStatus state : java.util.List.of(OrderStatus.PENDING,OrderStatus.CONFIRMED)) {
            Long id = transactions.execute(s -> OrderFixtures.persist(entities,owner,state,2).getId());
            java.util.List<java.util.Map<String,Object>> itemSnapshot = jdbc.queryForList("SELECT * FROM order_items WHERE order_id=? ORDER BY id",id);
            Statistics statistics = factory.unwrap(SessionFactory.class).getStatistics();
            statistics.clear();
            try (Connection held = source.getConnection(); ExecutorService workers = Executors.newFixedThreadPool(2)) {
                held.setAutoCommit(false);
                lock(held,id);
                try {
                    Future<?> one = workers.submit(() -> service.cancelOrder(owner,id,new CancelOrderRequest("one")));
                    Future<?> two = workers.submit(() -> service.cancelOrder(owner,id,new CancelOrderRequest("two")));
                    awaitBlocked(2);
                    held.commit();
                    one.get(10,TimeUnit.SECONDS);
                    two.get(10,TimeUnit.SECONDS);
                } finally { held.rollback(); }
            }
            assertThat(statistics.getEntityUpdateCount()).isEqualTo(1);
            java.util.Map<String,Object> winner = jdbc.queryForMap("SELECT * FROM orders WHERE id=?",id);
            assertThat(winner.get("status")).isEqualTo("CANCELLED");
            assertThat(winner.get("cancellation_reason")).isIn("one","two");
            service.cancelOrder(owner,id,new CancelOrderRequest("third"));
            assertThat(jdbc.queryForMap("SELECT * FROM orders WHERE id=?",id)).isEqualTo(winner);
            assertThat(jdbc.queryForList("SELECT * FROM order_items WHERE order_id=? ORDER BY id",id)).isEqualTo(itemSnapshot);
            assertThat(statistics.getEntityUpdateCount()).isEqualTo(1);
        }
    }

    @Test void cancelOrder_shouldRollbackTimedOutCompetitorAndExposeSafeConflict() throws Exception {
        Long id = transactions.execute(s -> OrderFixtures.persist(entities,owner,OrderStatus.PENDING,1).getId());
        java.util.Map<String,Object> before = jdbc.queryForMap("SELECT * FROM orders WHERE id=?",id);
        try (Connection held = source.getConnection(); ExecutorService worker = Executors.newSingleThreadExecutor()) {
            held.setAutoCommit(false);
            lock(held,id);
            try {
                Future<?> loser = worker.submit(() -> service.cancelOrder(owner,id,new CancelOrderRequest("must not persist")));
                awaitBlocked(1);
                try {
                    loser.get(10,TimeUnit.SECONDS);
                    org.assertj.core.api.Assertions.fail("Held lock should cause a bounded conflict");
                } catch (ExecutionException failure) {
                    assertThat(errors.from(failure.getCause(),"/api/me/orders/"+id+"/cancel").getStatus()).isEqualTo(409);
                }
                assertThat(jdbc.queryForMap("SELECT * FROM orders WHERE id=?",id)).isEqualTo(before);
            } finally { held.rollback(); }
        }
        service.cancelOrder(owner,id,new CancelOrderRequest("after release"));
        assertThat(jdbc.queryForObject("SELECT cancellation_reason FROM orders WHERE id=?",String.class,id)).isEqualTo("after release");
    }

    private void lock(Connection held, Long id) throws Exception {
        try (PreparedStatement statement = held.prepareStatement("SELECT id FROM orders WHERE id=? FOR UPDATE")) {
            statement.setLong(1,id);
            try (java.sql.ResultSet result = statement.executeQuery()) { assertThat(result.next()).isTrue(); }
        }
    }

    private void awaitBlocked(int expected) throws Exception {
        long deadline = System.nanoTime()+TimeUnit.SECONDS.toNanos(4);
        while (System.nanoTime()<deadline) {
            Long blocked = jdbc.queryForObject("SELECT COUNT(*) FROM pg_stat_activity WHERE wait_event_type='Lock' AND query ILIKE '%from orders%' AND pid<>pg_backend_pid()",Long.class);
            if (blocked >= expected) return;
            Thread.sleep(10);
        }
        org.assertj.core.api.Assertions.fail("Did not observe "+expected+" independent blocked order operations");
    }
}
