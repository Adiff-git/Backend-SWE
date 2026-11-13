package com.example.demo.behavior;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

class BehaviorMigrationIT {
    @Test void upgradeFrom19PreservesAppliedHistoryAndBusinessData() {
        try (var database = new PostgreSQLContainer<>("postgres:17-alpine")) {
            database.start();
            var ds = new DriverManagerDataSource(database.getJdbcUrl(),database.getUsername(),database.getPassword());
            var jdbc = new JdbcTemplate(ds);
            Flyway.configure().dataSource(ds).target("19").load().migrate();
            jdbc.update("INSERT INTO users(username,password,account_status,joined_at) VALUES ('upgrade','test','ACTIVE',CURRENT_TIMESTAMP)");
            var history = jdbc.queryForList("SELECT version,checksum FROM flyway_schema_history WHERE success ORDER BY installed_rank");
            var rows = jdbc.queryForList("SELECT * FROM users");
            var flyway = Flyway.configure().dataSource(ds).target("20").load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
            flyway.validate();
            assertThat(jdbc.queryForList("SELECT version,checksum FROM flyway_schema_history WHERE success AND version<>'20' ORDER BY installed_rank")).isEqualTo(history);
            assertThat(jdbc.queryForList("SELECT * FROM users")).isEqualTo(rows);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM user_behavior_events",Long.class)).isZero();
            assertThat(flyway.migrate().migrationsExecuted).isZero();
        }
    }
}
