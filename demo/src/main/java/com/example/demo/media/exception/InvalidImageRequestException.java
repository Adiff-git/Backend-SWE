package com.example.demo.media.exception;

import com.example.demo.common.exception.ApplicationException;

public class InvalidImageRequestException extends ApplicationException {
    public InvalidImageRequestException() { super(400, "Invalid image request"); }
}
