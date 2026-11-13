package com.example.demo.cart.service;

import com.example.demo.cart.dto.request.AddCartItemRequest;
import com.example.demo.cart.dto.request.UpdateCartItemRequest;
import com.example.demo.cart.dto.request.CartSelectionRequest;
import com.example.demo.cart.exception.CartItemUnavailableException;
import com.example.demo.cart.exception.CartItemNotFoundException;
import com.example.demo.cart.exception.CartVariantSwitchException;
import com.example.demo.cart.dto.response.CartResponse;
import com.example.demo.cart.exception.CartQuantityLimitException;
import com.example.demo.catalog.CatalogFixtures;
import com.example.demo.catalog.entity.ProductStatus;
import com.example.demo.fixture.wishlist.WishlistFixtures;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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

@SpringBootTest(properties = {"spring.datasource.hikari.maximum-pool-size=6", "cart.lock-timeout-ms=200", "user.lock-timeout-ms=200"})
@ActiveProfiles("test")
class CartServiceIT {
    @Autowired CartService service;
    @Autowired EntityManager entities;
    @Autowired TransactionTemplate transactions;
    @Autowired JdbcTemplate jdbc;
    @Autowired javax.sql.DataSource dataSource;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    com.example.demo.storage.service.ImageUrlResolver imageUrls;
    Long owner;
    UUID variant;
    UUID product;

    @BeforeEach void prepare() {
        cleanup();
        transactions.executeWithoutResult(status -> {
            owner = WishlistFixtures.persistUser(entities).getId();
            CatalogFixtures.ProductFixture fixture = CatalogFixtures.persistProduct(entities, ProductStatus.PUBLISHED);
            variant = fixture.variant().getId();
            product = fixture.product().getId();
        });
    }
    @AfterEach void cleanup() { jdbc.execute("TRUNCATE TABLE users, categories CASCADE"); }

    AddCartItemRequest addRequest(int quantity) {
        AddCartItemRequest request = new AddCartItemRequest();
        request.setVariantId(variant);
        request.setQuantity(quantity);
        return request;
    }

    @Test void getCart_shouldNotCreateMissingCartAndAddShouldAccumulateWithoutCapping() {
        assertThat(service.getCart(owner).getItems()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM carts", Long.class)).isZero();
        assertThat(service.addItem(owner, addRequest(2)).isCreated()).isTrue();
        assertThat(service.addItem(owner, addRequest(3)).isCreated()).isFalse();
        assertThat(service.getCart(owner).getItems().getFirst().getQuantity()).isEqualTo(5);
        service.addItem(owner, addRequest(995));
        assertThatThrownBy(() -> service.addItem(owner, addRequest(1))).isInstanceOf(CartQuantityLimitException.class);
        assertThat(service.getCart(owner).getItems().getFirst().getQuantity()).isEqualTo(1000);
    }

    @Test void getCart_shouldPreserveSelectionAndSnapshotsWhenArchivedWithoutWrites() {
        Long id = service.addItem(owner, addRequest(2)).getItemId();
        jdbc.update("UPDATE cart_items SET selected=FALSE WHERE id=?", id);
        service.addItem(owner, addRequest(1));
        String snapshot = jdbc.queryForObject("SELECT public_product_name FROM cart_items WHERE id=?", String.class, id);
        jdbc.update("UPDATE products SET status='ARCHIVED', name='Hidden new name' WHERE id=?", product);
        CartResponse response = service.getCart(owner);
        assertThat(response.getItems().getFirst().getProductName()).isEqualTo(snapshot);
        assertThat(response.getItems().getFirst().isSelected()).isFalse();
        assertThat(response.getItems().getFirst().isPurchasable()).isFalse();
        assertThat(response.getFinalTotal().getAmount()).isZero();
        assertThat(jdbc.queryForObject("SELECT quantity FROM cart_items WHERE id=?", Integer.class, id)).isEqualTo(3);
    }

    @Test void addItem_shouldSerializeFirstCreationAndPreserveEveryAcceptedIncrement() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> one = executor.submit(() -> { await(start); service.addItem(owner, addRequest(2)); });
            Future<?> two = executor.submit(() -> { await(start); service.addItem(owner, addRequest(3)); });
            start.countDown();
            one.get(10, TimeUnit.SECONDS);
            two.get(10, TimeUnit.SECONDS);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM carts WHERE user_id=?", Long.class, owner)).isEqualTo(1);
        assertThat(service.getCart(owner).getItems()).hasSize(1);
        assertThat(service.getCart(owner).getItems().getFirst().getQuantity()).isEqualTo(5);
    }

