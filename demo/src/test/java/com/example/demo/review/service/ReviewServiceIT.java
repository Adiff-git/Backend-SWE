package com.example.demo.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo.config.ReviewIntegrationSupport;
import com.example.demo.review.dto.request.CreateReviewRequest;
import com.example.demo.review.dto.response.ReviewResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.json.JsonMapper;

class ReviewServiceIT extends ReviewIntegrationSupport {
    @Autowired ReviewService reviews;
    private CreateReviewRequest request(String body) { return JsonMapper.builder().build().readValue(body, CreateReviewRequest.class); }
    @Test void create_shouldAcceptTenOrderedImagesWithoutAComment() {
        java.util.List<java.util.UUID> images = java.util.stream.IntStream.range(0, 10).mapToObj(i -> completedImage()).toList();
        CreateReviewRequest input = request(JsonMapper.builder().build().writeValueAsString(java.util.Map.of("rating", 5, "imageIds", images.stream().map(java.util.UUID::toString).toList())));
        ReviewResponse created = reviews.create(purchase.owner(), purchase.order(), purchase.item(), input);
        assertThat(created.getComment()).isNull();
        assertThat(created.getImages()).extracting(image -> image.getId()).containsExactlyElementsOf(images);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = com.example.demo.order.entity.OrderStatus.class, names = {"PENDING", "CONFIRMED", "SHIPPING", "CANCELLED", "FAILED"})
    void create_shouldRejectEveryNonCompletedOrder(com.example.demo.order.entity.OrderStatus status) {
        jdbc.update("UPDATE orders SET status=?, completed_at=NULL, cancelled_at=CASE WHEN ?='CANCELLED' THEN placed_at+INTERVAL '1 second' ELSE NULL END WHERE id=?", status.name(), status.name(), purchase.order());
        assertThatThrownBy(() -> reviews.create(purchase.owner(), purchase.order(), purchase.item(), request("{\"rating\":5}")))
                .isInstanceOf(com.example.demo.order.exception.OrderConflictException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_reviews WHERE order_item_id=?", Long.class, purchase.item())).isZero();
    }
    @Test void create_shouldAllowSeparateCompletedPurchaseOfSameProduct() {
        Long other = transactions.execute(s -> com.example.demo.fixture.review.ReviewFixtures.additionalItem(entities, purchase.owner(), purchase.product()));
        Long order = jdbc.queryForObject("SELECT order_id FROM order_items WHERE id=?", Long.class, other);
        reviews.create(purchase.owner(), purchase.order(), purchase.item(), request("{\"rating\":1}"));
        reviews.create(purchase.owner(), order, other, request("{\"rating\":5}"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_reviews WHERE product_id=?", Long.class, purchase.product())).isEqualTo(2L);
    }
    @Test void create_shouldCommitOneVerifiedRatingOnlyReviewAndRejectDuplicate() {
        ReviewResponse response = reviews.create(purchase.owner(), purchase.order(), purchase.item(), request("{\"rating\":5}"));
        assertThat(response.isVerifiedPurchase()).isTrue(); assertThat(response.getImages()).isEmpty();
        assertThat(response.getCreatedAt()).isEqualTo(response.getUpdatedAt());
        assertThatThrownBy(() -> reviews.create(purchase.owner(), purchase.order(), purchase.item(), request("{\"rating\":1}")))
                .isInstanceOf(com.example.demo.review.exception.ReviewConflictException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_reviews WHERE order_item_id=?", Long.class, purchase.item())).isEqualTo(1L);
    }
    @Test void create_shouldRejectForeignPurchaseWithoutMutation() {
        assertThatThrownBy(() -> reviews.create(purchase.foreign(), purchase.order(), purchase.item(), request("{\"rating\":5}")))
                .isInstanceOf(com.example.demo.order.exception.OrderNotFoundException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_reviews WHERE order_item_id=?", Long.class, purchase.item())).isZero();
    }
}
