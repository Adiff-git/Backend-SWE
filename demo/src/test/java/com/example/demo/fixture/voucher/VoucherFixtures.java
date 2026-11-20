package com.example.demo.fixture.voucher;

import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;

/** Explicit test-only administrator grants; caller owns transaction and cleanup. */
public final class VoucherFixtures {
    private VoucherFixtures() { }
    public static long definition(JdbcTemplate jdbc, String code, String type, BigDecimal value,
            Long globalLimit, Long customerLimit) {
        return jdbc.queryForObject("INSERT INTO vouchers(code,title,discount_type,discount_value,minimum_subtotal,enabled,global_usage_limit,per_customer_usage_limit) VALUES (?,? ,?,?,0,TRUE,?,?) RETURNING id",
                Long.class, code, code, type, value, globalLimit, customerLimit);
    }
    public static long usage(JdbcTemplate jdbc, long voucher, long owner) {
        return jdbc.queryForObject("INSERT INTO voucher_customer_usages(voucher_id,customer_id) VALUES (?,?) RETURNING id", Long.class, voucher, owner);
    }
    public static long grant(JdbcTemplate jdbc, long voucher, long owner, long usage, Instant event) {
        return jdbc.queryForObject("INSERT INTO voucher_entitlements(voucher_id,customer_id,voucher_customer_usage_id,granted_at) VALUES (?,?,?,?) RETURNING id",
                Long.class, voucher, owner, usage, java.sql.Timestamp.from(event));
    }
}
