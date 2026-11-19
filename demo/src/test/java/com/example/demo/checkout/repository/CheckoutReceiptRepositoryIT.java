package com.example.demo.checkout.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CheckoutReceiptRepositoryIT {
    @Autowired JdbcTemplate jdbc;
    @Autowired CheckoutReceiptRepository receipts;
    @Autowired DataSource dataSource;

    @Test
    void receiptSchema_shouldUseExactScopedCaseSensitiveKeyAndRestrictiveOwnerReference() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pg_constraint WHERE conname IN ('uk_checkout_receipts_customer_id_operation_idempotency_key','uk_checkout_receipts_order_id','fk_checkout_receipts_order_id_customer_id')", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM checkout_receipts", Integer.class)).isZero();
    }

    @Test
    void findByCustomerIdAndOperationAndIdempotencyKey_shouldNotCacheAbsentReceipt() {
        Long owner = com.example.demo.fixture.checkout.CheckoutFixtures.customer(jdbc);
        assertThat(receipts.findByCustomerIdAndOperationAndIdempotencyKey(owner,"CHECKOUT","Fresh_lookup_1234")).isEmpty();
        com.example.demo.fixture.checkout.CheckoutFixtures.HistoricalOrder order = com.example.demo.fixture.checkout.CheckoutFixtures.order(jdbc, owner);
        com.example.demo.fixture.checkout.CheckoutFixtures.receipt(jdbc, owner, order, "Fresh_lookup_1234");
        assertThat(receipts.findByCustomerIdAndOperationAndIdempotencyKey(owner,"CHECKOUT","Fresh_lookup_1234"))
                .hasValueSatisfying(receipt -> {
                    assertThat(receipt.getOrderId()).isEqualTo(order.id());
                    assertThat(receipt.getReceiptVersion()).isEqualTo(1);
                    assertThat(receipt.getResponseStatus()).isEqualTo(201);
                    assertThat(receipt.getReceiptBody()).contains("PENDING");
                });
        assertThat(receipts.findByCustomerIdAndOperationAndIdempotencyKey(owner,"CHECKOUT","fresh_lookup_1234")).isEmpty();
        assertThat(receipts.findByCustomerIdAndOperationAndIdempotencyKey(owner + 1,"CHECKOUT","Fresh_lookup_1234")).isEmpty();
        assertThat(receipts.tryFence(987654321L)).isTrue();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void tryFence_shouldReleaseOnRollbackAndCommitAcrossIndependentConnections() throws Exception {
        try (Connection first = dataSource.getConnection(); Connection second = dataSource.getConnection()) {
            first.setAutoCommit(false);
            second.setAutoCommit(false);
            assertThat(tryFence(first)).isTrue();
            assertThat(tryFence(second)).isFalse();
            first.rollback();
            assertThat(tryFence(second)).isTrue();
            second.commit();
            assertThat(tryFence(first)).isTrue();
            first.rollback();
        }
    }

    private boolean tryFence(Connection connection) throws Exception {
        try (java.sql.Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT pg_try_advisory_xact_lock(1234567890123)")) {
            rows.next();
            return rows.getBoolean(1);
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void findCommittedReceipt_shouldSeeCommitOnFreshBusyAndAcquiredFenceRechecks() throws Exception {
        Long owner=com.example.demo.fixture.checkout.CheckoutFixtures.customer(jdbc);
        org.springframework.transaction.support.TransactionTemplate tx=new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource));
        com.example.demo.fixture.checkout.CheckoutFixtures.HistoricalOrder order=tx.execute(s ->
                com.example.demo.fixture.checkout.CheckoutFixtures.order(jdbc,owner));
        try(Connection writer=dataSource.getConnection(); Connection reader=dataSource.getConnection()) {
            writer.setAutoCommit(false);
            reader.setAutoCommit(false);
            reader.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            assertThat(tryFence(writer)).isTrue();
            try(java.sql.PreparedStatement insert=writer.prepareStatement("INSERT INTO checkout_receipts(customer_id,operation,idempotency_key,request_address_id,request_fingerprint,order_id,receipt_version,response_status,location,receipt_body,created_at) VALUES (?,'CHECKOUT','Visible_commit_123',1,repeat('c',64),?,1,201,?,CAST(? AS JSONB),?)")) {
                insert.setLong(1,owner);
                insert.setLong(2,order.id());
                insert.setString(3,"/api/me/orders/"+order.id());
                insert.setString(4,com.example.demo.fixture.checkout.CheckoutFixtures.body(order));
                insert.setTimestamp(5,java.sql.Timestamp.from(com.example.demo.fixture.checkout.CheckoutFixtures.PLACED_AT));
                insert.executeUpdate();
            }
            assertThat(committedCount(reader,owner)).isZero();
            assertThat(tryFence(reader)).isFalse();
            writer.commit();
            assertThat(committedCount(reader,owner)).isEqualTo(1); // Busy-path fresh read, not initial absence.
            assertThat(tryFence(reader)).isTrue();
            assertThat(committedCount(reader,owner)).isEqualTo(1); // Acquired-path fresh read.
            reader.rollback();
        } finally {
            jdbc.update("DELETE FROM checkout_receipts WHERE order_id=?",order.id());
            tx.executeWithoutResult(s -> {
                jdbc.update("DELETE FROM order_items WHERE order_id=?",order.id());
                jdbc.update("DELETE FROM orders WHERE id=?",order.id());
            });
            jdbc.update("DELETE FROM users WHERE id=?",owner);
        }
    }

    private int committedCount(Connection connection, Long owner) throws Exception {
        try(java.sql.PreparedStatement query=connection.prepareStatement("SELECT COUNT(*) FROM checkout_receipts WHERE customer_id=? AND operation='CHECKOUT' AND idempotency_key='Visible_commit_123'")) {
            query.setLong(1,owner);
            try(ResultSet rows=query.executeQuery()) { rows.next(); return rows.getInt(1); }
        }
    }
}
