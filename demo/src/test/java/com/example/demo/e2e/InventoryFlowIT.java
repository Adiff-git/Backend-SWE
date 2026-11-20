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

/** Actual bearer/cart/checkout/history/cancel flow plus the trusted internal completion boundary. */
@AutoConfigureMockMvc
class InventoryFlowIT extends CheckoutIntegrationSupport {
    @Autowired MockMvc mvc;
    @Autowired PasswordEncoder passwords;
    @Autowired JsonMapper json;
    @Autowired InventoryService inventory;
    @Autowired OrderCompletionService completion;
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

    private JsonNode purchase(String key) throws Exception {
        return json.readTree(mvc.perform(post("/api/me/cart/checkout").header("Authorization", token)
                .header("Idempotency-Key", key).contentType("application/json")
                .content(json.writeValueAsString(Map.of("addressId", fixture.address()))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    @Test void inventoryLifecycle_shouldKeepOtherOwnershipFrozenReceiptsAndNonReservingCartPreferences() throws Exception {
        InventoryFixtures.supplyStock(jdbc, fixture.variant(), 7);
        UUID unavailable = jdbc.queryForObject("SELECT product_variant_id FROM cart_items WHERE id=?", UUID.class, fixture.unselected());
        InventoryFixtures.supplyStock(jdbc, unavailable, 0);
        jdbc.update("UPDATE cart_items SET selected=true WHERE id=?", fixture.unselected());
        long foreignOrder = transactions.execute(s -> {
            long id = InventoryFixtures.order(jdbc, fixture.foreign(), true, fixture.variant(), 4);
            long item = jdbc.queryForObject("SELECT id FROM order_items WHERE order_id=?", Long.class, id);
            inventory.reserveReservations(new ReservationCommand(id, Instant.parse("2026-01-01T00:00:00Z"),
                    List.of(new ReservationLine(item, fixture.variant(), 4))));
            return id;
        });
        JsonNode cart = json.readTree(mvc.perform(get("/api/me/cart").header("Authorization", token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(cart.get("totalItemCount").asLong()).isEqualTo(2);
        assertThat(cart.get("selectedTotalQuantity").asLong()).isEqualTo(2);
        mvc.perform(post("/api/me/cart/items").header("Authorization", token).contentType("application/json")
                .content(json.writeValueAsString(Map.of("productVariantId", fixture.variant(), "quantity", 2))))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForMap("SELECT stock,reserved FROM inventory_balances WHERE original_variant_id=?", fixture.variant()))
                .containsEntry("stock", 7L).containsEntry("reserved", 4L);
        JsonNode first = purchase("Inventory_flow_key_1");
        long cancelled = first.get("order").get("id").asLong();
        assertThat(first.get("skippedItems").size()).isEqualTo(1);
        assertThat(first.get("skippedItems").get(0).get("reason").asString()).isEqualTo("INSUFFICIENT_STOCK");
        mvc.perform(get("/api/me/orders/"+foreignOrder).header("Authorization", token)).andExpect(status().isNotFound());
        assertThat(purchase("Inventory_flow_key_1")).isEqualTo(first);
        for (int repeat = 0; repeat < 2; repeat++) {
            mvc.perform(post("/api/me/orders/"+cancelled+"/cancel").header("Authorization", token))
                    .andExpect(status().isNoContent());
        }
        assertThat(purchase("Inventory_flow_key_1")).isEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT reserved FROM inventory_balances WHERE original_variant_id=?", Long.class, fixture.variant())).isEqualTo(4);
        mvc.perform(post("/api/me/cart/items").header("Authorization", token).contentType("application/json")
                .content(json.writeValueAsString(Map.of("productVariantId", fixture.variant(), "quantity", 2))))
                .andExpect(status().isOk());
        JsonNode second = purchase("Inventory_flow_key_2");
        long completed = second.get("order").get("id").asLong();
        var snapshots = jdbc.queryForList("SELECT * FROM order_items WHERE order_id=?", completed);
        jdbc.update("UPDATE orders SET status='SHIPPING' WHERE id=?", completed); // Fixture only; no shipping/confirmation API is added.
        completion.completeOrder(fixture.owner(), completed);
        completion.completeOrder(fixture.owner(), completed);
        assertThat(purchase("Inventory_flow_key_2")).isEqualTo(second);
        assertThat(jdbc.queryForList("SELECT * FROM order_items WHERE order_id=?", completed)).isEqualTo(snapshots);
        assertThat(jdbc.queryForMap("SELECT stock,reserved FROM inventory_balances WHERE original_variant_id=?", fixture.variant()))
                .containsEntry("stock", 5L).containsEntry("reserved", 4L);
        assertThat(jdbc.queryForObject("SELECT state FROM inventory_reservations WHERE order_id=?", String.class, foreignOrder)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT quantity FROM inventory_reservations WHERE order_id=?", Integer.class, foreignOrder)).isEqualTo(4);
        JsonNode remaining = json.readTree(mvc.perform(get("/api/me/cart").header("Authorization", token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(remaining.get("items").size()).isEqualTo(1);
        assertThat(remaining.get("items").get(0).get("quantity").asInt()).isEqualTo(2);
        assertThat(remaining.get("items").get(0).get("selected").asBoolean()).isTrue();
        assertThat(remaining.get("selectedItemCount").asLong()).isZero();
        long failed = transactions.execute(s -> OrderFixtures.persist(entities, fixture.owner(), OrderStatus.FAILED, 1).getId());
        mvc.perform(get("/api/me/orders/"+failed).header("Authorization", token)).andExpect(status().isOk());
        mvc.perform(post("/api/me/orders/"+failed+"/cancel").header("Authorization", token)).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_reservations", Long.class)).isEqualTo(3);
    }
}
