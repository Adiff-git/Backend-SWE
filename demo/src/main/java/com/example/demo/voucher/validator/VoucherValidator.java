package com.example.demo.voucher.validator;
import java.math.BigInteger;
import java.time.Instant;
import org.springframework.stereotype.Component;
@Component
public class VoucherValidator {
    public String eligibilityReason(boolean owned, String state, boolean enabled, Instant start, Instant end,
            long global, Long globalLimit, long customer, Long customerLimit, BigInteger base, BigInteger minimum, Instant event) {
        if (!owned) return "VOUCHER_UNAVAILABLE";
        if (!"AVAILABLE".equals(state)) return "ENTITLEMENT_UNAVAILABLE";
        if (!enabled) return "VOUCHER_DISABLED";
        if (start != null && event.isBefore(start)) return "VOUCHER_NOT_STARTED";
        if (end != null && !event.isBefore(end)) return "VOUCHER_EXPIRED";
        if (globalLimit != null && global >= globalLimit) return "GLOBAL_USAGE_EXHAUSTED";
        if (customerLimit != null && customer >= customerLimit) return "CUSTOMER_USAGE_EXHAUSTED";
        if (base == null || base.signum() <= 0) return "NO_ELIGIBLE_ITEMS";
        if (base.compareTo(minimum) < 0) return "MINIMUM_SUBTOTAL_NOT_MET";
        return null;
    }
}
