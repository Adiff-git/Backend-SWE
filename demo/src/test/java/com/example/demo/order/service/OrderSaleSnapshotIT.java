package com.example.demo.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo.checkout.converter.CheckoutConverter;
import com.example.demo.checkout.dto.request.CheckoutRequest;
import com.example.demo.checkout.dto.response.CheckoutReceiptResponse;
import com.example.demo.checkout.service.CheckoutService;
import com.example.demo.config.CheckoutIntegrationSupport;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;

class OrderSaleSnapshotIT extends CheckoutIntegrationSupport {
    @Autowired private CheckoutService checkout;
    @Autowired private CheckoutConverter converter;

    @Test
    void publishedVariantRejectsZeroBasePrice() {
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE product_variants SET base_price=0 WHERE id=?", fixture.variant()))
                .hasStackTraceContaining("Invalid Published variants");
    }

    @ParameterizedTest
    @CsvSource({"1000,,1000,0,false", "101,12.50,89,12,false", "1000,20,800,200,true", "1000,100,0,1000,false"})
    void checkout_shouldCommitReloadableImmutableSaleEvidenceAndReplayOriginalReceipt(
            long base, BigDecimal percentage, long paid, long discount, boolean bounded) {
        jdbc.update("UPDATE product_variants SET base_price=? WHERE id=?", base, fixture.variant());
        if (percentage != null) {
            jdbc.update("UPDATE products SET sale_percentage=?,sale_enabled=TRUE WHERE id=?", percentage, fixture.product());
        }
        if (bounded) jdbc.update("UPDATE products SET sale_starts_at=CURRENT_TIMESTAMP-INTERVAL '1 hour',sale_ends_at=CURRENT_TIMESTAMP+INTERVAL '1 hour' WHERE id=?",fixture.product());
        CheckoutReceiptResponse receipt = checkout.checkout(fixture.owner(), "Snapshot_reload_1234", new CheckoutRequest(fixture.address()));
        Long order = receipt.getOrder().getId();
        // The service call has committed. This JDBC read cannot return a managed command/entity snapshot.
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM order_items WHERE order_id=?", order);
        assertThat((BigDecimal) row.get("original_unit_price_snapshot")).isEqualByComparingTo(BigDecimal.valueOf(base));
        assertThat((BigDecimal) row.get("unit_price_snapshot")).isEqualByComparingTo(BigDecimal.valueOf(paid));
        assertThat((BigDecimal) row.get("direct_unit_discount_snapshot")).isEqualByComparingTo(BigDecimal.valueOf(discount));
        assertThat((BigDecimal) row.get("line_total")).isEqualByComparingTo(BigDecimal.valueOf(paid * 2));
        if (percentage == null) assertThat(row.get("direct_sale_percentage_snapshot")).isNull();
        else assertThat((BigDecimal) row.get("direct_sale_percentage_snapshot")).isEqualByComparingTo(percentage);
        assertThat(((Timestamp) row.get("pricing_instant_snapshot")).toInstant()).isEqualTo(receipt.getOrder().getPlacedAt());
        assertThat(jdbc.queryForObject("SELECT receipt_version FROM checkout_receipts WHERE order_id=?", Integer.class, order)).isEqualTo(3);
        var item = (com.example.demo.checkout.dto.response.CheckoutItemReceiptV3)receipt.getOrder().getItems().getFirst();
        assertThat(item.getOriginalUnitPriceSnapshot().getAmount()).isEqualTo(((BigDecimal)row.get("original_unit_price_snapshot")).toBigIntegerExact());
        assertThat(item.getDirectUnitDiscountSnapshot().getAmount()).isEqualTo(((BigDecimal)row.get("direct_unit_discount_snapshot")).toBigIntegerExact());
        if (bounded) {
            assertThat(((Timestamp)row.get("direct_sale_starts_at_snapshot")).toInstant()).isEqualTo(item.getDirectSaleSnapshot().getStartsAt());
            assertThat(((Timestamp)row.get("direct_sale_ends_at_snapshot")).toInstant()).isEqualTo(item.getDirectSaleSnapshot().getEndsAt());
        }
        String original = converter.encode(receipt);
        jdbc.update("UPDATE product_variants SET base_price=3000,option_combination='{\"choice\":\"renamed\"}' WHERE id=?", fixture.variant());
        jdbc.update("UPDATE products SET sale_percentage=25,sale_enabled=TRUE WHERE id=?", fixture.product());
        assertThat(converter.encode(checkout.checkout(fixture.owner(), "Snapshot_reload_1234", new CheckoutRequest(fixture.address()))))
                .isEqualTo(original);
        assertThat(jdbc.queryForObject("SELECT original_unit_price_snapshot FROM order_items WHERE order_id=?", BigDecimal.class, order))
                .isEqualByComparingTo(BigDecimal.valueOf(base));
        assertThatThrownBy(() -> jdbc.update("UPDATE order_items SET original_unit_price_snapshot=9999 WHERE order_id=?", order))
                .hasStackTraceContaining("immutable");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_reservations WHERE order_id=?", Integer.class, order)).isEqualTo(1);
    }
}
