package com.example.demo.storage.service;

import com.example.demo.storage.dto.response.StoredObjectContent;
import com.example.demo.storage.dto.response.StoredObjectMetadata;
import com.example.demo.storage.dto.response.PreparedUpload;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public interface ObjectStorageService {
    PreparedUpload prepareUpload(String key, String contentType, Instant deadline);
    Optional<StoredObjectMetadata> inspect(String key);
    StoredObjectContent readIfMatch(String key, String etag);
    void copyIfMatch(String source, String etag, String target, String approvedContentType);
    void verifyFinalExists(String key);
    String prepareDownload(String key, Duration lifetime);
    PreparedDownloadSigner prepareDownloadSigner(Duration lifetime);
    void deleteStaging(String key);
}
