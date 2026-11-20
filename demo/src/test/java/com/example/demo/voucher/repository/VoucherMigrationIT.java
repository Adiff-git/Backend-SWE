package com.example.demo.voucher.repository;

import com.example.demo.fixture.voucher.VoucherFixtures;
import com.example.demo.fixture.checkout.CheckoutFixtures;
import java.math.BigDecimal;
import java.time.Instant;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VoucherMigrationIT {
    private static final PostgreSQLContainer<?> DATABASE = new PostgreSQLContainer<>("postgres:17-alpine");
    private DriverManagerDataSource source;
    private JdbcTemplate jdbc;
    private TransactionTemplate transactions;
    @BeforeAll static void startDatabase() { DATABASE.start(); }
    @AfterAll static void stopDatabase() { DATABASE.stop(); }
    @BeforeEach void prepare() {
        source = new DriverManagerDataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
        jdbc = new JdbcTemplate(source);
        jdbc.execute("DROP SCHEMA public CASCADE"); jdbc.execute("CREATE SCHEMA public");
        transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
    }
    @Test void upgrade_shouldPreserveExistingOwnerAndProvisionNoGrants() {
        Flyway.configure().dataSource(source).target("25").load().migrate();
        long owner = owner();
        long cart = jdbc.queryForObject("INSERT INTO carts(user_id) VALUES (?) RETURNING id", Long.class, owner);
        var historical = transactions.execute(tx -> {
            var order = CheckoutFixtures.order(jdbc, owner);
            CheckoutFixtures.receipt(jdbc, owner, order, "voucher-upgrade-key-0001");
            return order;
        });
        String originalReceipt = jdbc.queryForObject("SELECT receipt_body::TEXT FROM checkout_receipts WHERE order_id=?", String.class, historical.id());
        Flyway.configure().dataSource(source).load().migrate();
        assertThat(jdbc.queryForObject("SELECT MAX(version::INTEGER) FROM flyway_schema_history WHERE success", Integer.class)).isEqualTo(26);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id=?", Integer.class, owner)).isOne();
        assertThat(jdbc.queryForObject("SELECT voucher_entitlement_id FROM carts WHERE id=?", Long.class, cart)).isNull();
        assertThat(jdbc.queryForObject("SELECT voucher_discount FROM orders WHERE id=?", BigDecimal.class, historical.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT inventory_managed FROM orders WHERE id=?", Boolean.class, historical.id())).isFalse();
        assertThat(jdbc.queryForObject("SELECT receipt_body::TEXT FROM checkout_receipts WHERE order_id=?", String.class, historical.id())).isEqualTo(originalReceipt);
        for (String table : new String[]{"vouchers", "voucher_customer_usages", "voucher_entitlements", "voucher_redemptions"}) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class)).isZero();
        }
    }
    @Test void definitions_shouldRejectInvalidPercentageCodeAndFractionalMoney() {
        Flyway.configure().dataSource(source).load().migrate();
        assertThatThrownBy(() -> VoucherFixtures.definition(jdbc, "lower", "FIXED_AMOUNT", BigDecimal.ONE, null, null)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> VoucherFixtures.definition(jdbc, "PRECISION", "PERCENTAGE", new BigDecimal("0.001"), null, null)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> VoucherFixtures.definition(jdbc, "FRACTION", "FIXED_AMOUNT", new BigDecimal("1.5"), null, null)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> VoucherFixtures.definition(jdbc, "LIMIT", "FIXED_AMOUNT", BigDecimal.ONE, 0L, null)).isInstanceOf(RuntimeException.class);
    }
    @Test void finalCommit_shouldRejectLeakedCounterOrUnattributedConsumedGrant() {
        Flyway.configure().dataSource(source).load().migrate();
        long owner = owner();
        long voucher = VoucherFixtures.definition(jdbc, "ATOMIC", "FIXED_AMOUNT", BigDecimal.ONE, 1L, 1L);
        long usage = VoucherFixtures.usage(jdbc, voucher, owner);
        long grant = VoucherFixtures.grant(jdbc, voucher, owner, usage, Instant.now().minusSeconds(1));
        assertThatThrownBy(() -> transactions.executeWithoutResult(s -> jdbc.update("UPDATE vouchers SET redeemed_count=1 WHERE id=?", voucher))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> transactions.executeWithoutResult(s -> jdbc.update("UPDATE voucher_entitlements SET state='CONSUMED',consumed_at=CURRENT_TIMESTAMP WHERE id=?", grant))).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT state FROM voucher_entitlements WHERE id=?", String.class, grant)).isEqualTo("AVAILABLE");
        assertThat(jdbc.queryForObject("SELECT redeemed_count FROM vouchers WHERE id=?", Long.class, voucher)).isZero();
    }
    @Test void ownership_shouldRejectForeignUsageAndTerminalBootstrap() {
        Flyway.configure().dataSource(source).load().migrate();
        long first = owner();
        long second = jdbc.queryForObject("INSERT INTO users(username,password,account_status,joined_at) VALUES ('voucher-foreign','test','ACTIVE',CURRENT_TIMESTAMP) RETURNING id", Long.class);
        long voucher = VoucherFixtures.definition(jdbc, "OWNERSHIP", "FIXED_AMOUNT", BigDecimal.ONE, null, null);
        long usage = VoucherFixtures.usage(jdbc, voucher, first);
        assertThatThrownBy(() -> VoucherFixtures.grant(jdbc, voucher, second, usage, Instant.now())).isInstanceOf(RuntimeException.class);
        long grant = VoucherFixtures.grant(jdbc, voucher, first, usage, Instant.now());
        assertThatThrownBy(() -> jdbc.update("INSERT INTO carts(user_id,voucher_entitlement_id) VALUES (?,?)", second, grant)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE voucher_entitlements SET customer_id=? WHERE id=?", second, grant)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM voucher_entitlements WHERE id=?", grant)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO voucher_entitlements(voucher_customer_usage_id,voucher_id,customer_id,granted_at,state,consumed_at) VALUES (?,?,?,CURRENT_TIMESTAMP,'CONSUMED',CURRENT_TIMESTAMP)", usage, voucher, first)).isInstanceOf(RuntimeException.class);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"MISSING_REDEMPTION","WRONG_SNAPSHOT","WRONG_COUNT","WRONG_RECEIPT","RETAINED_CART"})
    void finalCommit_shouldRejectIncompleteOrMismatchedOriginalPurchase(String defect) {
        Flyway.configure().dataSource(source).load().migrate();
        long owner=owner();
        assertThatThrownBy(() -> transactions.executeWithoutResult(tx -> aggregate(owner,defect))).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders",Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM voucher_redemptions",Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM voucher_entitlements",Long.class)).isZero();
    }
    @Test void originalPurchase_shouldCommitWithImmutableHistoryAndVersionTwoReceipt() {
        Flyway.configure().dataSource(source).load().migrate();
        long owner=owner(); transactions.executeWithoutResult(tx -> aggregate(owner,"VALID"));
        String original=jdbc.queryForObject("SELECT receipt_body::TEXT FROM checkout_receipts",String.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE voucher_redemptions SET title_snapshot='Edited'" )).hasStackTraceContaining("immutable");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM voucher_redemptions")).hasStackTraceContaining("immutable");
        assertThatThrownBy(() -> jdbc.update("UPDATE orders SET voucher_snapshot='{}'")).hasStackTraceContaining("immutable");
        assertThatThrownBy(() -> jdbc.update("UPDATE orders SET placed_at=placed_at+INTERVAL '1 second'")).hasStackTraceContaining("immutable");
        jdbc.update("UPDATE vouchers SET title='New title',enabled=FALSE");
        assertThat(jdbc.queryForObject("SELECT receipt_body::TEXT FROM checkout_receipts",String.class)).isEqualTo(original);
    }
    private void aggregate(long owner,String defect) {
        long voucher=VoucherFixtures.definition(jdbc,"ORIGINAL","FIXED_AMOUNT",BigDecimal.TEN,3L,3L);
        long usage=VoucherFixtures.usage(jdbc,voucher,owner);
        Instant at=CheckoutFixtures.PLACED_AT;
        long grant=VoucherFixtures.grant(jdbc,voucher,owner,usage,at.minusSeconds(1));
        var snapshot=new com.example.demo.voucher.dto.response.VoucherSnapshot(grant,voucher,"ORIGINAL","Voucher ORIGINAL","FIXED_AMOUNT",BigDecimal.TEN,
            new com.example.demo.catalog.dto.response.SellingPrice(java.math.BigInteger.ZERO,"VND"),null,
            new com.example.demo.catalog.dto.response.SellingPrice(java.math.BigInteger.valueOf(100),"VND"),
            new com.example.demo.catalog.dto.response.SellingPrice(java.math.BigInteger.TEN,"VND"),at);
        var json=tools.jackson.databind.json.JsonMapper.builder().build();
        String stored=defect.equals("WRONG_SNAPSHOT") ? "{}" : json.writeValueAsString(snapshot);
        long order=jdbc.queryForObject("SELECT nextval(pg_get_serial_sequence('orders','id'))",Long.class);
        jdbc.update("INSERT INTO orders(id,customer_id,order_number,placed_at,status,recipient_name_snapshot,recipient_phone_snapshot,delivery_address_snapshot,merchandise_total,payable_total,inventory_managed,voucher_discount,voucher_snapshot) VALUES (?, ?, ?, ?, 'PENDING','Recipient','0123456789','Address',100,90,false,10,CAST(? AS JSONB))",order,owner,"TEST-"+order,java.sql.Timestamp.from(at),stored);
        long item=jdbc.queryForObject("INSERT INTO order_items(order_id,product_name_snapshot,variant_sku_snapshot,variant_options_snapshot,quantity,unit_price_snapshot,line_total) VALUES (?,'Product','SKU','{}',1,100,100) RETURNING id",Long.class,order);
        jdbc.update("UPDATE vouchers SET redeemed_count=? WHERE id=?",defect.equals("WRONG_COUNT") ? 2 : 1,voucher);
        jdbc.update("UPDATE voucher_customer_usages SET redeemed_count=1 WHERE id=?",usage);
        jdbc.update("UPDATE voucher_entitlements SET state='CONSUMED',consumed_at=? WHERE id=?",java.sql.Timestamp.from(at),grant);
        if(!defect.equals("MISSING_REDEMPTION")) jdbc.update("INSERT INTO voucher_redemptions(voucher_id,voucher_entitlement_id,voucher_customer_usage_id,customer_id,order_id,redeemed_at,code_snapshot,title_snapshot,type_snapshot,value_snapshot,minimum_subtotal_snapshot,calculation_base,applied_discount) VALUES (?,?,?,?,?,?,'ORIGINAL','Voucher ORIGINAL','FIXED_AMOUNT',10,0,100,10)",voucher,grant,usage,owner,order,java.sql.Timestamp.from(at));
        if(defect.equals("RETAINED_CART")) jdbc.update("INSERT INTO carts(user_id,voucher_entitlement_id) VALUES (?,?)",owner,grant);
        var body=new java.util.LinkedHashMap<String,Object>(com.example.demo.testdata.checkout.CheckoutTestData.receipt(order,item,at));
        var header=new java.util.LinkedHashMap<String,Object>((java.util.Map<String,Object>)body.get("order"));
        header.put("voucher",snapshot); header.put("voucherDiscount",java.util.Map.of("amount",10,"currency","VND"));
        header.put("payableTotal",java.util.Map.of("amount",defect.equals("WRONG_RECEIPT") ? 91 : 90,"currency","VND"));body.put("order",header);
        jdbc.update("INSERT INTO checkout_receipts(customer_id,operation,idempotency_key,request_address_id,request_fingerprint,order_id,receipt_version,response_status,location,receipt_body,created_at) VALUES (?,'CHECKOUT','Voucher_original_123',1,?,?,2,201,?,CAST(? AS JSONB),?)",owner,com.example.demo.testdata.checkout.CheckoutTestData.ADDRESS_ONE_FINGERPRINT,order,"/api/me/orders/"+order,json.writeValueAsString(body),java.sql.Timestamp.from(at));
    }
    private long owner() {
        return jdbc.queryForObject("INSERT INTO users(username,password,account_status,joined_at) VALUES ('voucher-migration','test','ACTIVE',CURRENT_TIMESTAMP) RETURNING id", Long.class);
    }
}
