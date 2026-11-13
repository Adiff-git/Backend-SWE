package com.example.demo.cart.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

class CartMigrationIT {
    @Test void upgradeFrom20_shouldPreserveUsersAndHistoryAndGrantOnlyUserRole() {
        try (var database = new PostgreSQLContainer<>("postgres:17-alpine")) {
            database.start();
            var source = new DriverManagerDataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword());
            var jdbc = new JdbcTemplate(source);
            Flyway.configure().dataSource(source).target("20").load().migrate();
            jdbc.update("INSERT INTO users(username,password,account_status,joined_at) VALUES ('cart-upgrade','existing-password','ACTIVE',CURRENT_TIMESTAMP)");
            jdbc.update("INSERT INTO roles(name) VALUES ('CUSTOM')");
            jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT u.id,r.id FROM users u CROSS JOIN roles r WHERE u.username='cart-upgrade' AND r.name='USER'");
            var users = jdbc.queryForList("SELECT * FROM users ORDER BY id");
            var assignments = jdbc.queryForList("SELECT * FROM user_roles ORDER BY user_id,role_id");
            var history = jdbc.queryForList("SELECT version,checksum FROM flyway_schema_history WHERE success ORDER BY installed_rank");
            var flyway = Flyway.configure().dataSource(source).target("21").load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
            flyway.validate();
            assertThat(jdbc.queryForList("SELECT * FROM users ORDER BY id")).isEqualTo(users);
            assertThat(jdbc.queryForList("SELECT * FROM user_roles ORDER BY user_id,role_id")).isEqualTo(assignments);
            assertThat(jdbc.queryForList("SELECT version,checksum FROM flyway_schema_history WHERE success AND version<>'21' ORDER BY installed_rank")).isEqualTo(history);
            assertThat(jdbc.queryForList("SELECT r.name FROM roles r JOIN role_permissions rp ON rp.role_id=r.id JOIN permissions p ON p.id=rp.permission_id WHERE p.name='MANAGE_CART'", String.class)).containsExactly("USER");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM carts", Long.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM cart_items", Long.class)).isZero();
            assertThat(flyway.migrate().migrationsExecuted).isZero();
        }
    }
}
