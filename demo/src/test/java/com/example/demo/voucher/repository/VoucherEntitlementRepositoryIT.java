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
class VoucherEntitlementRepositoryIT {
    @Autowired VoucherEntitlementRepository grants;
    @Autowired JdbcTemplate jdbc;
    @Test void findOwned_shouldRejectForeignOwnershipAndKeepDistinctRemainingGrants() {
        long owner = jdbc.queryForObject("INSERT INTO users(username,password,account_status,joined_at) VALUES (?,'test','ACTIVE',CURRENT_TIMESTAMP) RETURNING id", Long.class, "voucher-" + System.nanoTime());
        long voucher = VoucherFixtures.definition(jdbc, "OWN_" + System.nanoTime(), "FIXED_AMOUNT", BigDecimal.TEN, 3L, 3L);
        long usage = VoucherFixtures.usage(jdbc, voucher, owner);
        long first = VoucherFixtures.grant(jdbc, voucher, owner, usage, Instant.now());
        long second = VoucherFixtures.grant(jdbc, voucher, owner, usage, Instant.now());
        assertThat(grants.findOwned(owner, first)).isPresent();
        assertThat(grants.findOwned(owner + 1, first)).isEmpty();
        assertThat(grants.findOwned(owner, second).orElseThrow().getState()).isEqualTo("AVAILABLE");
        assertThat(jdbc.queryForObject("SELECT redeemed_count FROM voucher_customer_usages WHERE id=?", Long.class, usage)).isZero();
    }
}
