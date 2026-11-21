package com.example.demo.review.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.fixture.review.ReviewFixtures;
import com.example.demo.order.entity.OrderStatus;
import com.example.demo.review.entity.ProductReview;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest @ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) @ActiveProfiles("test")
class ProductReviewReadRepositoryIT {
    @Autowired EntityManager entities;
    @Autowired ProductReviewRepository reviews;
    @Test void aggregate_shouldCountAllPurchasesIndependentlyOfPage() {
        ReviewFixtures.Purchase purchase = ReviewFixtures.purchase(entities, OrderStatus.COMPLETED);
        for (int rating : new int[]{1, 4, 5}) entities.persist(new ProductReview(ReviewFixtures.additionalItem(entities, purchase.owner(), purchase.product()), purchase.owner(), purchase.product(), rating, null, Instant.EPOCH));
        entities.flush(); entities.clear();
        assertThat(reviews.aggregate(purchase.product()).getReviewCount()).isEqualTo(3);
        assertThat(reviews.aggregate(purchase.product()).getAverageRating()).isBetween(new java.math.BigDecimal("3.33"), new java.math.BigDecimal("3.34"));
        assertThat(reviews.findByProductId(purchase.product(), PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "rating", "id"))).getContent()).extracting(ProductReview::getRating).containsExactly((short) 5);
    }
}
