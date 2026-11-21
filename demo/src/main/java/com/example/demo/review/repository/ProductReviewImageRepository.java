package com.example.demo.review.repository;

import com.example.demo.review.entity.ProductReviewImage;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductReviewImageRepository extends JpaRepository<ProductReviewImage, Long> {
    List<ProductReviewImage> findByReviewIdInOrderByReviewIdAscDisplayOrderAsc(Collection<Long> ids);
    List<ProductReviewImage> findByReviewIdOrderByDisplayOrderAsc(Long id);
    void deleteByReviewId(Long id);
}
