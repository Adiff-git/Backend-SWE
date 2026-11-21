package com.example.demo.review.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.config.ReviewIntegrationSupport;
import com.example.demo.review.dto.request.CreateReviewRequest;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.json.JsonMapper;

class ReviewConcurrencyIT extends ReviewIntegrationSupport {
    @Autowired ReviewService reviews;
    @Autowired com.example.demo.common.exception.ApiErrorFactory errors;
    @Test void create_shouldExposeSafeConflictAfterBoundedLockTimeout() throws Exception {
        try (java.util.concurrent.ExecutorService executor = Executors.newSingleThreadExecutor()) {
            transactions.executeWithoutResult(s -> {
                jdbc.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE", Long.class, purchase.owner());
                Future<Throwable> attempt = executor.submit(() -> {
                    try {
                        reviews.create(purchase.owner(), purchase.order(), purchase.item(), JsonMapper.builder().build().readValue("{\"rating\":5}", CreateReviewRequest.class));
                        return null;
                    } catch (RuntimeException conflict) { return conflict; }
                });
                try {
                    Throwable failure = attempt.get(15, TimeUnit.SECONDS);
                    assertThat(failure).isNotNull();
                    assertThat(errors.from(failure, "/api/me/orders/1/items/1/reviews").getStatus()).isEqualTo(409);
                } catch (Exception failure) { throw new AssertionError(failure); }
            });
        }
    }
    @Test void update_shouldKeepCompleteImageOrder_whenReorderAndRatingCompete() throws Exception {
        Long id = reviews.create(purchase.owner(), purchase.order(), purchase.item(), JsonMapper.builder().build().readValue("{\"rating\":1}", CreateReviewRequest.class)).getId();
        java.util.UUID first = completedImage(); java.util.UUID second = completedImage();
        CountDownLatch start = new CountDownLatch(1);
        try (java.util.concurrent.ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> images = executor.submit(() -> { await(start); reviews.update(purchase.owner(), id, JsonMapper.builder().build().readValue("{\"imageIds\":[\"" + second + "\",\"" + first + "\"]}", com.example.demo.review.dto.request.PatchReviewRequest.class)); });
            Future<?> rating = executor.submit(() -> { await(start); reviews.update(purchase.owner(), id, JsonMapper.builder().build().readValue("{\"rating\":5}", com.example.demo.review.dto.request.PatchReviewRequest.class)); });
            start.countDown(); images.get(20, TimeUnit.SECONDS); rating.get(20, TimeUnit.SECONDS);
        }
        assertThat(jdbc.queryForList("SELECT image_id FROM product_review_images WHERE review_id=? ORDER BY display_order", java.util.UUID.class, id)).containsExactly(second, first);
        assertThat(jdbc.queryForObject("SELECT rating FROM product_reviews WHERE id=?", Integer.class, id)).isEqualTo(5);
    }
    @Test void create_shouldRecheckActiveStateAfterConcurrentUserLock() throws Exception {
        java.util.concurrent.atomic.AtomicReference<Future<?>> waiting = new java.util.concurrent.atomic.AtomicReference<>();
        CountDownLatch started = new CountDownLatch(1);
        try (java.util.concurrent.ExecutorService executor = Executors.newSingleThreadExecutor()) {
            transactions.executeWithoutResult(s -> {
                jdbc.update("UPDATE users SET account_status='INACTIVE' WHERE id=?", purchase.owner());
                waiting.set(executor.submit(() -> {
                    started.countDown();
                    reviews.create(purchase.owner(), purchase.order(), purchase.item(), JsonMapper.builder().build().readValue("{\"rating\":5}", CreateReviewRequest.class));
                }));
                await(started);
            });
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> waiting.get().get(20, TimeUnit.SECONDS))
                    .hasRootCauseInstanceOf(com.example.demo.common.identity.IdentityUnavailableException.class);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_reviews WHERE order_item_id=?", Long.class, purchase.item())).isZero();
    }
    @Test void create_shouldNotInventProductIdentity_whenCatalogLinkIsRemovedConcurrently() throws Exception {
        java.util.concurrent.atomic.AtomicReference<Future<?>> waiting = new java.util.concurrent.atomic.AtomicReference<>();
        CountDownLatch started = new CountDownLatch(1);
        try (java.util.concurrent.ExecutorService executor = Executors.newSingleThreadExecutor()) {
            transactions.executeWithoutResult(s -> {
                jdbc.queryForObject("SELECT id FROM orders WHERE id=? FOR UPDATE", Long.class, purchase.order());
                jdbc.update("UPDATE order_items SET product_id=NULL WHERE id=?", purchase.item());
                waiting.set(executor.submit(() -> {
                    started.countDown();
                    reviews.create(purchase.owner(), purchase.order(), purchase.item(), JsonMapper.builder().build().readValue("{\"rating\":5}", CreateReviewRequest.class));
                }));
                await(started);
            });
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> waiting.get().get(20, TimeUnit.SECONDS))
                    .hasRootCauseInstanceOf(com.example.demo.order.exception.OrderConflictException.class);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_reviews WHERE order_item_id=?", Long.class, purchase.item())).isZero();
    }
    @Test void update_shouldPreserveDisjointChanges_whenPatchesCompete() throws Exception {
        Long id = reviews.create(purchase.owner(), purchase.order(), purchase.item(), JsonMapper.builder().build().readValue("{\"rating\":1}", CreateReviewRequest.class)).getId();
        CountDownLatch start = new CountDownLatch(1);
        try (java.util.concurrent.ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> rating = executor.submit(() -> { await(start); reviews.update(purchase.owner(), id, JsonMapper.builder().build().readValue("{\"rating\":5}", com.example.demo.review.dto.request.PatchReviewRequest.class)); });
            Future<?> comment = executor.submit(() -> { await(start); reviews.update(purchase.owner(), id, JsonMapper.builder().build().readValue("{\"comment\":\"latest\"}", com.example.demo.review.dto.request.PatchReviewRequest.class)); });
            start.countDown(); rating.get(20, TimeUnit.SECONDS); comment.get(20, TimeUnit.SECONDS);
        }
        assertThat(jdbc.queryForObject("SELECT rating FROM product_reviews WHERE id=?", Integer.class, id)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT comment FROM product_reviews WHERE id=?", String.class, id)).isEqualTo("latest");
    }
    private static void await(CountDownLatch start) {
        try { if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("Start barrier timed out"); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
    }
    @Test void create_shouldCommitExactlyOneReview_whenRequestsCompete() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (java.util.concurrent.ExecutorService executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Boolean> create = () -> {
                if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("Start barrier timed out");
                try {
                    reviews.create(purchase.owner(), purchase.order(), purchase.item(), JsonMapper.builder().build().readValue("{\"rating\":5}", CreateReviewRequest.class));
                    return true;
                } catch (com.example.demo.review.exception.ReviewConflictException duplicate) { return false; }
            };
            Future<Boolean> first = executor.submit(create); Future<Boolean> second = executor.submit(create); start.countDown();
            assertThat(java.util.List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_reviews WHERE order_item_id=?", Long.class, purchase.item())).isEqualTo(1L);
    }
}
