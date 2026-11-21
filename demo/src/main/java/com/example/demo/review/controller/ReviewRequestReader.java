package com.example.demo.review.controller;

import com.example.demo.review.exception.InvalidReviewRequestException;

public final class ReviewRequestReader {
    private ReviewRequestReader() { }
    public static Long positiveId(String value) {
        if (value == null || !value.matches("[1-9][0-9]*")) throw new InvalidReviewRequestException();
        try { return Long.parseLong(value); }
        catch (NumberFormatException overflow) { throw new InvalidReviewRequestException(); }
    }
}
