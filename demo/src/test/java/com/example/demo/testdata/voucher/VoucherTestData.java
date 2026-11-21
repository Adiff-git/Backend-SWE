package com.example.demo.testdata.voucher;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;

/** Fresh immutable values for meaningful Voucher rule tests. */
public final class VoucherTestData {
    private VoucherTestData() { }
    public static BigInteger subtotal() { return new BigInteger("500000"); }
    public static BigDecimal fixedAmount() { return new BigDecimal("50000"); }
    public static Instant event() { return Instant.parse("2026-11-20T12:00:00Z"); }
}
