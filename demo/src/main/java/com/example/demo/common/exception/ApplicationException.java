package com.example.demo.common.exception;
public abstract class ApplicationException extends RuntimeException {
    private final int status;
    protected ApplicationException(int status,String message) {
        super(message);
        this.status=status;
    }
    public int getStatus() {
        return status;
    }
}
