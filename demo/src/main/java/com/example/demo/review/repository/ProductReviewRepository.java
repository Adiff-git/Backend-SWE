package com.example.demo.review.repository;

import com.example.demo.review.entity.ProductReview;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ProductReviewRepository extends JpaRepository<ProductReview, Long> {
    org.springframework.data.domain.Page<ProductReview> findByProductId(java.util.UUID productId, org.springframework.data.domain.Pageable pageable);
    long countByProductId(java.util.UUID productId);
    @Query(value = "SELECT COUNT(*) AS reviewCount, AVG(rating::NUMERIC) AS averageRating FROM product_reviews WHERE product_id=:product", nativeQuery = true)
    RatingAggregate aggregate(java.util.UUID product);
    interface RatingAggregate {
        long getReviewCount();
        java.math.BigDecimal getAverageRating();
    }
    boolean existsByOrderItemId(Long item);
    Optional<ProductReview> findByIdAndAuthorId(Long id, Long author);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ProductReview r WHERE r.id=:id AND r.authorId=:author")
    Optional<ProductReview> findOwnedForUpdate(Long id, Long author);
}
