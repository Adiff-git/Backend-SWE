package com.example.demo.order.repository;

import com.example.demo.order.dto.response.OrderReadSeed;
import com.example.demo.order.entity.Order;
import com.example.demo.order.entity.OrderStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order,Long>, OrderPageRepository, OrderCreationRepository {
    String HEADER = "SELECT new com.example.demo.order.dto.response.OrderReadSeed$Header(o.id,o.orderNumber,o.placedAt,o.status,o.payableTotal,o.merchandiseTotal,o.recipientNameSnapshot,o.recipientPhoneSnapshot,o.deliveryAddressSnapshot,o.cancelledAt,o.completedAt,o.cancellationReason) FROM Order o ";
    @Query("SELECT COUNT(o) FROM Order o WHERE o.customerId=:owner AND (:status IS NULL OR o.status=:status)")
    long countOwnedOrders(@Param("owner") Long owner, @Param("status") OrderStatus status);

    @Query(HEADER + "WHERE o.customerId=:owner AND (:status IS NULL OR o.status=:status)")
    List<OrderReadSeed.Header> findOwnedHeaders(@Param("owner") Long owner, @Param("status") OrderStatus status, Pageable pageable);

    @Query(HEADER + "WHERE o.customerId=:owner AND o.id=:id")
    Optional<OrderReadSeed.Header> findOwnedHeader(@Param("owner") Long owner, @Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.customerId=:owner AND o.id=:id")
    Optional<Order> findOwnedForUpdate(@Param("owner") Long owner, @Param("id") Long id);

    @Query(value = "SELECT set_config('lock_timeout', :duration, TRUE)", nativeQuery = true)
    String setLockTimeout(@Param("duration") String duration);
}
