package com.example.demo.user.exception;
import com.example.demo.common.exception.ApplicationException;
public class IdentityConflictException extends ApplicationException {
    public IdentityConflictException() {
        super(409,"Username or email is already in use");
    }
}
