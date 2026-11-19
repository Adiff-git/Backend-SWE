package com.example.demo.checkout.service;

import com.example.demo.checkout.dto.request.CheckoutRequest;
import com.example.demo.checkout.dto.response.CheckoutReceiptResponse;
import com.example.demo.checkout.exception.NoEligibleCheckoutItemsException;
import com.example.demo.config.CheckoutIntegrationSupport;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CheckoutServiceIT extends CheckoutIntegrationSupport {
    @Autowired jakarta.persistence.EntityManagerFactory entityManagerFactory;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints={100,101,205})
    void checkout_shouldBoundBatchQueriesAndSerializeDetachedWithoutSql(int selectedRows) {
        transactions.executeWithoutResult(s -> {
            for (int index=1; index<selectedRows; index++) {
                com.example.demo.fixture.checkout.CheckoutFixtures.additionalItem(entities,fixture.cart(),true);
            }
        });
        org.hibernate.stat.Statistics statistics=entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        boolean previouslyEnabled=statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        try {
            statistics.clear();
            CheckoutReceiptResponse receipt=service.checkout(fixture.owner(),"Batch_query_key_123",new CheckoutRequest(fixture.address()));
            long statements=statistics.getPrepareStatementCount();
            int batches=(selectedRows+99)/100;
            assertThat(receipt.getOrder().getItems()).hasSize(selectedRows);
            assertThat(statements-selectedRows).isLessThanOrEqualTo(40L+10L*batches);
            assertThat(statistics.getEntityFetchCount()).isZero();
            assertThat(statistics.getQueries()).noneMatch(query -> query.contains("product_images"));
            String body=codec.encode(receipt);
            assertThat(codec.decode(body)).usingRecursiveComparison().isEqualTo(receipt);
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(statements);
            statistics.clear();
            assertThat(service.checkout(fixture.owner(),"Batch_query_key_123",new CheckoutRequest(fixture.address())))
                    .usingRecursiveComparison().isEqualTo(receipt);
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
            assertThat(statistics.getEntityFetchCount()).isZero();
            System.out.printf("CHECKOUT_QUERY_BOUND rows=%d statements=%d overhead=%d replay=1 detached=0%n",
                    selectedRows,statements,statements-selectedRows);
        } finally {
            statistics.setStatisticsEnabled(previouslyEnabled);
        }
    }
    @Autowired CheckoutService service;
    @Autowired com.example.demo.order.service.OrderService orderReads;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    com.example.demo.checkout.converter.CheckoutConverter codec;

    @Test void checkout_shouldCommitOneCurrentPricePendingOrderAndConsumeOnlySelectedRows() {
        jdbc.update("UPDATE product_variants SET selling_price=1500 WHERE id=?",fixture.variant());
        CheckoutReceiptResponse receipt = service.checkout(fixture.owner(),"First_checkout_123",new CheckoutRequest(fixture.address()));
        assertThat(receipt.getOrder().getStatus().name()).isEqualTo("PENDING");
        assertThat(receipt.getOrder().getPayableTotal().getAmount()).isEqualTo(BigInteger.valueOf(3000));
        assertThat(receipt.getOrder().getItems()).hasSize(1);
        assertThat(receipt.getSkippedItems()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM checkout_receipts",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT id FROM cart_items",Long.class)).containsExactly(fixture.unselected());
    }

    @Test void checkout_shouldRejectNoSelectionWithoutCreatingReceipt() {
        jdbc.update("UPDATE cart_items SET selected=FALSE");
        assertThatThrownBy(() -> service.checkout(fixture.owner(),"Empty_checkout_123",new CheckoutRequest(fixture.address())))
                .isInstanceOf(NoEligibleCheckoutItemsException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM checkout_receipts",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cart_items",Integer.class)).isEqualTo(2);
    }

    @Test void checkout_shouldPreserveHistoryAndReceiptAfterAddressAndCatalogChanges() {
        CheckoutReceiptResponse receipt = service.checkout(fixture.owner(),"Historical_key_123",new CheckoutRequest(fixture.address()));
        Long id = receipt.getOrder().getId();
        java.util.Map<String,Object> order = jdbc.queryForMap("SELECT * FROM orders WHERE id=?",id);
        jdbc.update("UPDATE addresses SET recipient_name='Changed',address_text='Changed' WHERE id=?",fixture.address());
        jdbc.update("UPDATE products SET name='Changed hidden product',status='ARCHIVED' WHERE id=?",fixture.product());
        jdbc.update("UPDATE product_variants SET selling_price=9999,sku=?,option_combination='{}' WHERE id=?","CHANGED-"+java.util.UUID.randomUUID(),fixture.variant());
        jdbc.update("DELETE FROM addresses WHERE id=?",fixture.address());
        assertThat(jdbc.queryForMap("SELECT * FROM orders WHERE id=?",id)).isEqualTo(order);
        assertThat(orderReads.getOrder(fixture.owner(),id).getDeliveryAddressSnapshot()).isEqualTo("Address");
        assertThat(service.checkout(fixture.owner(),"Historical_key_123",new CheckoutRequest(fixture.address())))
                .usingRecursiveComparison().isEqualTo(receipt);
        transactions.executeWithoutResult(s -> {
            jdbc.update("DELETE FROM product_variants WHERE id=?",fixture.variant());
            jdbc.update("DELETE FROM products WHERE id=?",fixture.product());
        });
        assertThat(orderReads.getOrder(fixture.owner(),id).getItems().getFirst().getProductId()).isNull();
        assertThat(service.checkout(fixture.owner(),"Historical_key_123",new CheckoutRequest(fixture.address())))
                .usingRecursiveComparison().isEqualTo(receipt);
    }

    @Test void checkout_shouldCommitOnlyEligibleSelectionsAndPreserveEverySkippedAndUnselectedField() {
        Long unselected=transactions.execute(s -> com.example.demo.fixture.checkout.CheckoutFixtures.additionalItem(entities,fixture.cart(),false).getId());
        jdbc.update("UPDATE cart_items SET selected=TRUE WHERE id=?",fixture.unselected());
        jdbc.update("UPDATE products SET status='ARCHIVED' WHERE id=?",fixture.product());
        java.util.Map<String,Object> skippedBefore=jdbc.queryForMap("SELECT * FROM cart_items WHERE id=?",fixture.item());
        java.util.Map<String,Object> unselectedBefore=jdbc.queryForMap("SELECT * FROM cart_items WHERE id=?",unselected);
        CheckoutReceiptResponse receipt=service.checkout(fixture.owner(),"Partial_checkout_123",new CheckoutRequest(fixture.address()));
        assertThat(receipt.getOrder().getItems()).hasSize(1);
        assertThat(receipt.getSkippedItems()).containsExactly(new com.example.demo.checkout.dto.response.SkippedCheckoutItem(
                fixture.item(),com.example.demo.checkout.dto.response.SkippedCheckoutItem.Reason.PRODUCT_NOT_FOR_SALE));
        assertThat(jdbc.queryForMap("SELECT * FROM cart_items WHERE id=?",fixture.item())).isEqualTo(skippedBefore);
        assertThat(jdbc.queryForMap("SELECT * FROM cart_items WHERE id=?",unselected)).isEqualTo(unselectedBefore);
        assertThat(jdbc.queryForList("SELECT id FROM cart_items",Long.class)).containsExactlyInAnyOrder(fixture.item(),unselected);
    }

    @Test void checkout_shouldRejectAllIneligibleWithoutReservingKeyAndAllowLaterRetry() {
        jdbc.update("UPDATE products SET status='ARCHIVED' WHERE id=?",fixture.product());
        assertThatThrownBy(() -> service.checkout(fixture.owner(),"Unavailable_key_123",new CheckoutRequest(fixture.address())))
                .isInstanceOfSatisfying(NoEligibleCheckoutItemsException.class,failure -> assertThat(failure.getSkippedItems()).hasSize(1));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM checkout_receipts",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cart_items",Integer.class)).isEqualTo(2);
        jdbc.update("UPDATE products SET status='PUBLISHED' WHERE id=?",fixture.product());
        assertThat(service.checkout(fixture.owner(),"Unavailable_key_123",new CheckoutRequest(fixture.address())).getOrder().getStatus().name()).isEqualTo("PENDING");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints={1,999})
    void checkout_shouldPreserveQuantityBoundariesAtCurrentZeroPrice(int quantity) {
        jdbc.update("UPDATE cart_items SET quantity=? WHERE id=?",quantity,fixture.item());
        jdbc.update("UPDATE product_variants SET selling_price=0 WHERE id=?",fixture.variant());
        CheckoutReceiptResponse receipt=service.checkout(fixture.owner(),"Quantity_boundary_123",new CheckoutRequest(fixture.address()));
        assertThat(receipt.getOrder().getItems().getFirst().getQuantity()).isEqualTo(quantity);
        assertThat(receipt.getOrder().getPayableTotal().getAmount()).isZero();
    }

    @Test void checkout_shouldRejectMissingOrEmptyCartWithoutCreatingOneOrReceipt() {
        Long address=jdbc.queryForObject("INSERT INTO addresses(user_id,recipient_name,recipient_phone,address_text,is_default) VALUES (?,'Recipient','0123456789','Address',TRUE) RETURNING id",Long.class,fixture.foreign());
        assertThatThrownBy(() -> service.checkout(fixture.foreign(),"Missing_cart_key_123",new CheckoutRequest(address)))
                .isInstanceOf(NoEligibleCheckoutItemsException.class);
        jdbc.update("DELETE FROM cart_items WHERE cart_id=?",fixture.cart());
        assertThatThrownBy(() -> service.checkout(fixture.owner(),"Empty_cart_key_1234",new CheckoutRequest(fixture.address())))
                .isInstanceOf(NoEligibleCheckoutItemsException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM carts",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM checkout_receipts",Integer.class)).isZero();
    }

    @Test void checkout_shouldRollBackOrderAndCartWhenDatabaseRejectsReceiptAfterConsumption() {
        jdbc.execute("ALTER TABLE checkout_receipts ADD CONSTRAINT ck_checkout_test_failure CHECK (FALSE)");
        try {
            assertThatThrownBy(() -> service.checkout(fixture.owner(),"Rollback_key_12345",new CheckoutRequest(fixture.address())))
                    .hasStackTraceContaining("ck_checkout_test_failure");
            assertNoCommittedCheckout();
        } finally { jdbc.execute("ALTER TABLE checkout_receipts DROP CONSTRAINT ck_checkout_test_failure"); }
        service.checkout(fixture.owner(),"Rollback_key_12345",new CheckoutRequest(fixture.address()));
    }

    @Test void checkout_shouldRollBackHeaderAndCartWhenItemWriteFails() {
        jdbc.execute("ALTER TABLE order_items ADD CONSTRAINT ck_checkout_test_item_failure CHECK (FALSE)");
        try {
            assertThatThrownBy(() -> service.checkout(fixture.owner(),"Item_failure_12345",new CheckoutRequest(fixture.address())))
                    .hasStackTraceContaining("ck_checkout_test_item_failure");
            assertNoCommittedCheckout();
        } finally { jdbc.execute("ALTER TABLE order_items DROP CONSTRAINT ck_checkout_test_item_failure"); }
    }

    @Test void checkout_shouldNotReturnSuccessWhenDeferredCommitFailsAndLeaveKeyReusable() {
        jdbc.execute("CREATE FUNCTION checkout_test_reject_commit() RETURNS TRIGGER LANGUAGE PLPGSQL AS $$ BEGIN RAISE EXCEPTION 'Injected checkout commit failure' USING ERRCODE='23514'; END; $$");
        jdbc.execute("CREATE CONSTRAINT TRIGGER ct_checkout_test_commit AFTER INSERT ON checkout_receipts DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION checkout_test_reject_commit()");
        try {
            assertThatThrownBy(() -> service.checkout(fixture.owner(),"Deferred_fail_12345",new CheckoutRequest(fixture.address())))
                    .hasStackTraceContaining("Injected checkout commit failure");
            assertNoCommittedCheckout();
        } finally {
            jdbc.execute("DROP TRIGGER ct_checkout_test_commit ON checkout_receipts");
            jdbc.execute("DROP FUNCTION checkout_test_reject_commit()");
        }
        Long correctedAddress=jdbc.queryForObject("INSERT INTO addresses(user_id,recipient_name,recipient_phone,address_text,is_default) VALUES (?,'Recipient','0123456789','New destination',FALSE) RETURNING id",Long.class,fixture.owner());
        assertThat(service.checkout(fixture.owner(),"Deferred_fail_12345",new CheckoutRequest(correctedAddress)).getOrder().getDeliveryAddressSnapshot())
                .isEqualTo("New destination");
        assertThatThrownBy(() -> service.checkout(fixture.owner(),"Deferred_fail_12345",new CheckoutRequest(fixture.address())))
                .isInstanceOf(com.example.demo.checkout.exception.CheckoutKeyConflictException.class);
    }

    private void assertNoCommittedCheckout() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_items",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM checkout_receipts",Integer.class)).isZero();
        assertThat(jdbc.queryForList("SELECT id FROM cart_items",Long.class)).containsExactlyInAnyOrder(fixture.item(),fixture.unselected());
        assertThat(jdbc.queryForObject("SELECT quantity FROM cart_items WHERE id=?",Integer.class,fixture.item())).isEqualTo(2);
    }

    @Test void checkout_shouldAbortOnActualCatalogQueryFailureInsteadOfSkipping() {
        assertThatThrownBy(() -> transactions.executeWithoutResult(s -> {
            jdbc.execute("ALTER TABLE product_variants RENAME TO checkout_test_unavailable_variants");
            service.checkout(fixture.owner(),"Database_failure_123",new CheckoutRequest(fixture.address()));
        })).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertNoCommittedCheckout();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_variants",Integer.class)).isEqualTo(2);
    }

    @Test void checkout_shouldRollBackRealOrderAndCartWhenReceiptEncodingFails() {
        org.mockito.Mockito.doThrow(new IllegalStateException("Injected receipt encoding failure"))
                .when(codec).encode(org.mockito.ArgumentMatchers.any());
        assertThatThrownBy(() -> service.checkout(fixture.owner(),"Encoding_fail_12345",new CheckoutRequest(fixture.address())))
                .isInstanceOf(IllegalStateException.class).hasMessage("Injected receipt encoding failure");
        assertNoCommittedCheckout();
    }

    @Test void checkout_shouldScopeSameExactKeyIndependentlyToTwoCustomers() {
        com.example.demo.fixture.checkout.CheckoutFixtures.Scenario other=transactions.execute(s ->
                com.example.demo.fixture.checkout.CheckoutFixtures.scenario(entities));
        CheckoutReceiptResponse first=service.checkout(fixture.owner(),"Independent_key_123",new CheckoutRequest(fixture.address()));
        CheckoutReceiptResponse second=service.checkout(other.owner(),"Independent_key_123",new CheckoutRequest(other.address()));
        assertThat(first.getOrder().getId()).isNotEqualTo(second.getOrder().getId());
        assertThat(jdbc.queryForObject("SELECT customer_id FROM orders WHERE id=?",Long.class,first.getOrder().getId())).isEqualTo(fixture.owner());
        assertThat(jdbc.queryForObject("SELECT customer_id FROM orders WHERE id=?",Long.class,second.getOrder().getId())).isEqualTo(other.owner());
        assertThat(service.checkout(fixture.owner(),"Independent_key_123",new CheckoutRequest(fixture.address())))
                .usingRecursiveComparison().isEqualTo(first);
        assertThat(service.checkout(other.owner(),"Independent_key_123",new CheckoutRequest(other.address())))
                .usingRecursiveComparison().isEqualTo(second);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isEqualTo(2);
    }

    @Test void checkout_shouldKeepCaseDistinctKeysIndependentAndNeverConsumeNewItemsOnOriginalReplay() {
        CheckoutReceiptResponse first=service.checkout(fixture.owner(),"Case_sensitive_123",new CheckoutRequest(fixture.address()));
        Long added=transactions.execute(s -> com.example.demo.fixture.checkout.CheckoutFixtures.additionalItem(entities,fixture.cart(),true).getId());
        assertThat(service.checkout(fixture.owner(),"Case_sensitive_123",new CheckoutRequest(fixture.address())))
                .usingRecursiveComparison().isEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cart_items WHERE id=?",Integer.class,added)).isEqualTo(1);
        CheckoutReceiptResponse second=service.checkout(fixture.owner(),"case_sensitive_123",new CheckoutRequest(fixture.address()));
        assertThat(second.getOrder().getId()).isNotEqualTo(first.getOrder().getId());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cart_items WHERE id=?",Integer.class,added)).isZero();
        assertThat(jdbc.queryForList("SELECT idempotency_key FROM checkout_receipts ORDER BY id",String.class))
                .containsExactly("Case_sensitive_123","case_sensitive_123");
    }
}
