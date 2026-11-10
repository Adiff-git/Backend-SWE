package com.example.demo.behavior;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
@RestControllerAdvice(basePackageClasses = BehaviorController.class)
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
public class BehaviorErrors {
    @ExceptionHandler(com.example.demo.common.exception.ApplicationException.class)
    ResponseEntity<ProblemDetail> business(com.example.demo.common.exception.ApplicationException e) {
        return response(e.getStatus(), e.getMessage());
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
        org.springframework.web.bind.MissingServletRequestParameterException.class})
    ResponseEntity<ProblemDetail> invalid(Exception e) { return response(400, "Invalid behavior request"); }
    private ResponseEntity<ProblemDetail> response(int status, String detail) {
        return ResponseEntity.status(status).header("Cache-Control", "no-store").body(ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), detail));
    }
}
