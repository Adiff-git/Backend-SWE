package com.example.demo.checkout.repository;

import com.example.demo.checkout.entity.CheckoutReceipt;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Successful immutable receipts only; no reservation/update/delete operation. */
public interface CheckoutReceiptRepository extends Repository<CheckoutReceipt, Long> {
    Optional<CheckoutReceipt> findByCustomerIdAndOperationAndIdempotencyKey(Long customerId, String operation, String idempotencyKey);

    @Query(value = "SELECT pg_try_advisory_xact_lock(:key)", nativeQuery = true)
    boolean tryFence(@Param("key") long key);

    @Query(value = "SELECT set_config('lock_timeout', :timeout, TRUE)", nativeQuery = true)
    String setLockTimeout(@Param("timeout") String timeout);

    @Modifying
    @Query(value = "INSERT INTO checkout_receipts(customer_id,operation,idempotency_key,request_address_id,request_fingerprint,order_id,receipt_version,response_status,location,receipt_body,created_at) VALUES (:owner,'CHECKOUT',:key,:address,:fingerprint,:order,3,201,:location,CAST(:body AS JSONB),:at)", nativeQuery = true)
    void insertSuccess(@Param("owner") Long owner, @Param("key") String key, @Param("address") Long address,
            @Param("fingerprint") String fingerprint, @Param("order") Long order, @Param("location") String location,
            @Param("body") String body, @Param("at") Instant at);
}
