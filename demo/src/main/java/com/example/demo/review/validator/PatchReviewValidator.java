package com.example.demo.review.validator;

import com.example.demo.review.dto.request.PatchReviewRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class PatchReviewValidator {
    private final ReviewValidator validator;
    public void validate(PatchReviewRequest request) { validator.validate(request); }
}
