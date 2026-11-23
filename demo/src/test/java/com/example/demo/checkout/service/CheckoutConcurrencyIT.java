package com.example.demo.checkout.service;

import com.example.demo.checkout.dto.request.CheckoutRequest;
import com.example.demo.checkout.dto.response.CheckoutReceiptResponse;
import com.example.demo.checkout.exception.CheckoutInProgressException;
import com.example.demo.checkout.exception.NoEligibleCheckoutItemsException;
import com.example.demo.checkout.repository.CheckoutReceiptRepository;
import com.example.demo.config.CheckoutIntegrationSupport;
import com.example.demo.fixture.checkout.CheckoutFixtures;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.mockito.stubbing.Answer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties={"spring.datasource.hikari.maximum-pool-size=6", "checkout.lock-timeout-ms=1000", "user.lock-timeout-ms=3000"})
@org.springframework.context.annotation.Import(com.example.demo.catalog.service.AdminCatalogIntegrationSupport.LocalSigning.class)
class CheckoutConcurrencyIT extends CheckoutIntegrationSupport {
    private static final String KEY="Synchronized_key_123";
    private static final int WAIT_SECONDS=20;
    @Autowired CheckoutService service;
    @MockitoSpyBean CheckoutReceiptRepository receipts;
    @MockitoSpyBean com.example.demo.cart.service.CartCheckoutService cartBoundary;
    @MockitoSpyBean com.example.demo.inventory.service.InventoryService inventoryBoundary;
    @Autowired com.example.demo.cart.service.CartService cartService;
    @MockitoSpyBean com.example.demo.catalog.service.CatalogCheckoutService catalogBoundary;
    @Autowired com.example.demo.user.service.AddressService addressService;
    @Autowired com.example.demo.order.service.OrderService orderService;
    @Autowired javax.sql.DataSource dataSource;
    @Autowired com.example.demo.common.exception.ApiErrorFactory errors;
    Answer<?> delegate;

    @BeforeEach void captureRealRepositoryDelegate() {
        delegate=mockingDetails(receipts).getMockCreationSettings().getDefaultAnswer();
    }

