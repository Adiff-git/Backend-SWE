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

@org.springframework.context.annotation.Import(com.example.demo.config.SqlCaptureConfiguration.class)
class CheckoutServiceIT extends CheckoutIntegrationSupport {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {100,101,205})
    void checkout_shouldReportDeferredValidationCostSeparatelyFromClientStatements(int selectedRows) {
        long grant=voucherGrant();cartVouchers.applyVoucher(fixture.owner(),grant);
        transactions.executeWithoutResult(s -> {
            for (int index = 1; index < selectedRows; index++) com.example.demo.fixture.checkout.CheckoutFixtures.additionalItem(entities, fixture.cart(), true);
        });
        transactions.executeWithoutResult(s -> {
            jdbc.execute("SET LOCAL track_functions='pl'");
            String functionStatsSql = """
                    SELECT funcname,calls,total_time,self_time FROM pg_stat_xact_user_functions
                    WHERE schemaname='public' AND funcname IN
                    ('check_inventory_balance_integrity','check_inventory_order_integrity','validate_inventory_order','validate_voucher_final','check_voucher_final')
                    ORDER BY funcname
                    """;
            var before = jdbc.queryForList(functionStatsSql);
            service.checkout(fixture.owner(), "Trigger_cost_key_123", new CheckoutRequest(fixture.address()));
            entities.flush();
            long start = System.nanoTime();
            jdbc.execute("SET CONSTRAINTS ALL IMMEDIATE");
            long validationNanos = System.nanoTime()-start;
            var functions = jdbc.queryForList(functionStatsSql);
            // Isolate this checkout from pending function counters retained by a pooled backend.
            for (var row : functions) {
                var baseline = before.stream().filter(previous -> previous.get("funcname").equals(row.get("funcname")))
                        .findFirst().orElse(java.util.Map.of());
                row.put("calls", ((Number) row.get("calls")).longValue()
                        - ((Number) baseline.getOrDefault("calls", 0L)).longValue());
                for (String timing : java.util.List.of("total_time", "self_time")) {
                    row.put(timing, ((Number) row.get(timing)).doubleValue()
                            - ((Number) baseline.getOrDefault(timing, 0.0)).doubleValue());
                }
            }
            assertThat(functions).anySatisfy(row -> {
                assertThat(row.get("funcname")).isEqualTo("check_inventory_balance_integrity");
                assertThat(((Number) row.get("calls")).longValue()).isEqualTo(2L*selectedRows);
            });
            assertThat(functions).anySatisfy(row -> {
                assertThat(row.get("funcname")).isEqualTo("check_inventory_order_integrity");
                assertThat(((Number) row.get("calls")).longValue()).isEqualTo(2L*selectedRows+1);
            });
            System.out.printf("INVENTORY_TRIGGER_COST rows=%d forcedValidationWallNanos=%d baseline=%s functions=%s%n", selectedRows, validationNanos, before, functions);
        }); // Actual commit still occurs; forcing deferred validation is measurement, not a flush-only commitment claim.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_reservations", Long.class)).isEqualTo(selectedRows);
    }
    @Test void checkout_shouldRetainStockShortageRowWhileReservingOnlyTheOtherFullRow() {
        jdbc.update("UPDATE cart_items SET selected=true WHERE id=?", fixture.unselected());
        com.example.demo.fixture.inventory.InventoryFixtures.supplyStock(jdbc, fixture.variant(), 1);
        var before = jdbc.queryForMap("SELECT * FROM cart_items WHERE id=?", fixture.item());
        CheckoutReceiptResponse receipt = service.checkout(fixture.owner(), "Mixed_stock_key_123", new CheckoutRequest(fixture.address()));
        assertThat(receipt.getOrder().getItems()).hasSize(1);
        assertThat(receipt.getSkippedItems()).hasSize(1);
        assertThat(receipt.getSkippedItems().getFirst().getReason().name()).isEqualTo("INSUFFICIENT_STOCK");
        assertThat(jdbc.queryForMap("SELECT * FROM cart_items WHERE id=?", fixture.item())).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT reserved FROM inventory_balances WHERE original_variant_id=?", Long.class, fixture.variant())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_reservations", Long.class)).isEqualTo(1);
    }
    @Test void checkout_shouldReserveFullQuantityWithoutDeductingStockAndReplayWithoutInventoryWork() {
        com.example.demo.fixture.inventory.InventoryFixtures.supplyStock(jdbc, fixture.variant(), 2);
        CheckoutReceiptResponse receipt = service.checkout(fixture.owner(), "Exact_stock_key_123", new CheckoutRequest(fixture.address()));
        assertThat(jdbc.queryForMap("SELECT stock,reserved FROM inventory_balances WHERE original_variant_id=?", fixture.variant()))
                .containsEntry("stock", 2L).containsEntry("reserved", 2L);
        assertThat(jdbc.queryForObject("SELECT quantity FROM inventory_reservations WHERE order_id=?", Integer.class, receipt.getOrder().getId())).isEqualTo(2);
        assertThat(service.checkout(fixture.owner(), "Exact_stock_key_123", new CheckoutRequest(fixture.address())))
                .usingRecursiveComparison().isEqualTo(receipt);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_reservations", Long.class)).isEqualTo(1);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"MISSING", "ZERO", "INSUFFICIENT"})
    void checkout_shouldPreserveAllUnavailableRowsAndLeaveNoDurableAttempt(String unavailable) {
        jdbc.update("UPDATE inventory_balances SET stock=0 WHERE original_variant_id=?", fixture.variant());
        if (unavailable.equals("MISSING")) com.example.demo.fixture.inventory.InventoryFixtures.removeUnusedBalance(jdbc, fixture.variant());
        if (unavailable.equals("INSUFFICIENT")) com.example.demo.fixture.inventory.InventoryFixtures.supplyStock(jdbc, fixture.variant(), 1);
        var before = jdbc.queryForMap("SELECT * FROM cart_items WHERE id=?", fixture.item());
        assertThatThrownBy(() -> service.checkout(fixture.owner(), "Unavailable_key_123", new CheckoutRequest(fixture.address())))
                .isInstanceOfSatisfying(NoEligibleCheckoutItemsException.class, failure ->
                        assertThat(failure.getProperties().toString()).contains(unavailable.equals("MISSING") ? "INVENTORY_UNAVAILABLE" : "INSUFFICIENT_STOCK"));
        assertNoCommittedCheckout();
        assertThat(jdbc.queryForMap("SELECT * FROM cart_items WHERE id=?", fixture.item())).isEqualTo(before);
    }
    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> purchaseQueryCases() {
        return java.util.stream.Stream.of(100,101,205).flatMap(rows -> java.util.stream.Stream.of(false,true).map(voucher -> org.junit.jupiter.params.provider.Arguments.of(rows,voucher)));
    }
    @Autowired jakarta.persistence.EntityManagerFactory entityManagerFactory;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("purchaseQueryCases")
    void checkout_shouldBoundBatchQueriesAndSerializeDetachedWithoutSql(int selectedRows,boolean withVoucher) {
        transactions.executeWithoutResult(s -> {
            for (int index=1; index<selectedRows; index++) {
                com.example.demo.fixture.checkout.CheckoutFixtures.additionalItem(entities,fixture.cart(),true);
            }
        });
        if(withVoucher) {long grant=voucherGrant();cartVouchers.applyVoucher(fixture.owner(),grant);}
        org.hibernate.stat.Statistics statistics=entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        boolean previouslyEnabled=statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        try {
            statistics.clear();
            com.example.demo.config.SqlCapture.begin();
            CheckoutReceiptResponse receipt;
            java.util.List<String> captured;
            try { receipt=service.checkout(fixture.owner(),"Batch_query_key_123",new CheckoutRequest(fixture.address())); }
            finally { captured=com.example.demo.config.SqlCapture.end(); }
            long statements=statistics.getPrepareStatementCount();
            int batches=(selectedRows+99)/100;
            assertThat(receipt.getOrder().getItems()).hasSize(selectedRows);
            java.util.List<String> sql = captured.stream().map(text -> text.toLowerCase(java.util.Locale.ROOT)).toList();
            long stockReads = sql.stream().filter(text -> text.contains("from inventory_balances")).count();
            long stockWrites = sql.stream().filter(text -> text.startsWith("update inventory_balances") || text.startsWith("insert into inventory_reservations")).count();
            assertThat(stockReads).isEqualTo(2L*batches);
            assertThat(stockWrites).isEqualTo(2L*selectedRows);
            long voucherSql=sql.stream().filter(text -> text.matches("(?s).*\\b(?:from|join|update|into)\\s+voucher[a-z_]*\\b.*")).count();
            assertThat(voucherSql).isEqualTo(withVoucher ? 8 : 0);
            long voucherAddition=voucherSql+(withVoucher ? 1 : 0); // exact captured Cart-selection clear
            // Keep the prior non-Inventory ceiling; account for exactly measured Inventory work separately.
            assertThat(statements-selectedRows-stockReads-stockWrites-voucherAddition).isLessThanOrEqualTo(40L+10L*batches);
            assertThat(captured.size()-selectedRows-stockReads-stockWrites-voucherAddition).isLessThanOrEqualTo(40L+10L*batches);
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
            System.out.printf("CHECKOUT_QUERY_BOUND rows=%d statements=%d jdbc=%d stockReads=%d stockWrites=%d replay=1 detached=0%n",
                    selectedRows,statements,captured.size(),stockReads,stockWrites);
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
        assertThatThrownBy(() -> transactions.executeWithoutResult(s -> {
            jdbc.update("DELETE FROM product_variants WHERE id=?",fixture.variant());
            jdbc.update("DELETE FROM products WHERE id=?",fixture.product());
        })).isInstanceOf(RuntimeException.class);
        orderReads.cancelOrder(fixture.owner(),id,new com.example.demo.order.dto.request.CancelOrderRequest("Release before deletion"));
        transactions.executeWithoutResult(s -> {
            jdbc.update("DELETE FROM product_variants WHERE id=?",fixture.variant());
            jdbc.update("DELETE FROM products WHERE id=?",fixture.product());
        });
        assertThat(orderReads.getOrder(fixture.owner(),id).getItems().getFirst().getProductId()).isNull();
        assertThat(jdbc.queryForMap("SELECT stock,reserved,original_variant_id FROM inventory_balances WHERE original_variant_id=?", fixture.variant()))
                .containsEntry("stock", 1000L).containsEntry("reserved", 0L).containsEntry("original_variant_id", fixture.variant());
        assertThat(jdbc.queryForObject("SELECT state FROM inventory_reservations WHERE order_id=?", String.class, id)).isEqualTo("RELEASED");
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
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_reservations", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COALESCE(SUM(reserved),0) FROM inventory_balances", Long.class)).isZero();
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
    @Autowired com.example.demo.cart.service.CartVoucherService cartVouchers;
    private long voucherGrant() {
        return transactions.execute(tx -> {
            long voucher = com.example.demo.fixture.voucher.VoucherFixtures.definition(jdbc, "BUY_" + System.nanoTime(), "FIXED_AMOUNT", new java.math.BigDecimal("50"), 3L, 3L);
            long usage = com.example.demo.fixture.voucher.VoucherFixtures.usage(jdbc, voucher, fixture.owner());
            return com.example.demo.fixture.voucher.VoucherFixtures.grant(jdbc, voucher, fixture.owner(), usage, java.time.Instant.now().minusSeconds(1));
        });
    }
    @Test void checkout_shouldConsumeOnlyChosenEntitlementAndPreserveDiscountOnReplay() {
        long grant = voucherGrant();
        long other = transactions.execute(tx -> {
            var row = jdbc.queryForMap("SELECT voucher_id,voucher_customer_usage_id FROM voucher_entitlements WHERE id=?", grant);
            return com.example.demo.fixture.voucher.VoucherFixtures.grant(jdbc, ((Number)row.get("voucher_id")).longValue(), fixture.owner(), ((Number)row.get("voucher_customer_usage_id")).longValue(), java.time.Instant.now().minusSeconds(1));
        });
        cartVouchers.applyVoucher(fixture.owner(), grant);
        var receipt = service.checkout(fixture.owner(), "Voucher_success_key_123", new CheckoutRequest(fixture.address()));
        assertThat(receipt.getOrder().getPayableTotal().getAmount()).isEqualTo(receipt.getOrder().getMerchandiseTotal().getAmount().subtract(BigInteger.valueOf(50)));
        assertThat(jdbc.queryForObject("SELECT state FROM voucher_entitlements WHERE id=?", String.class, grant)).isEqualTo("CONSUMED");
        assertThat(jdbc.queryForObject("SELECT state FROM voucher_entitlements WHERE id=?", String.class, other)).isEqualTo("AVAILABLE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM voucher_redemptions WHERE order_id=?", Integer.class, receipt.getOrder().getId())).isOne();
        assertThat(jdbc.queryForObject("SELECT voucher_entitlement_id FROM carts WHERE id=?", Long.class, fixture.cart())).isNull();
        assertThat(jdbc.queryForObject("SELECT receipt_version FROM checkout_receipts WHERE order_id=?", Integer.class, receipt.getOrder().getId())).isEqualTo(2);
        Long laterRow=transactions.execute(tx -> com.example.demo.fixture.checkout.CheckoutFixtures.additionalItem(entities,fixture.cart(),true).getId());
        cartVouchers.applyVoucher(fixture.owner(),other);
        var originalDetail=orderReads.getOrder(fixture.owner(),receipt.getOrder().getId());
        assertThat(originalDetail.getVoucherDiscount().getAmount()).isEqualTo(BigInteger.valueOf(50));
        assertThat(originalDetail.getVoucher().getWalletEntitlementId()).isEqualTo(grant);
        jdbc.update("UPDATE vouchers SET enabled=FALSE,title='Edited later',valid_until=CURRENT_TIMESTAMP WHERE id=(SELECT voucher_id FROM voucher_entitlements WHERE id=?)", grant);
        assertThat(orderReads.getOrder(fixture.owner(),receipt.getOrder().getId())).usingRecursiveComparison().isEqualTo(originalDetail);
        var summary=orderReads.listOrders(fixture.owner(),new com.example.demo.order.dto.request.OrderListRequest(0,20,null,"payableTotal,asc")).getItems().getFirst();
        assertThat(summary.getVoucher().getTitle()).isEqualTo(originalDetail.getVoucher().getTitle());
        assertThat(summary.getVoucherDiscount().getAmount()).isEqualTo(BigInteger.valueOf(50));
        assertThatThrownBy(() -> jdbc.update("UPDATE voucher_redemptions SET title_snapshot='Changed' WHERE order_id=?",receipt.getOrder().getId())).hasStackTraceContaining("immutable");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM voucher_redemptions WHERE order_id=?",receipt.getOrder().getId())).hasStackTraceContaining("immutable");
        assertThat(service.checkout(fixture.owner(), "Voucher_success_key_123", new CheckoutRequest(fixture.address()))).usingRecursiveComparison().isEqualTo(receipt);
        assertThat(jdbc.queryForObject("SELECT redeemed_count FROM vouchers WHERE id=(SELECT voucher_id FROM voucher_entitlements WHERE id=?)", Long.class, grant)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT voucher_entitlement_id FROM carts WHERE id=?",Long.class,fixture.cart())).isEqualTo(other);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cart_items WHERE id=?",Long.class,laterRow)).isOne();
        assertThat(jdbc.queryForObject("SELECT state FROM voucher_entitlements WHERE id=?",String.class,other)).isEqualTo("AVAILABLE");
    }
    @Test void checkout_shouldRejectInvalidCapturedVoucherWithoutClearingOrSpendingIt() {
        long grant = voucherGrant(); cartVouchers.applyVoucher(fixture.owner(), grant);
        var before = jdbc.queryForMap("SELECT * FROM cart_items WHERE id=?", fixture.item());
        jdbc.update("UPDATE vouchers SET enabled=FALSE WHERE id=(SELECT voucher_id FROM voucher_entitlements WHERE id=?)", grant);
        assertThatThrownBy(() -> service.checkout(fixture.owner(), "Voucher_invalid_key_123", new CheckoutRequest(fixture.address())))
            .isInstanceOf(com.example.demo.common.exception.ApplicationException.class)
            .satisfies(f -> assertThat(((com.example.demo.common.exception.ApplicationException)f).getStatus()).isEqualTo(409));
        assertThat(jdbc.queryForMap("SELECT * FROM cart_items WHERE id=?", fixture.item())).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT voucher_entitlement_id FROM carts WHERE id=?", Long.class, fixture.cart())).isEqualTo(grant);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_reservations", Long.class)).isZero();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"ENCODE","RECEIPT","COMMIT"})
    void checkout_shouldRollBackVoucherAggregateAndAllowSameKeyRetry(String failure) {
        jdbc.update("UPDATE cart_items SET selected=TRUE WHERE id=?",fixture.unselected());
        long grant=voucherGrant(); cartVouchers.applyVoucher(fixture.owner(),grant);
        if (failure.equals("ENCODE")) org.mockito.Mockito.doThrow(new IllegalStateException("voucher encode failure")).when(codec).encode(org.mockito.ArgumentMatchers.any());
        if (failure.equals("RECEIPT")) jdbc.execute("ALTER TABLE checkout_receipts ADD CONSTRAINT ck_voucher_test_receipt CHECK(FALSE)");
        if (failure.equals("COMMIT")) {
            jdbc.execute("CREATE FUNCTION voucher_test_commit() RETURNS TRIGGER LANGUAGE PLPGSQL AS $$ BEGIN RAISE EXCEPTION 'voucher deferred failure' USING ERRCODE='23514'; END; $$");
            jdbc.execute("CREATE CONSTRAINT TRIGGER ct_voucher_test_commit AFTER INSERT ON checkout_receipts DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION voucher_test_commit()");
        }
        try {
            assertThatThrownBy(() -> service.checkout(fixture.owner(),"Voucher_rollback_123",new CheckoutRequest(fixture.address()))).isInstanceOf(RuntimeException.class);
            assertNoCommittedCheckout();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM voucher_redemptions",Long.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT redeemed_count FROM vouchers",Long.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT redeemed_count FROM voucher_customer_usages",Long.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT state FROM voucher_entitlements WHERE id=?",String.class,grant)).isEqualTo("AVAILABLE");
            assertThat(jdbc.queryForObject("SELECT voucher_entitlement_id FROM carts WHERE id=?",Long.class,fixture.cart())).isEqualTo(grant);
        } finally {
            org.mockito.Mockito.reset(codec);
            if (failure.equals("RECEIPT")) jdbc.execute("ALTER TABLE checkout_receipts DROP CONSTRAINT ck_voucher_test_receipt");
            if (failure.equals("COMMIT")) { jdbc.execute("DROP TRIGGER ct_voucher_test_commit ON checkout_receipts"); jdbc.execute("DROP FUNCTION voucher_test_commit()"); }
        }
        assertThat(service.checkout(fixture.owner(),"Voucher_rollback_123",new CheckoutRequest(fixture.address())).getOrder()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM voucher_redemptions",Long.class)).isOne();
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"12.34","0.01"})
    void checkout_shouldPersistExactPercentageIncludingRoundedZero(String percent) {
        long grant=voucherGrant();
        jdbc.update("UPDATE product_variants SET selling_price=1 WHERE id=?",fixture.variant());
        jdbc.update("UPDATE vouchers SET discount_type='PERCENTAGE',discount_value=? WHERE id=(SELECT voucher_id FROM voucher_entitlements WHERE id=?)",new java.math.BigDecimal(percent),grant);
        cartVouchers.applyVoucher(fixture.owner(),grant);
        var receipt=service.checkout(fixture.owner(),"Voucher_percent_123",new CheckoutRequest(fixture.address()));
        var v2=(com.example.demo.checkout.dto.response.CheckoutOrderReceiptV2)receipt.getOrder();
        assertThat(v2.getVoucherDiscount().getAmount()).isZero();
        assertThat(v2.getVoucher()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT state FROM voucher_entitlements WHERE id=?",String.class,grant)).isEqualTo("CONSUMED");
    }

    @Test void checkout_shouldRejectVoucherMinimumAfterSkippingPreviouslyEligibleRows() {
        jdbc.update("UPDATE cart_items SET selected=TRUE WHERE id=?",fixture.unselected());
        long grant=voucherGrant();
        jdbc.update("UPDATE vouchers SET minimum_subtotal=3000 WHERE id=(SELECT voucher_id FROM voucher_entitlements WHERE id=?)",grant);
        cartVouchers.applyVoucher(fixture.owner(),grant);
        jdbc.update("UPDATE products SET status='ARCHIVED' WHERE id=?",fixture.product());
        assertThatThrownBy(() -> service.checkout(fixture.owner(),"Voucher_minimum_123",new CheckoutRequest(fixture.address())))
            .isInstanceOf(com.example.demo.voucher.exception.CheckoutVoucherNotApplicableException.class)
            .satisfies(f -> assertThat(((com.example.demo.common.exception.ApplicationException)f).getProperties()).containsEntry("reason","MINIMUM_SUBTOTAL_NOT_MET"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders",Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_reservations",Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM voucher_redemptions",Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT voucher_entitlement_id FROM carts WHERE id=?",Long.class,fixture.cart())).isEqualTo(grant);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cart_items WHERE cart_id=?",Long.class,fixture.cart())).isEqualTo(2);
    }

    @Test void history_shouldSortByDiscountedPayableAcrossTwoActualPurchases() {
        long firstGrant=voucherGrant();cartVouchers.applyVoucher(fixture.owner(),firstGrant);
        var first=service.checkout(fixture.owner(),"History_sort_first_123",new CheckoutRequest(fixture.address()));
        transactions.executeWithoutResult(tx -> com.example.demo.fixture.checkout.CheckoutFixtures.additionalItem(entities,fixture.cart(),true));
        long secondGrant=voucherGrant();
        jdbc.update("UPDATE vouchers SET discount_value=500 WHERE id=(SELECT voucher_id FROM voucher_entitlements WHERE id=?)",secondGrant);
        cartVouchers.applyVoucher(fixture.owner(),secondGrant);
        var second=service.checkout(fixture.owner(),"History_sort_second_123",new CheckoutRequest(fixture.address()));
        assertThat(first.getOrder().getMerchandiseTotal().getAmount()).isEqualTo(second.getOrder().getMerchandiseTotal().getAmount());
        assertThat(orderReads.listOrders(fixture.owner(),new com.example.demo.order.dto.request.OrderListRequest(0,20,null,"payableTotal,asc")).getItems())
            .extracting(com.example.demo.order.dto.response.OrderSummaryResponse::getId).containsExactly(second.getOrder().getId(),first.getOrder().getId());
        assertThat(orderReads.listOrders(fixture.owner(),new com.example.demo.order.dto.request.OrderListRequest(0,20,null,"payableTotal,desc")).getItems())
            .extracting(com.example.demo.order.dto.response.OrderSummaryResponse::getId).containsExactly(first.getOrder().getId(),second.getOrder().getId());
        assertThat(orderReads.getOrder(fixture.owner(),first.getOrder().getId()).getVoucherDiscount().getAmount()).isEqualTo(BigInteger.valueOf(50));
        assertThat(orderReads.getOrder(fixture.owner(),second.getOrder().getId()).getVoucherDiscount().getAmount()).isEqualTo(BigInteger.valueOf(500));
    }
}
