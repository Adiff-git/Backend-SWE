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
}
