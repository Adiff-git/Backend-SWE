package com.example.demo.review.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo.fixture.review.ReviewFixtures;
import com.example.demo.order.entity.OrderStatus;
import com.example.demo.review.entity.ProductReview;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest @ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) @ActiveProfiles("test")
class ProductReviewRepositoryIT {
    @Autowired EntityManager entities;
    @Autowired ProductReviewRepository reviews;
    @Autowired ProductReviewImageRepository images;
    @Test void saveImage_shouldRejectAnEleventhDisplayPosition() {
        ReviewFixtures.Purchase purchase = ReviewFixtures.purchase(entities, OrderStatus.COMPLETED);
        ProductReview review = reviews.saveAndFlush(new ProductReview(purchase.item(), purchase.owner(), purchase.product(), 5, null, Instant.EPOCH));
        java.util.UUID image = upload(purchase.owner());
        assertThatThrownBy(() -> images.saveAndFlush(new com.example.demo.review.entity.ProductReviewImage(review.getId(), image, 10)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void saveImage_shouldRejectTheSameUploadTwiceWithinOneReview() {
        ReviewFixtures.Purchase purchase = ReviewFixtures.purchase(entities, OrderStatus.COMPLETED);
        ProductReview review = reviews.saveAndFlush(new ProductReview(purchase.item(), purchase.owner(), purchase.product(), 5, null, Instant.EPOCH));
        java.util.UUID image = upload(purchase.owner());
        images.saveAndFlush(new com.example.demo.review.entity.ProductReviewImage(review.getId(), image, 0));
        assertThatThrownBy(() -> images.saveAndFlush(new com.example.demo.review.entity.ProductReviewImage(review.getId(), image, 1)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    private java.util.UUID upload(Long owner) {
        com.example.demo.media.entity.ImageUpload image = com.example.demo.media.entity.ImageUpload.pending(owner, "staging/" + java.util.UUID.randomUUID(), "image/png", Instant.EPOCH, Instant.EPOCH.plusSeconds(1));
        image.complete("images/" + java.util.UUID.randomUUID()); entities.persist(image); entities.flush(); return image.getId();
    }
    @Test void save_shouldRejectOverlongCommentAtPersistenceBoundary() {
        ReviewFixtures.Purchase purchase = ReviewFixtures.purchase(entities, OrderStatus.COMPLETED);
        assertThatThrownBy(() -> reviews.saveAndFlush(new ProductReview(purchase.item(), purchase.owner(), purchase.product(), 5, "a".repeat(2001), Instant.EPOCH)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void save_shouldRejectInvalidRatingAtPersistenceBoundary() {
        ReviewFixtures.Purchase purchase = ReviewFixtures.purchase(entities, OrderStatus.COMPLETED);
        assertThatThrownBy(() -> reviews.saveAndFlush(new ProductReview(purchase.item(), purchase.owner(), purchase.product(), 0, null, Instant.EPOCH)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void save_shouldPersistOneReviewPerOrderItem() {
        ReviewFixtures.Purchase purchase = ReviewFixtures.purchase(entities, OrderStatus.COMPLETED);
        ProductReview first = new ProductReview(purchase.item(), purchase.owner(), purchase.product(), 5, null, Instant.EPOCH);
        entities.persist(first); entities.flush(); entities.clear();
        assertThat(reviews.existsByOrderItemId(purchase.item())).isTrue();
        assertThatThrownBy(() -> reviews.saveAndFlush(new ProductReview(purchase.item(), purchase.owner(), purchase.product(), 4, "Again", Instant.EPOCH)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
