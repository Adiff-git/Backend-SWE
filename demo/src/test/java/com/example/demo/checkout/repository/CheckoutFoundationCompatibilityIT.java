package com.example.demo.checkout.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo.fixture.checkout.CheckoutFixtures;
import com.example.demo.testdata.checkout.CheckoutTestData;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

class CheckoutFoundationCompatibilityIT {
    @Test
    void foundation_shouldKeepLegacyWritersAndReceiptsValidAtEachSchemaStage() {
        try (PostgreSQLContainer<?> database = new PostgreSQLContainer<>("postgres:17-alpine")) {
            database.start();
            DriverManagerDataSource source = new DriverManagerDataSource(database.getJdbcUrl(),
                    database.getUsername(), database.getPassword());
            JdbcTemplate jdbc = new JdbcTemplate(source);
            TransactionTemplate transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
            Flyway.configure().dataSource(source).target("27").load().migrate();
            Long owner = CheckoutFixtures.customer(jdbc);
            for (String stage : List.of("28", "29", "30")) {
                Flyway.configure().dataSource(source).target(stage).load().migrate();
                CheckoutFixtures.HistoricalOrder order = transactions.execute(status -> {
                    CheckoutFixtures.HistoricalOrder created = CheckoutFixtures.order(jdbc, owner);
                    CheckoutFixtures.receipt(jdbc, owner, created, "Foundation_v" + stage + "_123456");
                    return created;
                });
                assertThat(jdbc.queryForObject("SELECT unit_price_snapshot FROM order_items WHERE id=?",
                        Integer.class, order.itemId())).isEqualTo(100);
                assertThat(jdbc.queryForObject("SELECT receipt_body->'order'->'items'->0->'unitPriceSnapshot'->>'amount' "
                        + "FROM checkout_receipts WHERE order_id=?", String.class, order.id())).isEqualTo("100");
                transactions.executeWithoutResult(status -> {
                    CheckoutFixtures.HistoricalOrder versionTwo = CheckoutFixtures.order(jdbc, owner);
                    String v2 = versionTwoBody(jdbc, versionTwo);
                    insertReceipt(jdbc, owner, versionTwo, 2, "Foundation_v2_" + stage + "_1234", v2);
                });
            }
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_items WHERE original_unit_price_snapshot IS NULL",
                    Integer.class)).isEqualTo(6);
            assertThat(jdbc.queryForObject("SELECT checkout_receipt_v3_is_valid(receipt_body) FROM checkout_receipts LIMIT 1",
                    Boolean.class)).isFalse();
            CheckoutFixtures.HistoricalOrder known = transactions.execute(status -> {
                Long id = jdbc.queryForObject("SELECT nextval(pg_get_serial_sequence('orders','id'))", Long.class);
                jdbc.update("INSERT INTO orders(id,customer_id,order_number,placed_at,status,recipient_name_snapshot,recipient_phone_snapshot,delivery_address_snapshot,merchandise_total,payable_total,inventory_managed) "
                        + "VALUES (?,?,?,?,'PENDING','Recipient','0123456789','Address',100,100,false)",
                        id, owner, "TEST-" + id, java.sql.Timestamp.from(CheckoutFixtures.PLACED_AT));
                Long item = jdbc.queryForObject("INSERT INTO order_items(order_id,product_name_snapshot,variant_sku_snapshot,variant_options_snapshot,quantity,unit_price_snapshot,line_total,original_unit_price_snapshot,direct_unit_discount_snapshot,pricing_instant_snapshot) "
                        + "VALUES (?,'Product','SKU','{}',1,100,100,100,0,?) RETURNING id", Long.class, id,
                        java.sql.Timestamp.from(CheckoutFixtures.PLACED_AT));
                CheckoutFixtures.HistoricalOrder created = new CheckoutFixtures.HistoricalOrder(id, item);
                String v3=versionThreeBody(jdbc,created);
                assertThat(jdbc.queryForObject("SELECT checkout_receipt_v3_is_valid(CAST(? AS JSONB))", Boolean.class, v3)).isTrue();
                insertReceipt(jdbc, owner, created, 3, "Foundation_v3_123456", v3);
                return created;
            });
            assertThat(jdbc.queryForObject("SELECT original_unit_price_snapshot FROM order_items WHERE id=?",
                    Integer.class, known.itemId())).isEqualTo(100);
            for(String evidence:List.of("100,NULL,NULL,NULL,NULL,NULL", "100,1,NULL,NULL,NULL,CURRENT_TIMESTAMP",
                    "100,0,20,NULL,NULL,CURRENT_TIMESTAMP", "NULL,0,NULL,NULL,NULL,CURRENT_TIMESTAMP")) {
                assertThatThrownBy(() -> transactions.executeWithoutResult(tx -> {
                    var parent=CheckoutFixtures.order(jdbc,owner);
                    jdbc.update("INSERT INTO order_items(order_id,product_name_snapshot,variant_sku_snapshot,variant_options_snapshot,quantity,unit_price_snapshot,line_total,original_unit_price_snapshot,direct_unit_discount_snapshot,direct_sale_percentage_snapshot,direct_sale_starts_at_snapshot,direct_sale_ends_at_snapshot,pricing_instant_snapshot) VALUES (?,'Product','SKU','{}',1,100,100,"+evidence+")",parent.id());
                })).hasStackTraceContaining("ck_order_items_direct_sale");
            }
            assertThatThrownBy(() -> transactions.executeWithoutResult(tx -> {
                var legacy=CheckoutFixtures.order(jdbc,owner);
                insertReceipt(jdbc,owner,legacy,3,"Legacy_bypass_12345",versionThreeBody(jdbc,legacy));
            })).hasStackTraceContaining("Missing v3 order sale evidence");
            String valid=jdbc.queryForObject("SELECT receipt_body::TEXT FROM checkout_receipts WHERE order_id=?",String.class,known.id());
            for(String tamper:List.of("CAST(? AS JSONB)#-'{order,items,0,pricingInstantSnapshot}'",
                    "JSONB_SET(CAST(? AS JSONB),'{order,items,0,directUnitDiscountSnapshot,amount}','1')",
                    "JSONB_SET(CAST(? AS JSONB),'{order,items,0,pricingInstantSnapshot}','\"2000-01-01T00:00:00Z\"')")) {
                assertThat(jdbc.queryForObject("SELECT checkout_receipt_v3_is_valid("+tamper+")",Boolean.class,valid)).isFalse();
            }
            assertThatThrownBy(() -> jdbc.update("UPDATE order_items SET original_unit_price_snapshot=120 WHERE id=?", known.itemId()))
                    .hasStackTraceContaining("immutable");
        }
    }

    private String versionThreeBody(JdbcTemplate jdbc,CheckoutFixtures.HistoricalOrder created) {
        String v2=versionTwoBody(jdbc,created);
        return jdbc.queryForObject("SELECT JSONB_SET(CAST(? AS JSONB),'{order,items,0}', "
                + "(CAST(? AS JSONB)->'order'->'items'->0)||JSONB_BUILD_OBJECT('originalUnitPriceSnapshot',JSONB_BUILD_OBJECT('amount',100,'currency','VND'),"
                + "'directUnitDiscountSnapshot',JSONB_BUILD_OBJECT('amount',0,'currency','VND'),'directSaleSnapshot',NULL,'pricingInstantSnapshot',?::TEXT))::TEXT",
                String.class,v2,v2,CheckoutFixtures.PLACED_AT.toString());
    }

    private String versionTwoBody(JdbcTemplate jdbc, CheckoutFixtures.HistoricalOrder order) {
        String original = CheckoutFixtures.body(order);
        return jdbc.queryForObject("SELECT JSONB_SET(CAST(? AS JSONB),'{order}',(CAST(? AS JSONB)->'order')||"
                + "JSONB_BUILD_OBJECT('voucherDiscount',JSONB_BUILD_OBJECT('amount',0,'currency','VND'),'voucher',NULL))::TEXT",
                String.class, original, original);
    }

    private void insertReceipt(JdbcTemplate jdbc, Long owner, CheckoutFixtures.HistoricalOrder order,
            int version, String key, String body) {
        jdbc.update("INSERT INTO checkout_receipts(customer_id,operation,idempotency_key,request_address_id,request_fingerprint,order_id,receipt_version,response_status,location,receipt_body,created_at) "
                + "VALUES (?,'CHECKOUT',?,1,?,?,?,201,?,CAST(? AS JSONB),?)", owner, key,
                CheckoutTestData.ADDRESS_ONE_FINGERPRINT, order.id(), version, "/api/me/orders/" + order.id(), body,
                java.sql.Timestamp.from(CheckoutFixtures.PLACED_AT));
    }
}
