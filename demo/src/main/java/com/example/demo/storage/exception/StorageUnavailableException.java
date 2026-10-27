package com.example.demo.storage.exception;

import com.example.demo.common.exception.ApplicationException;

public class StorageUnavailableException extends ApplicationException {
    public StorageUnavailableException() { super(503, "Image storage is unavailable; you may retry"); }
}
