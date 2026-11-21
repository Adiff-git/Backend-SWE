package com.example.demo.review.dto.request;

import jakarta.validation.constraints.AssertTrue;
public class PatchReviewRequest extends ReviewContentRequest {
    @AssertTrue public boolean isAnyFieldSupplied() { return isRatingPresent() || isCommentPresent() || isImageIdsPresent(); }
}
