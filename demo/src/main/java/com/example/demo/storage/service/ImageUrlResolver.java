package com.example.demo.storage.service;

/** URL distribution only. Callers retain ownership, publication and attachment checks. */
public interface ImageUrlResolver {
    String resolve(String objectKey);

    /** Resolve any remote credentials before domain locks; returned values own no resources. */
    PreparedImageUrls prepare();
}
