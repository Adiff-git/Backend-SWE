package com.example.demo.storage.dto.response;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public final class StoredObjectContent implements AutoCloseable {
    private final Path path;
    private final StoredObjectMetadata metadata;

    @Override public void close() throws IOException { Files.deleteIfExists(path); }
}
