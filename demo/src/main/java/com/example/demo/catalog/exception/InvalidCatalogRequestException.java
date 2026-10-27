package com.example.demo.catalog.exception;

import com.example.demo.common.exception.ApplicationException;
import org.springframework.http.HttpStatus;

public final class InvalidCatalogRequestException extends ApplicationException {
	public InvalidCatalogRequestException(String publicDetail) {
		super(HttpStatus.BAD_REQUEST, publicDetail);
	}
}
