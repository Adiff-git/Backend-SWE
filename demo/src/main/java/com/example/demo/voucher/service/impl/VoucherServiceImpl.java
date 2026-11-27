package com.example.demo.voucher.service.impl;
import com.example.demo.voucher.service.VoucherService;
import com.example.demo.voucher.repository.VoucherRepository;
import com.example.demo.voucher.repository.VoucherEntitlementRepository;
import com.example.demo.voucher.repository.VoucherCustomerUsageRepository;
import com.example.demo.voucher.validator.VoucherValidator;
import com.example.demo.voucher.calculator.VoucherCalculator;
import com.example.demo.voucher.converter.VoucherConverter;
import com.example.demo.voucher.dto.response.VoucherPreview;
import com.example.demo.common.identity.IdentityUnavailableException;
import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
@Service @RequiredArgsConstructor
public class VoucherServiceImpl implements VoucherService {
    private final VoucherRepository definitions;
    private final VoucherEntitlementRepository grants;
    private final VoucherCustomerUsageRepository usages;
    private final VoucherValidator validator;
    private final VoucherCalculator calculator;
    private final VoucherConverter converter;
    private final Clock clock;
    @Override @Transactional(readOnly=true, isolation=Isolation.READ_COMMITTED)
    public VoucherPreview getPreview(Long owner, Long grant, BigInteger base, Instant event) {
        if (owner == null || owner <= 0) throw new IdentityUnavailableException();
        if (grant == null) return new VoucherPreview(null, BigInteger.ZERO, null);
        var owned = grants.findOwned(owner, grant).orElse(null);
        if (owned == null) return new VoucherPreview(null, BigInteger.ZERO, "VOUCHER_UNAVAILABLE");
        var definition = definitions.findDefinition(owned.getVoucherId()).orElse(null);
        var usage = usages.findUsage(owned.getVoucherId(), owner).orElse(null);
        if (definition == null || usage == null) return new VoucherPreview(null, BigInteger.ZERO, "VOUCHER_UNAVAILABLE");
        String reason = validator.eligibilityReason(true, owned.getState(), definition.getEnabled(), definition.getValidFrom(), definition.getValidUntil(),
            definition.getRedeemedCount(), definition.getGlobalUsageLimit(), usage.getRedeemedCount(), definition.getPerCustomerUsageLimit(), base, definition.getMinimumSubtotal().toBigIntegerExact(), event);
        BigInteger discount = reason == null ? calculator.calculate(base, definition.getDiscountType(), definition.getDiscountValue(), definition.getMaximumDiscount() == null ? null : definition.getMaximumDiscount().toBigIntegerExact()) : BigInteger.ZERO;
        return converter.toPreview(definition, grant, base, discount, reason);
    }
    /** Compatibility entry: retain post-lock time sampling for callers without a prepared operation. */
    @Override @Transactional(isolation=Isolation.READ_COMMITTED)
    public com.example.demo.voucher.dto.response.CheckoutVoucherQuote lockCheckoutVoucher(Long owner, Long grant, BigInteger base) {
        return lockCheckoutQuote(owner,grant,base,() -> clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
    }
    @Override @Transactional(isolation=Isolation.READ_COMMITTED)
    public com.example.demo.voucher.dto.response.CheckoutVoucherQuote lockCheckoutVoucher(Long owner, Long grant, BigInteger base, Instant event) {
        java.util.Objects.requireNonNull(event,"Captured pricing instant is required");
        return lockCheckoutQuote(owner,grant,base,() -> event);
    }
    private com.example.demo.voucher.dto.response.CheckoutVoucherQuote lockCheckoutQuote(Long owner,Long grant,BigInteger base,java.util.function.Supplier<Instant> eventSource) {
        if (owner==null || owner<=0) throw new IdentityUnavailableException();
        if (grant==null) return new com.example.demo.voucher.dto.response.CheckoutVoucherQuote(owner, null, base, eventSource.get(), null);
        var discovered = grants.findOwned(owner, grant).orElseThrow(() -> new com.example.demo.voucher.exception.CheckoutVoucherNotApplicableException("VOUCHER_UNAVAILABLE"));
        var definition = definitions.lockDefinition(discovered.getVoucherId()).orElseThrow(com.example.demo.voucher.exception.VoucherConflictException::new);
        var usage = usages.lockUsage(discovered.getVoucherId(), owner).orElseThrow(com.example.demo.voucher.exception.VoucherConflictException::new);
        var locked = grants.lockOwned(owner, grant).orElseThrow(() -> new com.example.demo.voucher.exception.CheckoutVoucherNotApplicableException("VOUCHER_UNAVAILABLE"));
        if (!discovered.getVoucherId().equals(locked.getVoucherId())) throw new com.example.demo.voucher.exception.VoucherConflictException();
        Instant event=eventSource.get();
        String reason = validator.eligibilityReason(true, locked.getState(), definition.getEnabled(), definition.getValidFrom(), definition.getValidUntil(),
            definition.getRedeemedCount(), definition.getGlobalUsageLimit(), usage.getRedeemedCount(), definition.getPerCustomerUsageLimit(), base, definition.getMinimumSubtotal().toBigIntegerExact(), event);
        if (reason != null) throw new com.example.demo.voucher.exception.CheckoutVoucherNotApplicableException(reason);
        if (locked.getGrantedAt()!=null && event.isBefore(locked.getGrantedAt())) throw new com.example.demo.voucher.exception.VoucherConflictException();
        BigInteger discount = calculator.calculate(base, definition.getDiscountType(), definition.getDiscountValue(), definition.getMaximumDiscount()==null ? null : definition.getMaximumDiscount().toBigIntegerExact());
        var snapshot = new com.example.demo.voucher.dto.response.VoucherSnapshot(grant, definition.getId(), definition.getCode(), definition.getTitle(), definition.getDiscountType(), definition.getDiscountValue(),
            new com.example.demo.catalog.dto.response.SellingPrice(definition.getMinimumSubtotal().toBigIntegerExact(), "VND"),
            definition.getMaximumDiscount()==null ? null : new com.example.demo.catalog.dto.response.SellingPrice(definition.getMaximumDiscount().toBigIntegerExact(), "VND"),
            new com.example.demo.catalog.dto.response.SellingPrice(base,"VND"), new com.example.demo.catalog.dto.response.SellingPrice(discount,"VND"), event);
        return new com.example.demo.voucher.dto.response.CheckoutVoucherQuote(owner,usage.getId(),base,event,snapshot);
    }
    @Override @Transactional(isolation=Isolation.READ_COMMITTED)
    public void redeemVoucher(com.example.demo.voucher.dto.response.CheckoutVoucherQuote quote, Long orderId) {
        if (quote==null || orderId==null || orderId<=0) throw new com.example.demo.voucher.exception.VoucherConflictException();
        var snapshot=quote.getSnapshot();
        if (snapshot==null) return;
        if (definitions.incrementGlobal(snapshot.getVoucherId())!=1
            || definitions.incrementCustomer(quote.getUsageId(),snapshot.getVoucherId(),quote.getOwnerId())!=1
            || definitions.consumeEntitlement(snapshot.getWalletEntitlementId(),snapshot.getVoucherId(),quote.getOwnerId(),quote.getEvent())!=1
            || definitions.insertRedemption(quote,orderId)!=1) throw new com.example.demo.voucher.exception.VoucherConflictException();
    }

    @Override @Transactional(readOnly=true,isolation=Isolation.READ_COMMITTED)
    public com.example.demo.voucher.dto.response.VoucherWalletPage getWalletPage(Long owner,int page,int size,BigInteger base) {
        return getWalletPage(owner,page,size,base,clock.instant());
    }
    @Override @Transactional(readOnly=true,isolation=Isolation.READ_COMMITTED)
    public com.example.demo.voucher.dto.response.VoucherWalletPage getWalletPage(Long owner,int page,int size,BigInteger base,Instant event) {
        if(owner==null || owner<=0) throw new IdentityUnavailableException();
        if(page<0 || size<1 || size>100 || base==null || base.signum()<0) throw new com.example.demo.voucher.exception.InvalidVoucherRequestException();
        long count=definitions.countWallet(owner),offset=(long)page*size;
        if(offset>=count) return new com.example.demo.voucher.dto.response.VoucherWalletPage(java.util.List.of(),page,size,count);
        var rows=definitions.findWalletPage(owner,offset,size).stream().map(row -> converter.toWallet(row,walletReason(row,base,event))).toList();
        return new com.example.demo.voucher.dto.response.VoucherWalletPage(rows,page,size,count);
    }
    @Override @Transactional(readOnly=true,isolation=Isolation.READ_COMMITTED)
    public com.example.demo.voucher.dto.response.VoucherWalletDetail getWalletEntitlement(Long owner,Long grant,BigInteger base) {
        return getWalletEntitlement(owner,grant,base,clock.instant());
    }
    @Override @Transactional(readOnly=true,isolation=Isolation.READ_COMMITTED)
    public com.example.demo.voucher.dto.response.VoucherWalletDetail getWalletEntitlement(Long owner,Long grant,BigInteger base,Instant event) {
        if(owner==null || owner<=0) throw new IdentityUnavailableException();
        if(grant==null || grant<=0 || base==null || base.signum()<0) throw new com.example.demo.voucher.exception.InvalidVoucherRequestException();
        var row=definitions.findWalletDetail(owner,grant).orElseThrow(com.example.demo.voucher.exception.VoucherNotFoundException::new);
        return new com.example.demo.voucher.dto.response.VoucherWalletDetail(converter.toWallet(row,walletReason(row,base,event)),row.getEntitlementState(),row.getConsumedAt());
    }
    private String walletReason(VoucherRepository.WalletRow row,BigInteger base,Instant event) {
        return validator.eligibilityReason(true,row.getEntitlementState(),row.getEnabled(),row.getValidFrom(),row.getValidUntil(),row.getRedeemedCount(),row.getGlobalUsageLimit(),row.getCustomerRedeemedCount(),row.getPerCustomerUsageLimit(),base,row.getMinimumSubtotal().toBigIntegerExact(),event);
    }
}
