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
class VoucherRedemptionRepositoryIT {
    @Autowired VoucherRepository mutations;
    @Autowired JdbcTemplate jdbc;
    @Test void guardedMutations_shouldRejectDuplicateConsumptionAndQuotaOverflow() {
        long owner = jdbc.queryForObject("INSERT INTO users(username,password,account_status,joined_at) VALUES (?,'test','ACTIVE',CURRENT_TIMESTAMP) RETURNING id", Long.class, "quota-" + System.nanoTime());
        long voucher = VoucherFixtures.definition(jdbc, "LIMIT_" + System.nanoTime(), "FIXED_AMOUNT", BigDecimal.TEN, 1L, 1L);
        long usage = VoucherFixtures.usage(jdbc, voucher, owner);
        Instant event = Instant.now();
        long grant = VoucherFixtures.grant(jdbc, voucher, owner, usage, event.minusSeconds(1));
        assertThat(mutations.incrementGlobal(voucher)).isOne();
        assertThat(mutations.incrementGlobal(voucher)).isZero();
        assertThat(mutations.incrementCustomer(usage, voucher, owner)).isOne();
        assertThat(mutations.incrementCustomer(usage, voucher, owner)).isZero();
        assertThat(mutations.consumeEntitlement(grant, voucher, owner, event)).isOne();
        assertThat(mutations.consumeEntitlement(grant, voucher, owner, event)).isZero();
        // Transient guarded DML is intentionally rolled back: without an order/redemption
        // this intermediate state must never pass the separate final-commit migration tests.
    }
}
