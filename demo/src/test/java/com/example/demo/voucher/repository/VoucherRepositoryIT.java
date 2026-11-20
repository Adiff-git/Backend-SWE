package com.example.demo.voucher.repository;

import com.example.demo.fixture.voucher.VoucherFixtures;
import java.math.BigDecimal;
import java.time.Instant;
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
class VoucherRepositoryIT {
    @Autowired VoucherRepository vouchers;
    @Autowired JdbcTemplate jdbc;
    @Test void findDefinition_shouldPreserveExactPercentageAndUnlimitedNullLimits() {
        long id = VoucherFixtures.definition(jdbc, "PERCENT_" + System.nanoTime(), "PERCENTAGE", new BigDecimal("99.99"), null, null);
        var row = vouchers.findDefinition(id).orElseThrow();
        assertThat(row.getDiscountValue()).isEqualByComparingTo("99.99");
        assertThat(row.getGlobalUsageLimit()).isNull();
        assertThat(row.getPerCustomerUsageLimit()).isNull();
        assertThat(row.getRedeemedCount()).isZero();
    }
    @Test void walletProjection_shouldCountDistinctOwnedGrantsAndBindBoundaryLongOffsets() {
        long owner=com.example.demo.fixture.checkout.CheckoutFixtures.customer(jdbc);
        long foreign=com.example.demo.fixture.checkout.CheckoutFixtures.customer(jdbc);
        long definition=VoucherFixtures.definition(jdbc,"PAGE_"+System.nanoTime(),"FIXED_AMOUNT",BigDecimal.ONE,null,null);
        long usage=VoucherFixtures.usage(jdbc,definition,owner);
        long first=VoucherFixtures.grant(jdbc,definition,owner,usage,Instant.now());
        long second=VoucherFixtures.grant(jdbc,definition,owner,usage,Instant.now());
        assertThat(vouchers.countWallet(owner)).isEqualTo(2);assertThat(vouchers.countWallet(foreign)).isZero();
        assertThat(vouchers.findWalletPage(owner,0,1)).extracting(VoucherRepository.WalletRow::getWalletEntitlementId).containsExactly(first);
        assertThat(vouchers.findWalletPage(owner,1,1)).extracting(VoucherRepository.WalletRow::getWalletEntitlementId).containsExactly(second);
        for(long offset:new long[]{2,2147483647L,2147483648L,214748364700L}) assertThat(vouchers.findWalletPage(owner,offset,100)).isEmpty();
        assertThat(vouchers.findWalletDetail(foreign,first)).isEmpty();
    }
}
