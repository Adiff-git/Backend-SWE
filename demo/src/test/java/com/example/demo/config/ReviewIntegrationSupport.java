package com.example.demo.config;

import com.example.demo.fixture.review.ReviewFixtures;
import com.example.demo.order.entity.OrderStatus;
import com.example.demo.storage.service.ObjectStorageService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest @ActiveProfiles("test")
public abstract class ReviewIntegrationSupport {
    @Autowired protected EntityManager entities;
    @Autowired protected TransactionTemplate transactions;
    @Autowired protected JdbcTemplate jdbc;
    @MockitoBean protected ObjectStorageService storage;
    protected ReviewFixtures.Purchase purchase;
    @BeforeEach void createReviewFixture() {
        org.mockito.Mockito.when(storage.prepareDownloadSigner(org.mockito.ArgumentMatchers.any()))
                .thenReturn(key -> "https://isolated-review.invalid/" + key);
        purchase = transactions.execute(status -> ReviewFixtures.purchase(entities, OrderStatus.COMPLETED));
    }
    protected java.util.UUID completedImage() {
        return transactions.execute(status -> {
            com.example.demo.media.entity.ImageUpload image = com.example.demo.media.entity.ImageUpload.pending(purchase.owner(), "review/staging/" + java.util.UUID.randomUUID(), "image/png", java.time.Instant.EPOCH, java.time.Instant.EPOCH.plusSeconds(1));
            image.complete("review/final/" + java.util.UUID.randomUUID());
            entities.persist(image); entities.flush(); return image.getId();
        });
    }
    @AfterEach void cleanReviewFixture() {
        transactions.executeWithoutResult(status -> {
        jdbc.update("DELETE FROM product_review_images WHERE review_id IN (SELECT id FROM product_reviews WHERE author_id=?)", purchase.owner());
        jdbc.update("DELETE FROM product_reviews WHERE author_id=?", purchase.owner());
        jdbc.update("DELETE FROM order_items WHERE order_id IN (SELECT id FROM orders WHERE customer_id IN (?,?))", purchase.owner(), purchase.foreign());
        jdbc.update("DELETE FROM orders WHERE customer_id IN (?,?)", purchase.owner(), purchase.foreign());
        jdbc.update("DELETE FROM image_uploads WHERE owner_user_id IN (?,?)", purchase.owner(), purchase.foreign());
        jdbc.update("DELETE FROM users WHERE id IN (?,?)", purchase.owner(), purchase.foreign());
        });
    }
}
