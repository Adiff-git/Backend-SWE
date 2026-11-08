package com.example.demo.catalog.validator;

import com.example.demo.catalog.exception.InvalidProductImageException;
import com.example.demo.media.testdata.MediaTestData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;

class ProductImageContentValidatorTest {
    private final ProductImageContentValidator validator = new ProductImageContentValidator();

    @ParameterizedTest
    @CsvSource({"valid.jpg,image/jpeg", "valid.png,image/png", "lossy.webp,image/webp"})
    void supportedImagesHaveVerifiedDimensionsSizeAndHash(String fixture, String type) {
        byte[] bytes = MediaTestData.fixture(fixture);
        var result = validator.validate(bytes, type, bytes.length, "Front");
        assertThat(result.width()).isPositive();
        assertThat(result.height()).isPositive();
        assertThat(result.byteSize()).isEqualTo(bytes.length);
        assertThat(result.contentType()).isEqualTo(type);
        assertThat(result.sha256()).hasSize(32);
    }

    @Test void rejectsInvalidContentMetadataAndSize() {
        byte[] bytes = MediaTestData.fixture("valid.png");
        assertThatThrownBy(() -> validator.validate(bytes, "image/jpeg", bytes.length, "Front")).isInstanceOf(InvalidProductImageException.class);
        assertThatThrownBy(() -> validator.validate(bytes, "image/png", bytes.length + 1, "Front")).isInstanceOf(InvalidProductImageException.class);
        assertThatThrownBy(() -> validator.validate(bytes, "image/png", bytes.length, " ")).isInstanceOf(InvalidProductImageException.class);
        assertThatThrownBy(() -> validator.validate(new byte[]{1, 2, 3}, "image/png", 3, "Front")).isInstanceOf(InvalidProductImageException.class);
        assertThatThrownBy(() -> validator.validate(new byte[0], "image/png", 0, "Front")).isInstanceOf(InvalidProductImageException.class);
        assertThatThrownBy(() -> validator.validate(new byte[10_485_761], "image/png", 10_485_761, "Front")).isInstanceOf(InvalidProductImageException.class);
    }
}
