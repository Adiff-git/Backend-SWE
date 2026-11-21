package com.example.demo.review.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@org.springframework.boot.autoconfigure.ImportAutoConfiguration(org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ReviewMigrationIT {
    @Autowired JdbcTemplate jdbc;
    @Autowired javax.sql.DataSource dataSource;

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void migration_shouldUpgradeV26WithoutChangingExistingAccounts() {
        // Historical migrations intentionally create public functions: isolate the entire database.
        try (org.testcontainers.containers.PostgreSQLContainer<?> database = new org.testcontainers.containers.PostgreSQLContainer<>("postgres:17-alpine")) {
            database.start();
            org.springframework.jdbc.datasource.DriverManagerDataSource source = new org.springframework.jdbc.datasource.DriverManagerDataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword());
            JdbcTemplate upgrade = new JdbcTemplate(source);
            org.flywaydb.core.Flyway.configure().dataSource(source).target("26").load().migrate();
            upgrade.update("INSERT INTO users(username,account_status,joined_at) VALUES ('retained-review-customer','ACTIVE',CURRENT_TIMESTAMP)");
            org.flywaydb.core.Flyway.configure().dataSource(source).load().migrate();
            assertThat(upgrade.queryForObject("SELECT COUNT(*) FROM users WHERE username='retained-review-customer'", Long.class)).isEqualTo(1L);
            assertThat(upgrade.queryForObject("SELECT COUNT(*) FROM product_reviews", Long.class)).isZero();
        }
    }

    @Test void migration_shouldCreateReviewTablesAndChecks() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_name IN ('product_reviews','product_review_images')", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT conname FROM pg_constraint WHERE conrelid='product_reviews'::REGCLASS", String.class))
                .contains("uk_product_reviews_order_item_id", "ck_product_reviews_rating", "ck_product_reviews_comment_length");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO product_reviews(order_item_id,author_id,product_id,rating,created_at,updated_at) VALUES (1,1,?,6,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", UUID.randomUUID()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
