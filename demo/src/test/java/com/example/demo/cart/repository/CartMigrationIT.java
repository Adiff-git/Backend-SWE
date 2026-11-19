package com.example.demo.cart.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CartMigrationIT {
    @Test void upgradeTo22_shouldRejectLegacy1000WithoutCappingThenPreserveReviewed999() {
        try (PostgreSQLContainer<?> database = new PostgreSQLContainer<>("postgres:17-alpine")) {
            database.start();
            DriverManagerDataSource source = new DriverManagerDataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword());
            JdbcTemplate jdbc = new JdbcTemplate(source);
            Flyway.configure().dataSource(source).target("21").load().migrate();
            java.util.UUID category = java.util.UUID.randomUUID();
            java.util.UUID product = java.util.UUID.randomUUID();
            java.util.UUID variant = java.util.UUID.randomUUID();
            org.springframework.transaction.support.TransactionTemplate transaction = new org.springframework.transaction.support.TransactionTemplate(
                    new org.springframework.jdbc.datasource.DataSourceTransactionManager(source));
            transaction.executeWithoutResult(state -> {
                jdbc.update("INSERT INTO categories(id,name) VALUES (?,'Legacy category')", category);
                jdbc.update("INSERT INTO products(id,category_id,name,status) VALUES (?,?,'Legacy product','DRAFT')", product, category);
                jdbc.update("INSERT INTO product_variants(id,product_id,sku,option_combination,selling_price) VALUES (?,?,?,'{}'::jsonb,10)", variant, product, "SKU-" + variant);
                Long owner = jdbc.queryForObject("INSERT INTO users(username,password,account_status,joined_at) VALUES ('legacy-cart','existing-password','ACTIVE',CURRENT_TIMESTAMP) RETURNING id", Long.class);
                Long cart = jdbc.queryForObject("INSERT INTO carts(user_id) VALUES (?) RETURNING id", Long.class, owner);
                jdbc.update("INSERT INTO cart_items(cart_id,product_id,product_variant_id,quantity,selected,added_at,public_product_name,public_sku,public_options) VALUES (?,?,?,1000,FALSE,CURRENT_TIMESTAMP,'Legacy product','Prior SKU','{}'::jsonb)", cart, product, variant);
            });
            Flyway migration = Flyway.configure().dataSource(source).target("22").load();
            assertThatThrownBy(migration::migrate).hasStackTraceContaining("review incompatible rows before retrying");
            assertThat(jdbc.queryForObject("SELECT quantity FROM cart_items", Integer.class)).isEqualTo(1000);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version='22' AND success", Long.class)).isZero();
            jdbc.update("UPDATE cart_items SET quantity=999");
            java.util.Map<String, Object> reviewed = jdbc.queryForMap("SELECT * FROM cart_items");
            assertThat(migration.migrate().migrationsExecuted).isEqualTo(1);
            migration.validate();
            assertThat(jdbc.queryForMap("SELECT * FROM cart_items")).isEqualTo(reviewed);
            assertThatThrownBy(() -> jdbc.update("UPDATE cart_items SET quantity=1000"))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(jdbc.queryForObject("SELECT quantity FROM cart_items", Integer.class)).isEqualTo(999);
        }
    }

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
