package com.example.demo.cart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;

import com.example.demo.cart.dto.response.CartResponse;
import com.example.demo.config.CheckoutIntegrationSupport;
import com.example.demo.fixture.voucher.VoucherFixtures;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** A committed independent edit at the former validation/presentation read boundary. */
class CartPreparedPricingIT extends CheckoutIntegrationSupport {
    @Autowired private CartVoucherService vouchers;
    @Autowired private CartService carts;
    @Autowired private DataSource source;
    @MockitoSpyBean private CartPreviewService previews;
    @MockitoSpyBean private com.example.demo.catalog.repository.ProductVariantRepository pricingRows;
    @Autowired private com.example.demo.checkout.service.CheckoutService checkout;

    @ParameterizedTest
    @ValueSource(strings = {"base", "percentage", "enabled", "start", "end"})
    void applyVoucher_shouldReuseOperationPricesAndObserveEditsOnNextRequest(String edit) throws Exception {
        long grant = transactions.execute(status -> {
            long definition = VoucherFixtures.definition(jdbc, "PREPARED_" + edit.toUpperCase(java.util.Locale.ROOT), "FIXED_AMOUNT", BigDecimal.TEN, null, null);
            long usage = VoucherFixtures.usage(jdbc, definition, fixture.owner());
            return VoucherFixtures.grant(jdbc, definition, fixture.owner(), usage, Instant.now().minusSeconds(1));
        });
        if (edit.equals("enabled") || edit.equals("start") || edit.equals("end")) {
            jdbc.update("UPDATE products SET sale_percentage=20,sale_enabled=FALSE WHERE id=?", fixture.product());
        }
        var writeStarted = new CountDownLatch(1);
        var pendingWrite = new AtomicReference<Future<Integer>>();
        try (var writer = Executors.newSingleThreadExecutor()) {
            doAnswer(invocation -> {
                CartResponse response = (CartResponse) invocation.callRealMethod();
                if (invocation.getArgument(0) == null) {
                    pendingWrite.set(writer.submit(() -> updatePrice(edit, writeStarted)));
                    assertThat(writeStarted.await(10, TimeUnit.SECONDS)).isTrue();
                }
                return response;
            }).when(previews).getCartResponse(any(), any(), any(), anyBoolean(), any());

            org.mockito.Mockito.clearInvocations(pricingRows);
            CartResponse operation = vouchers.applyVoucher(fixture.owner(), grant);
            assertThat(pendingWrite.get()).isNotNull();
            assertThat(pendingWrite.get().get(15, TimeUnit.SECONDS)).isEqualTo(1);
            // Discover the parent, then refresh once while holding its publication lock.
            org.mockito.Mockito.verify(pricingRows, org.mockito.Mockito.times(2)).findPricedVariants(any(), any());
            assertThat(operation.getSelectedSubtotal().getAmount()).isEqualTo(BigInteger.valueOf(2000));
            assertThat(operation.getVoucherDiscount().getAmount()).isEqualTo(BigInteger.TEN);
            assertThat(operation.getFinalTotal().getAmount()).isEqualTo(BigInteger.valueOf(1990));
        }
        CartResponse next = carts.getCart(fixture.owner());
        BigInteger current = BigInteger.valueOf(edit.equals("base") ? 4000 : 1600);
        assertThat(next.getSelectedSubtotal().getAmount()).isEqualTo(current);
        if(edit.equals("base")) {
            jdbc.update("UPDATE vouchers SET enabled=FALSE WHERE id=(SELECT voucher_id FROM voucher_entitlements WHERE id=?)",grant);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> checkout.checkout(fixture.owner(),"Prepared_checkout_123",new com.example.demo.checkout.dto.request.CheckoutRequest(fixture.address())))
                    .isInstanceOf(com.example.demo.voucher.exception.CheckoutVoucherNotApplicableException.class);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isZero();
            jdbc.update("UPDATE vouchers SET enabled=TRUE WHERE id=(SELECT voucher_id FROM voucher_entitlements WHERE id=?)",grant);
        }
        var purchase = checkout.checkout(fixture.owner(),"Prepared_checkout_123",new com.example.demo.checkout.dto.request.CheckoutRequest(fixture.address()));
        assertThat(purchase.getOrder().getMerchandiseTotal().getAmount()).isEqualTo(current);
        assertThat(purchase.getOrder().getPayableTotal().getAmount()).isEqualTo(current.subtract(BigInteger.TEN));
        org.mockito.Mockito.verify(pricingRows,org.mockito.Mockito.times(edit.equals("base") ? 6 : 5)).findPricedVariants(any(),any());
    }

    private int updatePrice(String edit, CountDownLatch writeStarted) throws SQLException {
        String sql = switch (edit) {
            case "base" -> "UPDATE product_variants SET base_price=2000 WHERE id=?";
            case "percentage", "enabled" -> "UPDATE products SET sale_percentage=20,sale_enabled=TRUE WHERE id=?";
            case "start" -> "UPDATE products SET sale_enabled=TRUE,sale_starts_at=CURRENT_TIMESTAMP-INTERVAL '1 hour' WHERE id=?";
            case "end" -> "UPDATE products SET sale_enabled=TRUE,sale_ends_at=CURRENT_TIMESTAMP+INTERVAL '1 hour' WHERE id=?";
            default -> throw new IllegalArgumentException(edit);
        };
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(10);
            statement.setObject(1, edit.equals("base") ? fixture.variant() : fixture.product());
            writeStarted.countDown();
            return statement.executeUpdate();
        }
    }
}
