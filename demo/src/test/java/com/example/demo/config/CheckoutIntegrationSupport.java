package com.example.demo.config;

import com.example.demo.fixture.checkout.CheckoutFixtures;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/** Shared real infrastructure/committed setup for checkout's distinct domain integration boundaries. */
@SpringBootTest
@ActiveProfiles("test")
public abstract class CheckoutIntegrationSupport {
    @Autowired protected EntityManager entities;
    @Autowired protected TransactionTemplate transactions;
    @Autowired protected JdbcTemplate jdbc;
    protected CheckoutFixtures.Scenario fixture;

    @BeforeEach void prepareCheckoutFixture() {
        cleanCheckoutFixture();
        fixture = transactions.execute(s -> CheckoutFixtures.scenario(entities));
    }

    @AfterEach void cleanCheckoutFixture() { jdbc.execute("TRUNCATE TABLE users,categories CASCADE"); }
}
