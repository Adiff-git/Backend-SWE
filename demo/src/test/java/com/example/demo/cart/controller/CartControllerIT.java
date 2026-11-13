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
        variant = transactions.execute(s -> CatalogFixtures.persistProduct(entities, ProductStatus.PUBLISHED).variant().getId());
    }
    @AfterEach void cleanup() { jdbc.execute("TRUNCATE TABLE users, categories CASCADE"); }

    @Test void addAndRead_shouldUseRealSessionAndCurrentPrices() throws Exception {
        Credentials user = register();
        mvc.perform(get("/api/me/cart").header("Authorization", user.access())).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, no-store"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM carts", Long.class)).isZero();
        JsonNode first = body(add(user.access(), 2, 201));
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

    @Test void permission_shouldObserveRevokeAndRestoreOnSameToken() throws Exception {
        Credentials user = register();
        Long owner = jdbc.queryForObject("SELECT id FROM users WHERE username=?", Long.class, user.username());
        jdbc.update("DELETE FROM user_roles WHERE user_id=?", owner);
        add(user.access(), 2, 403);
        jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE name='USER'", owner);
        add(user.access(), 2, 201);
    }

    @Test void updateAndDelete_shouldProtectOwnerAndRetainEmptyCart() throws Exception {
        Credentials user = register();
        long item = body(add(user.access(), 2, 201)).get("items").get(0).get("id").asLong();
        Credentials other = register();
        String foreign = mvc.perform(put("/api/me/cart/items/" + item).header("Authorization", other.access())
                .contentType("application/json").content("{\"quantity\":4}")).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        String missing = mvc.perform(put("/api/me/cart/items/99999999").header("Authorization", other.access())
                .contentType("application/json").content("{\"quantity\":4}")).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(foreign).get("detail")).isEqualTo(json.readTree(missing).get("detail"));
        mvc.perform(put("/api/me/cart/items/" + item + "/selection").header("Authorization", other.access())
                .contentType("application/json").content("{\"selected\":false}")).andExpect(status().isNotFound());
        JsonNode updated = body(mvc.perform(put("/api/me/cart/items/" + item).header("Authorization", user.access())
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
        long item = body(add(user.access(), 2, 201)).get("items").get(0).get("id").asLong();
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
        transactions.executeWithoutResult(s -> entities.persist(CatalogFixtures.image(
                entities.find(com.example.demo.catalog.entity.ProductVariant.class, variant).getProduct(), 0, true)));
        org.mockito.Mockito.when(imageUrls.prepare()).thenReturn(key -> { throw new IllegalStateException("Controlled failure"); });
        add(user.access(), 2, 503);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM carts", Long.class)).isZero();
    }

    @Test void contention_shouldReturn409WithoutLeakingSqlOrChangingQuantity() throws Exception {
        Credentials user = register();
        add(user.access(), 2, 201);
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
                .content(json.writeValueAsString(Map.of("variantId", variant, "quantity", quantity))))
                .andExpect(status().is(expected)).andReturn();
    }
    JsonNode body(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsString()); }
    record Credentials(String username, String access, String refresh) { }
}
