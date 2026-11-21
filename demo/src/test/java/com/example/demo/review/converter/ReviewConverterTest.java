package com.example.demo.review.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.review.dto.response.ReviewResponse;
import com.example.demo.review.entity.ProductReview;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ReviewConverterTest {
    @Test void toResponse_shouldResolveActiveAvatarFromThePreparedMediaBatch() {
        UUID avatar = UUID.randomUUID();
        ProductReview review = new ProductReview(1L, 2L, UUID.randomUUID(), 5, null, Instant.EPOCH);
        ReviewResponse response = new ReviewConverter().toResponse(review, List.of(), Map.of(avatar, new com.example.demo.media.dto.response.ImageAccessResponse(avatar, "https://example.invalid/avatar")),
                new com.example.demo.user.dto.response.ReviewAuthorSummary("Customer", new com.example.demo.media.dto.response.ImageAccessResponse(avatar, null)));
        assertThat(response.getAuthor().getProfileImage().getFinalUrl()).isEqualTo("https://example.invalid/avatar");
    }
    @Test void toResponse_shouldPreserveOrderAndExcludePrivateIdentity_whenMediaIsUnavailable() {
        UUID first = UUID.randomUUID(); UUID second = UUID.randomUUID();
        ProductReview review = new ProductReview(42L, 92L, UUID.randomUUID(), 4, "Text", Instant.EPOCH);
        ReviewResponse response = new ReviewConverter().toResponse(review, List.of(first, second), Map.of(), null);
        assertThat(response.getImages()).extracting(i -> i.getId()).containsExactly(first, second);
        assertThat(response.getImages()).allSatisfy(i -> assertThat(i.getFinalUrl()).isNull());
        assertThat(response.getAuthor().getDisplayName()).isEqualTo("Former customer");
        assertThat(JsonMapper.builder().build().writeValueAsString(response)).doesNotContain("authorId", "orderItemId", "email", "phone", "password");
    }
}
