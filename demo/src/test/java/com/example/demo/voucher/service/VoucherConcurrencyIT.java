package com.example.demo.voucher.service;

import com.example.demo.config.CheckoutIntegrationSupport;
import com.example.demo.fixture.voucher.VoucherFixtures;
import com.example.demo.checkout.service.CheckoutService;
import com.example.demo.checkout.dto.request.CheckoutRequest;
import com.example.demo.cart.service.CartVoucherService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@TestPropertySource(properties={"spring.datasource.hikari.maximum-pool-size=6", "checkout.lock-timeout-ms=5000"})
class VoucherConcurrencyIT extends CheckoutIntegrationSupport {
    @Autowired CheckoutService checkout;
    @Autowired CartVoucherService carts;
    @Autowired VoucherService voucherService;
    @MockitoBean Clock clock;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean com.example.demo.cart.service.CartPreviewService previews;
    private final AtomicReference<Instant> current = new AtomicReference<>();
    @BeforeEach void prepareTime() {
        current.set(Instant.now().truncatedTo(ChronoUnit.MICROS));
        when(clock.instant()).thenAnswer(call -> current.get());
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void checkout_shouldUsePostLockTime_whenVoucherExpiresDuringObservedWait(boolean expired) throws Exception {
        Instant before = current.get(); Instant end = before.plusSeconds(10);
        Long[] ids = transactions.execute(tx -> {
            long voucher = VoucherFixtures.definition(jdbc, "WAIT_" + System.nanoTime(), "FIXED_AMOUNT", BigDecimal.TEN, 1L, 1L);
            jdbc.update("UPDATE vouchers SET valid_until=? WHERE id=?", java.sql.Timestamp.from(end), voucher);
            long usage = VoucherFixtures.usage(jdbc, voucher, fixture.owner());
            long grant = VoucherFixtures.grant(jdbc, voucher, fixture.owner(), usage, before.minusSeconds(1));
            return new Long[]{voucher, grant};
        });
        carts.applyVoucher(fixture.owner(), ids[1]);
        try (var workers = Executors.newSingleThreadExecutor()) {
            var result = transactions.execute(tx -> {
                jdbc.queryForObject("SELECT id FROM vouchers WHERE id=? FOR UPDATE", Long.class, ids[0]);
                var pending = workers.submit(() -> {
                    try { return (Object)checkout.checkout(fixture.owner(), "Voucher_wait_key_123", new CheckoutRequest(fixture.address())); }
                    catch (RuntimeException failure) { return failure; }
                });
                long deadline = System.nanoTime()+TimeUnit.SECONDS.toNanos(4);
                boolean observed = false;
                while (System.nanoTime()<deadline) {
                    jdbc.execute("SELECT pg_stat_clear_snapshot()");
                    observed = Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE wait_event_type='Lock' AND query ILIKE '%FROM vouchers%' AND pid<>pg_backend_pid())", Boolean.class));
                    if (observed) break;
                    Thread.onSpinWait();
                }
                assertThat(observed).as("worker actually waits for required definition lock").isTrue();
                current.set(expired ? end : before.plusSeconds(1));
                return pending;
            }); // Releasing actual lock happens at commit, after advancing controlled time.
            Object outcome = result.get(10, TimeUnit.SECONDS);
            if (expired) {
                assertThat(outcome).isInstanceOf(com.example.demo.common.exception.ApplicationException.class);
                var error = (com.example.demo.common.exception.ApplicationException)outcome;
                assertThat(error.getStatus()).isEqualTo(409);
                assertThat(error.getProperties()).containsEntry("reason", "VOUCHER_EXPIRED");
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders", Long.class)).isZero();
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM voucher_redemptions", Long.class)).isZero();
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_reservations", Long.class)).isZero();
                assertThat(jdbc.queryForObject("SELECT voucher_entitlement_id FROM carts WHERE id=?", Long.class, fixture.cart())).isEqualTo(ids[1]);
                assertThat(jdbc.queryForObject("SELECT state FROM voucher_entitlements WHERE id=?", String.class, ids[1])).isEqualTo("AVAILABLE");
            } else {
                assertThat(outcome).isInstanceOf(com.example.demo.checkout.dto.response.CheckoutReceiptResponse.class);
                var receipt = (com.example.demo.checkout.dto.response.CheckoutReceiptResponse)outcome;
                assertThat(receipt.getOrder().getPlacedAt()).isEqualTo(current.get());
                assertThat(jdbc.queryForObject("SELECT redeemed_at FROM voucher_redemptions WHERE order_id=?", java.sql.Timestamp.class, receipt.getOrder().getId()).toInstant()).isEqualTo(current.get());
                assertThat(jdbc.queryForObject("SELECT reserved_at FROM inventory_reservations WHERE order_id=?", java.sql.Timestamp.class, receipt.getOrder().getId()).toInstant()).isEqualTo(current.get());
                assertThat(jdbc.queryForObject("SELECT created_at FROM checkout_receipts WHERE order_id=?", java.sql.Timestamp.class, receipt.getOrder().getId()).toInstant()).isEqualTo(current.get());
            }
        }
    }

