package com.example.demo.review.dto.request;

import jakarta.validation.constraints.AssertTrue;

public class CreateReviewRequest extends ReviewContentRequest {
    @AssertTrue public boolean isRatingSupplied() { return isRatingPresent() && getRating() != null; }
}