    private CheckoutReceiptResponse checkout(String key) {
        return service.checkout(fixture.owner(),key,new CheckoutRequest(fixture.address()));
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void checkout_shouldReturnBothConcurrentCompletedReplaysWithoutAcquiringFence(boolean voucher) throws Exception {
        if(voucher) selectVoucher();
        CheckoutReceiptResponse original=checkout(KEY);
        clearInvocations(receipts, inventoryBoundary);
        CyclicBarrier start=new CyclicBarrier(2);
        ExecutorService workers=Executors.newFixedThreadPool(2);
        try {
            Future<CheckoutReceiptResponse> first=workers.submit(() -> { start.await(WAIT_SECONDS,TimeUnit.SECONDS); return checkout(KEY); });
            Future<CheckoutReceiptResponse> second=workers.submit(() -> { start.await(WAIT_SECONDS,TimeUnit.SECONDS); return checkout(KEY); });
            assertThat(first.get(WAIT_SECONDS,TimeUnit.SECONDS)).usingRecursiveComparison().isEqualTo(original);
            assertThat(second.get(WAIT_SECONDS,TimeUnit.SECONDS)).usingRecursiveComparison().isEqualTo(original);
            verify(receipts,never()).tryFence(anyLong());
            org.mockito.Mockito.verifyNoInteractions(inventoryBoundary);
            assertOneCommittedOutcome();
        } finally { stop(workers); }
    }

    @Autowired com.example.demo.cart.service.CartVoucherService cartVouchers;
    private long selectVoucher() {
        long grant=createVoucher();
        cartVouchers.applyVoucher(fixture.owner(),grant);
        return grant;
    }
    private long createVoucher() {
        long grant=transactions.execute(tx -> {
            long voucher=com.example.demo.fixture.voucher.VoucherFixtures.definition(jdbc,"RACE_"+System.nanoTime(),"FIXED_AMOUNT",java.math.BigDecimal.TEN,1L,1L);
            long usage=com.example.demo.fixture.voucher.VoucherFixtures.usage(jdbc,voucher,fixture.owner());
            return com.example.demo.fixture.voucher.VoucherFixtures.grant(jdbc,voucher,fixture.owner(),usage,Instant.now().minusSeconds(1));
        });
        return grant;
    }
    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> replayCases() {
        return java.util.Arrays.stream(Race.values()).flatMap(race -> java.util.stream.Stream.of(false,true).map(voucher -> org.junit.jupiter.params.provider.Arguments.of(race,voucher)));
    }
    enum Race { ACQUIRED_COMMIT, BUSY_COMMIT, GENUINELY_PROCESSING }

    @ParameterizedTest @org.junit.jupiter.params.provider.MethodSource("replayCases")
    @SuppressWarnings("unchecked")
    void checkout_shouldResolveFreshReceiptAtEachSynchronizedFenceBoundary(Race race, boolean voucher) throws Exception {
        if(voucher) selectVoucher();
        CountDownLatch inserted=new CountDownLatch(1);
        CountDownLatch releaseOriginal=new CountDownLatch(1);
        CountDownLatch retryBoundary=new CountDownLatch(1);
        CountDownLatch releaseRetry=new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean firstMiss=new java.util.concurrent.atomic.AtomicBoolean();
        doAnswer(invocation -> {
            Object result=delegate.answer(invocation);
            if(Thread.currentThread().getName().equals("checkout-original")) {
                inserted.countDown();
                await(releaseOriginal);
            }
            return result;
        }).when(receipts).insertSuccess(anyLong(),anyString(),anyLong(),anyString(),anyLong(),anyString(),anyString(),any(Instant.class));
        if(race==Race.ACQUIRED_COMMIT) {
            doAnswer(invocation -> {
                Optional<?> result=(Optional<?>)delegate.answer(invocation);
                if(Thread.currentThread().getName().equals("checkout-retry") && result.isEmpty() && firstMiss.compareAndSet(false,true)) {
                    retryBoundary.countDown();
                    await(releaseRetry);
                }
                return result;
            }).when(receipts).findByCustomerIdAndOperationAndIdempotencyKey(anyLong(),anyString(),anyString());
        } else if(race==Race.BUSY_COMMIT) {
            doAnswer(invocation -> {
                boolean result=(Boolean)delegate.answer(invocation);
                if(Thread.currentThread().getName().equals("checkout-retry") && !result) {
                    retryBoundary.countDown();
                    await(releaseRetry);
                }
                return result;
            }).when(receipts).tryFence(anyLong());
        }
        ExecutorService workers=Executors.newFixedThreadPool(2);
        try {
            Future<CheckoutReceiptResponse> original=workers.submit(() -> {
                Thread.currentThread().setName("checkout-original");
                return checkout(KEY);
            });
            await(inserted); // Actual order/item/cart/receipt SQL issued, transaction intentionally uncommitted.
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isZero();
            Future<CheckoutReceiptResponse> retry=workers.submit(() -> {
                Thread.currentThread().setName("checkout-retry");
                return checkout(KEY);
            });
            CheckoutReceiptResponse replay;
            if(race==Race.GENUINELY_PROCESSING) {
                assertThatThrownBy(() -> retry.get(WAIT_SECONDS,TimeUnit.SECONDS)).hasCauseInstanceOf(CheckoutInProgressException.class);
                releaseOriginal.countDown();
                CheckoutReceiptResponse winner=original.get(WAIT_SECONDS,TimeUnit.SECONDS);
                replay=checkout(KEY);
                assertThat(replay).usingRecursiveComparison().isEqualTo(winner);
            } else {
                await(retryBoundary);
                releaseOriginal.countDown();
                CheckoutReceiptResponse winner=original.get(WAIT_SECONDS,TimeUnit.SECONDS); // Commit before releasing recheck.
                releaseRetry.countDown();
                replay=retry.get(WAIT_SECONDS,TimeUnit.SECONDS);
                assertThat(replay).usingRecursiveComparison().isEqualTo(winner);
            }
            assertThat(replay.getOrder().getStatus().name()).isEqualTo("PENDING");
            assertOneCommittedOutcome();
        } finally {
            releaseOriginal.countDown();
            releaseRetry.countDown();
            stop(workers);
        }
    }

    @Test void checkout_shouldSerializeDifferentKeysWithoutDuplicateConsumptionAndRetainLaterAddition() throws Exception {
        CyclicBarrier start=new CyclicBarrier(2);
        ExecutorService workers=Executors.newFixedThreadPool(2);
        try {
            Future<CheckoutReceiptResponse> a=workers.submit(() -> attemptAfter(start,"Parallel_key_A123"));
            Future<CheckoutReceiptResponse> b=workers.submit(() -> attemptAfter(start,"Parallel_key_B123"));
            CheckoutReceiptResponse first=a.get(WAIT_SECONDS,TimeUnit.SECONDS);
            CheckoutReceiptResponse second=b.get(WAIT_SECONDS,TimeUnit.SECONDS);
            assertThat((first==null ? 0 : 1)+(second==null ? 0 : 1)).isEqualTo(1);
            assertOneCommittedOutcome();
            String successfulKey=first==null ? "Parallel_key_B123" : "Parallel_key_A123";
            String retryKey=first==null ? "Parallel_key_A123" : "Parallel_key_B123";
            CheckoutReceiptResponse original=first==null ? second : first;
            transactions.executeWithoutResult(s -> CheckoutFixtures.additionalItem(entities,fixture.cart(),true));
            assertThat(checkout(retryKey).getOrder().getId()).isNotEqualTo(original.getOrder().getId());
            assertThat(checkout(successfulKey)).usingRecursiveComparison().isEqualTo(original);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isEqualTo(2);
        } finally { stop(workers); }
    }

    private CheckoutReceiptResponse attemptAfter(CyclicBarrier start, String key) throws Exception {
        start.await(WAIT_SECONDS,TimeUnit.SECONDS);
        try { return checkout(key); }
        catch(NoEligibleCheckoutItemsException expected) { return null; }
    }

    private void assertOneCommittedOutcome() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_reservations", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT quantity FROM inventory_reservations", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForMap("SELECT stock,reserved FROM inventory_balances WHERE original_variant_id=?", fixture.variant()))
                .containsEntry("stock", 1000L).containsEntry("reserved", 2L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_items",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM checkout_receipts",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT id FROM cart_items",Long.class)).containsExactly(fixture.unselected());
        assertThat(jdbc.queryForObject("SELECT quantity FROM cart_items WHERE id=?",Integer.class,fixture.unselected())).isEqualTo(2);
        if(jdbc.queryForObject("SELECT COUNT(*) FROM vouchers",Long.class)>0) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM voucher_redemptions",Long.class)).isOne();
            assertThat(jdbc.queryForObject("SELECT redeemed_count FROM vouchers",Long.class)).isOne();
            assertThat(jdbc.queryForObject("SELECT redeemed_count FROM voucher_customer_usages",Long.class)).isOne();
            assertThat(jdbc.queryForObject("SELECT state FROM voucher_entitlements",String.class)).isEqualTo("CONSUMED");
            assertThat(jdbc.queryForObject("SELECT voucher_entitlement_id FROM carts WHERE id=?",Long.class,fixture.cart())).isNull();
        }
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        if(!latch.await(WAIT_SECONDS,TimeUnit.SECONDS)) throw new IllegalStateException("Synchronized checkout boundary timed out");
    }

    private static void stop(ExecutorService workers) throws InterruptedException {
        workers.shutdownNow();
        if(!workers.awaitTermination(WAIT_SECONDS,TimeUnit.SECONDS)) throw new IllegalStateException("Checkout workers did not stop");
    }

    enum CartMutation { ADD, QUANTITY, VARIANT, REMOVE, SELECT_TRUE, SELECT_FALSE, BULK_TRUE, BULK_FALSE }

    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> cartCases() {
        return java.util.Arrays.stream(CartMutation.values()).flatMap(mutation -> java.util.stream.Stream.of(
                org.junit.jupiter.params.provider.Arguments.of(mutation,true),org.junit.jupiter.params.provider.Arguments.of(mutation,false)));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("cartCases")
    void checkout_shouldPreserveOrRepresentEveryOverlappingCartMutation(CartMutation mutation, boolean mutationFirst) throws Exception {
        java.util.UUID target=transactions.execute(s -> {
            com.example.demo.catalog.entity.ProductVariant variant=com.example.demo.catalog.CatalogFixtures.variant(
                    entities.getReference(com.example.demo.catalog.entity.Product.class,fixture.product()),
                    java.util.Map.of("choice","target"),java.math.BigDecimal.valueOf(2000));
            entities.persist(variant);
            entities.flush();
            com.example.demo.fixture.inventory.InventoryFixtures.supplyStock(entities, variant.getId(), 1000);
            return variant.getId();
        });
        java.util.Map<String,Object> unselectedBefore=jdbc.queryForMap("SELECT * FROM cart_items WHERE id=?",fixture.unselected());
        CountDownLatch held=new CountDownLatch(1);
        CountDownLatch release=new CountDownLatch(1);
        if(!mutationFirst) {
            Answer<?> cartDelegate=mockingDetails(cartBoundary).getMockCreationSettings().getDefaultAnswer();
            doAnswer(invocation -> {
                Object result=cartDelegate.answer(invocation);
                held.countDown();
                await(release);
                return result;
            }).when(cartBoundary).getCheckoutCartSeed(anyLong());
        }
        ExecutorService workers=Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> changed;
            Future<CheckoutReceiptResponse> purchased;
            if(mutationFirst) {
                changed=workers.submit(() -> transactions.execute(s -> {
                    boolean accepted=mutate(mutation,target);
                    held.countDown();
                    try { await(release); } catch(InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
                    return accepted;
                }));
                await(held);
                purchased=workers.submit(this::checkoutOrNoEligible);
            } else {
                purchased=workers.submit(this::checkoutOrNoEligible);
                await(held);
                changed=workers.submit(() -> mutate(mutation,target));
            }
            awaitDatabaseLock("carts"); // Independent worker actually waits on the shared parent lock.
            release.countDown();
            boolean accepted=changed.get(WAIT_SECONDS,TimeUnit.SECONDS);
            CheckoutReceiptResponse receipt=purchased.get(WAIT_SECONDS,TimeUnit.SECONDS);
            boolean rejectedAfter=!mutationFirst && java.util.Set.of(CartMutation.QUANTITY,CartMutation.VARIANT,CartMutation.REMOVE,CartMutation.SELECT_FALSE).contains(mutation);
            assertThat(accepted).isEqualTo(!rejectedAfter);
            boolean noEligible=mutationFirst && java.util.Set.of(CartMutation.REMOVE,CartMutation.SELECT_FALSE,CartMutation.BULK_FALSE).contains(mutation);
            assertThat(receipt==null).isEqualTo(noEligible);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isEqualTo(noEligible ? 0 : 1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM checkout_receipts",Integer.class)).isEqualTo(noEligible ? 0 : 1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM carts",Integer.class)).isEqualTo(1);
            boolean bothConsumed=mutationFirst && java.util.Set.of(CartMutation.SELECT_TRUE,CartMutation.BULK_TRUE).contains(mutation);
            if(bothConsumed) assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cart_items",Integer.class)).isZero();
            else {
                java.util.Map<String,Object> expected=new java.util.HashMap<>(unselectedBefore);
                if(!mutationFirst && java.util.Set.of(CartMutation.SELECT_TRUE,CartMutation.BULK_TRUE).contains(mutation)) expected.put("selected",true);
                assertThat(jdbc.queryForMap("SELECT * FROM cart_items WHERE id=?",fixture.unselected())).isEqualTo(expected);
            }
            if(receipt!=null) {
                int quantity=mutationFirst && java.util.Set.of(CartMutation.ADD,CartMutation.QUANTITY).contains(mutation) ? 3
                        : mutationFirst && mutation==CartMutation.VARIANT ? 1 : 2;
                java.util.UUID orderedVariant=mutationFirst && mutation==CartMutation.VARIANT ? target : fixture.variant();
                assertThat(jdbc.queryForObject("SELECT quantity FROM order_items WHERE product_variant_id=?",Integer.class,orderedVariant)).isEqualTo(quantity);
                assertThat(receipt.getOrder().getItems()).hasSize(bothConsumed ? 2 : 1);
            }
            if(!mutationFirst && mutation==CartMutation.ADD) {
                assertThat(jdbc.queryForObject("SELECT quantity FROM cart_items WHERE product_variant_id=?",Integer.class,fixture.variant())).isEqualTo(1);
                assertThat(jdbc.queryForObject("SELECT id FROM cart_items WHERE product_variant_id=?",Long.class,fixture.variant())).isNotEqualTo(fixture.item());
            }
            if(noEligible && mutation!=CartMutation.REMOVE) assertThat(jdbc.queryForObject("SELECT selected FROM cart_items WHERE id=?",Boolean.class,fixture.item())).isFalse();
            assertInventoryIntegrity();
        } finally { release.countDown(); stop(workers); }
    }

    private CheckoutReceiptResponse checkoutOrNoEligible() {
        try { return checkout(KEY); }
        catch(NoEligibleCheckoutItemsException expected) { return null; }
    }

    private boolean mutate(CartMutation mutation, java.util.UUID target) {
        try {
            switch(mutation) {
                case ADD -> {
                    com.example.demo.cart.dto.request.AddCartItemRequest request=new com.example.demo.cart.dto.request.AddCartItemRequest();
                    request.setProductVariantId(fixture.variant());request.setQuantity(1);
                    cartService.addItem(fixture.owner(),request);
                }
                case QUANTITY -> {
                    com.example.demo.cart.dto.request.UpdateCartQuantityRequest request=new com.example.demo.cart.dto.request.UpdateCartQuantityRequest();
                    request.setQuantity(3);cartService.updateQuantity(fixture.owner(),fixture.item(),request);
                }
                case VARIANT -> {
                    com.example.demo.cart.dto.request.ChangeCartVariantRequest request=new com.example.demo.cart.dto.request.ChangeCartVariantRequest();
                    request.setProductVariantId(target);cartService.changeVariant(fixture.owner(),fixture.item(),request);
                }
                case REMOVE -> cartService.removeItem(fixture.owner(),fixture.item());
                case SELECT_TRUE, SELECT_FALSE, BULK_TRUE, BULK_FALSE -> {
                    com.example.demo.cart.dto.request.CartSelectionRequest request=new com.example.demo.cart.dto.request.CartSelectionRequest();
                    request.setSelected(mutation==CartMutation.SELECT_TRUE || mutation==CartMutation.BULK_TRUE);
                    if(mutation==CartMutation.SELECT_TRUE || mutation==CartMutation.SELECT_FALSE)
                        cartService.selectItem(fixture.owner(),mutation==CartMutation.SELECT_TRUE ? fixture.unselected() : fixture.item(),request);
                    else cartService.selectAll(fixture.owner(),request);
                }
            }
            return true;
        } catch(com.example.demo.cart.exception.CartItemNotFoundException expected) { return false; }
    }

    private void awaitDatabaseLock(String table) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        while(System.nanoTime()<deadline) {
            Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock' AND query ILIKE ?",Integer.class,"%"+table+"%");
            if(count!=null && count>0) return;
            Thread.sleep(10); // Poll an observed PostgreSQL wait; sleep alone is never the overlap proof.
        }
        throw new IllegalStateException("No real competing lock wait observed for "+table);
    }

    @Autowired com.example.demo.catalog.service.AdminProductService adminProducts;
    @Autowired com.example.demo.catalog.service.AdminVariantService adminVariants;
    @Autowired com.example.demo.catalog.service.AdminGalleryService adminGallery;
    @Autowired com.example.demo.catalog.service.AdminCategoryService adminCategories;
    @Autowired com.example.demo.order.service.OrderCompletionService completion;
    @MockitoSpyBean com.example.demo.catalog.repository.CatalogMutationRepository adminClaims;
    enum AdminEdit { UNPUBLISH, ARCHIVE, DISABLE, PRICE }
    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> adminCheckoutCases(){return java.util.Arrays.stream(AdminEdit.values()).flatMap(edit->java.util.stream.Stream.of(org.junit.jupiter.params.provider.Arguments.of(edit,false),org.junit.jupiter.params.provider.Arguments.of(edit,true)));}
    @ParameterizedTest @org.junit.jupiter.params.provider.MethodSource("adminCheckoutCases")
    void checkoutShouldSerializeActualAdministrativeCommands(AdminEdit edit,boolean adminFirst) throws Exception {
        if(edit==AdminEdit.DISABLE){var p=adminProducts.get(fixture.product());adminVariants.create(fixture.product(),new com.example.demo.catalog.dto.request.AdminVariantRequests.Create(Long.parseLong(p.getVersion()),Long.parseLong(p.getTaxonomyVersion()),"ENABLED-SIBLING-"+fixture.product(),java.math.BigDecimal.TEN,java.util.Map.of("choice","other"),java.util.Map.of("schema_version",1,"values",java.util.Map.of()),true));}
        var before=adminProducts.get(fixture.product());long version=Long.parseLong(before.getVersion()),taxonomy=Long.parseLong(before.getTaxonomyVersion());
        CountDownLatch held=new CountDownLatch(1),release=new CountDownLatch(1);
        if(adminFirst){
            doAnswer(call->{call.callRealMethod();held.countDown();await(release);return null;}).when(adminClaims).claim(org.mockito.ArgumentMatchers.eq(com.example.demo.catalog.repository.CatalogMutationRepository.Resource.PRODUCT),org.mockito.ArgumentMatchers.eq(fixture.product()),anyLong());
        }else{
            doAnswer(call->{Object result=call.callRealMethod();held.countDown();await(release);return result;}).when(catalogBoundary).getCheckoutVariantStates(any());
        }
        java.util.concurrent.Callable<Object> editCommand=()->{
            switch(edit){
                case UNPUBLISH,ARCHIVE->adminProducts.transition(fixture.product(),version,taxonomy,edit==AdminEdit.UNPUBLISH?com.example.demo.catalog.dto.request.AdminProductRequests.Action.UNPUBLISH:com.example.demo.catalog.dto.request.AdminProductRequests.Action.ARCHIVE);
                case DISABLE->adminVariants.setEnabled(fixture.product(),fixture.variant(),version,taxonomy,false);
                case PRICE->adminVariants.update(fixture.product(),fixture.variant(),new com.example.demo.catalog.dto.request.AdminVariantRequests.Patch(version,taxonomy,com.example.demo.catalog.dto.request.CatalogField.absent(),com.example.demo.catalog.dto.request.CatalogField.supplied(java.math.BigDecimal.valueOf(1750)),com.example.demo.catalog.dto.request.CatalogField.absent(),com.example.demo.catalog.dto.request.CatalogField.absent()));
            }return true;
        };
        ExecutorService workers=Executors.newFixedThreadPool(2);
        try{
            Future<?> edited;Future<CheckoutReceiptResponse> purchased;
            if(adminFirst){edited=workers.submit(editCommand);await(held);purchased=workers.submit(this::checkoutOrUnavailable);}
            else{purchased=workers.submit(this::checkoutOrUnavailable);await(held);edited=workers.submit(editCommand);}
            try{awaitDatabaseLock("products");}finally{release.countDown();}
            edited.get(WAIT_SECONDS,TimeUnit.SECONDS);var receipt=purchased.get(WAIT_SECONDS,TimeUnit.SECONDS);
            boolean unavailable=adminFirst && edit!=AdminEdit.PRICE;
            assertThat(receipt==null).isEqualTo(unavailable);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isEqualTo(unavailable?0:1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_reservations",Integer.class)).isEqualTo(unavailable?0:1);
            assertThat(Long.parseLong(adminProducts.get(fixture.product()).getVersion())).isEqualTo(version+1);
            if(receipt!=null){assertThat(receipt.getOrder().getPayableTotal().getAmount()).isEqualTo(java.math.BigInteger.valueOf(adminFirst?3500:2000));assertThat(checkout(KEY)).usingRecursiveComparison().isEqualTo(receipt);}
            else assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cart_items WHERE id=?",Integer.class,fixture.item())).isOne();
            assertInventoryIntegrity();
        }finally{release.countDown();stop(workers);org.mockito.Mockito.reset(adminClaims);}
    }
    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> historicalAdminCases(){return java.util.stream.Stream.of("SPECIFICATION","CATEGORY","GALLERY").flatMap(edit->java.util.stream.Stream.of(org.junit.jupiter.params.provider.Arguments.of(edit,false),org.junit.jupiter.params.provider.Arguments.of(edit,true)));}
    @ParameterizedTest @org.junit.jupiter.params.provider.MethodSource("historicalAdminCases")
    void heldAdministrativeEditMustNotCorruptOwnedCancellationOrCompletion(String edit,boolean complete) throws Exception {
        var receipt=checkout(KEY);long order=receipt.getOrder().getId();
        var snapshot=jdbc.queryForList("SELECT * FROM order_items WHERE order_id=?",order);var receiptBefore=jdbc.queryForMap("SELECT * FROM checkout_receipts WHERE order_id=?",order);
        if(complete)jdbc.update("UPDATE orders SET status='SHIPPING' WHERE id=?",order);
        if(edit.equals("GALLERY"))jdbc.update("INSERT INTO product_images(id,product_id,object_key,alt_text,content_type,byte_size,width,height,display_order,is_primary,created_at) SELECT ?,product_id,object_key||'-second',alt_text,content_type,byte_size,width,height,1,false,CURRENT_TIMESTAMP FROM product_images WHERE product_id=? AND active AND is_primary",java.util.UUID.randomUUID(),fixture.product());
        var p=adminProducts.get(fixture.product());long v=Long.parseLong(p.getVersion()),t=Long.parseLong(p.getTaxonomyVersion());
        var categoryId=p.getCategory().getId();CountDownLatch held=new CountDownLatch(1),release=new CountDownLatch(1);
        if(edit.equals("CATEGORY"))doAnswer(call->{Object result=call.callRealMethod();held.countDown();await(release);return result;}).when(adminClaims).lockTaxonomy(true);
        else doAnswer(call->{call.callRealMethod();held.countDown();await(release);return null;}).when(adminClaims).claim(org.mockito.ArgumentMatchers.eq(com.example.demo.catalog.repository.CatalogMutationRepository.Resource.PRODUCT),org.mockito.ArgumentMatchers.eq(fixture.product()),anyLong());
        ExecutorService workers=Executors.newFixedThreadPool(2);
        try{
            var edited=workers.submit(()->{
                if(edit.equals("CATEGORY"))adminCategories.update(categoryId,new com.example.demo.catalog.dto.request.AdminTaxonomyRequests.CategoryPatch(Long.parseLong(p.getCategory().getVersion()),t,com.example.demo.catalog.dto.request.CatalogField.supplied("Renamed historical category"),com.example.demo.catalog.dto.request.CatalogField.absent()));
                else if(edit.equals("GALLERY"))adminGallery.reorder(fixture.product(),v,t,java.util.List.of(p.getImages().getLast().getId(),p.getImages().getFirst().getId()));
                else adminVariants.replaceSpecifications(fixture.product(),fixture.variant(),new com.example.demo.catalog.dto.request.AdminVariantRequests.Patch(v,t,com.example.demo.catalog.dto.request.CatalogField.absent(),com.example.demo.catalog.dto.request.CatalogField.absent(),com.example.demo.catalog.dto.request.CatalogField.absent(),com.example.demo.catalog.dto.request.CatalogField.supplied(java.util.Map.of("schema_version",1,"values",java.util.Map.of("physical_ram",java.util.Map.of("value",8,"unit","GB"))))));
                return true;
            });
            await(held);
            var resolved=workers.submit(()->{if(complete)completion.completeOrder(fixture.owner(),order);else orderService.cancelOrder(fixture.owner(),order,new com.example.demo.order.dto.request.CancelOrderRequest("Concurrent owned cancellation"));return true;});
            try{assertThat(resolved.get(WAIT_SECONDS,TimeUnit.SECONDS)).isTrue();}finally{release.countDown();}
            assertThat(edited.get(WAIT_SECONDS,TimeUnit.SECONDS)).isTrue();
            assertThat(jdbc.queryForList("SELECT * FROM order_items WHERE order_id=?",order)).isEqualTo(snapshot);
            assertThat(jdbc.queryForMap("SELECT * FROM checkout_receipts WHERE order_id=?",order)).isEqualTo(receiptBefore);
            assertThat(jdbc.queryForObject("SELECT state FROM inventory_reservations WHERE order_id=?",String.class,order)).isEqualTo(complete?"CONSUMED":"RELEASED");
            assertThat(jdbc.queryForObject("SELECT reserved FROM inventory_balances WHERE original_variant_id=?",Long.class,fixture.variant())).isZero();
            assertThat(checkout(KEY)).usingRecursiveComparison().isEqualTo(receipt);
            assertInventoryIntegrity();
        }finally{release.countDown();stop(workers);org.mockito.Mockito.reset(adminClaims);}
    }
    enum DomainMutation { ADDRESS_UPDATE, ADDRESS_DELETE, UNPUBLISH, PRODUCT_NAME, VARIANT_PRICE_OPTIONS, PRODUCT_DELETE, VARIANT_DELETE }

    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> domainCases() {
        return java.util.Arrays.stream(DomainMutation.values()).flatMap(mutation -> java.util.stream.Stream.of(
                org.junit.jupiter.params.provider.Arguments.of(mutation,true),org.junit.jupiter.params.provider.Arguments.of(mutation,false)));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("domainCases")
    void checkout_shouldCaptureCoherentAddressAndCatalogAcrossRealCompetingWrites(DomainMutation mutation, boolean mutationFirst) throws Exception {
        if(mutation==DomainMutation.VARIANT_DELETE) transactions.executeWithoutResult(s -> entities.persist(
                com.example.demo.catalog.CatalogFixtures.variant(entities.getReference(com.example.demo.catalog.entity.Product.class,fixture.product()),
                java.util.Map.of("choice","replacement"),java.math.BigDecimal.ONE)));
        String originalName=jdbc.queryForObject("SELECT name FROM products WHERE id=?",String.class,fixture.product());
        String originalSku=jdbc.queryForObject("SELECT sku FROM product_variants WHERE id=?",String.class,fixture.variant());
        CountDownLatch held=new CountDownLatch(1);
        CountDownLatch release=new CountDownLatch(1);
        if(!mutationFirst) {
            Answer<?> catalogDelegate=mockingDetails(catalogBoundary).getMockCreationSettings().getDefaultAnswer();
            doAnswer(invocation -> {
                Object result=catalogDelegate.answer(invocation);
                held.countDown();
                await(release);
                return result;
            }).when(catalogBoundary).getCheckoutVariantStates(any());
        }
        ExecutorService workers=Executors.newFixedThreadPool(2);
        try {
            Future<?> changed;
            Future<CheckoutReceiptResponse> purchased;
            if(mutationFirst) {
                changed=workers.submit(() -> transactions.executeWithoutResult(s -> {
                    mutateDomain(mutation);
                    held.countDown();
                    try { await(release); } catch(InterruptedException failure) { Thread.currentThread().interrupt();throw new IllegalStateException(failure); }
                }));
                await(held);
                purchased=workers.submit(this::checkoutOrUnavailable);
            } else {
                purchased=workers.submit(this::checkoutOrUnavailable);
                await(held);
                changed=workers.submit(() -> transactions.executeWithoutResult(s -> mutateDomain(mutation)));
            }
            String table=java.util.Set.of(DomainMutation.ADDRESS_UPDATE,DomainMutation.ADDRESS_DELETE).contains(mutation) ? "users"
                    : java.util.Set.of(DomainMutation.PRODUCT_DELETE,DomainMutation.VARIANT_DELETE).contains(mutation) ? "carts"
                    : "products";
            awaitDatabaseLock(table);
            release.countDown();
            boolean activeDeletion = !mutationFirst && java.util.Set.of(DomainMutation.PRODUCT_DELETE,DomainMutation.VARIANT_DELETE).contains(mutation);
            if (activeDeletion) {
                assertThatThrownBy(() -> changed.get(WAIT_SECONDS,TimeUnit.SECONDS)).satisfies(failure ->
                        assertThat(errors.from(failure.getCause(), "/api/me/cart/checkout").getStatus()).isEqualTo(409));
            } else changed.get(WAIT_SECONDS,TimeUnit.SECONDS);
            CheckoutReceiptResponse receipt=purchased.get(WAIT_SECONDS,TimeUnit.SECONDS);
            boolean unavailable=mutationFirst && java.util.Set.of(DomainMutation.ADDRESS_DELETE,DomainMutation.UNPUBLISH,
                    DomainMutation.PRODUCT_DELETE,DomainMutation.VARIANT_DELETE).contains(mutation);
            assertThat(receipt==null).isEqualTo(unavailable);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isEqualTo(unavailable ? 0 : 1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM checkout_receipts",Integer.class)).isEqualTo(unavailable ? 0 : 1);
            if(receipt!=null) {
                assertThat(receipt.getOrder().getRecipientNameSnapshot()).isEqualTo(mutationFirst && mutation==DomainMutation.ADDRESS_UPDATE ? "Changed recipient" : "Recipient");
                assertThat(receipt.getOrder().getRecipientPhoneSnapshot()).isEqualTo(mutationFirst && mutation==DomainMutation.ADDRESS_UPDATE ? "0987654321" : "0123456789");
                assertThat(receipt.getOrder().getDeliveryAddressSnapshot()).isEqualTo(mutationFirst && mutation==DomainMutation.ADDRESS_UPDATE ? "Changed address" : "Address");
                assertThat(receipt.getOrder().getItems().getFirst().getProductNameSnapshot()).isEqualTo(mutationFirst && mutation==DomainMutation.PRODUCT_NAME ? "Concurrent product name" : originalName);
                assertThat(receipt.getOrder().getItems().getFirst().getVariantSkuSnapshot()).isEqualTo(mutationFirst && mutation==DomainMutation.VARIANT_PRICE_OPTIONS ? "CHANGED-"+fixture.variant() : originalSku);
                assertThat(receipt.getOrder().getPayableTotal().getAmount()).isEqualTo(java.math.BigInteger.valueOf(mutationFirst && mutation==DomainMutation.VARIANT_PRICE_OPTIONS ? 3500 : 2000));
                assertThat(checkout(KEY)).usingRecursiveComparison().isEqualTo(receipt);
                if (activeDeletion) {
                    assertThat(jdbc.queryForObject("SELECT state FROM inventory_reservations WHERE order_id=?", String.class, receipt.getOrder().getId())).isEqualTo("ACTIVE");
                    orderService.cancelOrder(fixture.owner(), receipt.getOrder().getId(), new com.example.demo.order.dto.request.CancelOrderRequest("Release before deletion"));
                    transactions.executeWithoutResult(s -> mutateDomain(mutation));
                    assertThat(checkout(KEY)).usingRecursiveComparison().isEqualTo(receipt);
                }
                if(mutation==DomainMutation.PRODUCT_DELETE) assertThat(jdbc.queryForObject("SELECT product_id FROM order_items",java.util.UUID.class)).isNull();
                if(mutation==DomainMutation.PRODUCT_DELETE || mutation==DomainMutation.VARIANT_DELETE)
                    assertThat(jdbc.queryForObject("SELECT product_variant_id FROM order_items",java.util.UUID.class)).isNull();
            }
            assertInventoryIntegrity();
        } finally { release.countDown();stop(workers); }
    }

    private CheckoutReceiptResponse checkoutOrUnavailable() {
        try { return checkout(KEY); }
        catch(NoEligibleCheckoutItemsException | com.example.demo.user.exception.ResourceNotFoundException expected) { return null; }
    }

    private void assertInventoryIntegrity() {
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM inventory_balances b WHERE stock<0 OR reserved<0 OR reserved>stock
                OR reserved::NUMERIC<>COALESCE((SELECT SUM(r.quantity::NUMERIC) FROM inventory_reservations r
                  WHERE r.inventory_balance_id=b.id AND r.state='ACTIVE'),0)
                """, Long.class)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM order_items i JOIN orders o ON o.id=i.order_id
                LEFT JOIN inventory_reservations r ON r.order_item_id=i.id WHERE o.inventory_managed
                AND (r.id IS NULL OR r.order_id<>o.id OR r.quantity<>i.quantity OR r.state<>
                  CASE o.status WHEN 'CANCELLED' THEN 'RELEASED' WHEN 'COMPLETED' THEN 'CONSUMED' ELSE 'ACTIVE' END)
                """, Long.class)).isZero();
    }

    private void mutateDomain(DomainMutation mutation) {
        switch(mutation) {
            case ADDRESS_UPDATE -> {
                com.example.demo.user.dto.request.UpdateAddressRequest request=new com.example.demo.user.dto.request.UpdateAddressRequest();
                request.setRecipientName("Changed recipient");request.setRecipientPhone("0987654321");request.setAddressText("Changed address");
                addressService.updateAddress(fixture.owner(),fixture.address(),request);
            }
            case ADDRESS_DELETE -> addressService.deleteAddress(fixture.owner(),fixture.address());
            case UNPUBLISH -> jdbc.update("UPDATE products SET status='ARCHIVED' WHERE id=?",fixture.product());
            case PRODUCT_NAME -> jdbc.update("UPDATE products SET name='Concurrent product name' WHERE id=?",fixture.product());
            case VARIANT_PRICE_OPTIONS -> {
                // V3's deferred invariant also locks the parent. Cooperative catalog writers acquire it first.
                jdbc.queryForObject("SELECT id FROM products WHERE id=? FOR UPDATE",java.util.UUID.class,fixture.product());
                jdbc.update("UPDATE product_variants SET selling_price=1750,sku=?,option_combination='{\"choice\":\"changed\"}' WHERE id=?","CHANGED-"+fixture.variant(),fixture.variant());
            }
            case PRODUCT_DELETE, VARIANT_DELETE -> {
                // Existing FK policy restricts deletion while cart rows refer to catalog. Respect the cart writer protocol.
                jdbc.queryForObject("SELECT id FROM carts WHERE id=? FOR UPDATE",Long.class,fixture.cart());
                jdbc.update("DELETE FROM cart_items WHERE product_variant_id=?",fixture.variant());
                if(mutation==DomainMutation.PRODUCT_DELETE) {
                    // Historical physical-delete drill only; ready fixtures now retain gallery metadata.
                    jdbc.update("DELETE FROM product_images WHERE product_id=?",fixture.product());
                    jdbc.update("DELETE FROM product_variants WHERE product_id=?",fixture.product());
                    jdbc.update("DELETE FROM products WHERE id=?",fixture.product());
                } else jdbc.update("DELETE FROM product_variants WHERE id=?",fixture.variant());
            }
        }
    }

    @Test void checkout_shouldReapplyCheckoutTimeoutAfterExistingUserLockPolicy() {
        Answer<?> cartDelegate=mockingDetails(cartBoundary).getMockCreationSettings().getDefaultAnswer();
        doAnswer(invocation -> {
            assertThat(jdbc.queryForObject("SELECT current_setting('lock_timeout')",String.class)).isEqualTo("1s");
            return cartDelegate.answer(invocation);
        }).when(cartBoundary).getCheckoutCartSeed(anyLong());
        checkout(KEY);
        assertOneCommittedOutcome();
    }

    @Test void checkout_shouldAllowUserWaitBeyondCheckoutTimeoutWithinExistingUserPolicy() throws Exception {
        ExecutorService workers=Executors.newSingleThreadExecutor();
        try(java.sql.Connection holder=dataSource.getConnection()) {
            holder.setAutoCommit(false);
            lock(holder,"users",fixture.owner());
            Future<CheckoutReceiptResponse> pending=workers.submit(() -> checkout(KEY));
            awaitDatabaseLock("users");
            assertThatThrownBy(() -> pending.get(1500,TimeUnit.MILLISECONDS)).isInstanceOf(java.util.concurrent.TimeoutException.class);
            holder.rollback();
            assertThat(pending.get(WAIT_SECONDS,TimeUnit.SECONDS).getOrder().getStatus().name()).isEqualTo("PENDING");
            assertOneCommittedOutcome();
        } finally { stop(workers); }
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"carts","products","product_variants"})
    void checkout_shouldRollBackHeldLockTimeoutAndPermitExplicitSameKeyRetry(String table) throws Exception {
        ExecutorService workers=Executors.newSingleThreadExecutor();
        try(java.sql.Connection holder=dataSource.getConnection()) {
            holder.setAutoCommit(false);
            Object id=table.equals("carts") ? fixture.cart() : table.equals("products") ? fixture.product() : fixture.variant();
            lock(holder,table,id);
            Future<CheckoutReceiptResponse> pending=workers.submit(() -> checkout(KEY));
            awaitDatabaseLock(table);
            Throwable failure=org.assertj.core.api.Assertions.catchThrowable(() -> pending.get(WAIT_SECONDS,TimeUnit.SECONDS));
            assertThat(failure).isInstanceOf(java.util.concurrent.ExecutionException.class);
            assertThat(sqlState(failure)).isEqualTo("55P03");
            com.example.demo.common.exception.ApiError error=errors.from(failure.getCause(),"/api/me/cart/checkout");
            assertThat(error.getStatus()).isEqualTo(409);
            assertThat(error.getProperties()).containsEntry("code","CHECKOUT_CONFLICT");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_items",Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM checkout_receipts",Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT quantity FROM cart_items WHERE id=?",Integer.class,fixture.item())).isEqualTo(2);
            holder.rollback();
        } finally { stop(workers); }
        checkout(KEY); // Explicit caller retry, never an automatic application retry.
        assertOneCommittedOutcome();
    }

    @Test void checkout_shouldRollBackRecognizedVariantFirstDeadlockWithoutPartialOutcome() throws Exception {
        Object target=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(service);
        org.springframework.test.util.ReflectionTestUtils.setField(target,"lockTimeoutMs",5000L); // Allow PostgreSQL deadlock detector to run.
        CountDownLatch variantHeld=new CountDownLatch(1);
        CountDownLatch releaseWriter=new CountDownLatch(1);
        ExecutorService workers=Executors.newFixedThreadPool(2);
        try {
            Future<?> writer=workers.submit(() -> transactions.executeWithoutResult(s -> {
                jdbc.update("UPDATE product_variants SET selling_price=1750 WHERE id=?",fixture.variant());
                variantHeld.countDown();
                try { await(releaseWriter); } catch(InterruptedException failure) { Thread.currentThread().interrupt();throw new IllegalStateException(failure); }
                // V3 deferred validation takes the product lock at commit, deliberately reversing checkout order.
            }));
            await(variantHeld);
            Future<CheckoutReceiptResponse> pending=workers.submit(() -> checkout(KEY));
            awaitDatabaseLock("product_variants");
            releaseWriter.countDown();
            Throwable writerFailure=org.assertj.core.api.Assertions.catchThrowable(() -> writer.get(WAIT_SECONDS,TimeUnit.SECONDS));
            Throwable checkoutFailure=null;
            CheckoutReceiptResponse receipt=null;
            try { receipt=pending.get(WAIT_SECONDS,TimeUnit.SECONDS); }
            catch(java.util.concurrent.ExecutionException failure) { checkoutFailure=failure; }
            assertThat((writerFailure==null ? 0 : 1)+(checkoutFailure==null ? 0 : 1)).isEqualTo(1);
            Throwable conflict=checkoutFailure==null ? writerFailure : checkoutFailure;
            assertThat(sqlState(conflict)).isEqualTo("40P01");
            com.example.demo.common.exception.ApiError error=errors.from(conflict.getCause(),"/api/me/cart/checkout");
            assertThat(error.getStatus()).isEqualTo(409);
            assertThat(error.getProperties()).containsEntry("code","CHECKOUT_CONFLICT");
            if(checkoutFailure!=null) {
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isZero();
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM checkout_receipts",Integer.class)).isZero();
                assertThat(jdbc.queryForObject("SELECT quantity FROM cart_items WHERE id=?",Integer.class,fixture.item())).isEqualTo(2);
                receipt=checkout(KEY); // Independent client retry after loser rollback and winner commit.
                assertThat(receipt.getOrder().getPayableTotal().getAmount()).isEqualTo(java.math.BigInteger.valueOf(3500));
            } else {
                assertThat(receipt.getOrder().getPayableTotal().getAmount()).isEqualTo(java.math.BigInteger.valueOf(2000));
                assertThat(jdbc.queryForObject("SELECT selling_price FROM product_variants WHERE id=?",java.math.BigDecimal.class,fixture.variant()))
                        .isEqualByComparingTo(java.math.BigDecimal.valueOf(1000));
            }
            assertOneCommittedOutcome();
        } finally {
            releaseWriter.countDown();
            stop(workers);
            org.springframework.test.util.ReflectionTestUtils.setField(target,"lockTimeoutMs",1000L);
        }
    }

    private void lock(java.sql.Connection connection, String table, Object id) throws Exception {
        try(java.sql.PreparedStatement query=connection.prepareStatement("SELECT id FROM "+table+" WHERE id=? FOR UPDATE")) {
            query.setObject(1,id);query.executeQuery();
        }
    }

    private String sqlState(Throwable failure) {
        for(Throwable cause=failure;cause!=null;cause=cause.getCause()) if(cause instanceof java.sql.SQLException sql) return sql.getSQLState();
        return null;
    }

    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> voucherMutationCases() {
        return java.util.stream.Stream.of(false,true).flatMap(remove -> java.util.stream.Stream.of(false,true).map(first -> org.junit.jupiter.params.provider.Arguments.of(remove,first)));
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.MethodSource("voucherMutationCases")
    void checkout_shouldSerializeVoucherReplacementAndRemoval(boolean remove,boolean mutationFirst) throws Exception {
        long old=selectVoucher(); long replacement=createVoucher();
        CountDownLatch held=new CountDownLatch(1),release=new CountDownLatch(1);
        if(!mutationFirst) {
            Answer<?> real=mockingDetails(cartBoundary).getMockCreationSettings().getDefaultAnswer();
            doAnswer(call -> { Object value=real.answer(call); held.countDown(); await(release); return value; }).when(cartBoundary).getCheckoutCartSeed(anyLong());
        }
        try(var workers=Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Object> mutate=() -> {
                try {
                    if(remove) cartVouchers.removeVoucher(fixture.owner());
                    else cartVouchers.applyVoucher(fixture.owner(),replacement);
                    return Boolean.TRUE;
                } catch(com.example.demo.common.exception.ApplicationException expected) { return expected; }
            };
            Future<Object> changed; Future<CheckoutReceiptResponse> purchase;
            if(mutationFirst) {
                changed=workers.submit(() -> transactions.execute(tx -> {
                    try { Object outcome=mutate.call(); held.countDown(); await(release); return outcome; }
                    catch(Exception error) { throw new IllegalStateException(error); }
                }));
                await(held); purchase=workers.submit(() -> checkout(KEY));
            } else {
                purchase=workers.submit(() -> checkout(KEY)); await(held); changed=workers.submit(mutate);
            }
            try { awaitDatabaseLock("carts"); } finally { release.countDown(); }
            Object mutation=changed.get(WAIT_SECONDS,TimeUnit.SECONDS);
            var receipt=(com.example.demo.checkout.dto.response.CheckoutOrderReceiptV2)purchase.get(WAIT_SECONDS,TimeUnit.SECONDS).getOrder();
            if(mutationFirst && remove) assertThat(receipt.getVoucher()).isNull();
            else assertThat(receipt.getVoucher().getWalletEntitlementId()).isEqualTo(mutationFirst ? replacement : old);
            assertThat(mutation instanceof Boolean).isEqualTo(mutationFirst || remove);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM voucher_redemptions",Long.class)).isEqualTo(mutationFirst && remove ? 0 : 1);
            assertThat(jdbc.queryForObject("SELECT voucher_entitlement_id FROM carts WHERE id=?",Long.class,fixture.cart())).isNull();
            assertInventoryIntegrity();
        } finally { release.countDown(); }
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void cleanup_shouldNotEraseAcceptedReplacementOrCheckout(boolean purchase) throws Exception {
        selectVoucher(); long replacement=createVoucher();
        jdbc.update("UPDATE vouchers SET enabled=FALSE WHERE id=(SELECT voucher_id FROM voucher_entitlements WHERE id=(SELECT voucher_entitlement_id FROM carts WHERE id=?))",fixture.cart());
        CountDownLatch held=new CountDownLatch(1),release=new CountDownLatch(1);
        try(var workers=Executors.newFixedThreadPool(2)) {
            var cleanup=workers.submit(() -> transactions.execute(tx -> {
                var response=cartService.getCart(fixture.owner());
                held.countDown();
                try {await(release);} catch(InterruptedException error) {Thread.currentThread().interrupt();throw new IllegalStateException(error);}
                return response;
            }));
            await(held);
            var following=workers.submit(() -> purchase ? checkout(KEY) : cartVouchers.applyVoucher(fixture.owner(),replacement));
            try {awaitDatabaseLock("carts");} finally {release.countDown();}
            assertThat(cleanup.get(WAIT_SECONDS,TimeUnit.SECONDS).getVoucherSelectionClearedReason()).isEqualTo("VOUCHER_DISABLED");
            following.get(WAIT_SECONDS,TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject("SELECT voucher_entitlement_id FROM carts WHERE id=?",Long.class,fixture.cart())).isEqualTo(purchase ? null : replacement);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM voucher_redemptions",Long.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders",Long.class)).isEqualTo(purchase ? 1 : 0);
        } finally {release.countDown();}
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void cleanup_shouldObserveCommittedReplacementOrRejectedCheckout(boolean purchase) throws Exception {
        selectVoucher();long replacement=createVoucher();
        jdbc.update("UPDATE vouchers SET enabled=FALSE WHERE id=(SELECT voucher_id FROM voucher_entitlements WHERE id=(SELECT voucher_entitlement_id FROM carts WHERE id=?))",fixture.cart());
        CountDownLatch held=new CountDownLatch(1),release=new CountDownLatch(1);
        if(purchase) {
            Answer<?> real=mockingDetails(cartBoundary).getMockCreationSettings().getDefaultAnswer();
            doAnswer(call -> {var value=real.answer(call);held.countDown();await(release);return value;}).when(cartBoundary).getCheckoutCartSeed(anyLong());
        }
        try(var workers=Executors.newFixedThreadPool(2)) {
            Future<Object> first=workers.submit(() -> {
                if(purchase) {
                    try {return checkout(KEY);} catch(com.example.demo.voucher.exception.CheckoutVoucherNotApplicableException expected) {return expected;}
                }
                return transactions.execute(tx -> {
                    var value=cartVouchers.applyVoucher(fixture.owner(),replacement);held.countDown();
                    try {await(release);} catch(InterruptedException error) {Thread.currentThread().interrupt();throw new IllegalStateException(error);}
                    return value;
                });
            });
            await(held);var cleanup=workers.submit(() -> cartService.getCart(fixture.owner()));
            try {awaitDatabaseLock("carts");} finally {release.countDown();}
            var outcome=first.get(WAIT_SECONDS,TimeUnit.SECONDS);var response=cleanup.get(WAIT_SECONDS,TimeUnit.SECONDS);
            if(purchase) {
                assertThat(outcome).isInstanceOf(com.example.demo.voucher.exception.CheckoutVoucherNotApplicableException.class);
                assertThat(response.getVoucherSelectionClearedReason()).isEqualTo("VOUCHER_DISABLED");
            } else {
                assertThat(response.getVoucher().getWalletEntitlementId()).isEqualTo(replacement);
                assertThat(response.getVoucherSelectionClearedReason()).isNull();
            }
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders",Long.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM voucher_redemptions",Long.class)).isZero();
        } finally {release.countDown();}
    }
}
