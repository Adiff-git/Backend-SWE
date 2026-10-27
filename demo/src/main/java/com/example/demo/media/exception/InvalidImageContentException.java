package com.example.demo.media.exception;

import com.example.demo.common.exception.ApplicationException;

public class InvalidImageContentException extends ApplicationException {
    public InvalidImageContentException() { super(422, "Image content is empty, invalid or does not match its declared type"); }
}
