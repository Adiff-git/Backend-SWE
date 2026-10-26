package com.example.demo.user.exception;
import com.example.demo.common.exception.ApplicationException;
public class ResourceNotFoundException extends ApplicationException {
    public ResourceNotFoundException() {
        super(404,"Resource not found");
    }
}
