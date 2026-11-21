package com.example.demo.review.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo.review.dto.request.CreateReviewRequest;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class ReviewValidatorTest {
    private final JsonMapper json = JsonMapper.builder().build();
    @org.junit.jupiter.api.Test void binding_shouldRejectDuplicateOrTooManyImageIdentifiers() {
        String image = java.util.UUID.randomUUID().toString();
        assertThatThrownBy(() -> json.readValue(json.writeValueAsString(java.util.Map.of("rating", 5, "imageIds", java.util.List.of(image, image))), CreateReviewRequest.class)).isInstanceOf(RuntimeException.class);
        java.util.List<String> many = java.util.stream.IntStream.range(0, 11).mapToObj(i -> java.util.UUID.randomUUID().toString()).toList();
        assertThatThrownBy(() -> json.readValue(json.writeValueAsString(java.util.Map.of("rating", 5, "imageIds", many)), CreateReviewRequest.class)).isInstanceOf(RuntimeException.class);
    }
    @ParameterizedTest @ValueSource(strings = {"{}", "{\"rating\":0}", "{\"rating\":6}"})
    void validate_shouldRejectMissingOrOutOfRangeRating(String body) {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(json.readValue(body, CreateReviewRequest.class))).isNotEmpty();
        }
    }
    @ParameterizedTest @ValueSource(strings = {"{\"rating\":null}", "{\"rating\":\"5\"}", "{\"rating\":1.5}", "{\"rating\":true}", "{\"rating\":5,\"imageIds\":null}", "{\"rating\":5,\"authorId\":2}", "{\"rating\":5,\"commentPresent\":true}"})
    void binding_shouldRejectCoercionAndUnknownFields(String body) {
        assertThatThrownBy(() -> json.readValue(body, CreateReviewRequest.class)).isInstanceOf(RuntimeException.class);
    }
    @ParameterizedTest @ValueSource(ints = {1999, 2000, 2001})
    void validate_shouldCountNormalizedUnicodeCodePoints(int length) {
        String body = json.writeValueAsString(java.util.Map.of("rating", 5, "comment", "  " + "😀".repeat(length) + "  "));
        CreateReviewRequest request = json.readValue(body, CreateReviewRequest.class);
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(request).isEmpty()).isEqualTo(length <= 2000);
        }
    }
}
