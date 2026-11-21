package com.example.demo.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo.config.ReviewIntegrationSupport;
import com.example.demo.review.dto.request.CreateReviewRequest;
import com.example.demo.review.dto.request.PatchReviewRequest;
import com.example.demo.review.dto.response.ReviewResponse;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.json.JsonMapper;

class ReviewUpdateServiceIT extends ReviewIntegrationSupport {
    @Autowired ReviewService reviews;
    private final JsonMapper json = JsonMapper.builder().build();
    @Test void update_shouldRollbackContentAndReferences_whenPersistenceFailsAfterReplacementStarts() {
        ReviewResponse created = reviews.create(purchase.owner(), purchase.order(), purchase.item(), json.readValue("{\"rating\":1}", CreateReviewRequest.class));
        UUID first = completedImage(); UUID second = completedImage();
        jdbc.execute("ALTER TABLE product_review_images ADD CONSTRAINT ck_review_test_failure CHECK(display_order=0) NOT VALID");
        try {
            PatchReviewRequest patch = json.readValue(json.writeValueAsString(Map.of("rating", 5, "imageIds", java.util.List.of(first.toString(), second.toString()))), PatchReviewRequest.class);
            assertThatThrownBy(() -> reviews.update(purchase.owner(), created.getId(), patch)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(jdbc.queryForObject("SELECT rating FROM product_reviews WHERE id=?", Integer.class, created.getId())).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_review_images WHERE review_id=?", Long.class, created.getId())).isZero();
        } finally { jdbc.execute("ALTER TABLE product_review_images DROP CONSTRAINT ck_review_test_failure"); }
    }
    @Test void update_shouldPreserveSubmittedImageOrderAndRetainObjectsWhenDetached() {
        ReviewResponse created = reviews.create(purchase.owner(), purchase.order(), purchase.item(), json.readValue("{\"rating\":5}", CreateReviewRequest.class));
        UUID first = completedImage(); UUID second = completedImage();
        PatchReviewRequest attach = json.readValue(json.writeValueAsString(Map.of("imageIds", java.util.List.of(second.toString(), first.toString()))), PatchReviewRequest.class);
        assertThat(reviews.update(purchase.owner(), created.getId(), attach).getImages()).extracting(i -> i.getId()).containsExactly(second, first);
        reviews.update(purchase.owner(), created.getId(), json.readValue("{\"imageIds\":[]}", PatchReviewRequest.class));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM image_uploads WHERE id IN (?,?)", Long.class, first, second)).isEqualTo(2L);
        org.mockito.Mockito.verify(storage, org.mockito.Mockito.never()).deleteStaging(org.mockito.ArgumentMatchers.anyString());
    }
    @Test void update_shouldPreserveHistoryAndNoOpTimestampAndChangeStatisticsWithoutIncreasingCount() {
        ReviewResponse created = reviews.create(purchase.owner(), purchase.order(), purchase.item(), json.readValue("{\"rating\":1,\"comment\":\"original\"}", CreateReviewRequest.class));
        ReviewResponse noOp = reviews.update(purchase.owner(), created.getId(), json.readValue("{\"rating\":1}", PatchReviewRequest.class));
        assertThat(noOp.getUpdatedAt()).isEqualTo(created.getUpdatedAt());
        jdbc.update("UPDATE products SET status='ARCHIVED' WHERE id=?", purchase.product());
        jdbc.update("UPDATE order_items SET product_id=NULL WHERE id=?", purchase.item());
        ReviewResponse edited = reviews.update(purchase.owner(), created.getId(), json.readValue("{\"rating\":5,\"comment\":null}", PatchReviewRequest.class));
        assertThat(edited.getRating()).isEqualTo(5); assertThat(edited.getComment()).isNull();
        assertThat(edited.getCreatedAt()).isEqualTo(created.getCreatedAt()); assertThat(edited.getUpdatedAt()).isAfter(created.getUpdatedAt());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_reviews WHERE order_item_id=?", Long.class, purchase.item())).isEqualTo(1L);
    }
    @Test void update_shouldRollbackAllContent_whenLastImageIsUnknown() {
        ReviewResponse created = reviews.create(purchase.owner(), purchase.order(), purchase.item(), json.readValue("{\"rating\":1}", CreateReviewRequest.class));
        PatchReviewRequest patch = json.readValue(json.writeValueAsString(Map.of("rating", 5, "imageIds", java.util.List.of(UUID.randomUUID().toString()))), PatchReviewRequest.class);
        assertThatThrownBy(() -> reviews.update(purchase.owner(), created.getId(), patch)).isInstanceOf(com.example.demo.media.exception.ImageUploadNotFoundException.class);
        assertThat(jdbc.queryForObject("SELECT rating FROM product_reviews WHERE id=?", Integer.class, created.getId())).isEqualTo(1);
    }
}
