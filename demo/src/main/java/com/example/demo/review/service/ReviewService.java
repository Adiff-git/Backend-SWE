package com.example.demo.review.service;

import com.example.demo.review.dto.request.CreateReviewRequest;
import com.example.demo.review.dto.response.ReviewResponse;

public interface ReviewService {
    ReviewResponse update(Long actor, Long review, com.example.demo.review.dto.request.PatchReviewRequest request);
    ReviewResponse create(Long actor, Long order, Long item, CreateReviewRequest request);
}
