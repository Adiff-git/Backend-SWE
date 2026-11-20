package com.example.demo.voucher.converter;

import com.example.demo.voucher.repository.VoucherRepository;
import java.math.BigDecimal;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VoucherConverterTest {
    @Test void toPreview_shouldPreservePreparedDiscountWithoutRecalculationAndHideInvalidSummary() {
        var definition = mock(VoucherRepository.DefinitionRow.class);
        when(definition.getId()).thenReturn(5L); when(definition.getCode()).thenReturn("TEN");
        when(definition.getDiscountValue()).thenReturn(new BigDecimal("10"));
        var converter = new VoucherConverter();
        var valid = converter.toPreview(definition, 7L, BigInteger.valueOf(101), BigInteger.TEN, null);
        assertThat(valid.getVoucher().getCalculationBase().getAmount()).isEqualTo(BigInteger.valueOf(101));
        assertThat(valid.getDiscount()).isEqualTo(BigInteger.TEN);
        var invalid = converter.toPreview(definition, 7L, BigInteger.TEN, BigInteger.ZERO, "VOUCHER_EXPIRED");
        assertThat(invalid.getVoucher()).isNull();
        assertThat(invalid.getReason()).isEqualTo("VOUCHER_EXPIRED");
    }
}
