package com.example.demo.e2e;

import com.example.demo.catalog.CatalogFixtures;
import com.example.demo.catalog.entity.ProductStatus;
import com.example.demo.user.service.UserService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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

/** Full internal account/session/catalog/cart flow; external email delivery is provisioned. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CartFlowIT {
    @Autowired MockMvc mvc;
    @Autowired EntityManager entities;
    @Autowired TransactionTemplate transactions;
    @Autowired UserService users;
    @Autowired PasswordEncoder passwords;
    @Autowired JsonMapper json;
    @Autowired JdbcTemplate jdbc;
    @org.springframework.boot.test.web.server.LocalServerPort int port;
    @AfterEach void cleanup() { jdbc.execute("TRUNCATE TABLE users, categories CASCADE"); }

    @Test void cart_shouldSurviveLogoutAndLaterLoginThroughFullInternalFlow() throws Exception {
        cleanup();
        UUID[] variants = transactions.execute(s -> {
            var product = CatalogFixtures.persistProduct(entities, ProductStatus.PUBLISHED);
            var second = CatalogFixtures.variant(product.product(), Map.of("color", "blue"), BigDecimal.valueOf(50));
            entities.persist(second);
            return new UUID[]{product.variant().getId(), second.getId()};
        });
        String username = "flow-" + UUID.randomUUID();
        users.createVerifiedAccount(username + "@example.com", passwords.encode("Password 123"), Instant.now());
        MvcResult login = login(username);
        String token = login.getResponse().getHeader("Authorization");
        mvc.perform(get("/api/me/cart").header("Authorization", token)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM carts", Long.class)).isZero();
        MvcResult first = mvc.perform(post("/api/me/cart/items").header("Authorization", token).contentType("application/json")
                .content(json.writeValueAsString(Map.of("variantId", variants[0], "quantity", 2)))).andExpect(status().isCreated()).andReturn();
        long item = body(first).get("items").get(0).get("id").asLong();
        mvc.perform(post("/api/me/cart/items").header("Authorization", token).contentType("application/json")
                .content(json.writeValueAsString(Map.of("variantId", variants[0], "quantity", 3)))).andExpect(status().isOk());
        mvc.perform(put("/api/me/cart/items/" + item).header("Authorization", token).contentType("application/json")
                .content(json.writeValueAsString(Map.of("variantId", variants[1], "quantity", 4)))).andExpect(status().isOk());
        JsonNode deselected = body(mvc.perform(put("/api/me/cart/items/" + item + "/selection").header("Authorization", token)
                .contentType("application/json").content("{\"selected\":false}")).andExpect(status().isOk()).andReturn());
        assertThat(deselected.get("finalTotal").get("amount").asLong()).isZero();
        mvc.perform(post("/api/auth/logout").contentType("application/json")
                .content(json.writeValueAsString(Map.of("refreshToken", body(login).get("refreshToken").asString())))).andExpect(status().isNoContent());
        mvc.perform(get("/api/me/cart").header("Authorization", token)).andExpect(status().isUnauthorized());
        String later = login(username).getResponse().getHeader("Authorization");
        JsonNode recovered = body(mvc.perform(get("/api/me/cart").header("Authorization", later)).andExpect(status().isOk()).andReturn());
        assertThat(recovered.get("items").get(0).get("id").asLong()).isEqualTo(item);
        assertThat(recovered.get("items").get(0).get("quantity").asInt()).isEqualTo(4);
        assertThat(recovered.get("items").get(0).get("selected").asBoolean()).isFalse();
        JsonNode selected = body(mvc.perform(put("/api/me/cart/selection").header("Authorization", later).contentType("application/json")
                .content("{\"selected\":true}")).andExpect(status().isOk()).andReturn());
        assertThat(selected.get("finalTotal").get("amount").asLong()).isEqualTo(200);
        mvc.perform(delete("/api/me/cart/items/" + item).header("Authorization", later)).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cart_items", Long.class)).isZero();
    }
    MvcResult login(String username) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType("application/json")
                .content(json.writeValueAsString(Map.of("identifier", username, "password", "Password 123"))))
                .andExpect(status().isOk()).andReturn();
    }
    JsonNode body(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsString()); }

    @Test void networkWalkthrough_shouldExerciseRealHttpOnIsolatedRuntime() throws Exception {
        cleanup();
        UUID variant = transactions.execute(s -> CatalogFixtures.persistProduct(entities, ProductStatus.PUBLISHED).variant().getId());
        String username = "http-" + UUID.randomUUID();
        users.createVerifiedAccount(username + "@example.com", passwords.encode("Password 123"), Instant.now());
        try (java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient()) {
            var login = http(client, "POST", "/api/auth/login", null,
                    json.writeValueAsString(Map.of("identifier", username, "password", "Password 123")), 200);
            String token = login.headers().firstValue("Authorization").orElseThrow();
            http(client, "GET", "/api/me/cart", null, null, 401);
            http(client, "GET", "/api/me/cart", token, null, 200);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM carts", Long.class)).isZero();
            String request = json.writeValueAsString(Map.of("variantId", variant, "quantity", 2));
            var created = http(client, "POST", "/api/me/cart/items", token, request, 201);
            long item = json.readTree(created.body()).get("items").get(0).get("id").asLong();
            assertThat(created.headers().firstValue("Location")).hasValue("/api/me/cart/items/" + item);
            var repeated = http(client, "POST", "/api/me/cart/items", token, request, 200);
            assertThat(json.readTree(repeated.body()).get("selectedTotalQuantity").asLong()).isEqualTo(4);
            assertThat(json.readTree(repeated.body()).get("finalTotal").get("amount").asLong()).isEqualTo(4000);
            http(client, "PUT", "/api/me/cart/items/" + item, token, "{\"quantity\":1001}", 400);
            http(client, "PUT", "/api/me/cart/items/" + item, token, "{\"quantity\":1000}", 200);
            http(client, "POST", "/api/me/cart/items", token, request, 422);
            var deselected = http(client, "PUT", "/api/me/cart/selection", token, "{\"selected\":false}", 200);
            assertThat(json.readTree(deselected.body()).get("finalTotal").get("amount").asLong()).isZero();
            http(client, "DELETE", "/api/me/cart/items/" + item, token, null, 204);
            http(client, "DELETE", "/api/me/cart/items/" + item, token, null, 404);
            Long owner = jdbc.queryForObject("SELECT id FROM users WHERE username=?", Long.class, username);
            jdbc.update("DELETE FROM user_roles WHERE user_id=?", owner);
            http(client, "GET", "/api/me/cart", token, null, 403);
        }
    }

    java.net.http.HttpResponse<String> http(java.net.http.HttpClient client, String method, String path,
            String token, String body, int expected) throws Exception {
        var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:" + port + path))
                .timeout(java.time.Duration.ofSeconds(10));
        if (token != null) request.header("Authorization", token);
        if (body != null) request.header("Content-Type", "application/json");
        var response = client.send(request.method(method, body == null ? java.net.http.HttpRequest.BodyPublishers.noBody()
                : java.net.http.HttpRequest.BodyPublishers.ofString(body)).build(), java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(method + " " + path).isEqualTo(expected);
        if (path.startsWith("/api/me/cart")) assertThat(response.headers().firstValue("Cache-Control")).hasValue("private, no-store");
        return response;
    }
}
