package com.example.demo.review.exception;

import com.example.demo.common.exception.ApplicationException;
public final class InvalidReviewRequestException extends ApplicationException {
    public InvalidReviewRequestException() { super(400, "Invalid review request"); }
}
