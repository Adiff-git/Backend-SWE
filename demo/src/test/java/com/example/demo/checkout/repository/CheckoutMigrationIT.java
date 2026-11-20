package com.example.demo.checkout.repository;

import com.example.demo.fixture.checkout.CheckoutFixtures;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CheckoutMigrationIT {
    @Test
    void migrate_shouldPreserveHistoryAndEnforceImmutableOwnedReceiptAtCommit() {
        try (PostgreSQLContainer<?> database = new PostgreSQLContainer<>("postgres:17-alpine")) {
            database.start();
            DriverManagerDataSource source = new DriverManagerDataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword());
            JdbcTemplate jdbc = new JdbcTemplate(source);
            TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(source));
            Flyway.configure().dataSource(source).target("23").load().migrate();
            Long owner = CheckoutFixtures.customer(jdbc);
            Long foreign = CheckoutFixtures.customer(jdbc);
            jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE name='USER'", owner);
            jdbc.update("INSERT INTO carts(user_id) VALUES (?)", owner);
            CheckoutFixtures.HistoricalOrder historical = tx.execute(s -> CheckoutFixtures.preInventoryOrder(jdbc, owner));
            List<?> originalCart = jdbc.queryForList("SELECT id,user_id FROM carts");
            List<?> originalOrder = jdbc.queryForList("SELECT to_jsonb(o)-ARRAY['inventory_managed','voucher_discount','voucher_snapshot'] AS historical FROM orders o");
            Flyway.configure().dataSource(source).load().migrate();
            assertThat(jdbc.queryForList("SELECT id,user_id FROM carts")).isEqualTo(originalCart);
            assertThat(jdbc.queryForList("SELECT to_jsonb(o)-ARRAY['inventory_managed','voucher_discount','voucher_snapshot'] AS historical FROM orders o")).isEqualTo(originalOrder);
            assertThat(jdbc.queryForObject("SELECT inventory_managed FROM orders WHERE id=?", Boolean.class, historical.id())).isFalse();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM permissions WHERE name='MANAGE_CHECKOUT'", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForList("SELECT r.name FROM roles r JOIN role_permissions rp ON r.id=rp.role_id JOIN permissions p ON p.id=rp.permission_id WHERE p.name='MANAGE_CHECKOUT'", String.class)).containsExactly("USER");
            assertThatThrownBy(() -> tx.executeWithoutResult(s -> CheckoutFixtures.receipt(jdbc, foreign, historical, "Foreign_key_12345")))
                    .hasStackTraceContaining("fk_checkout_receipts_order_id_customer_id");
            tx.executeWithoutResult(s -> CheckoutFixtures.receipt(jdbc, owner, historical, "Original_key_1234"));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM checkout_receipts", Integer.class)).isEqualTo(1);
            assertThatThrownBy(() -> jdbc.update("UPDATE checkout_receipts SET request_address_id=2"))
                    .hasStackTraceContaining("immutable");
            assertThatThrownBy(() -> tx.executeWithoutResult(s -> CheckoutFixtures.receipt(jdbc, owner, historical, "Original_key_1234")))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            jdbc.update("UPDATE orders SET status='CANCELLED',cancelled_at=CURRENT_TIMESTAMP WHERE id=?", historical.id());
            assertThat(jdbc.queryForObject("SELECT receipt_body->'order'->>'status' FROM checkout_receipts", String.class)).isEqualTo("PENDING");
            for (String invalid : List.of("short", "key with spaces___", "éxample_key_123456")) {
                assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
                    CheckoutFixtures.HistoricalOrder order = CheckoutFixtures.order(jdbc, owner);
                    CheckoutFixtures.receipt(jdbc, owner, order, invalid);
                })).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            }
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isEqualTo(1);
            for (String invalidBody : List.of("'{}'::JSONB", "jsonb_set(receipt_body,'{order,status}','\"CANCELLED\"')",
                    "jsonb_set(receipt_body,'{order,items}','[]')", "jsonb_set(receipt_body,'{order,items,0,quantity}','1000')",
                    "jsonb_set(receipt_body,'{order,payableTotal,amount}','101')",
                    "jsonb_set(receipt_body,'{order,merchandiseTotal,currency}','\"USD\"')",
                    "jsonb_set(receipt_body,'{order,items,0,unitPriceSnapshot,amount}','-1')")) {
                String body = jdbc.queryForObject("SELECT (" + invalidBody + ")::TEXT FROM checkout_receipts", String.class);
                assertThat(jdbc.queryForObject("SELECT checkout_receipt_is_valid(CAST(? AS JSONB))", Boolean.class, body)).isFalse();
                assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
                    CheckoutFixtures.HistoricalOrder order = CheckoutFixtures.order(jdbc, owner);
                    jdbc.update("INSERT INTO checkout_receipts(customer_id,operation,idempotency_key,request_address_id,request_fingerprint,order_id,receipt_version,response_status,location,receipt_body,created_at) VALUES (?,'CHECKOUT','Invalid_body_1234',1,repeat('b',64),?,1,201,?,CAST(? AS JSONB),?)",
                            owner,order.id(),"/api/me/orders/"+order.id(),body,java.sql.Timestamp.from(CheckoutFixtures.PLACED_AT));
                })).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            }
            assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
                CheckoutFixtures.HistoricalOrder order = CheckoutFixtures.order(jdbc, owner);
                String body = CheckoutFixtures.body(order).replace("Recipient", "Forged recipient");
                jdbc.update("INSERT INTO checkout_receipts(customer_id,operation,idempotency_key,request_address_id,request_fingerprint,order_id,receipt_version,response_status,location,receipt_body,created_at) VALUES (?,'CHECKOUT','Forged_body_12345',1,repeat('b',64),?,1,201,?,CAST(? AS JSONB),?)",
                        owner,order.id(),"/api/me/orders/"+order.id(),body,java.sql.Timestamp.from(CheckoutFixtures.PLACED_AT));
            })).rootCause().isInstanceOfSatisfying(org.postgresql.util.PSQLException.class, failure -> {
                assertThat(failure.getSQLState()).isEqualTo("23514");
                assertThat(failure.getServerErrorMessage().getConstraint()).isEqualTo("ct_checkout_receipts_integrity");
            });
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isEqualTo(1);
        }
    }
}
