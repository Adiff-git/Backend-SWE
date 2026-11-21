package com.example.demo.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.config.ReviewIntegrationSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class UserReviewReadServiceIT extends ReviewIntegrationSupport {
    @Autowired UserReviewReadService users;
    @Test void getReviewAuthors_shouldAnonymizePersistedInactiveIdentity() {
        jdbc.update("UPDATE users SET account_status='INACTIVE',display_name='Private display' WHERE id=?", purchase.owner());
        assertThat(users.getReviewAuthors(java.util.List.of(purchase.owner())).get(purchase.owner()).getDisplayName()).isEqualTo("Former customer");
    }
}
