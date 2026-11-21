package com.example.demo.review.dto.response;

import com.example.demo.user.dto.response.ReviewAuthorSummary;
import java.time.Instant;
import java.util.List;
import lombok.Value;

@Value
public class ReviewResponse {
    Long id;
    int rating;
    String comment;
    List<ReviewImageResponse> images;
    ReviewAuthorSummary author;
    Instant createdAt;
    Instant updatedAt;
    boolean verifiedPurchase;
}
