package com.example.demo.review.controller;

import com.example.demo.common.identity.CurrentUserIdProvider;
import com.example.demo.review.dto.request.PatchReviewRequest;
import com.example.demo.review.dto.response.ReviewResponse;
import com.example.demo.review.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequiredArgsConstructor
public class ReviewController {
    private final ReviewService reviews;
    private final CurrentUserIdProvider identity;
    @PatchMapping("/api/me/reviews/{reviewId}")
    public ResponseEntity<ReviewResponse> update(@PathVariable String reviewId, @Valid @RequestBody PatchReviewRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reviews.update(identity.getCurrentUserId(), ReviewRequestReader.positiveId(reviewId), request));
    }
}
