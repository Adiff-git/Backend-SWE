package com.example.demo.e2e;

import com.example.demo.config.CheckoutIntegrationSupport;
import com.example.demo.fixture.inventory.InventoryFixtures;
import com.example.demo.fixture.order.OrderFixtures;
import com.example.demo.inventory.dto.request.ReservationCommand;
import com.example.demo.inventory.dto.request.ReservationLine;
import com.example.demo.inventory.service.InventoryService;
import com.example.demo.order.entity.OrderStatus;
import com.example.demo.order.service.OrderCompletionService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Actual bearer wallet/Cart/Checkout/history and immutable old/new receipt flow. */
@AutoConfigureMockMvc
class VoucherFlowIT extends CheckoutIntegrationSupport {
    @Autowired MockMvc mvc;
    @Autowired PasswordEncoder passwords;
    @Autowired JsonMapper json;
    private String token;

    @BeforeEach void login() throws Exception {
        String identifier = jdbc.queryForObject("SELECT username FROM users WHERE id=?", String.class, fixture.owner());
        jdbc.update("UPDATE users SET password=?,email=?,email_verified_at=CURRENT_TIMESTAMP WHERE id=?",
                passwords.encode("Password 123"), "inventory-"+UUID.randomUUID()+"@example.com", fixture.owner());
        jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE name='USER'", fixture.owner());
        token = mvc.perform(post("/api/auth/login").contentType("application/json")
                .content(json.writeValueAsString(Map.of("identifier", identifier, "password", "Password 123"))))
                .andExpect(status().isOk()).andReturn().getResponse().getHeader("Authorization");
    }

