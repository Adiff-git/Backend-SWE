package com.example.demo.voucher.calculator;

import com.example.demo.testdata.voucher.VoucherTestData;
import java.math.BigDecimal;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VoucherCalculatorTest {
    private final VoucherCalculator calculator = new VoucherCalculator();
    @Test void calculate_shouldApplyApprovedExactExamplesWithoutChangingBase() {
        BigInteger base = VoucherTestData.subtotal();
        assertThat(calculator.calculate(base, "FIXED_AMOUNT", VoucherTestData.fixedAmount(), null)).isEqualTo(new BigInteger("50000"));
        assertThat(calculator.calculate(base, "PERCENTAGE", new BigDecimal("10"), null)).isEqualTo(new BigInteger("50000"));
        assertThat(calculator.calculate(base, "PERCENTAGE", new BigDecimal("20"), new BigInteger("60000"))).isEqualTo(new BigInteger("60000"));
        assertThat(calculator.calculate(new BigInteger("30000"), "FIXED_AMOUNT", new BigDecimal("50000"), null)).isEqualTo(new BigInteger("30000"));
        assertThat(base).isEqualTo(new BigInteger("500000"));
    }
    @Test void calculate_shouldFloorOnceAndPermitZeroRoundedDiscount() {
        assertThat(calculator.calculate(BigInteger.valueOf(101), "PERCENTAGE", new BigDecimal("10"), null)).isEqualTo(BigInteger.TEN);
        assertThat(calculator.calculate(BigInteger.ONE, "PERCENTAGE", new BigDecimal("0.01"), null)).isZero();
        assertThat(calculator.calculate(BigInteger.TEN, "PERCENTAGE", new BigDecimal("100"), BigInteger.ZERO)).isZero();
    }
    @Test void calculate_shouldPreserveHugeExactAmounts() {
        BigInteger base = BigInteger.TEN.pow(1000).add(BigInteger.ONE);
        assertThat(calculator.calculate(base, "PERCENTAGE", new BigDecimal("99.99"), null)).isEqualTo(base.multiply(BigInteger.valueOf(9999)).divide(BigInteger.valueOf(10000)));
        assertThat(calculator.calculate(base, "PERCENTAGE", new BigDecimal("100"), null)).isEqualTo(base);
    }
    @Test void calculate_shouldRejectInvalidDefinitionsRatherThanTruncatePrecision() {
        for (String value : new String[]{"0", "-1", "100.01", "0.001"}) {
            assertThatThrownBy(() -> calculator.calculate(BigInteger.TEN, "PERCENTAGE", new BigDecimal(value), null)).isInstanceOf(RuntimeException.class);
        }
        assertThatThrownBy(() -> calculator.calculate(BigInteger.ZERO, "FIXED_AMOUNT", BigDecimal.ONE, null)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> calculator.calculate(BigInteger.TEN, "FIXED_AMOUNT", new BigDecimal("1.5"), null)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> calculator.calculate(BigInteger.TEN, "PERCENTAGE", BigDecimal.ONE, BigInteger.valueOf(-1))).isInstanceOf(RuntimeException.class);
    }
}
