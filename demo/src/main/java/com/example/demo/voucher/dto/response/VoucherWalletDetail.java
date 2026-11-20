package com.example.demo.voucher.dto.response;
import java.time.Instant;
import lombok.Getter;
@Getter @lombok.EqualsAndHashCode(callSuper=true)
public final class VoucherWalletDetail extends VoucherWalletEntry {
    private final String entitlementState;
    private final Instant consumedAt;
    public VoucherWalletDetail(VoucherWalletEntry item,String state,Instant at) {
        super(item.getWalletEntitlementId(),item.getVoucherId(),item.getCode(),item.getTitle(),item.getDescription(),item.getDiscountType(),item.getDiscountValue(),item.getMinimumSubtotal(),item.getMaximumDiscount(),item.getValidFrom(),item.getValidUntil(),item.isEnabled(),item.getGrantedAt(),item.isApplicable(),item.getIneligibilityReason());
        entitlementState=state;consumedAt=at;
    }
}
