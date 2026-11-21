package com.example.demo.voucher.dto.response;
import com.example.demo.catalog.dto.response.SellingPrice;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Value;
@Value @lombok.experimental.NonFinal
public class VoucherWalletEntry {
    Long walletEntitlementId; Long voucherId; String code; String title; String description;
    String discountType; BigDecimal discountValue; SellingPrice minimumSubtotal; SellingPrice maximumDiscount;
    Instant validFrom; Instant validUntil; boolean enabled; Instant grantedAt; boolean applicable; String ineligibilityReason;
}