    private JsonNode purchase(String key) throws Exception {return purchase(key,fixture.address());}
    private JsonNode purchase(String key,Long address) throws Exception {
        return json.readTree(mvc.perform(post("/api/me/cart/checkout").header("Authorization", token)
                .header("Idempotency-Key", key).contentType("application/json")
                .content(json.writeValueAsString(Map.of("addressId", address))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"1000000,20,1,800000", "101,12.50,3,267", "1000,100,1,0"})
    void directSaleFlow_shouldPersistAuthoritativePricesAndStackSeparateVoucher(long base,java.math.BigDecimal percent,int quantity,long total) throws Exception {
        jdbc.update("UPDATE product_variants SET base_price=? WHERE id=?",base,fixture.variant());
        jdbc.update("UPDATE products SET sale_percentage=?,sale_enabled=TRUE WHERE id=?",percent,fixture.product());
        jdbc.update("UPDATE cart_items SET quantity=? WHERE id=?",quantity,fixture.item());
        if (base==1000000) {
            long grant=transactions.execute(tx -> {
                long voucher=com.example.demo.fixture.voucher.VoucherFixtures.definition(jdbc,"SALE_STACK","PERCENTAGE",java.math.BigDecimal.TEN,null,null);
                long usage=com.example.demo.fixture.voucher.VoucherFixtures.usage(jdbc,voucher,fixture.owner());
                return com.example.demo.fixture.voucher.VoucherFixtures.grant(jdbc,voucher,fixture.owner(),usage,Instant.now().minusSeconds(1));
            });
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/me/cart/voucher").header("Authorization",token).contentType("application/json")
                .content(json.writeValueAsString(Map.of("walletEntitlementId",grant)))).andExpect(status().isOk());
        }
        var result=purchase("Sale_http_flow_12345");
        assertThat(result.get("order").get("merchandiseTotal").get("amount").longValue()).isEqualTo(total);
        assertThat(result.get("order").get("payableTotal").get("amount").longValue()).isEqualTo(total-(base==1000000 ? 80000 : 0));
        long order=result.get("order").get("id").longValue();
        var stored=jdbc.queryForMap("SELECT original_unit_price_snapshot,unit_price_snapshot,direct_unit_discount_snapshot,line_total,pricing_instant_snapshot FROM order_items WHERE order_id=?",order);
        var item=result.get("order").get("items").get(0);
        assertThat(((java.math.BigDecimal)stored.get("original_unit_price_snapshot")).longValueExact()).isEqualTo(base);
        assertThat(((java.math.BigDecimal)stored.get("unit_price_snapshot")).toBigIntegerExact()).isEqualTo(item.get("unitPriceSnapshot").get("amount").bigIntegerValue());
        assertThat(((java.math.BigDecimal)stored.get("line_total")).longValueExact()).isEqualTo(total);
        assertThat(((java.sql.Timestamp)stored.get("pricing_instant_snapshot")).toInstant()).isEqualTo(Instant.parse(result.get("order").get("placedAt").asText()));
        jdbc.update("UPDATE products SET sale_enabled=FALSE WHERE id=?",fixture.product());
        jdbc.update("UPDATE product_variants SET base_price=99999 WHERE id=?",fixture.variant());
        assertThat(purchase("Sale_http_flow_12345")).isEqualTo(result);
    }

    @Test void voucherFlow_shouldKeepOtherGrantsSkippedRowsAndImmutableReceipts() throws Exception {
        Long[] grants=transactions.execute(tx -> {
            long definition=com.example.demo.fixture.voucher.VoucherFixtures.definition(jdbc,"FLOW","PERCENTAGE",new java.math.BigDecimal("12.34"),3L,3L);
            long usage=com.example.demo.fixture.voucher.VoucherFixtures.usage(jdbc,definition,fixture.owner());
            return new Long[]{com.example.demo.fixture.voucher.VoucherFixtures.grant(jdbc,definition,fixture.owner(),usage,Instant.now().minusSeconds(1)),com.example.demo.fixture.voucher.VoucherFixtures.grant(jdbc,definition,fixture.owner(),usage,Instant.now().minusSeconds(1))};
        });
        // Original v1 successful receipt predates Voucher; replay must preserve its exact shape.
        long legacy=transactions.execute(tx -> {
            var order=com.example.demo.fixture.checkout.CheckoutFixtures.order(jdbc,fixture.owner());
            com.example.demo.fixture.checkout.CheckoutFixtures.receipt(jdbc,fixture.owner(),order,"Legacy_flow_key_123");return order.id();
        });
        String stored=jdbc.queryForObject("SELECT receipt_body::TEXT FROM checkout_receipts WHERE order_id=?",String.class,legacy);
        assertThat(purchase("Legacy_flow_key_123",1L)).isEqualTo(json.readTree(stored));
        var wallet=json.readTree(mvc.perform(get("/api/me/vouchers").header("Authorization",token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(wallet.get("totalElements").longValue()).isEqualTo(2);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/me/cart/voucher").header("Authorization",token).contentType("application/json").content(json.writeValueAsString(Map.of("walletEntitlementId",grants[0])))).andExpect(status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/me/cart/items/"+fixture.item()+"/quantity").header("Authorization",token).contentType("application/json").content("{\"quantity\":3}")).andExpect(status().isOk());
        jdbc.update("UPDATE cart_items SET selected=true WHERE id=?",fixture.unselected());
        UUID skipped=jdbc.queryForObject("SELECT product_variant_id FROM cart_items WHERE id=?",UUID.class,fixture.unselected());
        InventoryFixtures.supplyStock(jdbc,skipped,0);
        var preserved=jdbc.queryForMap("SELECT * FROM cart_items WHERE id=?",fixture.unselected());
        var receipt=purchase("Voucher_flow_key_123");
        assertThat(receipt.get("order").get("merchandiseTotal").get("amount").intValue()).isEqualTo(3000);
        assertThat(receipt.get("order").get("voucherDiscount").get("amount").intValue()).isEqualTo(370);
        assertThat(receipt.get("order").get("payableTotal").get("amount").intValue()).isEqualTo(2630);
        assertThat(receipt.get("order").get("items").get(0).get("unitPriceSnapshot").get("amount").intValue()).isEqualTo(1000);
        assertThat(receipt.get("skippedItems").size()).isEqualTo(1);
        long order=receipt.get("order").get("id").longValue();
        var detail=json.readTree(mvc.perform(get("/api/me/orders/"+order).header("Authorization",token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(detail.get("voucher")).isEqualTo(receipt.get("order").get("voucher"));
        assertThat(jdbc.queryForMap("SELECT * FROM cart_items WHERE id=?",fixture.unselected())).isEqualTo(preserved);
        assertThat(jdbc.queryForMap("SELECT stock,reserved FROM inventory_balances WHERE original_variant_id=?",fixture.variant())).containsEntry("stock",1000L).containsEntry("reserved",3L);
        assertThat(jdbc.queryForObject("SELECT state FROM voucher_entitlements WHERE id=?",String.class,grants[1])).isEqualTo("AVAILABLE");
        jdbc.update("UPDATE vouchers SET enabled=false,title='Edited after purchase'");
        assertThat(purchase("Voucher_flow_key_123")).isEqualTo(receipt);
        assertThat(purchase("Legacy_flow_key_123",1L)).isEqualTo(json.readTree(stored));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM voucher_redemptions",Long.class)).isOne();
    }
}
