package com.example.demo.review.service.impl;

import com.example.demo.catalog.service.ProductCatalogService;
import com.example.demo.media.dto.response.ImageAccessResponse;
import com.example.demo.media.service.MediaService;
import com.example.demo.review.converter.ReviewConverter;
import com.example.demo.review.dto.request.ReviewListRequest;
import com.example.demo.review.dto.response.ReviewPage;
import com.example.demo.review.dto.response.ReviewResponse;
import com.example.demo.review.entity.ProductReview;
import com.example.demo.review.entity.ProductReviewImage;
import com.example.demo.review.repository.ProductReviewImageRepository;
import com.example.demo.review.repository.ProductReviewRepository;
import com.example.demo.review.service.ReviewReadService;
import com.example.demo.user.dto.response.ReviewAuthorSummary;
import com.example.demo.user.service.UserReviewReadService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class ReviewReadServiceImpl implements ReviewReadService {
    private final ProductReviewRepository reviews;
    private final ProductReviewImageRepository references;
    private final ProductCatalogService catalog;
    private final UserReviewReadService users;
    private final MediaService media;
    private final ReviewConverter converter;
    private final com.example.demo.order.service.OrderReviewReadService orders;
    @Override @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ReviewPage getReviews(UUID product, ReviewListRequest request) {
        catalog.getPublishedProductEntity(product);
        long count = reviews.countMatching(product,request);
        long pages = count / request.getSize() + (count % request.getSize() == 0 ? 0 : 1);
        if (request.pageable().getOffset() >= count) return new ReviewPage(List.of(), request.getPage(), request.getSize(), count, pages);
        List<ProductReview> rows = reviews.findMatching(product,request);
        var purchased=orders.getReviewOptionSnapshots(rows.stream().map(ProductReview::getOrderItemId).toList());
        List<Long> ids = rows.stream().map(ProductReview::getId).toList();
        Map<Long, List<UUID>> ordered = new HashMap<>();
        List<ProductReviewImage> imageRows = ids.isEmpty() ? List.of() : references.findByReviewIdInOrderByReviewIdAscDisplayOrderAsc(ids);
        for (ProductReviewImage image : imageRows) ordered.computeIfAbsent(image.getReviewId(), ignored -> new ArrayList<>()).add(image.getImageId());
        Map<Long, ReviewAuthorSummary> authors = users.getReviewAuthors(rows.stream().map(ProductReview::getAuthorId).distinct().toList());
        java.util.Set<UUID> imageIds = new java.util.LinkedHashSet<>(imageRows.stream().map(ProductReviewImage::getImageId).toList());
        authors.values().stream().map(ReviewAuthorSummary::getProfileImage).filter(java.util.Objects::nonNull).forEach(avatar -> imageIds.add(avatar.getId()));
        Map<UUID, ImageAccessResponse> images = media.getSafeAttachedImages(imageIds);
        List<ReviewResponse> items = rows.stream().map(row -> converter.toResponse(row, ordered.getOrDefault(row.getId(), List.of()), images, authors.get(row.getAuthorId()),purchased.get(row.getOrderItemId()))).toList();
        return new ReviewPage(items, request.getPage(), request.getSize(), count, pages);
    }
}
