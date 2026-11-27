package com.example.demo.review.repository;
import com.example.demo.review.dto.request.ReviewListRequest;
import com.example.demo.review.entity.ProductReview;
import java.util.List;
import java.util.UUID;
public interface ProductReviewQueryRepository {
    long countMatching(UUID product, ReviewListRequest request);
    List<ProductReview> findMatching(UUID product, ReviewListRequest request);
}
