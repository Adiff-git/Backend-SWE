package com.example.demo.common.exception;

import org.springframework.http.HttpStatus;

/** A business failure with a safe, client-facing description. */
public abstract class ApplicationException extends RuntimeException {
	private final int status;

	protected ApplicationException(int status, String publicDetail) {
		super(publicDetail);
		this.status = status;
	}

	protected ApplicationException(HttpStatus status, String publicDetail) {
		this(status.value(), publicDetail);
	}

	public int getStatus() {
		return status;
	}
}
