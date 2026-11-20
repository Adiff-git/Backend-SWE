package com.example.demo.cart.repository;

import com.example.demo.cart.entity.Cart;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartRepository extends JpaRepository<Cart, Long> {
    Optional<Cart> findByUserId(Long userId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Cart c WHERE c.userId = :owner")
    Optional<Cart> findOwnedForUpdate(@Param("owner") Long owner);
    @Query(value = "SELECT set_config('lock_timeout', :timeout, TRUE)", nativeQuery = true)
    String setLockTimeout(@Param("timeout") String timeout);
    @org.springframework.data.jpa.repository.Modifying
    @Query(value="UPDATE carts SET voucher_entitlement_id=NULL,voucher_selection_revision=voucher_selection_revision+1 WHERE id=:cart AND user_id=:owner AND voucher_entitlement_id=:grant AND voucher_selection_revision=:revision AND voucher_selection_revision<9223372036854775807",nativeQuery=true)
    int clearCheckoutVoucher(Long owner, Long cart, Long grant, long revision);
}
