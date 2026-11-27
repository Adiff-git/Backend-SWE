package com.example.demo.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity @Table(name = "order_items") @Getter @Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private Order order;
    @Column(updatable = false)
    private UUID productId;
    @Column(updatable = false)
    private UUID productVariantId;
    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String productNameSnapshot;
    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String variantSkuSnapshot;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false, columnDefinition = "JSONB")
    private Map<String, String> variantOptionsSnapshot;
    @Column(nullable = false, updatable = false)
    private Integer quantity;
    @Column(nullable = false, updatable = false, columnDefinition = "NUMERIC")
    private BigDecimal unitPriceSnapshot;
    @Column(nullable = false, updatable = false, columnDefinition = "NUMERIC")
    private BigDecimal lineTotal;
    @Column(updatable = false, columnDefinition = "NUMERIC")
    private BigDecimal originalUnitPriceSnapshot;
    @Column(updatable = false, columnDefinition = "NUMERIC")
    private BigDecimal directUnitDiscountSnapshot;
    @Column(updatable = false, columnDefinition = "NUMERIC")
    private BigDecimal directSalePercentageSnapshot;
    @Column(updatable = false)
    private java.time.Instant directSaleStartsAtSnapshot;
    @Column(updatable = false)
    private java.time.Instant directSaleEndsAtSnapshot;
    @Column(updatable = false)
    private java.time.Instant pricingInstantSnapshot;
}
