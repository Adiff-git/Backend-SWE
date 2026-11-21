package com.example.demo.voucher.validator;

import com.example.demo.testdata.voucher.VoucherTestData;
import java.math.BigInteger;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class VoucherValidatorTest {
    private final VoucherValidator validator = new VoucherValidator();
    private final Instant event = VoucherTestData.event();
    @Test void eligibilityReason_shouldUseInclusiveStartExclusiveEndAndUnboundedNulls() {
        assertThat(reason(true, "AVAILABLE", true, event, event.plusSeconds(1), 0, null, 0, null, BigInteger.ONE, BigInteger.ZERO)).isNull();
        assertThat(reason(true, "AVAILABLE", true, null, event, 0, null, 0, null, BigInteger.ONE, BigInteger.ZERO)).isEqualTo("VOUCHER_EXPIRED");
        assertThat(reason(true, "AVAILABLE", true, event.plusSeconds(1), null, 0, null, 0, null, BigInteger.ONE, BigInteger.ZERO)).isEqualTo("VOUCHER_NOT_STARTED");
        assertThat(reason(true, "AVAILABLE", true, null, null, 0, null, 0, null, BigInteger.ONE, BigInteger.ONE)).isNull();
    }
    @Test void eligibilityReason_shouldEnforceOwnershipGrantAndSharedLimitsBeforeMinimum() {
        assertThat(reason(false, "AVAILABLE", true, null, null, 0, null, 0, null, BigInteger.TEN, BigInteger.ZERO)).isEqualTo("VOUCHER_UNAVAILABLE");
        assertThat(reason(true, "CONSUMED", true, null, null, 0, null, 0, null, BigInteger.TEN, BigInteger.ZERO)).isEqualTo("ENTITLEMENT_UNAVAILABLE");
        assertThat(reason(true, "AVAILABLE", false, null, null, 0, null, 0, null, BigInteger.TEN, BigInteger.ZERO)).isEqualTo("VOUCHER_DISABLED");
        assertThat(reason(true, "AVAILABLE", true, null, null, 1, 1L, 1, 1L, BigInteger.ZERO, BigInteger.TEN)).isEqualTo("GLOBAL_USAGE_EXHAUSTED");
        assertThat(reason(true, "AVAILABLE", true, null, null, 0, null, 2, 2L, BigInteger.TEN, BigInteger.ZERO)).isEqualTo("CUSTOMER_USAGE_EXHAUSTED");
        assertThat(reason(true, "AVAILABLE", true, null, null, 0, null, 0, null, BigInteger.ZERO, BigInteger.ZERO)).isEqualTo("NO_ELIGIBLE_ITEMS");
        assertThat(reason(true, "AVAILABLE", true, null, null, 0, null, 0, null, BigInteger.ONE, BigInteger.TEN)).isEqualTo("MINIMUM_SUBTOTAL_NOT_MET");
    }
    private String reason(boolean owned, String state, boolean enabled, Instant start, Instant end,
            long global, Long globalLimit, long customer, Long customerLimit, BigInteger base, BigInteger minimum) {
        return validator.eligibilityReason(owned, state, enabled, start, end, global, globalLimit, customer, customerLimit, base, minimum, event);
    }
}
