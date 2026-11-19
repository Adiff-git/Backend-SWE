package com.example.demo.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "orders")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, updatable = false)
    private Long customerId;
    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String orderNumber;
    @Column(nullable = false, updatable = false)
    private Instant placedAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false, columnDefinition = "TEXT")
    private OrderStatus status;
    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String recipientNameSnapshot;
    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String recipientPhoneSnapshot;
    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String deliveryAddressSnapshot;
    @Column(nullable = false, updatable = false, columnDefinition = "NUMERIC")
    private BigDecimal merchandiseTotal;
    @Column(nullable = false, updatable = false, columnDefinition = "NUMERIC")
    private BigDecimal payableTotal;
    private Instant cancelledAt;
    @Column(updatable = false)
    private Instant completedAt;
    @Column(columnDefinition = "TEXT")
    private String cancellationReason;

    /** Called only after the owning service validates the locked persisted state. */
    public void cancel(Instant at, String reason) {
        status = OrderStatus.CANCELLED;
        cancelledAt = at;
        cancellationReason = reason;
    }
}
