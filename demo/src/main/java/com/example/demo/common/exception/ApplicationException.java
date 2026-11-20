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

	public String getCode() { return null; }

    /** Explicit safe domain extensions, empty unless the domain contract defines them. */
    public java.util.Map<String,Object> getProperties() { return java.util.Map.of(); }

    public int getStatus() {
		return status;
	}
}
