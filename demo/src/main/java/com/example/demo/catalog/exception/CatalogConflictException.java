package com.example.demo.catalog.exception;

import com.example.demo.common.exception.ApplicationException;
import org.springframework.http.HttpStatus;

public final class CatalogConflictException extends ApplicationException {
	public CatalogConflictException() {
		super(HttpStatus.CONFLICT, "Catalog operation conflicts with the current state");
	}
}
