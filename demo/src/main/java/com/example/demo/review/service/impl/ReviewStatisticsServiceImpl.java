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
        java.util.Map<String,Long> distribution=new java.util.LinkedHashMap<>();
        for(int rating=1;rating<=5;rating++) distribution.put(Integer.toString(rating),0L);
        for(var row:reviews.countRatings(product)) distribution.put(Integer.toString(row.getRating()),row.getRatingCount());
        return new ProductRatingSummary(aggregate.getReviewCount(),average,java.util.Collections.unmodifiableMap(distribution));
    }
}
