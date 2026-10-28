package com.example.demo.auth.exception;
import com.example.demo.common.exception.ApplicationException;
public class AuthenticationFailedException extends ApplicationException {
    public AuthenticationFailedException() {
        super(401,"Authentication failed");
    }
}
