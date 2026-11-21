package com.example.demo.review.controller;

import com.example.demo.common.identity.CurrentUserIdProvider;
import com.example.demo.review.dto.request.CreateReviewRequest;
import com.example.demo.review.dto.response.ReviewResponse;
import com.example.demo.review.service.ReviewService;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequiredArgsConstructor
public class OrderItemReviewController {
    private final ReviewService reviews;
    private final CurrentUserIdProvider identity;
    @PostMapping("/api/me/orders/{orderId}/items/{orderItemId}/reviews")
    public ResponseEntity<ReviewResponse> create(@PathVariable String orderId, @PathVariable String orderItemId, @Valid @RequestBody CreateReviewRequest request) {
        ReviewResponse result = reviews.create(identity.getCurrentUserId(), ReviewRequestReader.positiveId(orderId), ReviewRequestReader.positiveId(orderItemId), request);
        return ResponseEntity.created(URI.create("/api/me/reviews/" + result.getId())).cacheControl(CacheControl.noStore()).body(result);
    }
}
