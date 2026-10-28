package com.example.demo.media.exception;

import com.example.demo.common.exception.ApplicationException;

public class UploadStateConflictException extends ApplicationException {
    public UploadStateConflictException() { super(409, "Image is not available in the required state"); }
}
