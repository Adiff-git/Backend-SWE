package com.example.demo.review.service.impl;

import com.example.demo.common.identity.IdentityUnavailableException;
import com.example.demo.media.dto.response.PreparedReviewImages;
import com.example.demo.media.service.MediaService;
import com.example.demo.order.dto.response.ReviewPurchaseSeed;
import com.example.demo.order.service.OrderReviewReadService;
import com.example.demo.review.dto.request.CreateReviewRequest;
import com.example.demo.review.dto.response.CommittedReview;
import com.example.demo.review.entity.ProductReview;
import com.example.demo.review.entity.ProductReviewImage;
import com.example.demo.review.exception.ReviewConflictException;
import com.example.demo.review.repository.ProductReviewImageRepository;
import com.example.demo.review.repository.ProductReviewRepository;
import com.example.demo.user.enums.AccountStatus;
import com.example.demo.user.service.UserService;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class ReviewWriteServiceImpl implements com.example.demo.review.service.ReviewWriteService {
    private final ProductReviewRepository reviews;
    private final ProductReviewImageRepository images;
    private final UserService users;
    private final OrderReviewReadService orders;
    private final MediaService media;
    private final Clock clock;

    @Override @Transactional
    public CommittedReview update(Long actor, Long id, com.example.demo.review.dto.request.PatchReviewRequest request, PreparedReviewImages prepared) {
        if (users.getUserEntityForUpdate(actor).getAccountStatus() != AccountStatus.ACTIVE) throw new IdentityUnavailableException();
        ProductReview identity = reviews.findByIdAndAuthorId(id, actor).orElseThrow(com.example.demo.review.exception.ReviewNotFoundException::new);
        Long order = orders.getReviewOrderId(actor, identity.getOrderItemId());
        orders.getReviewPurchase(actor, order, identity.getOrderItemId(), false);
        ProductReview review = reviews.findOwnedForUpdate(id, actor).orElseThrow(com.example.demo.review.exception.ReviewNotFoundException::new);
        List<UUID> previous = images.findByReviewIdOrderByDisplayOrderAsc(id).stream().map(ProductReviewImage::getImageId).toList();
        List<UUID> next = request.isImageIdsPresent() ? request.getImageIds() : previous;
        int rating = request.isRatingPresent() ? request.getRating() : review.getRating();
        String comment = request.isCommentPresent() ? request.getComment() : review.getComment();
        if (request.isImageIdsPresent()) media.validatePreparedReviewImages(actor, prepared);
        if (rating != review.getRating() || !java.util.Objects.equals(comment, review.getComment()) || !previous.equals(next)) {
            java.time.Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
            if (!now.isAfter(review.getUpdatedAt())) now = review.getUpdatedAt().plus(1, ChronoUnit.MICROS);
            review.edit(rating, comment, now);
            if (!previous.equals(next)) replaceImages(id, next);
            reviews.flush();
        }
        return new CommittedReview(review, List.copyOf(next));
    }

    @Override @Transactional
    public CommittedReview create(Long actor, Long order, Long item, CreateReviewRequest request, PreparedReviewImages prepared) {
        if (users.getUserEntityForUpdate(actor).getAccountStatus() != AccountStatus.ACTIVE) throw new IdentityUnavailableException();
        ReviewPurchaseSeed proof = orders.getReviewPurchase(actor, order, item, true);
        if (reviews.existsByOrderItemId(item)) throw new ReviewConflictException();
        media.validatePreparedReviewImages(actor, prepared);
        ProductReview review = reviews.saveAndFlush(new ProductReview(proof.getOrderItemId(), actor, proof.getProductId(), request.getRating(), request.getComment(), clock.instant().truncatedTo(ChronoUnit.MICROS)));
        replaceImages(review.getId(), request.getImageIds());
        return new CommittedReview(review, request.getImageIds());
    }
    private void replaceImages(Long review, List<UUID> ids) {
        images.deleteByReviewId(review); images.flush();
        for (int index = 0; index < ids.size(); index++) images.save(new ProductReviewImage(review, ids.get(index), index));
        images.flush();
    }
}
