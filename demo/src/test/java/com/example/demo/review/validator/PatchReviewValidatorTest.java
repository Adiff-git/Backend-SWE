package com.example.demo.review.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo.review.dto.request.PatchReviewRequest;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class PatchReviewValidatorTest {
    private final JsonMapper json = JsonMapper.builder().build();
    @Test void binding_shouldPreserveOmissionAndRecognizeExplicitClear() {
        PatchReviewRequest clear = json.readValue("{\"comment\":null,\"imageIds\":[]}", PatchReviewRequest.class);
        assertThat(clear.isRatingPresent()).isFalse(); assertThat(clear.isCommentPresent()).isTrue();
        assertThat(clear.getComment()).isNull(); assertThat(clear.isImageIdsPresent()).isTrue(); assertThat(clear.getImageIds()).isEmpty();
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(json.readValue("{}", PatchReviewRequest.class))).isNotEmpty();
        }
    }
    @ParameterizedTest @ValueSource(strings = {"{\"rating\":null}", "{\"imageIds\":null}", "{\"authorId\":7}", "{\"commentPresent\":true}"})
    void binding_shouldRejectInvalidNullOrIdentityInput(String body) {
        assertThatThrownBy(() -> json.readValue(body, PatchReviewRequest.class)).isInstanceOf(RuntimeException.class);
    }
}
