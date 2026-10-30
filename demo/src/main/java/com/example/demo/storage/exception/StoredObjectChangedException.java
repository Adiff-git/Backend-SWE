package com.example.demo.storage.exception;

import com.example.demo.common.exception.ApplicationException;

public class StoredObjectChangedException extends ApplicationException {
    public StoredObjectChangedException() { super(409, "Upload content is missing or changed; you may retry"); }
}
