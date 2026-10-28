package com.example.demo.user.exception;
import com.example.demo.common.exception.ApplicationException;
public class MediaUnavailableException extends ApplicationException {
    public MediaUnavailableException() {
        super(503,"Media integration is not available");
    }
}
