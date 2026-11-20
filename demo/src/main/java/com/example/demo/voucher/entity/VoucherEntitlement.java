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
import java.time.Instant;

@Entity @Table(name = "voucher_entitlements") @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VoucherEntitlement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "voucher_customer_usage_id") private Long voucherCustomerUsageId;
    @Column(name = "voucher_id") private Long voucherId;
    @Column(name = "customer_id") private Long customerId;
    @Column(name = "granted_at") private Instant grantedAt;
    @Column(name = "state") @Enumerated(EnumType.STRING) private VoucherEntitlementState state;
    @Column(name = "consumed_at") private Instant consumedAt;
}
