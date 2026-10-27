package com.example.demo.catalog.exception;

import com.example.demo.common.exception.ApplicationException;
import org.springframework.http.HttpStatus;

public final class InvalidProductImageException extends ApplicationException {
	public InvalidProductImageException() {
		super(HttpStatus.UNPROCESSABLE_CONTENT, "Invalid product image");
	}
}
