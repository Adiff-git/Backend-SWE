package com.example.demo.common.exception;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
@RestControllerAdvice @RequiredArgsConstructor
public class GlobalExceptionHandler {
    private final ApiErrorFactory factory;
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException failure,HttpServletRequest request) {
        Map<String,List<String>> fields=new LinkedHashMap<>();
        failure.getBindingResult().getFieldErrors().forEach(e->fields.computeIfAbsent(e.getField(),k->new ArrayList<>()).add(e.getDefaultMessage()));
        return response(factory.create(400,"Invalid input",request.getRequestURI(),fields));
    }
    @ExceptionHandler( {
        HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class,ConstraintViolationException.class
    }
    )
    public ResponseEntity<ApiError> invalid(Exception failure,HttpServletRequest request) {
        return response(factory.create(400,"Invalid input",request.getRequestURI(),Map.of()));
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> failure(Exception failure,HttpServletRequest request) {
        return response(factory.from(failure,request.getRequestURI()));
    }
    private ResponseEntity<ApiError> response(ApiError error) {
        return ResponseEntity.status(error.getStatus()).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(error);
    }
}
