package com.example.demo.review.validator;

import com.example.demo.review.dto.request.ReviewContentRequest;
import com.example.demo.review.exception.InvalidReviewRequestException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class ReviewValidator {
    private final Validator validator;
    public void validate(ReviewContentRequest request) {
        if (request == null || !validator.validate(request).isEmpty()) throw new InvalidReviewRequestException();
    }
}
