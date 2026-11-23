package com.example.demo.cart.service;

import com.example.demo.cart.dto.request.AddCartItemRequest;
import com.example.demo.cart.dto.request.UpdateCartQuantityRequest;
import com.example.demo.cart.dto.request.ChangeCartVariantRequest;
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
    @Autowired com.example.demo.checkout.service.CheckoutService checkout;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    com.example.demo.catalog.service.CatalogCartReadService catalogBoundary;

    @Test void stockChecks_shouldUseFullDemandAndKeepShortagePreferencesWithoutReserving() {
        com.example.demo.fixture.inventory.InventoryFixtures.supplyStock(jdbc, variant, 3);
        long item = service.addItem(owner, addRequest(2)).getItemId();
        var before = jdbc.queryForMap("SELECT * FROM cart_items WHERE id=?", item);
        assertThatThrownBy(() -> service.addItem(owner, addRequest(2))).isInstanceOf(com.example.demo.cart.exception.CartStockConflictException.class);
        assertThatThrownBy(() -> service.updateQuantity(owner, item, quantity(4))).isInstanceOf(com.example.demo.cart.exception.CartStockConflictException.class);
        assertThat(jdbc.queryForMap("SELECT * FROM cart_items WHERE id=?", item)).isEqualTo(before);
        service.updateQuantity(owner, item, quantity(3));
        com.example.demo.fixture.inventory.InventoryFixtures.supplyStock(jdbc, variant, 2);
        var shortage = service.getCart(owner);
        assertThat(shortage.getTotalItemCount()).isEqualTo(1);
        assertThat(shortage.getSelectedItemCount()).isZero();
        assertThat(shortage.getSelectedTotalQuantity()).isZero();
        var row = shortage.getItems().getFirst();
        assertThat(row.getQuantity()).isEqualTo(3);
        assertThat(row.isSelected()).isTrue();
        assertThat(row.isPurchasable()).isFalse();
        assertThat(row.getAvailabilityReason()).isEqualTo("INSUFFICIENT_STOCK");
        assertThat(row.getUnitPrice()).isNotNull();
        assertThat(row.getSubtotal()).isNull();
        assertThat(row.getAvailability().getAvailableQuantity()).isEqualTo(2);
        assertThatThrownBy(() -> service.selectItem(owner, item, selection(true))).isInstanceOf(com.example.demo.cart.exception.CartStockConflictException.class);
        assertThat(service.selectAll(owner, selection(true)).getItems().getFirst().isSelected()).isTrue();
        assertThat(service.selectItem(owner, item, selection(false)).getItems().getFirst().isSelected()).isFalse();
        service.removeItem(owner, item);
        assertThat(jdbc.queryForMap("SELECT stock,reserved FROM inventory_balances WHERE original_variant_id=?", variant))
                .containsEntry("stock", 2L).containsEntry("reserved", 0L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_reservations", Long.class)).isZero();
    }

    @Test void variantStockChecks_shouldUseResetOneAndUnchangedExistingOrSameTargetQuantities() {
        long source = service.addItem(owner, addRequest(5)).getItemId();
        UUID target = sibling();
        com.example.demo.fixture.inventory.InventoryFixtures.supplyStock(jdbc, target, 1);
        assertThat(service.changeVariant(owner, source, variant(target)).getItems().getFirst().getQuantity()).isEqualTo(1);
        service.updateQuantity(owner, source, quantity(1));
        com.example.demo.fixture.inventory.InventoryFixtures.supplyStock(jdbc, target, 0);
        var sameBefore = jdbc.queryForMap("SELECT * FROM cart_items WHERE id=?", source);
        assertThatThrownBy(() -> service.changeVariant(owner, source, variant(target))).isInstanceOf(com.example.demo.cart.exception.CartStockConflictException.class);
        assertThat(jdbc.queryForMap("SELECT * FROM cart_items WHERE id=?", source)).isEqualTo(sameBefore);
        com.example.demo.fixture.inventory.InventoryFixtures.supplyStock(jdbc, target, 3);
        service.updateQuantity(owner, source, quantity(3));
        service.selectItem(owner, source, selection(false));
        long other = service.addItem(owner, addRequest(5)).getItemId();
        var targetBefore = jdbc.queryForMap("SELECT * FROM cart_items WHERE id=?", source);
        var sourceBefore = jdbc.queryForMap("SELECT * FROM cart_items WHERE id=?", other);
        com.example.demo.fixture.inventory.InventoryFixtures.supplyStock(jdbc, target, 2);
        assertThatThrownBy(() -> service.changeVariant(owner, other, variant(target))).isInstanceOf(com.example.demo.cart.exception.CartStockConflictException.class);
        assertThat(jdbc.queryForMap("SELECT * FROM cart_items WHERE id=?", source)).isEqualTo(targetBefore);
        assertThat(jdbc.queryForMap("SELECT * FROM cart_items WHERE id=?", other)).isEqualTo(sourceBefore);
        com.example.demo.fixture.inventory.InventoryFixtures.supplyStock(jdbc, target, 3);
        service.changeVariant(owner, other, variant(target));
        assertThat(jdbc.queryForMap("SELECT * FROM cart_items WHERE id=?", source)).isEqualTo(targetBefore);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_reservations", Long.class)).isZero();
    }

    @Test void missingStock_shouldRejectNewCartAtomicallyAndNeverAutoProvision() {
        com.example.demo.fixture.inventory.InventoryFixtures.supplyStock(jdbc, variant, 0);
        com.example.demo.fixture.inventory.InventoryFixtures.removeUnusedBalance(jdbc, variant);
        assertThatThrownBy(() -> service.addItem(owner, addRequest(1))).isInstanceOf(com.example.demo.cart.exception.CartStockConflictException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM carts", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_balances WHERE original_variant_id=?", Long.class, variant)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_reservations", Long.class)).isZero();
    }

    @Test void missingStockOnAnExistingRow_shouldRemainVisibleAndAllowDeselectAndRemoval() {
        long item = service.addItem(owner, addRequest(2)).getItemId();
        com.example.demo.fixture.inventory.InventoryFixtures.supplyStock(jdbc, variant, 0);
        com.example.demo.fixture.inventory.InventoryFixtures.removeUnusedBalance(jdbc, variant);
        var row = service.getCart(owner).getItems().getFirst();
        assertThat(row.getQuantity()).isEqualTo(2);
        assertThat(row.isSelected()).isTrue();
        assertThat(row.getAvailabilityReason()).isEqualTo("INVENTORY_UNAVAILABLE");
        assertThat(row.getAvailability().getAvailableQuantity()).isZero();
        assertThat(row.getAvailability().getStatus()).isEqualTo("INVENTORY_UNAVAILABLE");
        assertThat(row.getUnitPrice()).isNotNull();
        assertThat(row.getSubtotal()).isNull();
        assertThat(service.selectItem(owner, item, selection(false)).getItems().getFirst().isSelected()).isFalse();
        service.removeItem(owner, item);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_balances WHERE original_variant_id=?", Long.class, variant)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_reservations", Long.class)).isZero();
    }

    @Test void finalStockObservation_shouldRollbackCartChangeAfterAnotherCustomersCommittedReservation() throws Exception {
        com.example.demo.fixture.inventory.InventoryFixtures.supplyStock(jdbc, variant, 3);
        long item = service.addItem(owner, addRequest(1)).getItemId();
        var before = jdbc.queryForMap("SELECT * FROM cart_items WHERE id=?", item);
        var other = transactions.execute(s -> com.example.demo.fixture.checkout.CheckoutFixtures.scenario(entities));
        jdbc.update("UPDATE cart_items SET product_id=?,product_variant_id=? WHERE id=?", product, variant, other.item());
        CountDownLatch finalRead = new CountDownLatch(1);
        CountDownLatch continueRead = new CountDownLatch(1);
        org.mockito.Mockito.doAnswer(call -> {
            if (Thread.currentThread().getName().equals("inventory-cart-mutation")) {
                finalRead.countDown();
                if (!continueRead.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Final cart observation timed out");
            }
            return call.callRealMethod();
        }).when(catalogBoundary).getCartPresentation(org.mockito.ArgumentMatchers.anyCollection(), org.mockito.ArgumentMatchers.any());
        try (var worker = Executors.newSingleThreadExecutor()) {
            var changed = worker.submit(() -> {
                Thread.currentThread().setName("inventory-cart-mutation");
                return service.updateQuantity(owner, item, quantity(2));
            });
            try {
                assertThat(finalRead.await(10, TimeUnit.SECONDS)).isTrue();
                checkout.checkout(other.owner(), "Cart_recheck_key_123", new com.example.demo.checkout.dto.request.CheckoutRequest(other.address()));
            } finally { continueRead.countDown(); }
            assertThatThrownBy(() -> changed.get(15, TimeUnit.SECONDS)).hasCauseInstanceOf(com.example.demo.cart.exception.CartStockConflictException.class);
        } finally { continueRead.countDown(); }
        assertThat(jdbc.queryForMap("SELECT * FROM cart_items WHERE id=?", item)).isEqualTo(before);
        assertThat(jdbc.queryForMap("SELECT stock,reserved FROM inventory_balances WHERE original_variant_id=?", variant))
                .containsEntry("stock", 3L).containsEntry("reserved", 2L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE customer_id=?", Long.class, owner)).isZero();
        service.selectItem(owner, item, selection(false));
        assertThat(jdbc.queryForObject("SELECT selected FROM cart_items WHERE id=?", Boolean.class, item)).isFalse();
    }

    @BeforeEach void prepare() {
        cleanup();
        org.mockito.Mockito.doReturn((com.example.demo.storage.service.PreparedImageUrls) key -> "https://images.test/cart")
                .when(imageUrls).prepare();
        transactions.executeWithoutResult(status -> {
            owner = WishlistFixtures.persistUser(entities).getId();
            CatalogFixtures.ProductFixture fixture = CatalogFixtures.persistReadyProduct(entities);
            variant = fixture.variant().getId();
            product = fixture.product().getId();
        });
        com.example.demo.fixture.inventory.InventoryFixtures.supplyStock(jdbc, variant, 1000);
    }
    @AfterEach void cleanup() { com.example.demo.fixture.inventory.InventoryFixtures.cleanRetainedInventory(jdbc); }

    AddCartItemRequest addRequest(int quantity) {
        AddCartItemRequest request = new AddCartItemRequest();
        request.setProductVariantId(variant);
        request.setQuantity(quantity);
        return request;
    }

    @Test void getCart_shouldNotCreateMissingCartAndAddShouldAccumulateWithoutCapping() {
        assertThat(service.getCart(owner).getItems()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM carts", Long.class)).isZero();
        assertThat(service.addItem(owner, addRequest(2)).isCreated()).isTrue();
        assertThat(service.addItem(owner, addRequest(3)).isCreated()).isFalse();
        assertThat(service.getCart(owner).getItems().getFirst().getQuantity()).isEqualTo(5);
        service.addItem(owner, addRequest(994));
        assertThatThrownBy(() -> service.addItem(owner, addRequest(1))).isInstanceOf(CartQuantityLimitException.class);
        assertThat(service.getCart(owner).getItems().getFirst().getQuantity()).isEqualTo(999);
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
            entities.flush();
            com.example.demo.fixture.inventory.InventoryFixtures.supplyStock(entities, target.getId(), 1000);
            return target.getId();
        });
    }
    AddCartItemRequest addRequest(UUID target, int quantity) {
        var request = addRequest(quantity); request.setProductVariantId(target); return request;
    }
    UpdateCartQuantityRequest quantity(int quantity) {
        UpdateCartQuantityRequest request = new UpdateCartQuantityRequest();
        request.setQuantity(quantity);
        return request;
    }
    ChangeCartVariantRequest variant(UUID target) {
        ChangeCartVariantRequest request = new ChangeCartVariantRequest();
        request.setProductVariantId(target);
        return request;
    }

    @Test void changeVariant_shouldResetAbsentTargetAndKeepExistingTargetEntirelyUnchanged() {
        Long source = service.addItem(owner, addRequest(8)).getItemId();
        jdbc.update("UPDATE cart_items SET selected=FALSE WHERE id=?", source);
        UUID target = sibling();
        var sourceTime = jdbc.queryForObject("SELECT added_at FROM cart_items WHERE id=?", java.sql.Timestamp.class, source);
        service.updateQuantity(owner, source, quantity(4));
        service.changeVariant(owner, source, variant(target));
        var changed = service.getCart(owner).getItems().getFirst();
        assertThat(changed.getId()).isEqualTo(source);
        assertThat(changed.getQuantity()).isEqualTo(1);
        assertThat(changed.isSelected()).isFalse();
        assertThat(jdbc.queryForObject("SELECT added_at FROM cart_items WHERE id=?", java.sql.Timestamp.class, source)).isEqualTo(sourceTime);
        service.updateQuantity(owner, source, quantity(8));
        service.changeVariant(owner, source, variant(target));
        assertThat(service.getCart(owner).getItems().getFirst().getQuantity()).isEqualTo(8);
        Long existing = service.addItem(owner, addRequest(999)).getItemId();
        jdbc.update("UPDATE cart_items SET selected=FALSE WHERE id=?", existing);
        var before = jdbc.queryForMap("SELECT * FROM cart_items WHERE id=?", existing);
        service.changeVariant(owner, source, variant(variant));
        assertThat(jdbc.queryForMap("SELECT * FROM cart_items WHERE id=?", existing)).isEqualTo(before);
        assertThat(service.getCart(owner).getTotalItemCount()).isEqualTo(1);
        service.removeItem(owner, existing);
        assertThat(service.getCart(owner).getTotalItemCount()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM carts WHERE user_id=?", Long.class, owner)).isEqualTo(1);
        assertThatThrownBy(() -> service.removeItem(owner, existing)).isInstanceOf(CartItemNotFoundException.class);
    }

    @Test void update_shouldCheckOwnershipBeforeTargetAndRejectCrossProductWithoutChangingRows() {
        Long source = service.addItem(owner, addRequest(8)).getItemId();
        UUID foreignProductVariant = transactions.execute(s -> CatalogFixtures.persistReadyProduct(entities).variant().getId());
        assertThatThrownBy(() -> service.changeVariant(owner, source, variant(foreignProductVariant))).isInstanceOf(CartVariantSwitchException.class);
        Long other = transactions.execute(s -> WishlistFixtures.persistUser(entities).getId());
        assertThatThrownBy(() -> service.changeVariant(other, source, variant(UUID.randomUUID()))).isInstanceOf(CartItemNotFoundException.class);
        assertThat(service.getCart(owner).getItems().getFirst().getQuantity()).isEqualTo(8);
    }

    CartSelectionRequest selection(boolean selected) {
        var request = new CartSelectionRequest(); request.setSelected(selected); return request;
    }

    @Test void selection_shouldKeepUnavailablePreferencesAndCountOnlyEligibleRows() {
        service.selectAll(owner, selection(true));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM carts", Long.class)).isZero();
        Long first = service.addItem(owner, addRequest(2)).getItemId();
        UUID other = transactions.execute(s -> CatalogFixtures.persistReadyProduct(entities).variant().getId());
        com.example.demo.fixture.inventory.InventoryFixtures.supplyStock(jdbc, other, 1000);
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
        // The shared positive fixture already owns one valid primary.
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
        assertThatThrownBy(() -> service.changeVariant(owner, source, variant(target))).isInstanceOf(com.example.demo.catalog.exception.CatalogStorageUnavailableException.class);
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
        race(() -> service.changeVariant(owner, source, variant(target)), () -> service.addItem(owner, addRequest(target, 3)));
        assertThat(service.getCart(owner).getItems()).hasSize(1);
        assertThat(service.getCart(owner).getItems().getFirst().getQuantity()).isEqualTo(9);
        race(() -> service.selectItem(owner, destination, selection(false)), () -> service.updateQuantity(owner, destination, quantity(7)));
        var row = service.getCart(owner).getItems().getFirst();
        assertThat(row.getQuantity()).isEqualTo(7); assertThat(row.isSelected()).isFalse();
        race(() -> service.selectItem(owner, destination, selection(false)), () -> service.addItem(owner, addRequest(target, 3)));
        assertThat(service.getCart(owner).getItems().getFirst().getQuantity()).isEqualTo(10);
        Long nextSource = service.addItem(owner, addRequest(2)).getItemId();
        race(() -> service.selectItem(owner, destination, selection(true)), () -> service.changeVariant(owner, nextSource, variant(target)));
        assertThat(service.getCart(owner).getItems().getFirst().getQuantity()).isEqualTo(10);
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
                var fixture = CatalogFixtures.persistReadyProduct(entities);
                entities.flush();
                com.example.demo.fixture.inventory.InventoryFixtures.supplyStock(entities, fixture.variant().getId(), 1000);
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
        race(() -> service.changeVariant(owner, source, variant(target)), () -> service.changeVariant(owner, second, variant(target)));
        assertThat(service.getCart(owner).getItems()).hasSize(1);
        assertThat(service.getCart(owner).getItems().getFirst().getQuantity()).isEqualTo(6);
        race(() -> service.removeItem(owner, destination), () -> {
            try { service.selectItem(owner, destination, selection(false)); }
            catch (CartItemNotFoundException removedFirst) { /* permitted serialized outcome */ }
        });
        assertThat(service.getCart(owner).getItems()).isEmpty();
    }

    @Test void update_shouldRecoverUnavailableSourceThroughEligibleSiblingAndRejectRetainedInvalidPrice() {
        Long source = service.addItem(owner, addRequest(2)).getItemId();
        UUID target = sibling();
        assertThatThrownBy(()->jdbc.update("UPDATE product_variants SET selling_price=-1 WHERE id=?",variant))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        // Disabled preparation may retain a basic-valid negative price; Enabled sibling stays sellable.
        jdbc.update("UPDATE product_variants SET enabled=FALSE,selling_price=-1 WHERE id=?",variant);
        assertThatThrownBy(() -> service.updateQuantity(owner, source, quantity(3))).isInstanceOf(CartItemUnavailableException.class);
        var response = service.changeVariant(owner, source, variant(target));
        assertThat(response.getItems().getFirst().getId()).isEqualTo(source);
        assertThat(response.getItems().getFirst().getVariantId()).isEqualTo(target);
        assertThat(response.getItems().getFirst().getQuantity()).isEqualTo(1);
        assertThat(response.getItems().getFirst().isPurchasable()).isTrue();
    }

    @Test void writes_shouldSerializeNearLimitAdditionsAndAllowIndependentOwners() throws Exception {
        service.addItem(owner, addRequest(998));
        var rejected = new java.util.concurrent.atomic.AtomicInteger();
        Runnable increment = () -> {
            try { service.addItem(owner, addRequest(1)); }
            catch (CartQuantityLimitException atLimit) { rejected.incrementAndGet(); }
        };
        race(increment, increment);
        assertThat(rejected.get()).isEqualTo(1);
        assertThat(service.getCart(owner).getItems().getFirst().getQuantity()).isEqualTo(999);
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
