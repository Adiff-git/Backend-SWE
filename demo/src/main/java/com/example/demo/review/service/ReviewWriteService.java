package com.example.demo.review.service;

import com.example.demo.media.dto.response.PreparedReviewImages;
import com.example.demo.review.dto.request.CreateReviewRequest;
import com.example.demo.review.dto.response.CommittedReview;

public interface ReviewWriteService {
    CommittedReview update(Long actor, Long review, com.example.demo.review.dto.request.PatchReviewRequest request, PreparedReviewImages prepared);
    CommittedReview create(Long actor, Long order, Long item, CreateReviewRequest request, PreparedReviewImages prepared);
}