    UUID sibling() {
        return transactions.execute(s -> {
            var parent = entities.find(com.example.demo.catalog.entity.Product.class, product);
            var target = CatalogFixtures.variant(parent, java.util.Map.of("color", UUID.randomUUID().toString()), java.math.BigDecimal.TEN);
            entities.persist(target);
            return target.getId();
        });
    }
    AddCartItemRequest addRequest(UUID target, int quantity) {
        var request = addRequest(quantity); request.setVariantId(target); return request;
    }
    UpdateCartItemRequest update(UUID target, int quantity) {
        var request = new UpdateCartItemRequest(); request.setQuantity(quantity);
        if (target != null) request.setVariantId(target);
        return request;
    }

    @Test void update_shouldReplaceSwitchAndMergePreservingTargetIdentitySelectionAndTime() {
        Long source = service.addItem(owner, addRequest(8)).getItemId();
        UUID target = sibling();
        service.updateItem(owner, source, update(null, 4));
        service.updateItem(owner, source, update(target, 6));
        assertThat(service.getCart(owner).getItems().getFirst().getId()).isEqualTo(source);
        assertThat(service.getCart(owner).getItems().getFirst().getQuantity()).isEqualTo(6);
        Long other = service.addItem(owner, addRequest(600)).getItemId();
        jdbc.update("UPDATE cart_items SET selected=FALSE WHERE id=?", other);
        var added = jdbc.queryForObject("SELECT added_at FROM cart_items WHERE id=?", java.sql.Timestamp.class, other);
        assertThatThrownBy(() -> service.updateItem(owner, source, update(variant, 401))).isInstanceOf(CartQuantityLimitException.class);
        assertThat(service.getCart(owner).getItems()).hasSize(2);
        service.updateItem(owner, source, update(variant, 400));
        var row = service.getCart(owner).getItems().getFirst();
        assertThat(row.getId()).isEqualTo(other);
        assertThat(row.getQuantity()).isEqualTo(1000);
        assertThat(row.isSelected()).isFalse();
        assertThat(jdbc.queryForObject("SELECT added_at FROM cart_items WHERE id=?", java.sql.Timestamp.class, other)).isEqualTo(added);
        service.removeItem(owner, other);
        assertThat(service.getCart(owner).getItems()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM carts WHERE user_id=?", Long.class, owner)).isEqualTo(1);
        assertThatThrownBy(() -> service.removeItem(owner, other)).isInstanceOf(CartItemNotFoundException.class);
    }

    @Test void update_shouldCheckOwnershipBeforeTargetAndRejectCrossProductWithoutChangingRows() {
        Long source = service.addItem(owner, addRequest(8)).getItemId();
        UUID foreignProductVariant = transactions.execute(s -> CatalogFixtures.persistProduct(entities, ProductStatus.PUBLISHED).variant().getId());
        assertThatThrownBy(() -> service.updateItem(owner, source, update(foreignProductVariant, 1))).isInstanceOf(CartVariantSwitchException.class);
        Long other = transactions.execute(s -> WishlistFixtures.persistUser(entities).getId());
        assertThatThrownBy(() -> service.updateItem(other, source, update(UUID.randomUUID(), 1))).isInstanceOf(CartItemNotFoundException.class);
        assertThat(service.getCart(owner).getItems().getFirst().getQuantity()).isEqualTo(8);
    }

    CartSelectionRequest selection(boolean selected) {
        var request = new CartSelectionRequest(); request.setSelected(selected); return request;
    }

    @Test void selection_shouldKeepUnavailablePreferencesAndCountOnlyEligibleRows() {
        service.selectAll(owner, selection(true));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM carts", Long.class)).isZero();
        Long first = service.addItem(owner, addRequest(2)).getItemId();
        UUID other = transactions.execute(s -> CatalogFixtures.persistProduct(entities, ProductStatus.PUBLISHED).variant().getId());
        service.addItem(owner, addRequest(other, 3));
        jdbc.update("UPDATE product_variants SET selling_price=100 WHERE id=?", variant);
        jdbc.update("UPDATE product_variants SET selling_price=50 WHERE id=?", other);
        assertThat(service.getCart(owner).getFinalTotal().getAmount()).isEqualTo(350);
        assertThat(service.selectItem(owner, first, selection(false)).getFinalTotal().getAmount()).isEqualTo(150);
        service.selectAll(owner, selection(true));
        jdbc.update("UPDATE products SET status='ARCHIVED' WHERE id=?", product);
        assertThatThrownBy(() -> service.selectItem(owner, first, selection(true))).isInstanceOf(CartItemUnavailableException.class);
        var response = service.selectAll(owner, selection(true));
        assertThat(response.getSelectedItemCount()).isEqualTo(1);
        assertThat(response.getFinalTotal().getAmount()).isEqualTo(150);
        assertThat(response.getItems().stream().filter(r -> r.getId().equals(first)).findFirst().orElseThrow().isSelected()).isTrue();
        service.selectAll(owner, selection(false));
        assertThat(service.getCart(owner).getItems()).allMatch(row -> !row.isSelected());
        jdbc.update("UPDATE products SET status='PUBLISHED' WHERE id=?", product);
        assertThat(service.selectItem(owner, first, selection(true)).getFinalTotal().getAmount()).isEqualTo(200);
        assertThat(service.getCart(owner).getItems().stream().filter(r -> r.getId().equals(first)).findFirst().orElseThrow().getQuantity()).isEqualTo(2);
    }

    @Test void responseFailure_shouldRollBackFirstCreationAndMergeAfterWrites() {
        transactions.executeWithoutResult(s -> entities.persist(CatalogFixtures.image(
                entities.find(com.example.demo.catalog.entity.Product.class, product), 0, true)));
        org.mockito.Mockito.doAnswer(call -> {
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return (com.example.demo.storage.service.PreparedImageUrls) key -> {
                assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                throw new IllegalStateException("Controlled signing failure");
            };
        }).when(imageUrls).prepare();
        assertThatThrownBy(() -> service.addItem(owner, addRequest(2))).isInstanceOf(com.example.demo.catalog.exception.CatalogStorageUnavailableException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM carts", Long.class)).isZero();
        org.mockito.Mockito.doReturn((com.example.demo.storage.service.PreparedImageUrls) key -> "https://example.test/image").when(imageUrls).prepare();
        Long source = service.addItem(owner, addRequest(8)).getItemId();
        UUID target = sibling();
        Long targetId = service.addItem(owner, addRequest(target, 6)).getItemId();
        org.mockito.Mockito.doReturn((com.example.demo.storage.service.PreparedImageUrls) key -> { throw new IllegalStateException("Controlled signing failure"); }).when(imageUrls).prepare();
        assertThatThrownBy(() -> service.updateItem(owner, source, update(target, 4))).isInstanceOf(com.example.demo.catalog.exception.CatalogStorageUnavailableException.class);
        assertThat(jdbc.queryForObject("SELECT quantity FROM cart_items WHERE id=?", Integer.class, source)).isEqualTo(8);
        assertThat(jdbc.queryForObject("SELECT quantity FROM cart_items WHERE id=?", Integer.class, targetId)).isEqualTo(6);
        assertThatThrownBy(() -> service.selectItem(owner, source, selection(false))).isInstanceOf(com.example.demo.catalog.exception.CatalogStorageUnavailableException.class);
        assertThat(jdbc.queryForObject("SELECT selected FROM cart_items WHERE id=?", Boolean.class, source)).isTrue();
    }

    @Test void writes_shouldTimeoutOnCartAndMissingCartUserLocksAndResetPooledTimeout() throws Exception {
        Long item = service.addItem(owner, addRequest(2)).getItemId();
        assertBlockedWrite("SELECT id FROM carts WHERE user_id=? FOR UPDATE", () -> service.addItem(owner, addRequest(3)));
        assertThat(jdbc.queryForObject("SELECT quantity FROM cart_items WHERE id=?", Integer.class, item)).isEqualTo(2);
        jdbc.update("DELETE FROM carts WHERE user_id=?", owner);
        assertBlockedWrite("SELECT id FROM users WHERE id=? FOR UPDATE", () -> service.addItem(owner, addRequest(3)));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM carts", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SHOW lock_timeout", String.class)).isEqualTo("0");
        java.util.List<java.sql.Connection> borrowed = new java.util.ArrayList<>();
        try {
            for (int index=0; index<6; index++) {
                var connection = dataSource.getConnection(); borrowed.add(connection);
                try (var statement = connection.createStatement(); var result = statement.executeQuery("SHOW lock_timeout")) {
                    result.next(); assertThat(result.getString(1)).isEqualTo("0");
                }
            }
        } finally { for (var connection : borrowed) connection.close(); }
        service.addItem(owner, addRequest(1));
        assertThat(service.getCart(owner).getItems().getFirst().getQuantity()).isEqualTo(1);
    }

