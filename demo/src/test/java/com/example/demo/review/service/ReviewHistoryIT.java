package com.example.demo.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo.config.ReviewIntegrationSupport;
import com.example.demo.review.dto.request.CreateReviewRequest;
import com.example.demo.review.dto.request.PatchReviewRequest;
import com.example.demo.review.dto.response.ReviewResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.json.JsonMapper;

class ReviewHistoryIT extends ReviewIntegrationSupport {
    @Autowired ReviewService reviews;
    @Autowired ReviewStatisticsService statistics;
    @Autowired ReviewReadService reads;
    @Test void purchasedOptions_shouldComeFromOrderSnapshotAfterLiveVariantRenameAndRemoval() {
        JsonMapper json=JsonMapper.builder().build();
        var created=reviews.create(purchase.owner(),purchase.order(),purchase.item(),json.readValue("{\"rating\":5}",CreateReviewRequest.class));
        var original=json.readTree(jdbc.queryForObject("SELECT variant_options_snapshot::TEXT FROM order_items WHERE id=?",String.class,purchase.item()));
        transactions.executeWithoutResult(tx -> {
            var product=entities.getReference(com.example.demo.catalog.entity.Product.class,purchase.product());
            entities.persist(com.example.demo.catalog.CatalogFixtures.variant(product,java.util.Map.of("choice","replacement"),java.math.BigDecimal.ONE));
            entities.flush();
            jdbc.update("UPDATE product_variants SET option_combination='{\"choice\":\"renamed\"}' WHERE id=(SELECT product_variant_id FROM order_items WHERE id=?)",purchase.item());
            jdbc.update("DELETE FROM product_variants WHERE id=(SELECT product_variant_id FROM order_items WHERE id=?)",purchase.item());
        });
        var response=json.valueToTree(reads.getReviews(purchase.product(),new com.example.demo.review.dto.request.ReviewListRequest(0,20,"createdAt,desc")));
        assertThat(response.get("items").get(0).get("purchasedOptions")).isEqualTo(original);
        assertThat(response.toString()).doesNotContain("orderId","customerId","recipientName","deliveryAddress");
        assertThat(response.get("items").get(0).get("id").longValue()).isEqualTo(created.getId());
    }
    @Test void reviewHistory_shouldSurviveCatalogRemovalAndRemainEditable() {
        JsonMapper json = JsonMapper.builder().build();
        ReviewResponse created = reviews.create(purchase.owner(), purchase.order(), purchase.item(), json.readValue("{\"rating\":1}", CreateReviewRequest.class));
        transactions.executeWithoutResult(s -> {
            jdbc.update("DELETE FROM product_variants WHERE product_id=?", purchase.product());
            jdbc.update("DELETE FROM products WHERE id=?", purchase.product());
        });
        assertThat(jdbc.queryForObject("SELECT product_id FROM product_reviews WHERE id=?", java.util.UUID.class, created.getId())).isEqualTo(purchase.product());
        assertThat(reviews.update(purchase.owner(), created.getId(), json.readValue("{\"rating\":5}", PatchReviewRequest.class)).getRating()).isEqualTo(5);
        assertThatThrownBy(() -> statistics.getSummary(purchase.product())).isInstanceOf(com.example.demo.catalog.exception.ProductNotFoundException.class);
    }
    @Test void reviewHistory_shouldRetainInactiveReviewsAndRejectMutation() {
        JsonMapper json = JsonMapper.builder().build();
        ReviewResponse created = reviews.create(purchase.owner(), purchase.order(), purchase.item(), json.readValue("{\"rating\":5}", CreateReviewRequest.class));
        jdbc.update("UPDATE users SET account_status='INACTIVE' WHERE id=?", purchase.owner());
        assertThat(statistics.getSummary(purchase.product()).getReviewCount()).isEqualTo(1);
        assertThatThrownBy(() -> reviews.update(purchase.owner(), created.getId(), json.readValue("{\"rating\":1}", PatchReviewRequest.class)))
                .isInstanceOf(com.example.demo.common.identity.IdentityUnavailableException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM users WHERE id=?", purchase.owner())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
