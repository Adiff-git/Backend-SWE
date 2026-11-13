package com.example.demo.cart.repository;

import com.example.demo.cart.entity.CartItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    List<CartItem> findByCartIdOrderByAddedAtDescIdAsc(Long cartId);
    Optional<CartItem> findByIdAndCartId(Long id, Long cartId);
    @Query("SELECT i.productVariantId FROM CartItem i, Cart c WHERE i.cartId = c.id AND c.userId = :owner")
    List<UUID> findVariantIdsForOwner(@Param("owner") Long owner);
    @Query("SELECT i.productVariantId FROM CartItem i, Cart c WHERE i.cartId = c.id AND c.userId = :owner AND i.id = :item")
    Optional<UUID> findOwnedVariantId(@Param("owner") Long owner, @Param("item") Long item);
}
