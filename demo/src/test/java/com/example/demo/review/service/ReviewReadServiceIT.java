package com.example.demo.review.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.config.ReviewIntegrationSupport;
import com.example.demo.fixture.review.ReviewFixtures;
import com.example.demo.review.dto.request.ReviewListRequest;
import com.example.demo.review.dto.response.ReviewPage;
import com.example.demo.review.entity.ProductReview;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ReviewReadServiceIT extends ReviewIntegrationSupport {
    @Autowired ReviewReadService reads;
    @Autowired ReviewStatisticsService statistics;
    @Autowired EntityManagerFactory factory;
    @Test void filters_shouldUseUnicodeCommentPresenceAndKeepProductWideStatisticsAndHistory() {
        transactions.executeWithoutResult(tx -> {
            for (int i=0;i<4;i++) {
                long item = i==0 ? purchase.item() : ReviewFixtures.additionalItem(entities,purchase.owner(),purchase.product());
                entities.persist(new ProductReview(item,purchase.owner(),purchase.product(),i==0 ? 1 : 5,
                        new String[]{null,"\u00a0\u2003\u3000", " text ", "\u200b"}[i],Instant.EPOCH));
            }
        });
        var params = new org.springframework.util.LinkedMultiValueMap<String,String>();
        params.add("rating","5"); params.add("hasComment","true"); params.add("hasImages","false");
        var result = reads.getReviews(purchase.product(),ReviewListRequest.parse(params));
        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getItems()).extracting(com.example.demo.review.dto.response.ReviewResponse::getComment).containsExactly("\u200b"," text ");
        var json=tools.jackson.databind.json.JsonMapper.builder().build().valueToTree(result);
        assertThat(json.get("items").get(0).has("purchasedOptions")).isTrue();
        assertThat(json.toString()).doesNotContain("recipientName", "orderId", "customerId");
        params.set("hasComment","false");
        assertThat(reads.getReviews(purchase.product(),ReviewListRequest.parse(params)).getTotalElements()).isEqualTo(1);
        params.set("page","100");
        var empty=reads.getReviews(purchase.product(),ReviewListRequest.parse(params));
        assertThat(empty.getItems()).isEmpty(); assertThat(empty.getTotalElements()).isEqualTo(1);
        var summary=tools.jackson.databind.json.JsonMapper.builder().build().valueToTree(statistics.getSummary(purchase.product()));
        assertThat(summary.get("reviewCount").asLong()).isEqualTo(4);
        assertThat(summary.get("ratingDistribution").get("1").asLong()).isEqualTo(1);
        assertThat(summary.get("ratingDistribution").get("5").asLong()).isEqualTo(3);
        assertThat(summary.get("ratingDistribution").get("2").asLong()).isZero();
    }
    @Test void getSummary_shouldExcludeUncommittedReview() {
        try (java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            transactions.executeWithoutResult(status -> {
                entities.persist(new ProductReview(purchase.item(), purchase.owner(), purchase.product(), 5, null, Instant.EPOCH));
                entities.flush();
                try {
                    assertThat(executor.submit(() -> statistics.getSummary(purchase.product()).getReviewCount()).get(10, java.util.concurrent.TimeUnit.SECONDS)).isZero();
                } catch (Exception failure) { throw new AssertionError(failure); }
                status.setRollbackOnly();
            });
        }
        assertThat(statistics.getSummary(purchase.product()).getReviewCount()).isZero();
    }
    @Test void getReviews_shouldKeepStablePaginationAndBoundQueriesAcrossPageSizes() {
        transactions.executeWithoutResult(s -> {
            for (int index = 0; index < 100; index++) {
                Long item = index == 0 ? purchase.item() : ReviewFixtures.additionalItem(entities, purchase.owner(), purchase.product());
                entities.persist(new ProductReview(item, purchase.owner(), purchase.product(), index % 5 + 1, null, Instant.EPOCH));
            }
        });
        Statistics sql = factory.unwrap(SessionFactory.class).getStatistics(); sql.setStatisticsEnabled(true);
        for (int size : new int[]{1, 20, 100}) {
            sql.clear(); ReviewPage result = reads.getReviews(purchase.product(), new ReviewListRequest(0, size, "createdAt,desc"));
            assertThat(result.getItems()).hasSize(size); assertThat(result.getTotalElements()).isEqualTo(100);
            assertThat(sql.getPrepareStatementCount()).isLessThanOrEqualTo(8);
        }
        Set<Long> seen = new HashSet<>();
        for (int page = 0; page < 5; page++) reads.getReviews(purchase.product(), new ReviewListRequest(page, 20, "rating,asc")).getItems().forEach(r -> assertThat(seen.add(r.getId())).isTrue());
        assertThat(seen).hasSize(100);
        assertThat(statistics.getSummary(purchase.product()).getAverageRating()).isEqualByComparingTo("3.00");
        assertThat(reads.getReviews(purchase.product(), new ReviewListRequest(Integer.MAX_VALUE, 100, "createdAt,desc")).getItems()).isEmpty();
    }
}
