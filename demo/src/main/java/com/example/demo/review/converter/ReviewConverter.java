package com.example.demo.review.converter;

import com.example.demo.media.dto.response.ImageAccessResponse;
import com.example.demo.review.dto.response.ReviewImageResponse;
import com.example.demo.review.dto.response.ReviewResponse;
import com.example.demo.review.entity.ProductReview;
import com.example.demo.user.dto.response.ReviewAuthorSummary;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ReviewConverter {
    public ReviewResponse toResponse(ProductReview review, List<UUID> imageIds, Map<UUID, ImageAccessResponse> images, ReviewAuthorSummary author) {
        return toResponse(review,imageIds,images,author,null);
    }
    public ReviewResponse toResponse(ProductReview review, List<UUID> imageIds, Map<UUID,ImageAccessResponse> images,
            ReviewAuthorSummary author,Map<String,String> purchasedOptions) {
        List<ReviewImageResponse> ordered = imageIds.stream().map(id -> {
            ImageAccessResponse access = images.get(id);
            return new ReviewImageResponse(id, access == null ? null : access.getFinalUrl());
        }).toList();
        ReviewAuthorSummary safe = author == null ? ReviewAuthorSummary.formerCustomer() : author;
        if (safe.getProfileImage() != null) {
            ImageAccessResponse avatar = images.get(safe.getProfileImage().getId());
            safe = new ReviewAuthorSummary(safe.getDisplayName(), avatar == null ? safe.getProfileImage() : avatar);
        }
        return new ReviewResponse(review.getId(), review.getRating(), review.getComment(), ordered,
                safe, review.getCreatedAt(), review.getUpdatedAt(), true,purchasedOptions==null ? null : Map.copyOf(purchasedOptions));
    }
}
