package com.example.demo.media.exception;

import com.example.demo.common.exception.ApplicationException;

public class UploadExpiredException extends ApplicationException {
    public UploadExpiredException() { super(409, "Upload expired; request a new upload"); }
}
