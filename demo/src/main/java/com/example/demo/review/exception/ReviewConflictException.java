package com.example.demo.review.exception;

import com.example.demo.common.exception.ApplicationException;
public final class ReviewConflictException extends ApplicationException {
    public ReviewConflictException() { super(409, "Review conflicts with the current state"); }
}
