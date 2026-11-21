package com.example.demo.review.service.impl;

import com.example.demo.catalog.service.ProductCatalogService;
import com.example.demo.review.dto.response.ProductRatingSummary;
import com.example.demo.review.repository.ProductReviewRepository;
import com.example.demo.review.service.ReviewStatisticsService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class ReviewStatisticsServiceImpl implements ReviewStatisticsService {
    private final ProductReviewRepository reviews;
    private final ProductCatalogService catalog;
    @Override @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ProductRatingSummary getSummary(UUID product) {
        catalog.getPublishedProductEntity(product);
        ProductReviewRepository.RatingAggregate aggregate = reviews.aggregate(product);
        BigDecimal average = aggregate.getReviewCount() == 0 ? null : aggregate.getAverageRating().setScale(2, RoundingMode.HALF_UP);
        return new ProductRatingSummary(aggregate.getReviewCount(), average);
    }
}
