package com.example.demo.cart.repository;

import com.example.demo.fixture.wishlist.WishlistFixtures;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class CartRepositoryIT {
    @Autowired EntityManager entities;
    @Autowired JdbcTemplate jdbc;

    @Test void schema_shouldPermitOnlyOneCartPerUser() {
        Long owner = WishlistFixtures.persistUser(entities).getId();
        entities.flush();
        jdbc.update("INSERT INTO carts(user_id) VALUES (?)", owner);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM carts WHERE user_id=?", Long.class, owner)).isEqualTo(1L);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO carts(user_id) VALUES (?)", owner))
                .hasMessageContaining("uk_carts_user_id");
    }
}
