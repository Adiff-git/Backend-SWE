package com.example.demo.wishlist.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.catalog.entity.ProductStatus;
import com.example.demo.fixture.wishlist.WishlistFixtures;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = "spring.datasource.hikari.maximum-pool-size=4")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WishlistControllerIT {
    @Autowired private MockMvc mvc;
    @Autowired private com.example.demo.user.service.UserService userService;
    @Autowired private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    @Autowired private JsonMapper json;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private TransactionTemplate transactions;
    private UUID productId;

    @BeforeEach
    void prepare() {
        cleanup();
        productId = transactions.execute(status -> WishlistFixtures.persistProduct(
                entityManager, ProductStatus.PUBLISHED).getId());
    }

    @AfterEach
    void cleanup() {
        jdbc.execute("TRUNCATE TABLE users, categories CASCADE");
    }

    @Test
    void addProduct_shouldPreserveSavedItemAcrossLogoutLogin_whenRealUserRegisters() throws Exception {
        Credentials first = register();
        MvcResult saved = add(first.access(), 201);
        mvc.perform(post("/api/auth/logout").contentType("application/json")
                .content(json.writeValueAsString(Map.of("refreshToken", first.refresh()))))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/me/wishlist/" + productId).header("Authorization", first.access()))
                .andExpect(status().isUnauthorized());
        MvcResult login = mvc.perform(post("/api/auth/login").contentType("application/json")
                .content(json.writeValueAsString(Map.of("identifier", first.username(), "password", "Password 123"))))
                .andExpect(status().isOk()).andReturn();
        MvcResult duplicate = add(login.getResponse().getHeader("Authorization"), 200);
        assertThat(body(duplicate).get("addedAt")).isEqualTo(body(saved).get("addedAt"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wishlist_items", Long.class)).isEqualTo(1);
    }

    @Test
    void addProduct_shouldUseCurrentAuthorities_whenPermissionIsRevokedAndRestored() throws Exception {
        Credentials user = register();
        Long id = userId(user);
        jdbc.update("DELETE FROM user_roles WHERE user_id = ?", id);
        mvc.perform(post("/api/me/wishlist/" + productId).header("Authorization", user.access()))
                .andExpect(status().isForbidden());
        jdbc.update("INSERT INTO user_roles(user_id, role_id) SELECT ?, id FROM roles WHERE name = 'USER'", id);
        add(user.access(), 201);
    }

    @Test
    void addProduct_shouldDenyUnrelatedPermissions_whenCustomRoleHasNoWishlistGrant() throws Exception {
        Credentials user = register();
        Long role = jdbc.queryForObject("INSERT INTO roles(name) VALUES (?) RETURNING id", Long.class,
                "CUSTOM_" + UUID.randomUUID());
        try {
            jdbc.update("INSERT INTO role_permissions(role_id, permission_id) SELECT ?, id FROM permissions "
                    + "WHERE name IN ('MANAGE_USER_PROFILE', 'MANAGE_ADDRESS')", role);
            jdbc.update("DELETE FROM user_roles WHERE user_id = ?", userId(user));
            jdbc.update("INSERT INTO user_roles(user_id, role_id) VALUES (?, ?)", userId(user), role);
            mvc.perform(post("/api/me/wishlist/" + productId).header("Authorization", user.access()))
                    .andExpect(status().isForbidden());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wishlist_items", Long.class)).isZero();
        } finally {
            jdbc.update("DELETE FROM user_roles WHERE role_id = ?", role);
            jdbc.update("DELETE FROM role_permissions WHERE role_id = ?", role);
            jdbc.update("DELETE FROM roles WHERE id = ?", role);
        }
    }

    @Test
    void addProduct_shouldRejectIneligibleAccountAndSession_whenCurrentStateChanges() throws Exception {
        Credentials user = register();
        jdbc.update("UPDATE users SET account_status = 'INACTIVE' WHERE id = ?", userId(user));
        mvc.perform(post("/api/me/wishlist/" + productId).header("Authorization", user.access()))
                .andExpect(status().isUnauthorized());
        jdbc.update("UPDATE users SET account_status = 'ACTIVE' WHERE id = ?", userId(user));
        jdbc.update("UPDATE authentication_sessions SET created_at = CURRENT_TIMESTAMP - INTERVAL '604801 seconds', expires_at = CURRENT_TIMESTAMP - INTERVAL '1 second' "
                + "WHERE user_id = ?", userId(user));
        mvc.perform(post("/api/me/wishlist/" + productId).header("Authorization", user.access()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void addProduct_shouldNotDiscloseNonpublicProducts_whenMissingDraftOrArchived() throws Exception {
        Credentials user = register();
        mvc.perform(post("/api/me/wishlist/" + UUID.randomUUID()).header("Authorization", user.access()))
                .andExpect(status().isNotFound());
        for (String state : List.of("DRAFT", "ARCHIVED")) {
            jdbc.update("UPDATE products SET status = ? WHERE id = ?", state, productId);
            mvc.perform(post("/api/me/wishlist/" + productId).header("Authorization", user.access()))
                    .andExpect(status().isNotFound());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wishlist_items", Long.class)).isZero();
    }

    @Test
    void addProduct_shouldCreateOneMembership_whenRequestsOverlapAtDatabase() throws Exception {
        Credentials user = register();
        Long owner = userId(user);
        CountDownLatch winnerInserted = new CountDownLatch(1);
        CountDownLatch releaseWinner = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        // A real transaction deliberately holds the uncommitted winning row while the API competes.
        try {
            Future<?> winner = workers.submit(() -> transactions.executeWithoutResult(state -> {
                jdbc.update("INSERT INTO wishlist_items(user_id, product_id, added_at) VALUES (?, ?, CURRENT_TIMESTAMP)",
                        owner, productId);
                winnerInserted.countDown();
                await(releaseWinner);
            }));
            assertThat(winnerInserted.await(10, TimeUnit.SECONDS)).isTrue();
            Future<MvcResult> loser = workers.submit(() -> add(user.access(), 200));
            // Observe PostgreSQL waiting on the winner, rather than relying on request scheduling/sleeps.
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            boolean waiting = false;
            while (System.nanoTime() < deadline && !waiting) {
                waiting = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_stat_activity "
                        + "WHERE datname = current_database() AND wait_event_type = 'Lock' "
                        + "AND query ILIKE '%wishlist_items%')", Boolean.class);
                Thread.onSpinWait();
            }
            assertThat(waiting).as("duplicate insert reached the database unique constraint").isTrue();
            releaseWinner.countDown();
            winner.get(10, TimeUnit.SECONDS);
            MvcResult duplicate = loser.get(10, TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wishlist_items", Long.class)).isEqualTo(1);
            assertThat(body(duplicate).get("addedAt").asString())
                    .isEqualTo(jdbc.queryForObject("SELECT added_at FROM wishlist_items", java.sql.Timestamp.class)
                            .toInstant().toString());
        } finally {
            releaseWinner.countDown();
            workers.shutdownNow();
            workers.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void addProduct_shouldReturnOneCreationAndOneDuplicate_whenTwoApiCallsStartTogether() throws Exception {
        Credentials user = register();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<MvcResult> call = () -> {
                await(start);
                return mvc.perform(post("/api/me/wishlist/" + productId)
                        .header("Authorization", user.access())).andReturn();
            };
            Future<MvcResult> first = workers.submit(call);
            Future<MvcResult> second = workers.submit(call);
            start.countDown();
            MvcResult a = first.get(15, TimeUnit.SECONDS);
            MvcResult b = second.get(15, TimeUnit.SECONDS);
            assertThat(List.of(a.getResponse().getStatus(), b.getResponse().getStatus()))
                    .containsExactlyInAnyOrder(201, 200);
            assertThat(body(a).get("addedAt")).isEqualTo(body(b).get("addedAt"));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wishlist_items", Long.class)).isEqualTo(1);
        } finally {
            workers.shutdownNow();
            workers.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void removeProduct_shouldKeepOtherUsersEntryAndAllowReAdd_whenProductWasHidden() throws Exception {
        Credentials user = register();
        Credentials other = register();
        add(user.access(), 201);
        add(other.access(), 201);
        jdbc.update("UPDATE products SET status = 'ARCHIVED' WHERE id = ?", productId);
        mvc.perform(delete("/api/me/wishlist/" + productId).header("Authorization", user.access()))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/me/wishlist/" + productId).header("Authorization", user.access()))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wishlist_items WHERE user_id = ?", Long.class,
                userId(other))).isEqualTo(1);
        jdbc.update("UPDATE products SET status = 'PUBLISHED' WHERE id = ?", productId);
        add(user.access(), 201);
        assertThat(body(mvc.perform(get("/api/me/wishlist").header("Authorization", user.access()))
                .andExpect(status().isOk()).andReturn()).get("items").size()).isEqualTo(1);
        jdbc.update("DELETE FROM user_roles WHERE user_id = ?", userId(user));
        mvc.perform(delete("/api/me/wishlist/" + productId).header("Authorization", user.access()))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/me/wishlist/" + productId)).andExpect(status().isUnauthorized());
    }

    private Credentials register() throws Exception {
        String username = "wish-" + UUID.randomUUID();
        userService.createVerifiedAccount(username+"@example.com",passwordEncoder.encode("Password 123"),java.time.Instant.now());
        MvcResult result = mvc.perform(post("/api/auth/login").contentType("application/json")
                .content(json.writeValueAsString(Map.of("identifier", username, "password", "Password 123"))))
                .andExpect(status().isOk()).andReturn();
        return new Credentials(username, result.getResponse().getHeader("Authorization"),
                body(result).get("refreshToken").asString());
    }

    @Test
    void listWishlist_shouldTraverseOnlyOwnedVisibleEntries_whenPagesIncludeHiddenTimestampTies() throws Exception {
        Credentials user = register();
        Credentials other = register();
        Long owner = userId(user);
        Long otherOwner = userId(other);
        transactions.executeWithoutResult(status -> {
            for (int index = 0; index < 150; index++) {
                UUID id = WishlistFixtures.persistProduct(entityManager,
                        index % 5 == 0 ? ProductStatus.DRAFT : ProductStatus.PUBLISHED).getId();
                entityManager.flush();
                jdbc.update("INSERT INTO wishlist_items(user_id, product_id, added_at) VALUES (?, ?, ?)",
                        owner, id, java.sql.Timestamp.from(java.time.Instant.parse("2026-10-31T03:00:00Z")
                                .minusSeconds(index / 3)));
            }
            jdbc.update("INSERT INTO wishlist_items(user_id, product_id, added_at) VALUES (?, ?, CURRENT_TIMESTAMP)",
                    otherOwner, productId);
        });
        List<String> expected = jdbc.queryForList("SELECT w.product_id::TEXT FROM wishlist_items w "
                + "JOIN products p ON p.id = w.product_id WHERE w.user_id = ? AND p.status = 'PUBLISHED' "
                + "ORDER BY w.added_at DESC, w.product_id ASC", String.class, owner);
        assertThat(expected).hasSize(120);
        for (int size : List.of(1, 20, 100)) {
            java.util.ArrayList<String> actual = new java.util.ArrayList<>();
            int pages = (120 + size - 1) / size;
            for (int page = 0; page < pages; page++) {
                JsonNode response = body(mvc.perform(get("/api/me/wishlist?page=" + page + "&size=" + size)
                        .header("Authorization", user.access())).andExpect(status().isOk()).andReturn());
                assertThat(response.get("totalItems").asLong()).isEqualTo(120);
                assertThat(response.get("totalPages").asInt()).isEqualTo(pages);
                assertThat(response.get("items").size()).isEqualTo(Math.min(size, 120 - page * size));
                for (JsonNode item : response.get("items")) {
                    actual.add(item.get("product").get("id").asString());
                    assertThat(item.get("product").get("primaryImageUrl").isNull()).isTrue();
                    assertThat(item.propertyNames()).containsExactlyInAnyOrder("addedAt", "product");
                    assertThat(item.get("product").propertyNames())
                            .containsExactlyInAnyOrder("id", "name", "category", "primaryImageUrl");
                }
            }
            assertThat(actual).containsExactlyElementsOf(expected);
        }
        JsonNode beyond = body(mvc.perform(get("/api/me/wishlist?page=999&size=20")
                .header("Authorization", user.access())).andExpect(status().isOk()).andReturn());
        assertThat(beyond.get("items").size()).isZero();
        assertThat(beyond.get("totalItems").asLong()).isEqualTo(120);
        JsonNode isolated = body(mvc.perform(get("/api/me/wishlist").header("Authorization", other.access()))
                .andExpect(status().isOk()).andReturn());
        assertThat(isolated.get("totalItems").asLong()).isEqualTo(1);
    }

    private Long userId(Credentials user) {
        return jdbc.queryForObject("SELECT id FROM users WHERE username = ?", Long.class, user.username());
    }

    private MvcResult add(String access, int expected) throws Exception {
        return mvc.perform(post("/api/me/wishlist/" + productId).header("Authorization", access))
                .andExpect(status().is(expected)).andReturn();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for competing transaction");
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failure);
        }
    }

    private record Credentials(String username, String access, String refresh) {
    }
}
