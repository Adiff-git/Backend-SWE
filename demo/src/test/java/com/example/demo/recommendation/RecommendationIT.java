package com.example.demo.recommendation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.demo.catalog.CatalogFixtures;
import com.example.demo.catalog.entity.ProductStatus;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecommendationIT {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired EntityManager em;
  @Autowired TransactionTemplate tx;
  @Autowired RecommendationStore store;
  @Autowired com.example.demo.user.service.UserService users;
  @Autowired org.springframework.security.crypto.password.PasswordEncoder passwords;
  @Autowired JsonMapper json;
  @MockitoBean RecommendationAiClient ai;
  @MockitoBean com.example.demo.catalog.storage.ProductImageStorage storage;
  UUID first, second, draft;

  record Actor(Long id, String token) {}

  @BeforeEach
  void setup() {
    cleanup();
    first =
        tx.execute(
            s -> CatalogFixtures.persistProduct(em, ProductStatus.PUBLISHED).product().getId());
    second =
        tx.execute(
            s -> CatalogFixtures.persistProduct(em, ProductStatus.PUBLISHED).product().getId());
    draft =
        tx.execute(s -> CatalogFixtures.persistProduct(em, ProductStatus.DRAFT).product().getId());
  }

  @AfterEach
  void cleanup() {
    jdbc.execute("TRUNCATE users,categories CASCADE");
  }

  Actor actor() throws Exception {
    String name = "rec-" + UUID.randomUUID();
    users.createVerifiedAccount(
        name + "@example.com", passwords.encode("Password 123"), Instant.now());
    var result =
        mvc.perform(
                post("/api/auth/login")
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(
                            Map.of("identifier", name, "password", "Password 123"))))
            .andExpect(status().isOk())
            .andReturn();
    return new Actor(
        jdbc.queryForObject("SELECT id FROM users WHERE username=?", Long.class, name),
        result.getResponse().getHeader("Authorization"));
  }

  @Test
  void accessLimitsAndNoTracking() throws Exception {
    mvc.perform(get("/api/recommendations"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[0].displayPrice.amount").value(1000))
        .andExpect(jsonPath("$.items[0].score").doesNotExist());
    mvc.perform(get("/api/me/recommendations")).andExpect(status().isUnauthorized());
    for (String value : List.of("0", "21", "-1", "x", ""))
      mvc.perform(get("/api/recommendations").param("limit", value))
          .andExpect(status().isBadRequest());
    mvc.perform(get("/api/recommendations").param("customerId", "1"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/recommendations").param("limit", "1", "2"))
        .andExpect(status().isBadRequest());
    assertThat(jdbc.queryForObject("SELECT count(*) FROM user_behavior_events", Long.class))
        .isZero();
  }

  @Test
  void ownerIsolationAndAdminDenied() throws Exception {
    Actor owner = actor(), other = actor();
    mvc.perform(
            post("/api/me/behavior/events")
                .header("Authorization", owner.token())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "eventType",
                            "PRODUCT_VIEW",
                            "productId",
                            first,
                            "clientEventId",
                            UUID.randomUUID()))))
        .andExpect(status().isCreated());
    when(ai.rank(any()))
        .thenAnswer(
            call -> {
              RecommendationContract.Request request = call.getArgument(0);
              assertThat(request.recentEvents()).hasSize(1);
              assertThat(request.recentEvents().getFirst().productId()).isEqualTo(first);
              return List.of(second);
            });
    mvc.perform(
            get("/api/me/recommendations")
                .header("Authorization", owner.token())
                .param("limit", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].id").value(second.toString()));
    mvc.perform(get("/api/me/recommendations").header("Authorization", other.token()))
        .andExpect(status().isOk());
    verify(ai, times(1)).rank(any());
    mvc.perform(
            get("/api/me/recommendations")
                .header("Authorization", other.token())
                .param("customerId", owner.id().toString()))
        .andExpect(status().isBadRequest());
    jdbc.update("INSERT INTO roles(name) VALUES('ADMIN') ON CONFLICT DO NOTHING");
    jdbc.update(
        "INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE name='ADMIN'",
        other.id());
    mvc.perform(get("/api/me/recommendations").header("Authorization", other.token()))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/recommendations").header("Authorization", other.token()))
        .andExpect(status().isOk());
  }

  @Test
  void similarFailureAndPublicationRace() throws Exception {
    when(ai.rank(any())).thenThrow(new java.net.http.HttpTimeoutException("test"));
    mvc.perform(get("/api/products/" + first + "/similar"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].id").value(second.toString()));
    mvc.perform(get("/api/products/" + draft + "/similar")).andExpect(status().isNotFound());
    mvc.perform(get("/api/products/" + UUID.randomUUID() + "/similar"))
        .andExpect(status().isNotFound());
    doAnswer(
            call -> {
              jdbc.update("UPDATE products SET status='DRAFT' WHERE id=?", second);
              return List.of(second);
            })
        .when(ai)
        .rank(any());
    mvc.perform(get("/api/products/" + first + "/similar"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(0));
    jdbc.update("UPDATE products SET status='DRAFT'");
    mvc.perform(get("/api/recommendations"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(0));
  }

  @Test
  void snapshotPriceVariantMembershipAndAggregateCap() throws Exception {
    var original =
        store.catalog().stream()
            .filter(r -> r.candidate().productId().equals(first))
            .findFirst()
            .orElseThrow();
    UUID variant = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO product_variants(id,product_id,sku,option_combination,selling_price)"
            + " VALUES(?,?,?,'{\"choice\":\"cheaper\"}',500)",
        variant,
        first,
        variant.toString());
    var row =
        store.catalog().stream()
            .filter(r -> r.candidate().productId().equals(first))
            .findFirst()
            .orElseThrow();
    assertThat(row.variantId()).isEqualTo(variant);
    assertThat(row.candidate().price()).isEqualByComparingTo("500");
    Actor owner = actor();
    for (int i = 0; i < 10; i++)
      mvc.perform(
              post("/api/me/behavior/events")
                  .header("Authorization", owner.token())
                  .contentType("application/json")
                  .content(
                      json.writeValueAsString(
                          Map.of(
                              "eventType",
                              "PRODUCT_VIEW",
                              "productId",
                              first,
                              "clientEventId",
                              UUID.randomUUID()))))
          .andExpect(status().isCreated());
    assertThat(store.popularity(Instant.now()).get(first)).isEqualTo(6);
    mvc.perform(get("/api/recommendations").param("limit", "1"))
        .andExpect(jsonPath("$.items[0].id").value(first.toString()))
        .andExpect(jsonPath("$.items[0].representativeVariantId").value(variant.toString()));
    jdbc.update("UPDATE product_variants SET selling_price=-1 WHERE product_id=?", second);
    assertThat(
            store.catalog().stream()
                .filter(r -> r.candidate().productId().equals(second))
                .findFirst()
                .orElseThrow()
                .candidate()
                .price())
        .isNull();
  }

  @Test
  void rejectsUntrustedMockRankingsAndFillsShortResponse() throws Exception {
    for (var output :
        List.of(
            List.of(first), List.of(draft), List.of(UUID.randomUUID()), List.of(second, second))) {
      doReturn(output).when(ai).rank(any());
      mvc.perform(get("/api/products/" + first + "/similar"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.items.length()").value(1))
          .andExpect(jsonPath("$.items[0].id").value(second.toString()));
    }
    doReturn(List.of()).when(ai).rank(any());
    mvc.perform(get("/api/products/" + first + "/similar"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].id").value(second.toString()));
  }

  @Test
  void currentWishlistOverridesEventOrderingAndRetainsOwnerScope() throws Exception {
    Actor owner = actor(), other = actor();
    for (String method : List.of("add", "remove", "add")) {
      var request =
          method.equals("add")
              ? post("/api/me/wishlist/" + first)
              : delete("/api/me/wishlist/" + first);
      mvc.perform(request.header("Authorization", owner.token()))
          .andExpect(status().is2xxSuccessful());
    }
    doAnswer(
            call -> {
              RecommendationContract.Request request = call.getArgument(0);
              assertThat(request.wishlist()).containsExactly(first);
              assertThat(
                      request.recentEvents().stream()
                          .map(RecommendationContract.Event::eventType)
                          .toList())
                  .containsExactly("WISHLIST_ADD", "WISHLIST_REMOVE", "WISHLIST_ADD");
              return List.of(first);
            })
        .when(ai)
        .rank(any());
    mvc.perform(get("/api/me/recommendations").header("Authorization", owner.token()))
        .andExpect(status().isOk());
    assertThat(store.wishlist(other.id(), Set.of(first, second))).isEmpty();
  }

  @Test
  void imageUsesTrustedSigningAndSourceUnpublicationIsRechecked() throws Exception {
    String key =
        tx.execute(
            transaction -> {
              var product = em.find(com.example.demo.catalog.entity.Product.class, second);
              var image = CatalogFixtures.image(product, 0, true);
              em.persist(image);
              return image.getObjectKey();
            });
    var signer =
        mock(com.example.demo.catalog.storage.ProductImageStorage.PreparedReadSigner.class);
    when(storage.prepareReadSigner()).thenReturn(signer);
    when(signer.signReadUrl(key)).thenReturn("https://media.example.test/signed-product");
    doReturn(List.of(second)).when(ai).rank(any());
    mvc.perform(get("/api/products/" + first + "/similar"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.items[0].primaryImageUrl")
                .value("https://media.example.test/signed-product"));
    verify(signer).close();
    doAnswer(
            call -> {
              jdbc.update("UPDATE products SET status='DRAFT' WHERE id=?", first);
              return List.of(second);
            })
        .when(ai)
        .rank(any());
    mvc.perform(get("/api/products/" + first + "/similar")).andExpect(status().isNotFound());
  }

  @Test
  void retainedOwnerEventsAreBoundedAndExcludeExpired() throws Exception {
    Actor owner = actor();
    jdbc.update(
        "INSERT INTO"
            + " user_behavior_events(id,customer_id,event_type,product_id,client_event_id,occurred_at)"
            + " SELECT"
            + " md5('bounded-'||i)::uuid,?,'PRODUCT_VIEW',?,md5('client-'||i)::uuid,now()-interval"
            + " '1 day' FROM generate_series(1,510) i",
        owner.id(),
        first);
    jdbc.update(
        "INSERT INTO"
            + " user_behavior_events(id,customer_id,event_type,product_id,client_event_id,occurred_at)"
            + " VALUES(?,?,'PRODUCT_CLICK',?,?,now()-interval '91 days')",
        UUID.randomUUID(),
        owner.id(),
        first,
        UUID.randomUUID());
    assertThat(store.events(owner.id(), Instant.now()))
        .hasSize(500)
        .allMatch(e -> e.eventType().equals("PRODUCT_VIEW"));
    assertThat(store.popularity(Instant.now()).get(first)).isEqualTo(6);
  }

  @Test
  void capacityIsExplicitInsteadOfArbitraryFirstPage() throws Exception {
    UUID category =
        jdbc.queryForObject("SELECT category_id FROM products WHERE id=?", UUID.class, first);
    tx.executeWithoutResult(
        transaction -> {
          jdbc.update(
              "INSERT INTO products(id,category_id,name,status) SELECT"
                  + " md5('capacity-'||i)::uuid,?,'Capacity fixture','PUBLISHED' FROM"
                  + " generate_series(1,3001) i",
              category);
          jdbc.execute(
              "INSERT INTO product_variants(id,product_id,sku,option_combination,selling_price)"
                  + " SELECT"
                  + " md5('capacity-v-'||i)::uuid,md5('capacity-'||i)::uuid,'capacity-'||i,'{}',1"
                  + " FROM generate_series(1,3001) i");
        });
    mvc.perform(get("/api/recommendations"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.detail").value("Recommendation catalog capacity exceeded"));
  }

  @Test
  void upstreamAuthenticationFailureHasSafeFallback() throws Exception {
    doThrow(new RecommendationAiClient.OperationalFailure("AI_HTTP_401")).when(ai).rank(any());
    mvc.perform(get("/api/products/" + first + "/similar"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].id").value(second.toString()));
  }
}
