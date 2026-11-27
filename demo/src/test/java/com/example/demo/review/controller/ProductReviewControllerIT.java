package com.example.demo.review.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.config.ReviewIntegrationSupport;
import com.example.demo.review.entity.ProductReview;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class ProductReviewControllerIT extends ReviewIntegrationSupport {
    @Autowired MockMvc mvc;
    @Test void list_shouldAllowAnonymousAndAnonymizeInactiveAuthor() throws Exception {
        transactions.executeWithoutResult(s -> entities.persist(new ProductReview(purchase.item(), purchase.owner(), purchase.product(), 4, "Purchased", Instant.EPOCH)));
        jdbc.update("UPDATE users SET account_status='INACTIVE',display_name='Private identity' WHERE id=?", purchase.owner());
        mvc.perform(get("/api/products/" + purchase.product() + "/reviews"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.items[0].author.displayName").value("Former customer"))
                .andExpect(jsonPath("$.items[0].author.profileImage").isEmpty())
                .andExpect(jsonPath("$.items[0].authorId").doesNotExist())
                .andExpect(jsonPath("$.items[0].purchasedOptions").isMap());
        mvc.perform(get("/api/products/" + purchase.product() + "/rating-summary"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reviewCount").value(1)).andExpect(jsonPath("$.averageRating").value(4))
                .andExpect(jsonPath("$.ratingDistribution.4").value(1)).andExpect(jsonPath("$.ratingDistribution.1").value(0));
    }
    @Test void list_shouldReturn404ForUnpublishedProductAnd401ForInvalidSuppliedToken() throws Exception {
        jdbc.update("UPDATE products SET status='ARCHIVED' WHERE id=?", purchase.product());
        mvc.perform(get("/api/products/" + purchase.product() + "/reviews")).andExpect(status().isNotFound());
        mvc.perform(get("/api/products/" + purchase.product() + "/rating-summary")).andExpect(status().isNotFound());
        mvc.perform(get("/api/products/" + purchase.product() + "/reviews").header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
    }
}
