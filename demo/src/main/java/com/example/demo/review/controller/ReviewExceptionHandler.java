package com.example.demo.review.controller;

import com.example.demo.common.exception.ApiError;
import com.example.demo.common.exception.ApiErrorFactory;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.example.demo.review.controller")
@Order(Ordered.HIGHEST_PRECEDENCE) @RequiredArgsConstructor
public class ReviewExceptionHandler {
    private final ApiErrorFactory errors;
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> unsupportedMedia(HttpMediaTypeNotSupportedException failure, HttpServletRequest request) {
        return ResponseEntity.status(415).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(errors.create(415, "Unsupported request media type", request.getRequestURI(), Map.of()));
    }
}
