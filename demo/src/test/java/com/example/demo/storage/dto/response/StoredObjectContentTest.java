package com.example.demo.storage.dto.response;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;

class StoredObjectContentTest {
    @TempDir Path directory;

    @Test void close_shouldDeleteTemporaryContent() throws Exception {
        Path file = Files.writeString(directory.resolve("image.upload"), "image");
        new StoredObjectContent(file, null).close();
        assertThat(file).doesNotExist();
    }

    @Test void close_shouldPreserveSuccessfulOutcome_whenCleanupFails() throws Exception {
        Path nonemptyDirectory = Files.createDirectory(directory.resolve("busy"));
        Files.writeString(nonemptyDirectory.resolve("child"), "busy");
        assertThatCode(() -> {
            try (StoredObjectContent content = new StoredObjectContent(nonemptyDirectory, null)) {
                assertThat(content.getPath()).isEqualTo(nonemptyDirectory);
            }
        }).doesNotThrowAnyException();
    }

    @Test void close_shouldPreserveOriginalBusinessError_whenCleanupAlsoFails() throws Exception {
        Path nonemptyDirectory = Files.createDirectory(directory.resolve("busy"));
        Files.writeString(nonemptyDirectory.resolve("child"), "busy");
        IllegalStateException businessFailure = new IllegalStateException("Business validation failed");
        Throwable thrown = catchThrowable(() -> {
            try (StoredObjectContent content = new StoredObjectContent(nonemptyDirectory, null)) {
                throw businessFailure;
            }
        });
        assertThat(thrown).isSameAs(businessFailure);
        assertThat(thrown.getSuppressed()).isEmpty();
    }
}
