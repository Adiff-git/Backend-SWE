package com.example.demo.review.service.impl;

import com.example.demo.media.dto.response.PreparedReviewImages;
import com.example.demo.media.service.MediaService;
import com.example.demo.review.converter.ReviewConverter;
import com.example.demo.review.dto.request.CreateReviewRequest;
import com.example.demo.review.dto.response.CommittedReview;
import com.example.demo.review.dto.response.ReviewResponse;
import com.example.demo.review.service.ReviewService;
import com.example.demo.review.service.ReviewWriteService;
import com.example.demo.review.validator.ReviewValidator;
import com.example.demo.user.service.UserReviewReadService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {
    private final ReviewWriteService writer;
    private final ReviewValidator validator;
    private final com.example.demo.review.validator.PatchReviewValidator patchValidator;
    private final MediaService media;
    private final UserReviewReadService authors;
    private final ReviewConverter converter;
    @Override public ReviewResponse update(Long actor, Long review, com.example.demo.review.dto.request.PatchReviewRequest request) {
        requireEntryBoundary();
        patchValidator.validate(request);
        PreparedReviewImages prepared = request.isImageIdsPresent() && !request.getImageIds().isEmpty()
                ? media.prepareReviewImages(actor, request.getImageIds()) : new PreparedReviewImages(java.util.Map.of());
        return response(writer.update(actor, review, request, prepared));
    }
    @Override public ReviewResponse create(Long actor, Long order, Long item, CreateReviewRequest request) {
        requireEntryBoundary();
        validator.validate(request);
        PreparedReviewImages prepared = media.prepareReviewImages(actor, request.getImageIds());
        return response(writer.create(actor, order, item, request, prepared));
    }
    private ReviewResponse response(CommittedReview result) {
        com.example.demo.user.dto.response.ReviewAuthorSummary author = authors.getReviewAuthors(List.of(result.getReview().getAuthorId())).get(result.getReview().getAuthorId());
        java.util.Set<java.util.UUID> ids = new java.util.LinkedHashSet<>(result.getImageIds());
        if (author != null && author.getProfileImage() != null) ids.add(author.getProfileImage().getId());
        return converter.toResponse(result.getReview(), result.getImageIds(), media.getSafeAttachedImages(ids), author);
    }
    private static void requireEntryBoundary() {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Review orchestration must prepare media before entering its writer transaction");
    }
}
