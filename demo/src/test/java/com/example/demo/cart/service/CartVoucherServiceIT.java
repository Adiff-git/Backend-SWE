package com.example.demo.cart.service;
import com.example.demo.config.CheckoutIntegrationSupport;
import com.example.demo.fixture.voucher.VoucherFixtures;
import com.example.demo.cart.dto.request.UpdateCartQuantityRequest;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@org.springframework.context.annotation.Import(com.example.demo.config.SqlCaptureConfiguration.class)
class CartVoucherServiceIT extends CheckoutIntegrationSupport {
    @Test
    void directSale_shouldPrecedeVoucherEligibilityAndOrderLevelDiscount() {
        long grant = grant();
        jdbc.update("UPDATE products SET sale_percentage=20,sale_enabled=TRUE WHERE id=?", fixture.product());
        var response = vouchers.applyVoucher(fixture.owner(), grant);
        assertThat(response.getSelectedSubtotal().getAmount()).isEqualTo(BigInteger.valueOf(1600));
        assertThat(response.getVoucherDiscount().getAmount()).isEqualTo(BigInteger.TEN);
        assertThat(response.getFinalTotal().getAmount()).isEqualTo(BigInteger.valueOf(1590));
    }
    @Test void sale_shouldApplyMinimumAndPercentageCapToAfterSaleSubtotalAndClearFreeCartSelection() {
        long grant=grant();
        jdbc.update("UPDATE products SET sale_percentage=20,sale_enabled=TRUE WHERE id=?",fixture.product());
        jdbc.update("UPDATE vouchers SET minimum_subtotal=1800 WHERE id=(SELECT voucher_id FROM voucher_entitlements WHERE id=?)",grant);
        assertThatThrownBy(() -> vouchers.applyVoucher(fixture.owner(),grant)).isInstanceOf(com.example.demo.voucher.exception.VoucherNotApplicableException.class);
        jdbc.update("UPDATE vouchers SET minimum_subtotal=0,discount_type='PERCENTAGE',discount_value=12.50,maximum_discount=100 WHERE id=(SELECT voucher_id FROM voucher_entitlements WHERE id=?)",grant);
        var applied=vouchers.applyVoucher(fixture.owner(),grant);
        assertThat(applied.getSelectedSubtotal().getAmount()).isEqualTo(BigInteger.valueOf(1600));
        assertThat(applied.getVoucherDiscount().getAmount()).isEqualTo(BigInteger.valueOf(100));
        jdbc.update("UPDATE products SET sale_percentage=100 WHERE id=?",fixture.product());
        var free=carts.getCart(fixture.owner());
        assertThat(free.getSelectedSubtotal().getAmount()).isZero();
        assertThat(free.getVoucherDiscount().getAmount()).isZero();
        assertThat(free.getVoucherSelectionClearedReason()).isEqualTo("NO_ELIGIBLE_ITEMS");
        assertThat(jdbc.queryForObject("SELECT state FROM voucher_entitlements WHERE id=?",String.class,grant)).isEqualTo("AVAILABLE");
    }
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean java.time.Clock eventClock;
    @Test void hiddenCart_shouldRetainPreparedVoucherEventWithoutSamplingClockAgain() {
        long grant=grant(); vouchers.applyVoucher(fixture.owner(),grant);
        Instant at=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS),end=at.plusSeconds(1);
        jdbc.update("UPDATE products SET status='ARCHIVED' WHERE id=?",fixture.product());
        jdbc.update("UPDATE vouchers SET valid_until=? WHERE id=(SELECT voucher_id FROM voucher_entitlements WHERE id=?)",java.sql.Timestamp.from(end),grant);
        org.mockito.Mockito.doReturn(at,end.plusSeconds(1)).when(eventClock).instant();
        org.mockito.Mockito.clearInvocations(eventClock);
        var result=carts.getCart(fixture.owner());
        assertThat(result.getVoucherSelectionClearedReason()).isEqualTo("NO_ELIGIBLE_ITEMS");
        assertThat(result.getItems().getFirst().getPricing()).isNull();
        org.mockito.Mockito.verify(eventClock).instant();
    }
    @Autowired CartVoucherService vouchers;
    @Autowired CartService carts;
    private long grant() {
        return transactions.execute(tx -> {
            long definition = VoucherFixtures.definition(jdbc, "CART_" + System.nanoTime(), "FIXED_AMOUNT", BigDecimal.TEN, 3L, 3L);
            long usage = VoucherFixtures.usage(jdbc, definition, fixture.owner());
            return VoucherFixtures.grant(jdbc, definition, fixture.owner(), usage, Instant.now().minusSeconds(1));
        });
    }
    @ParameterizedTest @ValueSource(strings={"add", "quantity", "variant", "individual-false", "individual-true", "bulk-true", "bulk-false"})
    void cartMutation_shouldRecalculateAndNeverSpendGrant(String action) {
        long grant = grant(); vouchers.applyVoucher(fixture.owner(), grant);
        switch (action) {
            case "add" -> { var r = new com.example.demo.cart.dto.request.AddCartItemRequest(); r.setProductVariantId(fixture.variant()); r.setQuantity(1); carts.addItem(fixture.owner(), r); }
            case "quantity" -> { var r = new UpdateCartQuantityRequest(); r.setQuantity(1); carts.updateQuantity(fixture.owner(), fixture.item(), r); }
            case "variant" -> { var r = new com.example.demo.cart.dto.request.ChangeCartVariantRequest(); r.setProductVariantId(fixture.variant()); carts.changeVariant(fixture.owner(), fixture.item(), r); }
            case "individual-false" -> { var r = new com.example.demo.cart.dto.request.CartSelectionRequest(); r.setSelected(false); carts.selectItem(fixture.owner(), fixture.item(), r); }
            case "individual-true" -> { var r = new com.example.demo.cart.dto.request.CartSelectionRequest(); r.setSelected(true); carts.selectItem(fixture.owner(), fixture.unselected(), r); }
            case "bulk-true" -> { var r = new com.example.demo.cart.dto.request.CartSelectionRequest(); r.setSelected(true); carts.selectAll(fixture.owner(), r); }
            case "bulk-false" -> { var r = new com.example.demo.cart.dto.request.CartSelectionRequest(); r.setSelected(false); carts.selectAll(fixture.owner(), r); }
            default -> throw new IllegalArgumentException(action);
        }
        var result = carts.getCart(fixture.owner());
        assertThat(result.getFinalTotal().getAmount()).isEqualTo(result.getSelectedSubtotal().getAmount().subtract(result.getVoucherDiscount().getAmount()));
        assertThat(jdbc.queryForObject("SELECT state FROM voucher_entitlements WHERE id=?", String.class, grant)).isEqualTo("AVAILABLE");
        assertThat(jdbc.queryForObject("SELECT redeemed_count FROM vouchers WHERE id=(SELECT voucher_id FROM voucher_entitlements WHERE id=?)", Long.class, grant)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_reservations", Long.class)).isZero();
    }
    @Test void quantityBelowMinimum_shouldClearAndNeverAutomaticallyReselect() {
        long grant = grant();
        BigInteger base = carts.getCart(fixture.owner()).getSelectedSubtotal().getAmount();
        jdbc.update("UPDATE vouchers SET minimum_subtotal=? WHERE id=(SELECT voucher_id FROM voucher_entitlements WHERE id=?)", new BigDecimal(base.subtract(BigInteger.ONE)), grant);
        vouchers.applyVoucher(fixture.owner(), grant);
        var request = new UpdateCartQuantityRequest(); request.setQuantity(1);
        var changed = carts.updateQuantity(fixture.owner(), fixture.item(), request);
        assertThat(changed.getVoucher()).isNull();
        assertThat(changed.getVoucherSelectionClearedReason()).isEqualTo("MINIMUM_SUBTOTAL_NOT_MET");
        request.setQuantity(2); carts.updateQuantity(fixture.owner(), fixture.item(), request);
        assertThat(carts.getCart(fixture.owner()).getVoucher()).isNull();
        assertThat(jdbc.queryForObject("SELECT state FROM voucher_entitlements WHERE id=?", String.class, grant)).isEqualTo("AVAILABLE");
    }
    @Test void reapplyAndFailedReplacement_shouldKeepOneSelectionWithoutSpendingOrCleaningOnFailure() {
        long grant = grant(); vouchers.applyVoucher(fixture.owner(), grant); vouchers.applyVoucher(fixture.owner(), grant);
        jdbc.update("UPDATE vouchers SET enabled=FALSE WHERE id=(SELECT voucher_id FROM voucher_entitlements WHERE id=?)", grant);
        assertThatThrownBy(() -> vouchers.applyVoucher(fixture.owner(), Long.MAX_VALUE)).isInstanceOf(com.example.demo.voucher.exception.VoucherNotFoundException.class);
        assertThat(jdbc.queryForObject("SELECT voucher_entitlement_id FROM carts WHERE id=?", Long.class, fixture.cart())).isEqualTo(grant);
        assertThat(jdbc.queryForObject("SELECT state FROM voucher_entitlements WHERE id=?", String.class, grant)).isEqualTo("AVAILABLE");
    }
    @Test void removeVoucher_shouldNotCreateMissingCartOrDeleteGrant() {
        long before = jdbc.queryForObject("SELECT COUNT(*) FROM carts", Long.class);
        vouchers.removeVoucher(fixture.foreign());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM carts", Long.class)).isEqualTo(before);
    }
    @Test void applyAndRecalculate_shouldNeverSpendGrantAndClearInvalidSelectionOnRead() {
        long grant = grant();
        var before = carts.getCart(fixture.owner());
        var applied = vouchers.applyVoucher(fixture.owner(), grant);
        assertThat(applied.getVoucher().getWalletEntitlementId()).isEqualTo(grant);
        assertThat(applied.getVoucherDiscount().getAmount()).isEqualTo(BigInteger.TEN.min(before.getSelectedSubtotal().getAmount()));
        assertThat(jdbc.queryForObject("SELECT state FROM voucher_entitlements WHERE id=?", String.class, grant)).isEqualTo("AVAILABLE");
        jdbc.update("UPDATE vouchers SET enabled=FALSE WHERE id=(SELECT voucher_id FROM voucher_entitlements WHERE id=?)", grant);
        var cleared = carts.getCart(fixture.owner());
        assertThat(cleared.getVoucher()).isNull();
        assertThat(cleared.getVoucherSelectionClearedReason()).isEqualTo("VOUCHER_DISABLED");
        assertThat(cleared.getVoucherDiscount().getAmount()).isZero();
        assertThat(jdbc.queryForObject("SELECT voucher_entitlement_id FROM carts WHERE id=?", Long.class, fixture.cart())).isNull();
        assertThat(carts.getCart(fixture.owner()).getVoucherSelectionClearedReason()).isNull();
    }
    @Test void failedReplacement_shouldPreserveSelectionAndForeignOwnershipSafety() {
        long grant = grant(); vouchers.applyVoucher(fixture.owner(), grant);
        assertThatThrownBy(() -> vouchers.applyVoucher(fixture.foreign(), grant)).isInstanceOf(com.example.demo.voucher.exception.VoucherNotFoundException.class);
        assertThatThrownBy(() -> vouchers.applyVoucher(fixture.owner(), Long.MAX_VALUE)).isInstanceOf(com.example.demo.voucher.exception.VoucherNotFoundException.class);
        assertThat(jdbc.queryForObject("SELECT voucher_entitlement_id FROM carts WHERE id=?", Long.class, fixture.cart())).isEqualTo(grant);
    }
    @Test void removeItem_shouldReconcileSelectionEvenWithNoResponseBody() {
        long grant = grant(); vouchers.applyVoucher(fixture.owner(), grant);
        carts.removeItem(fixture.owner(), fixture.item());
        assertThat(jdbc.queryForObject("SELECT voucher_entitlement_id FROM carts WHERE id=?", Long.class, fixture.cart())).isNull();
        assertThat(jdbc.queryForObject("SELECT state FROM voucher_entitlements WHERE id=?", String.class, grant)).isEqualTo("AVAILABLE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM voucher_redemptions", Long.class)).isZero();
    }
    @ParameterizedTest @ValueSource(ints={100,101,205})
    void cartPreview_shouldAddOnlyThreeVoucherReadsRegardlessOfRowCount(int count) {
        transactions.executeWithoutResult(tx -> {
            for(int i=2;i<count;i++) com.example.demo.fixture.checkout.CheckoutFixtures.additionalItem(entities,fixture.cart(),true);
        });
        com.example.demo.config.SqlCapture.begin();carts.getCart(fixture.owner());
        var baseline=com.example.demo.config.SqlCapture.end();
        assertThat(baseline).noneMatch(q -> q.toLowerCase().matches("(?s).*\\b(?:from|join|update|into)\\s+voucher[a-z_]*\\b.*"));
        long grant=grant();vouchers.applyVoucher(fixture.owner(),grant);
        com.example.demo.config.SqlCapture.begin();var response=carts.getCart(fixture.owner());var sql=com.example.demo.config.SqlCapture.end();
        long voucherReads=sql.stream().filter(q -> q.toLowerCase().matches("(?s).*\\b(?:from|join)\\s+voucher[a-z_]*\\b.*")).count();
        assertThat(voucherReads).isEqualTo(3);assertThat(response.getItems()).hasSize(count);
        assertThat(sql.size()-voucherReads).isEqualTo(baseline.size());
        com.example.demo.config.SqlCapture.begin();tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(response);assertThat(com.example.demo.config.SqlCapture.end()).isEmpty();
        System.out.printf("VOUCHER_CART_SQL rows=%d baseline=%d voucherAddition=%d detached=0%n",count,baseline.size(),voucherReads);
    }
}
