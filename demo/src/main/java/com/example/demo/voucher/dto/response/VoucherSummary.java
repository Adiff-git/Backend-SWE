package com.example.demo.voucher.dto.response;
import com.example.demo.catalog.dto.response.SellingPrice;
import java.math.BigDecimal;
import lombok.Value;
@Value
public class VoucherSummary {
    Long walletEntitlementId; Long voucherId; String code; String title; String discountType;
    BigDecimal discountValue; SellingPrice maximumDiscount; SellingPrice calculationBase; SellingPrice appliedDiscount;
}
