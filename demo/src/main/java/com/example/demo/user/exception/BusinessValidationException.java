package com.example.demo.user.exception;
import com.example.demo.common.exception.ApplicationException;
public class BusinessValidationException extends ApplicationException {
    public BusinessValidationException(String message) {
        super(400,message);
    }
}
