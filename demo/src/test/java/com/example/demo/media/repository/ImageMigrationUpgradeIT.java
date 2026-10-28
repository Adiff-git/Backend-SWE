package com.example.demo.media.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

class ImageMigrationUpgradeIT {
    @Test void migrate_shouldRetainPreexistingUser_whenUpgradingFromAuthenticationSchema() {
        try (PostgreSQLContainer<?> database = new PostgreSQLContainer<>("postgres:17-alpine")) {
            database.start();
            DriverManagerDataSource data = new DriverManagerDataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword());
            Flyway.configure().dataSource(data).target("7").load().migrate();
            JdbcTemplate jdbc = new JdbcTemplate(data);
            Long id = jdbc.queryForObject("INSERT INTO users(username,password,account_status,joined_at) VALUES ('Existing','encoded','ACTIVE',NOW()) RETURNING id", Long.class);
            Flyway.configure().dataSource(data).load().migrate();
            assertThat(jdbc.queryForObject("SELECT username FROM users WHERE id=?", String.class, id)).isEqualTo("Existing");
            assertThat(jdbc.queryForObject("SELECT profile_image_id FROM users WHERE id=?", Long.class, id)).isNull();
        }
    }
}
