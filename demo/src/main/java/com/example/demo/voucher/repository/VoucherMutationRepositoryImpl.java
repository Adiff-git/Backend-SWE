package com.example.demo.voucher.repository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
@RequiredArgsConstructor
public class VoucherMutationRepositoryImpl implements VoucherMutationRepository {
    private final EntityManager entities;
    @Override public int incrementGlobal(long voucher) {
        return entities.createNativeQuery("UPDATE vouchers SET redeemed_count=redeemed_count+1 WHERE id=:id AND redeemed_count<9223372036854775807 AND (global_usage_limit IS NULL OR redeemed_count<global_usage_limit)").setParameter("id", voucher).executeUpdate();
    }
    @Override public int incrementCustomer(long usage, long voucher, long owner) {
        return entities.createNativeQuery("UPDATE voucher_customer_usages u SET redeemed_count=u.redeemed_count+1 FROM vouchers v WHERE u.id=:id AND u.voucher_id=:voucher AND u.customer_id=:owner AND v.id=u.voucher_id AND u.redeemed_count<9223372036854775807 AND (v.per_customer_usage_limit IS NULL OR u.redeemed_count<v.per_customer_usage_limit)")
            .setParameter("id", usage).setParameter("voucher", voucher).setParameter("owner", owner).executeUpdate();
    }
    @Override public int consumeEntitlement(long grant, long voucher, long owner, Instant event) {
        return entities.createNativeQuery("UPDATE voucher_entitlements SET state='CONSUMED',consumed_at=:event WHERE id=:id AND voucher_id=:voucher AND customer_id=:owner AND state='AVAILABLE' AND granted_at<=:event")
            .setParameter("id", grant).setParameter("voucher", voucher).setParameter("owner", owner).setParameter("event", event).executeUpdate();
    }
    @Override public int insertRedemption(com.example.demo.voucher.dto.response.CheckoutVoucherQuote quote, long orderId) {
        var v=quote.getSnapshot();
        return entities.createNativeQuery("INSERT INTO voucher_redemptions(voucher_id,voucher_entitlement_id,voucher_customer_usage_id,customer_id,order_id,redeemed_at,code_snapshot,title_snapshot,type_snapshot,value_snapshot,minimum_subtotal_snapshot,maximum_discount_snapshot,calculation_base,applied_discount) VALUES (:v,:g,:u,:c,:o,:at,:code,:title,:type,:value,:minimum,:cap,:base,:discount)")
            .setParameter("v",v.getVoucherId()).setParameter("g",v.getWalletEntitlementId()).setParameter("u",quote.getUsageId()).setParameter("c",quote.getOwnerId()).setParameter("o",orderId)
            .setParameter("at",quote.getEvent()).setParameter("code",v.getCode()).setParameter("title",v.getTitle()).setParameter("type",v.getDiscountType()).setParameter("value",v.getDiscountValue())
            .setParameter("minimum",new java.math.BigDecimal(v.getMinimumSubtotal().getAmount())).setParameter("cap",v.getMaximumDiscount()==null ? null : new java.math.BigDecimal(v.getMaximumDiscount().getAmount()))
            .setParameter("base",new java.math.BigDecimal(quote.getBase())).setParameter("discount",new java.math.BigDecimal(quote.getDiscount())).executeUpdate();
    }
}
