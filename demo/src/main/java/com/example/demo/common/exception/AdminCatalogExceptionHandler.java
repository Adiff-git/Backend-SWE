package com.example.demo.common.exception;

import com.example.demo.catalog.controller.ProductImageController;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Extend exact concrete types only in their later controller-owning tasks. */
@RestControllerAdvice(assignableTypes = {ProductImageController.class, com.example.demo.catalog.controller.admin.AdminProductController.class, com.example.demo.catalog.controller.admin.AdminVariantController.class, com.example.demo.catalog.controller.admin.AdminGalleryController.class, com.example.demo.catalog.controller.admin.AdminCategoryController.class, com.example.demo.catalog.controller.admin.AdminBrandController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
@Import({AdminCatalogErrorSupport.class, ApiErrorFactory.class})
@RequiredArgsConstructor
public class AdminCatalogExceptionHandler {
    private final AdminCatalogErrorSupport support;

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> failure(Exception failure, HttpServletRequest request) {
        ApiError error = support.from(failure, request.getRequestURI());
        ResponseEntity.BodyBuilder response = ResponseEntity.status(error.getStatus()).cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON);
        if (error.getStatus() == 401) response.header("WWW-Authenticate", "Bearer");
        return response.body(error);
    }
}
