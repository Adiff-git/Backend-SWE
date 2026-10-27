package com.example.demo.media.validator;

import com.example.demo.media.exception.InvalidImageRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageUploadValidatorTest {
    private final ImageUploadValidator validator = new ImageUploadValidator();
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"image/jpg", "image/PNG", "text/plain", " "})
    void validateType_shouldRejectMissingOrUnsupportedDeclaration(String type) {
        assertThatThrownBy(() -> validator.validateType(type)).isInstanceOf(InvalidImageRequestException.class);
    }
    @Test void validateId_shouldRejectMissingAndNonpositiveIds() {
        for (Long id : new Long[] {null, 0L, -1L})
            assertThatThrownBy(() -> validator.validateId(id)).isInstanceOf(InvalidImageRequestException.class);
    }
}
