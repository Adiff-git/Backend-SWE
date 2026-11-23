package com.example.demo.cart.controller;

import com.example.demo.catalog.CatalogFixtures;
import com.example.demo.catalog.entity.ProductStatus;
import com.example.demo.user.service.UserService;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@SpringBootTest(properties = {"spring.datasource.hikari.maximum-pool-size=6", "cart.lock-timeout-ms=200"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CartControllerIT {
    @Test void stockAwareHttp_shouldRejectFullDemandAndKeepRemovalAndBulkSelectionUsable() throws Exception {
        com.example.demo.fixture.inventory.InventoryFixtures.supplyStock(jdbc, variant, 3);
        Credentials user = register();
        long item = body(add(user.access(), 2, 200)).get("items").get(0).get("id").asLong();
        var before = jdbc.queryForMap("SELECT * FROM cart_items WHERE id=?", item);
        assertThat(body(add(user.access(), 2, 409)).get("code").asString()).isEqualTo("CART_STOCK_CONFLICT");
        assertThat(jdbc.queryForMap("SELECT * FROM cart_items WHERE id=?", item)).isEqualTo(before);
        mvc.perform(put("/api/me/cart/items/"+item+"/quantity").header("Authorization", user.access())
                .contentType("application/json").content("{\"quantity\":4}")).andExpect(status().isConflict());
        com.example.demo.fixture.inventory.InventoryFixtures.supplyStock(jdbc, variant, 1);
        JsonNode cart = body(mvc.perform(get("/api/me/cart").header("Authorization", user.access())).andExpect(status().isOk()).andReturn());
        assertThat(cart.get("items").get(0).get("quantity").asInt()).isEqualTo(2);
        assertThat(cart.get("items").get(0).get("availabilityReason").asString()).isEqualTo("INSUFFICIENT_STOCK");
        assertThat(cart.get("items").get(0).get("subtotal").isNull()).isTrue();
        assertThat(cart.get("selectedItemCount").asLong()).isZero();
        mvc.perform(put("/api/me/cart/items/"+item+"/selection").header("Authorization", user.access())
                .contentType("application/json").content("{\"selected\":true}")).andExpect(status().isConflict());
        mvc.perform(put("/api/me/cart/selection").header("Authorization", user.access())
                .contentType("application/json").content("{\"selected\":true}")).andExpect(status().isOk());
        mvc.perform(put("/api/me/cart/items/"+item+"/selection").header("Authorization", user.access())
                .contentType("application/json").content("{\"selected\":false}")).andExpect(status().isOk());
        mvc.perform(delete("/api/me/cart/items/"+item).header("Authorization", user.access())).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT reserved FROM inventory_balances WHERE original_variant_id=?", Long.class, variant)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_reservations", Long.class)).isZero();
    }
    @Autowired MockMvc mvc;
    @Autowired UserService users;
    @Autowired PasswordEncoder passwords;
    @Autowired JsonMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entities;
    @Autowired TransactionTemplate transactions;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.example.demo.storage.service.ImageUrlResolver imageUrls;
    UUID variant;

    @BeforeEach void prepare() {
        cleanup();
        org.mockito.Mockito.when(imageUrls.prepare()).thenReturn(key -> "https://images.test/cart");
        variant = transactions.execute(s -> CatalogFixtures.persistReadyProduct(entities).variant().getId());
        com.example.demo.fixture.inventory.InventoryFixtures.supplyStock(jdbc, variant, 1000);
    }
    @AfterEach void cleanup() { com.example.demo.fixture.inventory.InventoryFixtures.cleanRetainedInventory(jdbc); }

    @Test void addAndRead_shouldUseRealSessionAndCurrentPrices() throws Exception {
        Credentials user = register();
        mvc.perform(get("/api/me/cart").header("Authorization", user.access())).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, no-store"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM carts", Long.class)).isZero();
        JsonNode first = body(add(user.access(), 2, 200));
        JsonNode second = body(add(user.access(), 3, 200));
        assertThat(second.get("items").get(0).get("id")).isEqualTo(first.get("items").get(0).get("id"));
        assertThat(second.get("selectedTotalQuantity").asLong()).isEqualTo(5);
        jdbc.update("UPDATE product_variants SET selling_price=50 WHERE id=?", variant);
        assertThat(body(mvc.perform(get("/api/me/cart").header("Authorization", user.access()))
                .andExpect(status().isOk()).andReturn()).get("finalTotal").get("amount").asLong()).isEqualTo(250);
        Credentials other = register();
        assertThat(body(mvc.perform(get("/api/me/cart").header("Authorization", other.access()))
                .andExpect(status().isOk()).andReturn()).get("items").size()).isZero();
    }

    @Test void changeVariant_shouldResetOnlyAbsentTargetAndPreserveExistingTarget() throws Exception {
        Credentials user = register();
        long source = body(add(user.access(), 8, 200)).get("items").get(0).get("id").asLong();
        jdbc.update("UPDATE cart_items SET selected=FALSE WHERE id=?", source);
        UUID sibling = transactions.execute(s -> {
            var parent = entities.find(com.example.demo.catalog.entity.ProductVariant.class, variant).getProduct();
            var target = CatalogFixtures.variant(parent, Map.of("color", "sibling"), java.math.BigDecimal.TEN);
            entities.persist(target);
            entities.flush();
            com.example.demo.fixture.inventory.InventoryFixtures.supplyStock(entities, target.getId(), 1000);
            return target.getId();
        });
        String route = "/api/me/cart/items/" + source + "/variant";
        String siblingBody = json.writeValueAsString(Map.of("productVariantId", sibling));
        JsonNode reset = body(mvc.perform(put(route).header("Authorization", user.access())
                .contentType("application/json").content(siblingBody)).andExpect(status().isOk()).andReturn());
        assertThat(reset.get("totalItemCount").asLong()).isEqualTo(1);
        assertThat(reset.get("selectedItemCount").asLong()).isZero();
        assertThat(reset.get("items").get(0).get("quantity").asInt()).isEqualTo(1);
        assertThat(reset.get("items").get(0).get("selected").asBoolean()).isFalse();
        mvc.perform(put("/api/me/cart/items/" + source + "/quantity").header("Authorization", user.access())
                .contentType("application/json").content("{\"quantity\":8}")).andExpect(status().isOk());
        mvc.perform(put(route).header("Authorization", user.access()).contentType("application/json")
                .content(siblingBody)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT quantity FROM cart_items WHERE id=?", Integer.class, source)).isEqualTo(8);
        long target = body(add(user.access(), 4, 200)).get("items").get(0).get("id").asLong();
        jdbc.update("UPDATE cart_items SET selected=FALSE WHERE id=?", target);
        var before = jdbc.queryForMap("SELECT * FROM cart_items WHERE id=?", target);
        JsonNode merged = body(mvc.perform(put(route).header("Authorization", user.access())
                .contentType("application/json").content(json.writeValueAsString(Map.of("productVariantId", variant))))
                .andExpect(status().isOk()).andReturn());
        assertThat(merged.get("totalItemCount").asLong()).isEqualTo(1);
        assertThat(merged.get("items").get(0).get("id").asLong()).isEqualTo(target);
        assertThat(jdbc.queryForMap("SELECT * FROM cart_items WHERE id=?", target)).isEqualTo(before);
    }

    @Test void permission_shouldObserveRevokeAndRestoreOnSameToken() throws Exception {
        Credentials user = register();
        Long owner = jdbc.queryForObject("SELECT id FROM users WHERE username=?", Long.class, user.username());
        jdbc.update("DELETE FROM user_roles WHERE user_id=?", owner);
        add(user.access(), 2, 403);
        jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE name='USER'", owner);
        add(user.access(), 2, 200);
    }

    @Test void updateAndDelete_shouldProtectOwnerAndRetainEmptyCart() throws Exception {
        Credentials user = register();
        long item = body(add(user.access(), 2, 200)).get("items").get(0).get("id").asLong();
        Credentials other = register();
        String foreign = mvc.perform(put("/api/me/cart/items/" + item + "/quantity").header("Authorization", other.access())
                .contentType("application/json").content("{\"quantity\":4}")).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        String missing = mvc.perform(put("/api/me/cart/items/99999999/quantity").header("Authorization", other.access())
                .contentType("application/json").content("{\"quantity\":4}")).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(foreign).get("detail")).isEqualTo(json.readTree(missing).get("detail"));
        mvc.perform(put("/api/me/cart/items/" + item + "/selection").header("Authorization", other.access())
                .contentType("application/json").content("{\"selected\":false}")).andExpect(status().isNotFound());
        JsonNode updated = body(mvc.perform(put("/api/me/cart/items/" + item + "/quantity").header("Authorization", user.access())
                .contentType("application/json").content("{\"quantity\":4}")).andExpect(status().isOk()).andReturn());
        assertThat(updated.get("items").get(0).get("quantity").asInt()).isEqualTo(4);
        mvc.perform(delete("/api/me/cart/items/" + item).header("Authorization", user.access())).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cart_items", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM carts", Long.class)).isEqualTo(1);
    }

    @Test void selection_shouldUseCurrentPermissionAndExactTotals() throws Exception {
        Credentials user = register();
        mvc.perform(put("/api/me/cart/selection").header("Authorization", user.access()).contentType("application/json")
                .content("{\"selected\":true}")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM carts", Long.class)).isZero();
        long item = body(add(user.access(), 2, 200)).get("items").get(0).get("id").asLong();
        Long owner = jdbc.queryForObject("SELECT id FROM users WHERE username=?", Long.class, user.username());
        for (String route : java.util.List.of("/api/me/cart/selection", "/api/me/cart/items/" + item + "/selection")) {
            jdbc.update("DELETE FROM user_roles WHERE user_id=?", owner);
            mvc.perform(put(route).header("Authorization", user.access()).contentType("application/json").content("{\"selected\":false}"))
                    .andExpect(status().isForbidden());
            jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE name='USER'", owner);
            assertThat(body(mvc.perform(put(route).header("Authorization", user.access()).contentType("application/json")
                    .content("{\"selected\":false}")).andExpect(status().isOk()).andReturn()).get("finalTotal").get("amount").asLong()).isZero();
        }
        jdbc.update("UPDATE products SET status='ARCHIVED' WHERE id=(SELECT product_id FROM product_variants WHERE id=?)", variant);
        mvc.perform(put("/api/me/cart/items/" + item + "/selection").header("Authorization", user.access())
                .contentType("application/json").content("{\"selected\":true}")).andExpect(status().is(422));
    }

    @Test void signingFailure_shouldReturn503AndLeaveNoCreatedCart() throws Exception {
        Credentials user = register();
        // Existing positive fixture supplies the primary; exercise signing failure, not a duplicate gallery.
        org.mockito.Mockito.when(imageUrls.prepare()).thenReturn(key -> { throw new IllegalStateException("Controlled failure"); });
        add(user.access(), 2, 503);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM carts", Long.class)).isZero();
    }

    @Test void contention_shouldReturn409WithoutLeakingSqlOrChangingQuantity() throws Exception {
        Credentials user = register();
        add(user.access(), 2, 200);
        Long owner = jdbc.queryForObject("SELECT id FROM users WHERE username=?", Long.class, user.username());
        var held = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        var holder = executor.submit(() -> transactions.executeWithoutResult(s -> {
            jdbc.queryForObject("SELECT id FROM carts WHERE user_id=? FOR UPDATE", Long.class, owner);
            held.countDown(); com.example.demo.cart.service.CartTestBarrier.await(release);
        }));
        try {
            assertThat(held.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            String response = add(user.access(), 3, 409).getResponse().getContentAsString();
            assertThat(response).doesNotContain("SELECT", "55P03", "cart_items", "Exception");
        } finally {
            release.countDown(); holder.get(10, java.util.concurrent.TimeUnit.SECONDS); executor.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT quantity FROM cart_items", Integer.class)).isEqualTo(2);
    }

    Credentials register() throws Exception {
        String username = "cart-" + UUID.randomUUID();
        users.createVerifiedAccount(username + "@example.com", passwords.encode("Password 123"), Instant.now());
        MvcResult login = mvc.perform(post("/api/auth/login").contentType("application/json")
                .content(json.writeValueAsString(Map.of("identifier", username, "password", "Password 123"))))
                .andExpect(status().isOk()).andReturn();
        return new Credentials(username, login.getResponse().getHeader("Authorization"), body(login).get("refreshToken").asString());
    }
    MvcResult add(String access, int quantity, int expected) throws Exception {
        return mvc.perform(post("/api/me/cart/items").header("Authorization", access).contentType("application/json")
                .content(json.writeValueAsString(Map.of("productVariantId", variant, "quantity", quantity))))
                .andExpect(status().is(expected)).andReturn();
    }
    JsonNode body(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsString()); }
    record Credentials(String username, String access, String refresh) { }
}
