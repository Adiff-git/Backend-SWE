package com.example.demo.auth.exception;
import com.example.demo.common.exception.ApplicationException;
public class IdentityException extends ApplicationException {
    private final String code;
    public IdentityException(int status, String code, String detail) {
        super(status, detail); this.code = code;
    }
    public String getCode() { return code; }
}
