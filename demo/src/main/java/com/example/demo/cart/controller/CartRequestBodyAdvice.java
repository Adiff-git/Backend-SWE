package com.example.demo.cart.controller;

import com.example.demo.cart.exception.InvalidCartRequestException;
import com.example.demo.common.exception.ApiError;
import com.example.demo.common.exception.ApiErrorFactory;
import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

@RestControllerAdvice
@Import(CartRequestReader.class)
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RequiredArgsConstructor
public class CartRequestBodyAdvice extends RequestBodyAdviceAdapter {
    private final CartRequestReader reader;
    private final ApiErrorFactory errors;

    @Override public boolean supports(MethodParameter parameter, Type type,
            Class<? extends HttpMessageConverter<?>> converter) {
        return parameter.getContainingClass() == CartController.class;
    }

    @Override public HttpInputMessage beforeBodyRead(HttpInputMessage input, MethodParameter parameter, Type type,
            Class<? extends HttpMessageConverter<?>> converter) throws IOException {
        byte[] body = input.getBody().readAllBytes();
        reader.validate(body, parameter.getParameterType());
        return new HttpInputMessage() {
            @Override public InputStream getBody() { return new ByteArrayInputStream(body); }
            @Override public HttpHeaders getHeaders() { return input.getHeaders(); }
        };
    }

    @Override public Object handleEmptyBody(Object body, HttpInputMessage input, MethodParameter parameter,
            Type type, Class<? extends HttpMessageConverter<?>> converter) {
        throw new InvalidCartRequestException();
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> unsupportedMedia(HttpMediaTypeNotSupportedException failure, HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        ApiError error = path.equals("/api/me/cart") || path.startsWith("/api/me/cart/")
                ? errors.create(415, "Unsupported media type", request.getRequestURI(), java.util.Map.of())
                : errors.from(failure, request.getRequestURI());
        return ResponseEntity.status(error.getStatus()).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(error);
    }
}