    @org.junit.jupiter.api.Test
    void checkout_shouldSerializeFinalGlobalUseAcrossIndependentCustomers() throws Exception {
        var other=transactions.execute(tx -> com.example.demo.fixture.checkout.CheckoutFixtures.scenario(entities));
        Long[] ids=transactions.execute(tx -> {
            long definition=VoucherFixtures.definition(jdbc,"LAST_"+System.nanoTime(),"FIXED_AMOUNT",BigDecimal.ONE,1L,1L);
            long firstUsage=VoucherFixtures.usage(jdbc,definition,fixture.owner());
            long secondUsage=VoucherFixtures.usage(jdbc,definition,other.owner());
            return new Long[]{definition,VoucherFixtures.grant(jdbc,definition,fixture.owner(),firstUsage,current.get().minusSeconds(1)),
                VoucherFixtures.grant(jdbc,definition,other.owner(),secondUsage,current.get().minusSeconds(1))};
        });
        carts.applyVoucher(fixture.owner(),ids[1]); carts.applyVoucher(other.owner(),ids[2]);
        var start=new CountDownLatch(1);
        try(var workers=Executors.newFixedThreadPool(2)) {
            var first=workers.submit(() -> { start.await(); return attempt(fixture.owner(),fixture.address(),"Last_global_first_123"); });
            var second=workers.submit(() -> { start.await(); return attempt(other.owner(),other.address(),"Last_global_second_123"); });
            transactions.executeWithoutResult(tx -> {
                jdbc.queryForObject("SELECT id FROM vouchers WHERE id=? FOR UPDATE",Long.class,ids[0]);
                start.countDown();
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(4);
                boolean observed=false;
                while(System.nanoTime()<deadline) {
                    jdbc.execute("SELECT pg_stat_clear_snapshot()");
                    observed=Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE wait_event_type='Lock' AND query ILIKE '%FROM vouchers%' AND pid<>pg_backend_pid())",Boolean.class));
                    if(observed) break;
                    Thread.onSpinWait();
                }
                assertThat(observed).as("independent checkout waits for shared definition").isTrue();
            });
            var outcomes=java.util.List.of(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS));
            assertThat(outcomes.stream().filter(com.example.demo.checkout.dto.response.CheckoutReceiptResponse.class::isInstance)).hasSize(1);
            assertThat(outcomes.stream().filter(com.example.demo.voucher.exception.CheckoutVoucherNotApplicableException.class::isInstance)).hasSize(1);
            assertThat(jdbc.queryForObject("SELECT redeemed_count FROM vouchers WHERE id=?",Long.class,ids[0])).isOne();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders",Long.class)).isOne();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM checkout_receipts",Long.class)).isOne();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM voucher_redemptions",Long.class)).isOne();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_reservations",Long.class)).isOne();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM voucher_entitlements WHERE state='AVAILABLE'",Long.class)).isOne();
        }
    }
    private Object attempt(Long owner,Long address,String key) {
        try { return checkout.checkout(owner,key,new CheckoutRequest(address)); }
        catch(RuntimeException failure) { return failure; }
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void checkout_shouldNeverSpendSameGrantTwiceOrExceedPersonalQuota(boolean differentGrant) throws Exception {
        Long[] ids=transactions.execute(tx -> {
            long voucher=VoucherFixtures.definition(jdbc,"PERSONAL_"+System.nanoTime(),"FIXED_AMOUNT",BigDecimal.ONE,10L,1L);
            long usage=VoucherFixtures.usage(jdbc,voucher,fixture.owner());
            return new Long[]{voucher,VoucherFixtures.grant(jdbc,voucher,fixture.owner(),usage,current.get().minusSeconds(1)),
                VoucherFixtures.grant(jdbc,voucher,fixture.owner(),usage,current.get().minusSeconds(1))};
        });
        carts.applyVoucher(fixture.owner(),ids[1]);
        try(var workers=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            var first=workers.submit(() -> {start.await();return attempt(fixture.owner(),fixture.address(),"Personal_first_123");});
            var second=workers.submit(() -> {start.await();return attempt(fixture.owner(),fixture.address(),"Personal_second_123");});
            transactions.executeWithoutResult(tx -> {
                jdbc.queryForObject("SELECT id FROM carts WHERE id=? FOR UPDATE",Long.class,fixture.cart());
                start.countDown();
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(4); boolean observed=false;
                while(System.nanoTime()<deadline) {
                    jdbc.execute("SELECT pg_stat_clear_snapshot()");
                    observed=Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE wait_event_type='Lock' AND (query ILIKE '%carts%' OR query ILIKE '%users%') AND pid<>pg_backend_pid())",Boolean.class));
                    if(observed) break;
                    Thread.onSpinWait();
                }
                assertThat(observed).isTrue();
            });
            assertThat(java.util.List.of(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS)).stream().filter(com.example.demo.checkout.dto.response.CheckoutReceiptResponse.class::isInstance)).hasSize(1);
        }
        transactions.executeWithoutResult(tx -> com.example.demo.fixture.checkout.CheckoutFixtures.additionalItem(entities,fixture.cart(),true));
        // Capture the retained choice directly: checkout must independently reject it without preview cleanup.
        long retryGrant=differentGrant ? ids[2] : ids[1];
        Object retry;
        if(differentGrant) {
            jdbc.update("UPDATE carts SET voucher_entitlement_id=?,voucher_selection_revision=voucher_selection_revision+1 WHERE id=?",retryGrant,fixture.cart());
            retry=attempt(fixture.owner(),fixture.address(),"Personal_retry_123");
        } else {
            retry=org.assertj.core.api.Assertions.catchThrowable(() -> voucherService.lockCheckoutVoucher(fixture.owner(),retryGrant,java.math.BigInteger.valueOf(2000)));
        }
        assertThat(retry).isInstanceOf(com.example.demo.voucher.exception.CheckoutVoucherNotApplicableException.class);
        assertThat(((com.example.demo.common.exception.ApplicationException)retry).getProperties()).containsEntry("reason",differentGrant ? "CUSTOMER_USAGE_EXHAUSTED" : "ENTITLEMENT_UNAVAILABLE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM voucher_redemptions",Long.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT redeemed_count FROM vouchers WHERE id=?",Long.class,ids[0])).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders",Long.class)).isOne();
    }

    @org.junit.jupiter.api.Test
    void selection_shouldCommitWithoutReversingThePurchaseVoucherLockGraph() throws Exception {
        var other=transactions.execute(tx -> com.example.demo.fixture.checkout.CheckoutFixtures.scenario(entities));
        Long[] ids=transactions.execute(tx -> {
            long definition=VoucherFixtures.definition(jdbc,"SELECT_"+System.nanoTime(),"FIXED_AMOUNT",BigDecimal.ONE,3L,3L);
            long firstUsage=VoucherFixtures.usage(jdbc,definition,fixture.owner());
            long secondUsage=VoucherFixtures.usage(jdbc,definition,other.owner());
            return new Long[]{definition,VoucherFixtures.grant(jdbc,definition,fixture.owner(),firstUsage,current.get().minusSeconds(1)),VoucherFixtures.grant(jdbc,definition,other.owner(),secondUsage,current.get().minusSeconds(1))};
        });
        carts.applyVoucher(fixture.owner(),ids[1]);
        var purchaseReady=new CountDownLatch(1);var selectionReady=new CountDownLatch(1);
        var releasePurchase=new CountDownLatch(1);var releaseSelection=new CountDownLatch(1);
        var real=org.mockito.Mockito.mockingDetails(previews).getMockCreationSettings().getDefaultAnswer();
        org.mockito.Mockito.doAnswer(call -> {
            Object result=real.answer(call);
            com.example.demo.cart.entity.Cart cart=call.getArgument(0);
            if(cart!=null && other.owner().equals(cart.getUserId())) {
                selectionReady.countDown();
                if(!releaseSelection.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("Selection barrier timed out");
            }
            return result;
        }).when(previews).getCartResponse(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyList(),org.mockito.ArgumentMatchers.anyMap(),org.mockito.ArgumentMatchers.anyBoolean());
        var workers=Executors.newFixedThreadPool(2);
        try {
            var purchase=workers.submit(() -> transactions.execute(tx -> {
                var receipt=checkout.checkout(fixture.owner(),"Selection_graph_123",new CheckoutRequest(fixture.address()));
                purchaseReady.countDown();
                try {if(!releasePurchase.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("Purchase barrier timed out");}
                catch(InterruptedException error) {Thread.currentThread().interrupt();throw new IllegalStateException(error);}
                return receipt;
            }));
            assertThat(purchaseReady.await(10,TimeUnit.SECONDS)).isTrue();
            var selection=workers.submit(() -> carts.applyVoucher(other.owner(),ids[2]));
            assertThat(selectionReady.await(10,TimeUnit.SECONDS)).isTrue();
            releasePurchase.countDown();releaseSelection.countDown();
            assertThat(purchase.get(15,TimeUnit.SECONDS).getOrder()).isNotNull();
            assertThat(selection.get(15,TimeUnit.SECONDS).getVoucher().getWalletEntitlementId()).isEqualTo(ids[2]);
            assertThat(jdbc.queryForObject("SELECT voucher_entitlement_id FROM carts WHERE id=?",Long.class,other.cart())).isEqualTo(ids[2]);
            assertThat(jdbc.queryForObject("SELECT state FROM voucher_entitlements WHERE id=?",String.class,ids[2])).isEqualTo("AVAILABLE");
            assertThat(jdbc.queryForObject("SELECT redeemed_count FROM vouchers WHERE id=?",Long.class,ids[0])).isOne();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders",Long.class)).isOne();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_reservations",Long.class)).isOne();
        } finally {
            releasePurchase.countDown();releaseSelection.countDown();workers.shutdownNow();
            assertThat(workers.awaitTermination(15,TimeUnit.SECONDS)).isTrue();
        }
    }
}
