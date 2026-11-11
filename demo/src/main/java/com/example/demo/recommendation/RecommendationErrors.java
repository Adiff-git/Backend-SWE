package com.example.demo.recommendation;

import com.example.demo.common.exception.ApplicationException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(basePackageClasses = RecommendationController.class)
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
public class RecommendationErrors {
  @ExceptionHandler(ApplicationException.class)
  ResponseEntity<ProblemDetail> business(ApplicationException error) {
    return response(error.getStatus(), error.getMessage());
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ResponseEntity<ProblemDetail> invalid(Exception error) {
    return response(400, "Invalid recommendation request");
  }

  private ResponseEntity<ProblemDetail> response(int status, String detail) {
    return ResponseEntity.status(status)
        .header("Cache-Control", "no-store")
        .body(ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), detail));
  }
}
