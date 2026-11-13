package com.example.demo.storage.dto.response;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Getter
@RequiredArgsConstructor
public final class StoredObjectContent implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(StoredObjectContent.class);
    private final Path path;
    private final StoredObjectMetadata metadata;

    @Override public void close() {
        try {
            Files.deleteIfExists(path);
        } catch (IOException | RuntimeException failure) {
            // Cleanup must not replace a successful outcome or an in-flight business error.
            // Do not log paths, object keys, signed URLs, or exception messages.
            LOG.warn("Temporary image cleanup failed; deferred cleanup required");
            try {
                path.toFile().deleteOnExit();
            } catch (RuntimeException deferredFailure) {
                LOG.warn("Temporary image deferred cleanup could not be registered");
            }
        }
    }
}
