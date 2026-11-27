package com.example.demo.voucher.service;

import com.example.demo.voucher.service.impl.VoucherServiceImpl;
import com.example.demo.voucher.repository.VoucherRepository;
import com.example.demo.voucher.repository.VoucherEntitlementRepository;
import com.example.demo.voucher.repository.VoucherCustomerUsageRepository;
import com.example.demo.voucher.calculator.VoucherCalculator;
import com.example.demo.voucher.validator.VoucherValidator;
import com.example.demo.voucher.converter.VoucherConverter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

class VoucherServiceImplTest {
    private final VoucherRepository definitions = mock(VoucherRepository.class);
    private final VoucherEntitlementRepository grants = mock(VoucherEntitlementRepository.class);
    private final VoucherCustomerUsageRepository usages = mock(VoucherCustomerUsageRepository.class);
    private final Instant event = Instant.parse("2026-11-20T12:00:00Z");
    private final VoucherServiceImpl service = new VoucherServiceImpl(definitions, grants, usages,
            new VoucherValidator(), new VoucherCalculator(), new VoucherConverter(), Clock.systemUTC());
    @Test void getPreview_shouldAvoidAllVoucherReadsWithoutSelection() {
        var preview = service.getPreview(1L, null, BigInteger.TEN, event);
        assertThat(preview.getDiscount()).isZero();
        assertThat(preview.getVoucher()).isNull();
        verifyNoInteractions(definitions, grants, usages);
    }
    @Test void getPreview_shouldRejectUnownedReferenceWithoutReadingDefinition() {
        when(grants.findOwned(1L, 2L)).thenReturn(Optional.empty());
        var preview = service.getPreview(1L, 2L, BigInteger.TEN, event);
        assertThat(preview.getReason()).isEqualTo("VOUCHER_UNAVAILABLE");
        assertThat(preview.getDiscount()).isZero();
        verifyNoInteractions(definitions, usages);
    }
    @Test void getPreview_shouldCalculateOwnedGrantWithoutConsumingIt() {
        var grant = mock(VoucherEntitlementRepository.GrantRow.class);
        when(grant.getId()).thenReturn(2L); when(grant.getVoucherId()).thenReturn(3L);
        when(grant.getState()).thenReturn("AVAILABLE");
        var definition = mock(VoucherRepository.DefinitionRow.class);
        when(definition.getId()).thenReturn(3L); when(definition.getEnabled()).thenReturn(true);
        when(definition.getDiscountType()).thenReturn("PERCENTAGE");
        when(definition.getDiscountValue()).thenReturn(new BigDecimal("10"));
        when(definition.getMinimumSubtotal()).thenReturn(BigDecimal.ZERO);
        when(definition.getGlobalUsageLimit()).thenReturn(null);
        when(definition.getPerCustomerUsageLimit()).thenReturn(null);
        var usage = mock(VoucherCustomerUsageRepository.UsageRow.class);
        when(grants.findOwned(1L, 2L)).thenReturn(Optional.of(grant));
        when(definitions.findDefinition(3L)).thenReturn(Optional.of(definition));
        when(usages.findUsage(3L, 1L)).thenReturn(Optional.of(usage));
        var preview = service.getPreview(1L, 2L, BigInteger.valueOf(101), event);
        assertThat(preview.getDiscount()).isEqualTo(BigInteger.TEN);
        assertThat(preview.getVoucher().getWalletEntitlementId()).isEqualTo(2L);
        assertThat(preview.getReason()).isNull();
        org.mockito.Mockito.verify(definitions, org.mockito.Mockito.never()).incrementGlobal(3L);
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void lockCheckoutVoucher_shouldKeepLockOrderAndUseCapturedTimeOrLegacyPostLockClock(boolean captured) {
        var clock = mock(Clock.class);
        var grant = mock(VoucherEntitlementRepository.GrantRow.class);
        when(grant.getId()).thenReturn(2L); when(grant.getVoucherId()).thenReturn(3L);
        when(grant.getState()).thenReturn("AVAILABLE"); when(grant.getCustomerId()).thenReturn(1L);
        var definition = mock(VoucherRepository.DefinitionRow.class);
        when(definition.getId()).thenReturn(3L); when(definition.getEnabled()).thenReturn(true);
        when(definition.getDiscountType()).thenReturn("FIXED_AMOUNT");
        when(definition.getDiscountValue()).thenReturn(BigDecimal.ONE);
        when(definition.getMinimumSubtotal()).thenReturn(BigDecimal.ZERO);
        when(definition.getGlobalUsageLimit()).thenReturn(null); when(definition.getPerCustomerUsageLimit()).thenReturn(null);
        var usage = mock(VoucherCustomerUsageRepository.UsageRow.class);
        when(grants.findOwned(1L, 2L)).thenReturn(Optional.of(grant));
        when(definitions.lockDefinition(3L)).thenReturn(Optional.of(definition));
        when(usages.lockUsage(3L, 1L)).thenReturn(Optional.of(usage));
        when(grants.lockOwned(1L, 2L)).thenReturn(Optional.of(grant));
        when(clock.instant()).thenReturn(event);
        var lockedService = new VoucherServiceImpl(definitions, grants, usages, new VoucherValidator(), new VoucherCalculator(), new VoucherConverter(), clock);
        var quote = captured ? lockedService.lockCheckoutVoucher(1L,2L,BigInteger.TEN,event) : lockedService.lockCheckoutVoucher(1L, 2L, BigInteger.TEN);
        assertThat(quote.getEvent()).isEqualTo(event);
        var sequence = org.mockito.Mockito.inOrder(definitions, usages, grants, clock);
        sequence.verify(grants).findOwned(1L, 2L);
        sequence.verify(definitions).lockDefinition(3L);
        sequence.verify(usages).lockUsage(3L, 1L);
        sequence.verify(grants).lockOwned(1L, 2L);
        if(captured) org.mockito.Mockito.verifyNoInteractions(clock); else sequence.verify(clock).instant();
    }
    @Test void wallet_shouldCountFirstAndBindLongOffsetForNonemptyOverflow() {
        when(definitions.countWallet(1L)).thenReturn(20L);
        var empty=service.getWalletPage(1L,1,20,BigInteger.ZERO);
        assertThat(empty.getItems()).isEmpty();
        assertThat(empty.getTotalElements()).isEqualTo(20);
        org.mockito.Mockito.verify(definitions,org.mockito.Mockito.never()).findWalletPage(org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.anyInt());
        when(definitions.countWallet(1L)).thenReturn(2147483649L);
        when(definitions.findWalletPage(1L,2147483648L,2)).thenReturn(java.util.List.of());
        assertThat(service.getWalletPage(1L,1073741824,2,BigInteger.ZERO).getTotalPages()).isEqualTo(1073741825L);
        org.mockito.Mockito.verify(definitions).findWalletPage(1L,2147483648L,2);
        verifyNoInteractions(grants,usages);
    }
}
