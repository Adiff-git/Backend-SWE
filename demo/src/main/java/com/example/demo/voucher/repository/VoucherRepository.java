package com.example.demo.voucher.repository;
import com.example.demo.voucher.entity.Voucher;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
public interface VoucherRepository extends Repository<Voucher, Long>, VoucherMutationRepository {
    interface DefinitionRow {
        Long getId(); String getCode(); String getTitle(); String getDescription();
        String getDiscountType(); BigDecimal getDiscountValue(); BigDecimal getMinimumSubtotal(); BigDecimal getMaximumDiscount();
        Instant getValidFrom(); Instant getValidUntil(); boolean getEnabled(); Long getGlobalUsageLimit(); Long getPerCustomerUsageLimit(); long getRedeemedCount();
    }
    String PROJECTION = "SELECT id,code,title,description,discount_type AS discountType,discount_value AS discountValue,minimum_subtotal AS minimumSubtotal,maximum_discount AS maximumDiscount,valid_from AS validFrom,valid_until AS validUntil,enabled,global_usage_limit AS globalUsageLimit,per_customer_usage_limit AS perCustomerUsageLimit,redeemed_count AS redeemedCount FROM vouchers WHERE id=:id";
    @Query(value = PROJECTION, nativeQuery = true) Optional<DefinitionRow> findDefinition(long id);
    @Query(value = PROJECTION + " FOR UPDATE", nativeQuery = true) Optional<DefinitionRow> lockDefinition(long id);

    interface WalletRow extends DefinitionRow {
        Long getWalletEntitlementId(); String getEntitlementState(); Instant getGrantedAt(); Instant getConsumedAt(); long getCustomerRedeemedCount();
    }
    String WALLET="SELECT v.id,v.code,v.title,v.description,v.discount_type AS discountType,v.discount_value AS discountValue,v.minimum_subtotal AS minimumSubtotal,v.maximum_discount AS maximumDiscount,v.valid_from AS validFrom,v.valid_until AS validUntil,v.enabled,v.global_usage_limit AS globalUsageLimit,v.per_customer_usage_limit AS perCustomerUsageLimit,v.redeemed_count AS redeemedCount,e.id AS walletEntitlementId,e.state AS entitlementState,e.granted_at AS grantedAt,e.consumed_at AS consumedAt,u.redeemed_count AS customerRedeemedCount FROM voucher_entitlements e JOIN vouchers v ON v.id=e.voucher_id JOIN voucher_customer_usages u ON u.id=e.voucher_customer_usage_id WHERE e.customer_id=:owner";
    @Query(value="SELECT COUNT(*) FROM voucher_entitlements WHERE customer_id=:owner AND state='AVAILABLE'",nativeQuery=true)
    long countWallet(long owner);
    @Query(value=WALLET+" AND e.state='AVAILABLE' ORDER BY v.code ASC,e.id ASC LIMIT :size OFFSET :offset",nativeQuery=true)
    java.util.List<WalletRow> findWalletPage(long owner,long offset,int size);
    @Query(value=WALLET+" AND e.id=:grant",nativeQuery=true)
    Optional<WalletRow> findWalletDetail(long owner,long grant);
}
