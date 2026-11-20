package com.example.demo.cart.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "carts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cart {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "voucher_entitlement_id") private Long voucherEntitlementId;
    @Column(name = "voucher_selection_revision", nullable = false) private long voucherSelectionRevision;
    public void selectVoucher(Long grant) {
        try { voucherSelectionRevision = Math.incrementExact(voucherSelectionRevision); }
        catch (ArithmeticException overflow) { throw new com.example.demo.cart.exception.CartConflictException(); }
        voucherEntitlementId = grant;
    }
    public Cart(Long userId) { this.userId = userId; }
}
