package com.example.demo.cart.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "cart_items")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    private Long id;
    @Column(name = "cart_id", nullable = false, updatable = false)
    private Long cartId;
    @Column(name = "product_id", nullable = false)
    private UUID productId;
    @Column(name = "product_variant_id", nullable = false)
    private UUID productVariantId;
    @Column(nullable = false)
    private int quantity;
    @Column(nullable = false)
    private boolean selected;
    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;
    @Column(name = "public_product_name", nullable = false, columnDefinition = "text")
    private String publicProductName;
    @Column(name = "public_sku", nullable = false, columnDefinition = "text")
    private String publicSku;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "public_options", nullable = false, columnDefinition = "jsonb")
    private Map<String, String> publicOptions;

    public CartItem(Long cartId, Instant addedAt) {
        this.cartId = cartId;
        this.addedAt = addedAt;
        this.selected = true;
    }
}
