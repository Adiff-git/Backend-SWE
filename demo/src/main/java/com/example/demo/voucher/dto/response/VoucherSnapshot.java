package com.example.demo.voucher.dto.response;
import com.example.demo.catalog.dto.response.SellingPrice;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Value;
@Value
public class VoucherSnapshot {
    Long walletEntitlementId; Long voucherId; String code; String title; String discountType;
    BigDecimal discountValue; SellingPrice minimumSubtotal; SellingPrice maximumDiscount;
    SellingPrice calculationBase; SellingPrice appliedDiscount;
    @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING, pattern="yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", timezone="UTC")
    Instant redeemedAt;
    @com.fasterxml.jackson.annotation.JsonCreator
    public VoucherSnapshot(@com.fasterxml.jackson.annotation.JsonProperty("walletEntitlementId") Long walletEntitlementId,
            @com.fasterxml.jackson.annotation.JsonProperty("voucherId") Long voucherId,
            @com.fasterxml.jackson.annotation.JsonProperty("code") String code,
            @com.fasterxml.jackson.annotation.JsonProperty("title") String title,
            @com.fasterxml.jackson.annotation.JsonProperty("discountType") String discountType,
            @com.fasterxml.jackson.annotation.JsonProperty("discountValue") BigDecimal discountValue,
            @com.fasterxml.jackson.annotation.JsonProperty("minimumSubtotal") SellingPrice minimumSubtotal,
            @com.fasterxml.jackson.annotation.JsonProperty("maximumDiscount") SellingPrice maximumDiscount,
            @com.fasterxml.jackson.annotation.JsonProperty("calculationBase") SellingPrice calculationBase,
            @com.fasterxml.jackson.annotation.JsonProperty("appliedDiscount") SellingPrice appliedDiscount,
            @com.fasterxml.jackson.annotation.JsonProperty("redeemedAt") Instant redeemedAt) {
        this.walletEntitlementId=walletEntitlementId;this.voucherId=voucherId;this.code=code;this.title=title;
        this.discountType=discountType;this.discountValue=discountValue;this.minimumSubtotal=minimumSubtotal;
        this.maximumDiscount=maximumDiscount;this.calculationBase=calculationBase;this.appliedDiscount=appliedDiscount;this.redeemedAt=redeemedAt;
    }
}
