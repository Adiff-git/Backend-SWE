package com.example.demo.voucher.repository;
import com.example.demo.voucher.entity.VoucherCustomerUsage;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
public interface VoucherCustomerUsageRepository extends Repository<VoucherCustomerUsage, Long> {
    interface UsageRow { Long getId(); long getRedeemedCount(); }
    @Query(value = "SELECT id,redeemed_count AS redeemedCount FROM voucher_customer_usages WHERE voucher_id=:voucher AND customer_id=:owner", nativeQuery = true)
    Optional<UsageRow> findUsage(long voucher, long owner);
    @Query(value = "SELECT id,redeemed_count AS redeemedCount FROM voucher_customer_usages WHERE voucher_id=:voucher AND customer_id=:owner FOR UPDATE", nativeQuery = true)
    Optional<UsageRow> lockUsage(long voucher, long owner);
}
