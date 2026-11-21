package com.example.demo.review.controller;

import com.example.demo.media.validator.ImageUploadValidator;
import com.example.demo.review.dto.request.ReviewListRequest;
import com.example.demo.review.dto.response.ProductRatingSummary;
import com.example.demo.review.dto.response.ReviewPage;
import com.example.demo.review.exception.InvalidReviewRequestException;
import com.example.demo.review.service.ReviewReadService;
import com.example.demo.review.service.ReviewStatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequiredArgsConstructor
public class ProductReviewController {
    private final ReviewReadService reviews;
    private final ReviewStatisticsService statistics;
    @GetMapping("/api/products/{productId}/reviews")
    public ResponseEntity<ReviewPage> list(@PathVariable String productId, @RequestParam MultiValueMap<String, String> parameters) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reviews.getReviews(ImageUploadValidator.parseId(productId), ReviewListRequest.parse(parameters)));
    }
    @GetMapping("/api/products/{productId}/rating-summary")
    public ResponseEntity<ProductRatingSummary> summary(@PathVariable String productId, @RequestParam MultiValueMap<String, String> parameters) {
        if (!parameters.isEmpty()) throw new InvalidReviewRequestException();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(statistics.getSummary(ImageUploadValidator.parseId(productId)));
    }
}