    void assertBlockedWrite(String lockSql, Runnable write) throws Exception {
        CountDownLatch held = new CountDownLatch(1), release = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> holder = executor.submit(() -> transactions.executeWithoutResult(s -> {
            jdbc.queryForObject(lockSql, Long.class, owner); held.countDown(); await(release);
        }));
        try {
            assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();
            long started = System.nanoTime();
            Throwable failure = org.assertj.core.api.Assertions.catchThrowable(write::run);
            assertThat(failure).isNotNull();
            assertThat(new com.example.demo.common.exception.ApiErrorFactory().from(failure, "/api/me/cart").getStatus()).isEqualTo(409);
            assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).isLessThan(3000);
        } finally {
            release.countDown(); holder.get(10, TimeUnit.SECONDS); executor.shutdownNow();
        }
    }

    @Test void concurrentSelectionAndMergeWithAdd_shouldSerializeWithoutLostState() throws Exception {
        Long source = service.addItem(owner, addRequest(9)).getItemId();
        UUID target = sibling();
        Long destination = service.addItem(owner, addRequest(target, 6)).getItemId();
        race(() -> service.updateItem(owner, source, update(target, 4)), () -> service.addItem(owner, addRequest(target, 3)));
        assertThat(service.getCart(owner).getItems()).hasSize(1);
        assertThat(service.getCart(owner).getItems().getFirst().getQuantity()).isEqualTo(13);
        race(() -> service.selectItem(owner, destination, selection(false)), () -> service.updateItem(owner, destination, update(null, 7)));
        var row = service.getCart(owner).getItems().getFirst();
        assertThat(row.getQuantity()).isEqualTo(7); assertThat(row.isSelected()).isFalse();
        race(() -> service.selectItem(owner, destination, selection(false)), () -> service.addItem(owner, addRequest(target, 3)));
        assertThat(service.getCart(owner).getItems().getFirst().getQuantity()).isEqualTo(10);
        Long nextSource = service.addItem(owner, addRequest(2)).getItemId();
        race(() -> service.selectItem(owner, destination, selection(true)), () -> service.updateItem(owner, nextSource, update(target, 2)));
        assertThat(service.getCart(owner).getItems().getFirst().getQuantity()).isEqualTo(12);
        assertThat(service.getCart(owner).getItems().getFirst().isSelected()).isTrue();
    }

    void race(Runnable first, Runnable second) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> a = executor.submit(() -> { await(start); first.run(); });
            Future<?> b = executor.submit(() -> { await(start); second.run(); });
            start.countDown(); a.get(10, TimeUnit.SECONDS); b.get(10, TimeUnit.SECONDS);
        }
    }

    @Test void getCart_shouldReturnAll105RowsInOrderWithoutWritesOrPerRowQueries() {
        service.addItem(owner, addRequest(1));
        Long cartId = jdbc.queryForObject("SELECT id FROM carts WHERE user_id=?", Long.class, owner);
        transactions.executeWithoutResult(s -> {
            for (int index=0; index<104; index++) {
                var fixture = CatalogFixtures.persistProduct(entities, ProductStatus.PUBLISHED);
                entities.flush();
                jdbc.update("INSERT INTO cart_items(cart_id,product_id,product_variant_id,quantity,selected,added_at,public_product_name,public_sku,public_options) "
                        + "SELECT ?,product_id,id,1,TRUE,TIMESTAMPTZ '2026-01-01 00:00:00+00', 'Prior name',sku,option_combination FROM product_variants WHERE id=?",
                        cartId, fixture.variant().getId());
            }
        });
        var statistics = entities.getEntityManagerFactory().unwrap(org.hibernate.SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true); statistics.clear();
        CartResponse response;
        try {
            response = service.getCart(owner);
            assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(15);
            assertThat(statistics.getEntityUpdateCount()).isZero();
            assertThat(statistics.getEntityInsertCount()).isZero();
            assertThat(statistics.getEntityDeleteCount()).isZero();
        } finally { statistics.setStatisticsEnabled(false); }
        assertThat(response.getItems()).hasSize(105);
        assertThat(response.getItems().stream().map(row -> row.getId()).toList()).containsExactlyElementsOf(
                jdbc.queryForList("SELECT id FROM cart_items WHERE cart_id=? ORDER BY added_at DESC,id ASC", Long.class, cartId));
    }

    @Test void competingMergesAndDelete_shouldPreserveEveryAcceptedOperation() throws Exception {
        Long source = service.addItem(owner, addRequest(9)).getItemId();
        UUID target = sibling(), third = sibling();
        Long second = service.addItem(owner, addRequest(third, 8)).getItemId();
        Long destination = service.addItem(owner, addRequest(target, 6)).getItemId();
        race(() -> service.updateItem(owner, source, update(target, 4)), () -> service.updateItem(owner, second, update(target, 3)));
        assertThat(service.getCart(owner).getItems()).hasSize(1);
        assertThat(service.getCart(owner).getItems().getFirst().getQuantity()).isEqualTo(13);
        race(() -> service.removeItem(owner, destination), () -> {
            try { service.selectItem(owner, destination, selection(false)); }
            catch (CartItemNotFoundException removedFirst) { /* permitted serialized outcome */ }
        });
        assertThat(service.getCart(owner).getItems()).isEmpty();
    }

    @Test void update_shouldRecoverUnavailableSourceThroughEligibleSiblingAndRejectRetainedInvalidPrice() {
        Long source = service.addItem(owner, addRequest(2)).getItemId();
        UUID target = sibling();
        jdbc.update("UPDATE product_variants SET selling_price=-1 WHERE id=?", variant);
        assertThatThrownBy(() -> service.updateItem(owner, source, update(null, 3))).isInstanceOf(CartItemUnavailableException.class);
        var response = service.updateItem(owner, source, update(target, 3));
        assertThat(response.getItems().getFirst().getId()).isEqualTo(source);
        assertThat(response.getItems().getFirst().getVariantId()).isEqualTo(target);
        assertThat(response.getItems().getFirst().getQuantity()).isEqualTo(3);
        assertThat(response.getItems().getFirst().isPurchasable()).isTrue();
    }

    @Test void writes_shouldSerializeNearLimitAdditionsAndAllowIndependentOwners() throws Exception {
        service.addItem(owner, addRequest(999));
        var rejected = new java.util.concurrent.atomic.AtomicInteger();
        Runnable increment = () -> {
            try { service.addItem(owner, addRequest(1)); }
            catch (CartQuantityLimitException atLimit) { rejected.incrementAndGet(); }
        };
        race(increment, increment);
        assertThat(rejected.get()).isEqualTo(1);
        assertThat(service.getCart(owner).getItems().getFirst().getQuantity()).isEqualTo(1000);
        Long other = transactions.execute(s -> WishlistFixtures.persistUser(entities).getId());
        service.addItem(other, addRequest(1));
        var held = new CountDownLatch(1); var release = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        var holder = executor.submit(() -> transactions.executeWithoutResult(s -> {
            jdbc.queryForObject("SELECT id FROM carts WHERE user_id=? FOR UPDATE", Long.class, owner);
            held.countDown(); await(release);
        }));
        try {
            assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(service.addItem(other, addRequest(1)).getResponse().getItems().getFirst().getQuantity()).isEqualTo(2);
        } finally {
            release.countDown(); holder.get(10, TimeUnit.SECONDS); executor.shutdownNow();
        }
    }

    static void await(CountDownLatch latch) {
        CartTestBarrier.await(latch);
    }
}
