package com.example.demo.catalog.exception;

import com.example.demo.common.exception.ApplicationException;
import org.springframework.http.HttpStatus;

public final class CatalogStorageUnavailableException extends ApplicationException {
	public CatalogStorageUnavailableException() {
		super(HttpStatus.SERVICE_UNAVAILABLE, "Product image access is temporarily unavailable");
	}
}
