package com.example.demo.voucher.calculator;
import com.example.demo.voucher.exception.VoucherConflictException;
import java.math.BigDecimal;
import java.math.BigInteger;
import org.springframework.stereotype.Component;
@Component
public class VoucherCalculator {
    private static final BigInteger PERCENT_DENOMINATOR = BigInteger.valueOf(10000);
    public BigInteger calculate(BigInteger base, String type, BigDecimal value, BigInteger cap) {
        if (base == null || base.signum() <= 0 || value == null || value.signum() <= 0
                || (cap != null && cap.signum() < 0)) throw new VoucherConflictException();
        BigInteger raw;
        try {
            if ("FIXED_AMOUNT".equals(type)) {
                if (cap != null) throw new VoucherConflictException();
                raw = value.toBigIntegerExact();
            } else if ("PERCENTAGE".equals(type)) {
                if (value.compareTo(BigDecimal.valueOf(100)) > 0) throw new VoucherConflictException();
                BigInteger hundredths = value.movePointRight(2).toBigIntegerExact();
                // Exact rational arithmetic: cap and base are integral, so floor commutes
                // with these minima without intermediate decimal rounding.
                raw = base.multiply(hundredths).divide(PERCENT_DENOMINATOR);
                if (cap != null) raw = raw.min(cap);
            } else throw new VoucherConflictException();
        } catch (ArithmeticException invalid) { throw new VoucherConflictException(); }
        return raw.min(base);
    }
}
