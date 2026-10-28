package com.example.demo.catalog.exception;

import com.example.demo.common.exception.ApplicationException;
import org.springframework.http.HttpStatus;

public final class ProductNotFoundException extends ApplicationException {
	public ProductNotFoundException() {
		super(HttpStatus.NOT_FOUND, "Product not found");
	}
}
