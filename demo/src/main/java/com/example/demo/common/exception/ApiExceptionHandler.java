package com.example.demo.common.exception;

import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(basePackages = "com.example.demo.catalog")
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
public class ApiExceptionHandler {
	private static final URI ABOUT_BLANK = URI.create("about:blank");

	@ExceptionHandler(ApplicationException.class)
	public ResponseEntity<ProblemDetail> handleApplicationException(ApplicationException exception) {
		return problem(HttpStatus.valueOf(exception.getStatus()), exception.getMessage());
	}

	@ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class,
			HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
	public ResponseEntity<ProblemDetail> handleInvalidRequest(Exception exception) {
		return problem(HttpStatus.BAD_REQUEST, "Invalid request");
	}

	private ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		// The catalog OpenAPI contract requires the RFC 9457 default type explicitly.
		problem.setType(ABOUT_BLANK);
		return ResponseEntity.status(status).body(problem);
	}
}
