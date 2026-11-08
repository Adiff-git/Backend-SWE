package com.example.demo.wishlist.repository;

import com.example.demo.wishlist.entity.WishlistItem;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface WishlistRepository extends JpaRepository<WishlistItem, Long> {
    @Modifying
    @Query(value = "INSERT INTO wishlist_items(user_id, product_id, added_at) VALUES (:owner, :product, :addedAt) "
            + "ON CONFLICT ON CONSTRAINT uk_wishlist_items_user_id_product_id DO NOTHING", nativeQuery = true)
    int insertIfAbsent(@Param("owner") Long owner, @Param("product") UUID product, @Param("addedAt") Instant addedAt);

    @Query("SELECT w.addedAt FROM WishlistItem w WHERE w.user.id = :owner AND w.product.id = :product")
    Optional<Instant> findAddedAt(@Param("owner") Long owner, @Param("product") UUID product);

    @Query(value = "SELECT w.product.id AS productId, w.addedAt AS addedAt FROM WishlistItem w "
            + "WHERE w.user.id = :owner AND w.product.status = com.example.demo.catalog.entity.ProductStatus.PUBLISHED "
            + "ORDER BY w.addedAt DESC, w.product.id ASC",
            countQuery = "SELECT COUNT(w) FROM WishlistItem w WHERE w.user.id = :owner "
            + "AND w.product.status = com.example.demo.catalog.entity.ProductStatus.PUBLISHED")
    Page<WishlistMembershipView> findVisibleMemberships(@Param("owner") Long owner, Pageable pageable);

    @Modifying
    @Query("DELETE FROM WishlistItem w WHERE w.user.id = :owner AND w.product.id = :product")
    int deleteOwned(@Param("owner") Long owner, @Param("product") UUID product);
}
