package com.example.demo.storage.dto.response;

import lombok.Value;

@Value
public class StoredObjectMetadata {
    String etag;
    long length;
    String contentType;
}
