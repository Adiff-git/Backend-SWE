package com.example.demo.voucher.repository;
import com.example.demo.voucher.entity.VoucherEntitlement;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
public interface VoucherEntitlementRepository extends Repository<VoucherEntitlement, Long> {
    interface GrantRow { Long getId(); Long getVoucherId(); Long getCustomerId(); Long getVoucherCustomerUsageId(); String getState(); Instant getGrantedAt(); Instant getConsumedAt(); }
    String PROJECTION = "SELECT id,voucher_id AS voucherId,customer_id AS customerId,voucher_customer_usage_id AS voucherCustomerUsageId,state,granted_at AS grantedAt,consumed_at AS consumedAt FROM voucher_entitlements WHERE customer_id=:owner AND id=:id";
    @Query(value = PROJECTION, nativeQuery = true) Optional<GrantRow> findOwned(long owner, long id);
    @Query(value = PROJECTION + " FOR UPDATE", nativeQuery = true) Optional<GrantRow> lockOwned(long owner, long id);
}
