package com.example.demo.voucher.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
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

@Entity @Table(name = "vouchers") @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Voucher {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "code") private String code;
    @Column(name = "title") private String title;
    @Column(name = "description") private String description;
    @Column(name = "discount_type") @Enumerated(EnumType.STRING) private VoucherDiscountType discountType;
    @Column(name = "discount_value") private BigDecimal discountValue;
    @Column(name = "minimum_subtotal") private BigDecimal minimumSubtotal;
    @Column(name = "maximum_discount") private BigDecimal maximumDiscount;
    @Column(name = "valid_from") private Instant validFrom;
    @Column(name = "valid_until") private Instant validUntil;
    @Column(name = "enabled") private boolean enabled;
    @Column(name = "global_usage_limit") private Long globalUsageLimit;
    @Column(name = "per_customer_usage_limit") private Long perCustomerUsageLimit;
    @Column(name = "redeemed_count") private long redeemedCount;
}
