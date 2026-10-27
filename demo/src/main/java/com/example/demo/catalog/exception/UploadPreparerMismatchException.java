package com.example.demo.catalog.exception;

import com.example.demo.common.exception.ApplicationException;
import org.springframework.http.HttpStatus;

public final class UploadPreparerMismatchException extends ApplicationException {
	public UploadPreparerMismatchException() {
		super(HttpStatus.FORBIDDEN, "Only the administrator who prepared the upload may complete it");
	}
}
