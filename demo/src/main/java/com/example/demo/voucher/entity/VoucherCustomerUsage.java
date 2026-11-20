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

@Entity @Table(name = "voucher_customer_usages") @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VoucherCustomerUsage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "voucher_id") private Long voucherId;
    @Column(name = "customer_id") private Long customerId;
    @Column(name = "redeemed_count") private long redeemedCount;
}
