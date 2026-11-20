package com.example.demo.order.repository;

import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderMigrationIT {
    @Test
    void migrate_shouldPreservePriorDataAndEnforceHistoricalIntegrity() {
        try (PostgreSQLContainer<?> database = new PostgreSQLContainer<>("postgres:17-alpine")) {
            database.start();
            DriverManagerDataSource source = new DriverManagerDataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword());
            JdbcTemplate jdbc = new JdbcTemplate(source);
            TransactionTemplate transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
            Flyway.configure().dataSource(source).target("22").load().migrate();
            Long owner = jdbc.queryForObject("INSERT INTO users(username,password,account_status,joined_at) VALUES ('order-migration','test-password','ACTIVE',CURRENT_TIMESTAMP) RETURNING id", Long.class);
            jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE name='USER'",owner);
            jdbc.update("INSERT INTO carts(user_id) VALUES (?)",owner);
            java.util.List<Map<String,Object>> rolesBefore = jdbc.queryForList("SELECT * FROM user_roles ORDER BY user_id,role_id");
            java.util.List<Map<String,Object>> cartsBefore = jdbc.queryForList("SELECT id,user_id FROM carts ORDER BY id");
            Map<String, Object> original = jdbc.queryForMap("SELECT * FROM users WHERE id=?", owner);
            Flyway migration = Flyway.configure().dataSource(source).load();
            migration.migrate();
            migration.validate();
            assertThat(jdbc.queryForMap("SELECT * FROM users WHERE id=?", owner)).isEqualTo(original);
            assertThat(jdbc.queryForList("SELECT * FROM user_roles ORDER BY user_id,role_id")).isEqualTo(rolesBefore);
            assertThat(jdbc.queryForList("SELECT id,user_id FROM carts ORDER BY id")).isEqualTo(cartsBefore);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_roles ur JOIN role_permissions rp ON ur.role_id=rp.role_id JOIN permissions p ON p.id=rp.permission_id WHERE ur.user_id=? AND p.name='MANAGE_ORDER'",Long.class,owner)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders", Long.class)).isZero();
            assertThat(jdbc.queryForList("SELECT r.name FROM roles r JOIN role_permissions rp ON r.id=rp.role_id JOIN permissions p ON p.id=rp.permission_id WHERE p.name='MANAGE_ORDER'", String.class)).containsExactly("USER");
            assertThatThrownBy(() -> transactions.executeWithoutResult(s -> insertOrder(jdbc, owner)))
                    .hasStackTraceContaining("Order items do not match historical merchandise total");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders", Long.class)).isZero();
            Long id = transactions.execute(s -> {
                Long order = insertOrder(jdbc, owner);
                jdbc.update("INSERT INTO order_items(order_id,product_name_snapshot,variant_sku_snapshot,variant_options_snapshot,quantity,unit_price_snapshot,line_total) VALUES (?,'Old name','Old SKU','{}',2,50,100)", order);
                return order;
            });
            for (String assignment : new String[] {"payable_total=-1", "payable_total=1.5", "payable_total='NaN'", "payable_total='Infinity'", "status='DELIVERED'", "status='CANCELLED'", "completed_at=CURRENT_TIMESTAMP", "recipient_name_snapshot=''", "cancellation_reason='invalid'"}) {
                assertThatThrownBy(() -> jdbc.update("UPDATE orders SET " + assignment + " WHERE id=?", id))
                        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            }
            for (String assignment : new String[] {"quantity=0", "quantity=-1", "quantity=3", "unit_price_snapshot=0.5", "line_total=-1", "variant_options_snapshot='[]'", "variant_options_snapshot='{\"color\":4}'", "product_name_snapshot=''"}) {
                assertThatThrownBy(() -> jdbc.update("UPDATE order_items SET " + assignment + " WHERE order_id=?", id))
                        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            }
            jdbc.update("DELETE FROM user_roles WHERE user_id=?",owner);
            jdbc.update("DELETE FROM carts WHERE user_id=?",owner);
            assertThatThrownBy(() -> jdbc.update("DELETE FROM users WHERE id=?", owner)).hasStackTraceContaining("fk_orders_customer_id");
            assertThatThrownBy(() -> transactions.executeWithoutResult(s -> jdbc.update("UPDATE orders SET merchandise_total=200 WHERE id=?", id)))
                    .hasStackTraceContaining("Order items do not match historical merchandise total");
            assertThat(jdbc.queryForObject("SELECT merchandise_total FROM orders WHERE id=?", Integer.class, id)).isEqualTo(100);
            jdbc.update("UPDATE orders SET status='CANCELLED',cancelled_at=CURRENT_TIMESTAMP,cancellation_reason='Changed mind' WHERE id=?", id);
            assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, id)).isEqualTo("CANCELLED");
            String emoji = new String(Character.toChars(0x1F600));
            jdbc.update("UPDATE orders SET cancellation_reason=? WHERE id=?",emoji.repeat(500),id);
            assertThatThrownBy(() -> jdbc.update("UPDATE orders SET cancellation_reason=? WHERE id=?",emoji.repeat(501),id))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        }
    }

    private Long insertOrder(JdbcTemplate jdbc, Long owner) {
        return jdbc.queryForObject("INSERT INTO orders(customer_id,order_number,placed_at,status,recipient_name_snapshot,recipient_phone_snapshot,delivery_address_snapshot,merchandise_total,payable_total,inventory_managed) VALUES (?,'HISTORICAL-'||gen_random_uuid(),CURRENT_TIMESTAMP,'PENDING','Recipient','0123456789','Old address',100,90,false) RETURNING id", Long.class, owner);
    }
}
