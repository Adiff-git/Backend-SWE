package com.example.demo.checkout.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "checkout_receipts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CheckoutReceipt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, updatable = false) private Long customerId;
    @Column(nullable = false, updatable = false) private String operation;
    @Column(nullable = false, updatable = false, length = 128) private String idempotencyKey;
    @Column(nullable = false, updatable = false) private Long requestAddressId;
    @Column(nullable = false, updatable = false) private String requestFingerprint;
    @Column(nullable = false, updatable = false) private Long orderId;
    @Column(nullable = false, updatable = false) private int receiptVersion;
    @Column(nullable = false, updatable = false) private int responseStatus;
    @Column(nullable = false, updatable = false) private String location;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false, columnDefinition = "JSONB") private String receiptBody;
    @Column(nullable = false, updatable = false) private Instant createdAt;
}
