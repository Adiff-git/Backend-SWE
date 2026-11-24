package com.example.demo.inventory.controller.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.user.integration.PostgresTestSupport;
import com.example.demo.user.service.UserService;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.test.context.TestPropertySource(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:17-alpine:///admin_inventory_http",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
class AdminInventorySecurityIT extends PostgresTestSupport {
    private static final String BASE = "/api/admin/inventory/variants";
    @LocalServerPort int port;
    @Autowired org.springframework.security.oauth2.jwt.JwtEncoder encoder;
    @Autowired org.springframework.security.oauth2.jwt.JwtDecoder decoder;
    private final HttpClient http = HttpClient.newHttpClient();
    @Autowired JdbcTemplate jdbc;
    @Autowired UserService users;
    @Autowired PasswordEncoder passwords;
    @Autowired JsonMapper json;
    @Autowired jakarta.persistence.EntityManagerFactory entities;
    @Autowired org.springframework.transaction.support.TransactionTemplate transactions;

    @Test void bearerAuthorizationAndOriginalReplaySurviveStockAndLifecycleChanges() throws Exception {
        UUID category = UUID.randomUUID(), product = UUID.randomUUID(), variant = UUID.randomUUID();
        jdbc.update("INSERT INTO categories(id,name) VALUES (?,?)", category, "Inventory " + category);
        jdbc.update("INSERT INTO products(id,category_id,name,status) VALUES (?,?,?,'DRAFT')", product, category, "Test inventory");
        jdbc.update("INSERT INTO product_variants(id,product_id,sku,option_combination,selling_price,enabled) VALUES (?,?,?,'{}',100,false)", variant, product, "INV_" + variant);
        String detail = BASE + "/" + variant;
        String replenishment = detail + "/replenishments";
        String correction = detail + "/corrections";
        var original = Map.of("operationId", UUID.randomUUID(), "delta", "7", "reason", "  ");
        Actor admin = actor(true), customer = actor(false);
        var valid = decoder.decode(admin.token().substring("Bearer ".length()));
        Instant now = Instant.now();
        var expiredClaims = org.springframework.security.oauth2.jwt.JwtClaimsSet.builder()
                .claims(claims -> claims.putAll(valid.getClaims())).issuedAt(now.minusSeconds(600))
                .expiresAt(now.minusSeconds(300)).build();
        String expired = "Bearer " + encoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(
                org.springframework.security.oauth2.jwt.JwsHeader.with(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build(),
                expiredClaims)).getTokenValue();
        for (String path : new String[] {BASE, detail, detail + "/history", replenishment, correction}) {
            var method = path.endsWith("s") && !path.equals(BASE) ? HttpMethod.POST : HttpMethod.GET;
            call(method, path, null, null, 401);
            call(method, path, customer.token(), null, 403);
            call(method, path, "Bearer invalid-token", null, 401);
            call(method, path, expired, null, 401);
        }
        var initial = json.readTree(call(HttpMethod.GET, detail, admin.token(), null, 200));
        assertThat(initial.path("stockStatus").asText()).isEqualTo("OUT_OF_STOCK");
        assertThat(initial.path("catalogSaleEligible").asBoolean()).isFalse();
        var authStatistics = entities.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        long authBefore = authStatistics.getPrepareStatementCount();
        var found = json.readTree(call(HttpMethod.GET, BASE + "?q=INV_" + variant + "&status=DRAFT&size=1", admin.token(), null, 200));
        // This path uses JDBC for domain reads, so these Hibernate statements are from authentication.
        assertThat(authStatistics.getPrepareStatementCount() - authBefore).isPositive();
        assertThat(found.path("totalItems").asInt()).isOne();
        assertThat(found.path("items").get(0).path("variantEnabled").asBoolean()).isFalse();
        UUID missing = UUID.randomUUID(), otherProduct = UUID.randomUUID();
        jdbc.update("INSERT INTO products(id,category_id,name,status) VALUES (?,?,?,'DRAFT')", otherProduct, category, "Missing");
        jdbc.update("INSERT INTO product_variants(id,product_id,sku,option_combination,selling_price,enabled) VALUES (?,?,?,?::jsonb,100,false)",
                missing, otherProduct, "INV_" + missing, "{\"model\":\"other\"}");
        jdbc.update("DELETE FROM inventory_balances WHERE product_variant_id=?", missing);
        var unavailable = json.readTree(call(HttpMethod.GET, BASE + "/" + missing, admin.token(), null, 200));
        assertThat(unavailable.path("stockStatus").asText()).isEqualTo("UNAVAILABLE");
        assertThat(unavailable.path("onHand").isNull()).isTrue();
        var absent = json.readTree(call(HttpMethod.POST, BASE + "/" + missing + "/replenishments", admin.token(),
                Map.of("operationId", UUID.randomUUID(), "delta", "1"), 409));
        assertThat(absent.path("code").asText()).isEqualTo("INVENTORY_BALANCE_UNAVAILABLE");
        String result = call(HttpMethod.POST, replenishment, admin.token(), original, 200);
        call(HttpMethod.POST, correction, admin.token(), Map.of("operationId", UUID.randomUUID(), "delta", "-2", "reason", " adjustment "), 200);
        var floor = json.readTree(call(HttpMethod.POST, correction, admin.token(),
                Map.of("operationId", UUID.randomUUID(), "delta", Long.toString(Long.MIN_VALUE), "reason", "Test"), 409));
        assertThat(floor.path("code").asText()).isEqualTo("INVENTORY_RESERVATION_FLOOR");
        assertThat(floor.path("onHand").asText()).isEqualTo("5");
        assertThat(floor.path("reserved").asText()).isEqualTo("0");
        assertThat(floor.path("available").asText()).isEqualTo("5");
        jdbc.update("UPDATE products SET status='ARCHIVED' WHERE id=?", product);
        assertThat(call(HttpMethod.POST, replenishment, admin.token(),
                Map.of("operationId", original.get("operationId"), "delta", "7"), 200)).isEqualTo(result);
        call(HttpMethod.POST, replenishment, admin.token(), Map.of("operationId", original.get("operationId"), "delta", "8"), 409);
        call(HttpMethod.POST, replenishment, admin.token(), Map.of("operationId", UUID.randomUUID(), "delta", "1"), 409);
        var history = json.readTree(call(HttpMethod.GET, detail + "/history?page=100", admin.token(), null, 200));
        assertThat(history.path("totalItems").asInt()).isEqualTo(2);
        assertThat(history.path("items").isEmpty()).isTrue();
        assertThat(jdbc.queryForObject("SELECT stock FROM inventory_balances WHERE product_variant_id=?", Long.class, variant)).isEqualTo(5);
        call(HttpMethod.DELETE, detail, admin.token(), null, 403);
        jdbc.update("DELETE FROM product_variants WHERE id=?", variant);
        call(HttpMethod.GET, detail, admin.token(), null, 404);
        var detached = json.readTree(call(HttpMethod.GET, detail + "/history", admin.token(), null, 200));
        assertThat(detached.path("totalItems").asInt()).isEqualTo(2);
        assertThat(detached.path("items").get(0).path("operationType").asText()).isEqualTo("CORRECTION");
        assertConcurrentHttpReplay(admin);
        jdbc.update("UPDATE users SET account_status='INACTIVE' WHERE id=?", admin.id());
        call(HttpMethod.POST, replenishment, admin.token(), original, 401);
        jdbc.update("UPDATE users SET account_status='ACTIVE' WHERE id=?", admin.id());
        jdbc.update("UPDATE authentication_sessions SET revoked_at=now(),revocation_reason='LOGOUT' WHERE user_id=?", admin.id());
        call(HttpMethod.POST, replenishment, admin.token(), original, 401);
    }

    private void assertConcurrentHttpReplay(Actor admin) throws Exception {
        UUID variant = com.example.demo.fixture.inventory.AdminInventoryFixtures.variant(
                jdbc, transactions, com.example.demo.catalog.entity.ProductStatus.DRAFT, true);
        UUID operation = UUID.randomUUID();
        var input = Map.of("operationId", operation, "delta", "3");
        String path = BASE + "/" + variant + "/replenishments";
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var workers = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<HttpResponse<String>> retry = () -> {
                start.await();
                return send(HttpMethod.POST, path, admin.token(), input);
            };
            var first = workers.submit(retry);
            var second = workers.submit(retry);
            start.countDown();
            var a = first.get(15, java.util.concurrent.TimeUnit.SECONDS);
            var b = second.get(15, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(a.statusCode()).isIn(200, 409);
            assertThat(b.statusCode()).isIn(200, 409);
            assertThat(java.util.List.of(a.statusCode(), b.statusCode())).contains(200);
            assertThat(call(HttpMethod.POST, path, admin.token(), input, 200))
                    .isEqualTo(a.statusCode() == 200 ? a.body() : b.body());
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM admin_inventory_operations WHERE operation_id=?", Long.class, operation)).isOne();
        assertThat(jdbc.queryForObject("SELECT stock FROM inventory_balances WHERE product_variant_id=?", Long.class, variant)).isEqualTo(3);
    }

    private Actor actor(boolean elevated) throws Exception {
        String name = "inventory-" + UUID.randomUUID();
        long id = users.createVerifiedAccount(name + "@example.test", passwords.encode("Password 123"), Instant.now());
        if (elevated) {
            jdbc.update("INSERT INTO roles(name) VALUES ('ADMIN') ON CONFLICT(name) DO NOTHING");
            jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE name='ADMIN'", id);
        }
        var response = send(HttpMethod.POST, "/api/auth/login", null,
                Map.of("identifier", name, "password", "Password 123"));
        assertThat(response.statusCode()).isEqualTo(200);
        return new Actor(id, response.headers().firstValue("Authorization").orElseThrow());
    }

    private String call(HttpMethod method, String path, String token, Object body, int expected) throws Exception {
        var response = send(method, path, token, body);
        assertThat(response.statusCode()).as("%s %s: %s", method, path, response.body()).isEqualTo(expected);
        return response.body();
    }

    private HttpResponse<String> send(HttpMethod method, String path, String token, Object body) throws Exception {
        var req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(java.time.Duration.ofSeconds(15));
        if (token != null) req.header("Authorization", token);
        if (body != null) req.header("Content-Type", "application/json");
        return http.send(req.method(method.name(), body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private record Actor(long id, String token) {}
}
