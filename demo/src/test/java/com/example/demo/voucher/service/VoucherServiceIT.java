package com.example.demo.voucher.service;
import com.example.demo.config.CheckoutIntegrationSupport;
import com.example.demo.config.SqlCapture;
import com.example.demo.fixture.voucher.VoucherFixtures;
import com.example.demo.cart.service.CartVoucherService;
import com.example.demo.checkout.service.CheckoutService;
import com.example.demo.checkout.dto.request.CheckoutRequest;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
@Import(com.example.demo.config.SqlCaptureConfiguration.class)
class VoucherServiceIT extends CheckoutIntegrationSupport {
    @Autowired CartVoucherService wallet;
    @Autowired CheckoutService checkout;
    @MockitoSpyBean com.example.demo.storage.service.ImageUrlResolver images;
    private long grant(long owner,String code) {
        return transactions.execute(tx -> {
            long definition=VoucherFixtures.definition(jdbc,code,"FIXED_AMOUNT",BigDecimal.TEN,null,null);
            long usage=VoucherFixtures.usage(jdbc,definition,owner);
            return VoucherFixtures.grant(jdbc,definition,owner,usage,Instant.now().minusSeconds(1));
        });
    }
    @Test void wallet_shouldScopeDistinctGrantsAndObserveInvalidSelectionWithoutCleanup() {
        long grant=grant(fixture.owner(),"OWN_A");
        long second=transactions.execute(tx -> {
            var row=jdbc.queryForMap("SELECT voucher_id,voucher_customer_usage_id FROM voucher_entitlements WHERE id=?",grant);
            return VoucherFixtures.grant(jdbc,((Number)row.get("voucher_id")).longValue(),fixture.owner(),((Number)row.get("voucher_customer_usage_id")).longValue(),Instant.now().minusSeconds(1));
        });
        grant(fixture.foreign(),"FOREIGN"); wallet.applyVoucher(fixture.owner(),grant);
        jdbc.update("UPDATE vouchers SET enabled=FALSE WHERE id=(SELECT voucher_id FROM voucher_entitlements WHERE id=?)",grant);
        var page=wallet.getWalletPage(fixture.owner(),0,20);
        assertThat(page.getItems()).extracting(row -> row.getWalletEntitlementId()).containsExactly(grant,second);
        assertThat(page.getItems()).allSatisfy(row -> assertThat(row.getIneligibilityReason()).isEqualTo("VOUCHER_DISABLED"));
        assertThat(jdbc.queryForObject("SELECT voucher_entitlement_id FROM carts WHERE id=?",Long.class,fixture.cart())).isEqualTo(grant);
        assertThatThrownBy(() -> wallet.getWalletEntitlement(fixture.foreign(),grant)).isInstanceOf(com.example.demo.voucher.exception.VoucherNotFoundException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders",Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT SUM(redeemed_count) FROM vouchers",Long.class)).isZero();
    }
    @Test void wallet_shouldRetainOwnConsumedDetailAndRemoveOnlyConsumedGrantFromActivePage() {
        long grant=grant(fixture.owner(),"CONSUMED");
        wallet.applyVoucher(fixture.owner(),grant);
        checkout.checkout(fixture.owner(),"Wallet_consumed_123",new CheckoutRequest(fixture.address()));
        assertThat(wallet.getWalletPage(fixture.owner(),0,20).getItems()).isEmpty();
        var detail=wallet.getWalletEntitlement(fixture.owner(),grant);
        assertThat(detail.getEntitlementState()).isEqualTo("CONSUMED");
        assertThat(detail.getConsumedAt()).isNotNull();
        assertThat(detail.isApplicable()).isFalse();
        assertThat(detail.getIneligibilityReason()).isEqualTo("ENTITLEMENT_UNAVAILABLE");
    }
    @Test void wallet_shouldReturnOwnedGrantForEmptyCartWithoutCreatingCartOrPreparingImages() {
        long grant=grant(fixture.foreign(),"EMPTY");
        org.mockito.Mockito.clearInvocations(images);
        assertThat(wallet.getWalletEntitlement(fixture.foreign(),grant).getIneligibilityReason()).isEqualTo("NO_ELIGIBLE_ITEMS");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM carts WHERE user_id=?",Long.class,fixture.foreign())).isZero();
        org.mockito.Mockito.verifyNoInteractions(images);
    }
    @ParameterizedTest @ValueSource(ints={100,101,205})
    void wallet_shouldUseFixedReadCountAndLongEmptyShortcut(int count) {
        transactions.executeWithoutResult(tx -> {
            long definition=VoucherFixtures.definition(jdbc,"MANY","FIXED_AMOUNT",BigDecimal.TEN,null,null);
            long usage=VoucherFixtures.usage(jdbc,definition,fixture.owner());
            for(int i=0;i<count;i++) VoucherFixtures.grant(jdbc,definition,fixture.owner(),usage,Instant.now().minusSeconds(1));
        });
        org.mockito.Mockito.clearInvocations(images);
        SqlCapture.begin();var page=wallet.getWalletPage(fixture.owner(),0,100);var sql=SqlCapture.end();
        assertThat(page.getItems()).hasSize(100);assertThat(page.getTotalElements()).isEqualTo(count);
        assertThat(sql.stream().filter(q -> q.toLowerCase().matches("(?s).*\\b(?:from|join|update|into)\\s+voucher[a-z_]*\\b.*"))).hasSize(2);
        assertThat(sql).noneMatch(q -> q.toLowerCase().contains("for update") || q.toLowerCase().startsWith("insert") || q.toLowerCase().startsWith("update"));
        assertThat(wallet.getWalletPage(fixture.owner(),Integer.MAX_VALUE,100).getTotalElements()).isEqualTo(count);
        SqlCapture.begin();tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(page);assertThat(SqlCapture.end()).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(images);
        System.out.printf("VOUCHER_WALLET_SQL rows=%d statements=%d voucherStatements=2%n",count,sql.size());
    }
    @ParameterizedTest @ValueSource(strings={"VOUCHER_EXPIRED","VOUCHER_NOT_STARTED"})
    void wallet_shouldObserveCurrentValidityWithoutAnyGrantMutation(String reason) {
        long grant=grant(fixture.owner(),"TIME");
        if(reason.equals("VOUCHER_EXPIRED")) jdbc.update("UPDATE vouchers SET valid_until=CURRENT_TIMESTAMP-INTERVAL '1 hour'");
        else jdbc.update("UPDATE vouchers SET valid_from=CURRENT_TIMESTAMP+INTERVAL '1 hour'");
        assertThat(wallet.getWalletEntitlement(fixture.owner(),grant).getIneligibilityReason()).isEqualTo(reason);
        assertThat(wallet.getWalletPage(fixture.owner(),0,20).getItems().getFirst().getIneligibilityReason()).isEqualTo(reason);
        assertThat(jdbc.queryForObject("SELECT state FROM voucher_entitlements WHERE id=?",String.class,grant)).isEqualTo("AVAILABLE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM voucher_redemptions",Long.class)).isZero();
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void wallet_shouldKeepRemainingGrantVisibleWithExhaustedQuotaReason(boolean global) {
        long first=grant(fixture.owner(),"QUOTA");
        long second=transactions.execute(tx -> {
            var row=jdbc.queryForMap("SELECT voucher_id,voucher_customer_usage_id FROM voucher_entitlements WHERE id=?",first);
            long definition=((Number)row.get("voucher_id")).longValue(),usage=((Number)row.get("voucher_customer_usage_id")).longValue();
            jdbc.update(global ? "UPDATE vouchers SET global_usage_limit=1 WHERE id=?" : "UPDATE vouchers SET per_customer_usage_limit=1 WHERE id=?",definition);
            return VoucherFixtures.grant(jdbc,definition,fixture.owner(),usage,Instant.now().minusSeconds(1));
        });
        wallet.applyVoucher(fixture.owner(),first);
        checkout.checkout(fixture.owner(),"Wallet_quota_key_123",new CheckoutRequest(fixture.address()));
        var page=wallet.getWalletPage(fixture.owner(),0,20);
        assertThat(page.getTotalElements()).isOne();
        assertThat(page.getItems().getFirst().getWalletEntitlementId()).isEqualTo(second);
        assertThat(page.getItems().getFirst().getIneligibilityReason()).isEqualTo(global ? "GLOBAL_USAGE_EXHAUSTED" : "CUSTOMER_USAGE_EXHAUSTED");
        assertThat(jdbc.queryForObject("SELECT state FROM voucher_entitlements WHERE id=?",String.class,second)).isEqualTo("AVAILABLE");
    }
}
