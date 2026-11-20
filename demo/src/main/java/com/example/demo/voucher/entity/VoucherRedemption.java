package com.example.demo.voucher.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;

@Entity @Table(name = "voucher_redemptions") @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VoucherRedemption {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "voucher_id") private Long voucherId;
    @Column(name = "voucher_entitlement_id") private Long voucherEntitlementId;
    @Column(name = "voucher_customer_usage_id") private Long voucherCustomerUsageId;
    @Column(name = "customer_id") private Long customerId;
    @Column(name = "order_id") private Long orderId;
    @Column(name = "redeemed_at") private Instant redeemedAt;
    @Column(name = "code_snapshot") private String codeSnapshot;
    @Column(name = "title_snapshot") private String titleSnapshot;
    @Column(name = "type_snapshot") private String typeSnapshot;
    @Column(name = "value_snapshot") private BigDecimal valueSnapshot;
    @Column(name = "minimum_subtotal_snapshot") private BigDecimal minimumSubtotalSnapshot;
    @Column(name = "maximum_discount_snapshot") private BigDecimal maximumDiscountSnapshot;
    @Column(name = "calculation_base") private BigDecimal calculationBase;
    @Column(name = "applied_discount") private BigDecimal appliedDiscount;
}
