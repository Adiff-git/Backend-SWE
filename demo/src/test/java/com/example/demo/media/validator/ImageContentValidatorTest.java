package com.example.demo.media.validator;

import com.example.demo.media.exception.InvalidImageContentException;
import com.example.demo.media.testdata.MediaTestData;
import com.example.demo.storage.dto.response.StoredObjectContent;
import com.example.demo.storage.dto.response.StoredObjectMetadata;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageContentValidatorTest {
    private final ImageContentValidator validator = new ImageContentValidator();

    @org.junit.jupiter.api.Test
    void validate_shouldRejectDecodedFormatMismatchEvenWhenMetadataMatchesDeclaration() throws Exception {
        try (StoredObjectContent content = content(MediaTestData.fixture("valid.jpg"), "image/png")) {
            assertThatThrownBy(() -> validator.validate(content, "image/png")).isInstanceOf(InvalidImageContentException.class);
        }
    }

    @ParameterizedTest
    @CsvSource({"valid.jpg,image/jpeg", "valid.png,image/png", "lossy.webp,image/webp",
            "lossless.webp,image/webp", "alpha.webp,image/webp"})
    void validate_shouldDecodeActualImage_whenSupported(String name, String mime) throws Exception {
        try (StoredObjectContent content = content(MediaTestData.fixture(name), mime)) {
            assertThatCode(() -> validator.validate(content, mime)).doesNotThrowAnyException();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"corrupt.bin", "truncated.webp"})
    void validate_shouldRejectInvalidBytes(String name) throws Exception {
        try (StoredObjectContent content = content(MediaTestData.fixture(name), "image/webp")) {
            assertThatThrownBy(() -> validator.validate(content, "image/webp"))
                    .isInstanceOf(InvalidImageContentException.class);
        }
    }

    @ParameterizedTest
    @CsvSource({"image/png,image/jpeg", "image/jpeg,image/jpeg", "image/png,image/png"})
    void validate_shouldRejectEmptyOrMismatchedContent(String stored, String declared) throws Exception {
        byte[] bytes = stored.equals(declared) ? new byte[0] : MediaTestData.fixture("valid.png");
        try (StoredObjectContent content = content(bytes, stored)) {
            assertThatThrownBy(() -> validator.validate(content, declared))
                    .isInstanceOf(InvalidImageContentException.class);
        }
    }

    private StoredObjectContent content(byte[] bytes, String type) throws Exception {
        Path file = Files.createTempFile("fixture-", ".image");
        Files.write(file, bytes);
        return new StoredObjectContent(file, new StoredObjectMetadata("etag", bytes.length, type));
    }
}
