package com.example.demo.review.exception;

import com.example.demo.common.exception.ApplicationException;
public final class ReviewNotFoundException extends ApplicationException {
    public ReviewNotFoundException() { super(404, "Review not found"); }
}
