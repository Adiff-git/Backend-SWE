package com.example.demo.storage.service;

/** Immutable request-local URL capability; no per-request client, close or cleanup. */
@FunctionalInterface
public interface PreparedImageUrls {
    String resolve(String objectKey);
}
