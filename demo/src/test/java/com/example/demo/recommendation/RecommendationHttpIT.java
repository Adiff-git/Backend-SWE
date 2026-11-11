package com.example.demo.recommendation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Real socket Backend -> real uvicorn -> sklearn; isolated Testcontainers PostgreSQL. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RecommendationHttpIT {
  static final String KEY = "local-test-" + UUID.randomUUID();
  static Process aiProcess;
  static int aiPort;
  static final HttpClient HTTP =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  @Autowired org.springframework.transaction.support.TransactionTemplate tx;
  @Autowired JsonMapper json;
  @Autowired RecommendationStore store;
  @Autowired RecommendationHydrator hydrator;
  @Autowired com.example.demo.user.service.UserService users;
  @Autowired org.springframework.security.crypto.password.PasswordEncoder passwords;
  @MockitoSpyBean RecommendationAiClient ai;

  @DynamicPropertySource
  static void aiService(DynamicPropertyRegistry registry) throws Exception {
    try (var socket = new java.net.ServerSocket(0)) {
      aiPort = socket.getLocalPort();
    }
    Path project = Path.of("..").toAbsolutePath().normalize().resolve("../AI-SWE").normalize();
    Path log = Path.of("target/recommendation-ai-http.log").toAbsolutePath();
    var builder =
        new ProcessBuilder(
                "uv",
                "run",
                "--locked",
                "uvicorn",
                "ai_service.main:app",
                "--host",
                "127.0.0.1",
                "--port",
                Integer.toString(aiPort))
            .directory(project.toFile())
            .redirectErrorStream(true)
            .redirectOutput(log.toFile());
    builder.environment().put("RECOMMENDATION_SERVICE_KEY", KEY);
    aiProcess = builder.start();
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  if (aiProcess != null) aiProcess.destroy();
                }));
    long until = System.nanoTime() + Duration.ofSeconds(45).toNanos();
    boolean ready = false;
    while (System.nanoTime() < until) {
      if (!aiProcess.isAlive()) throw new IllegalStateException("AI startup failed; see " + log);
      try {
        if (HTTP.send(
                    HttpRequest.newBuilder(URI.create(aiUrl() + "/api/v1/health")).GET().build(),
                    HttpResponse.BodyHandlers.discarding())
                .statusCode()
            == 200) {
          ready = true;
          break;
        }
      } catch (Exception ignored) {
      }
      Thread.sleep(100);
    }
    if (!ready) throw new IllegalStateException("AI startup timeout");
    registry.add("recommendation.ai.url", RecommendationHttpIT::aiUrl);
    registry.add("recommendation.ai.key", () -> KEY);
  }

  static String aiUrl() {
    return "http://127.0.0.1:" + aiPort;
  }

  @AfterAll
  static void stop() {
    if (aiProcess != null) aiProcess.destroy();
  }

  @AfterEach
  void cleanup() {
    jdbc.execute("TRUNCATE users,categories CASCADE");
  }

  record Actor(Long id, String token) {}

  HttpResponse<String> call(String method, String path, String token, Object body)
      throws Exception {
    var builder =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .timeout(Duration.ofSeconds(10));
    if (token != null) builder.header("Authorization", token);
    builder.header("Content-Type", "application/json");
    builder.method(
        method,
        body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
    return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  Actor actor() throws Exception {
    String name = "rec-http-" + UUID.randomUUID();
    users.createVerifiedAccount(
        name + "@example.com", passwords.encode("Password 123"), Instant.now());
    var login =
        call(
            "POST",
            "/api/auth/login",
            null,
            Map.of("identifier", name, "password", "Password 123"));
    assertThat(login.statusCode()).isEqualTo(200);
    return new Actor(
        jdbc.queryForObject("SELECT id FROM users WHERE username=?", Long.class, name),
        login.headers().firstValue("Authorization").orElseThrow());
  }

  JsonNode ok(HttpResponse<String> response) {
    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    return json.readTree(response.body());
  }

  long timed(String path, String token, List<Double> times) throws Exception {
    long start = System.nanoTime();
    var response = call("GET", path, token, null);
    ok(response);
    long elapsed = System.nanoTime() - start;
    times.add(elapsed / 1e6);
    return elapsed;
  }

  @Test
  void actualTwoServices1500ProductsAndOutage() throws Exception {
    jdbc.execute("TRUNCATE users,categories CASCADE");
    jdbc.execute(
        "INSERT INTO categories(id,name) SELECT md5('category-'||i)::uuid,CASE i WHEN 0 THEN"
            + " 'Laptop' WHEN 1 THEN 'Smartphone' ELSE 'Tablet' END FROM generate_series(0,2) i");
    tx.executeWithoutResult(
        transaction -> {
          jdbc.execute(
              "INSERT INTO products(id,category_id,name,description,status) SELECT"
                  + " md5('rec-product-'||i)::uuid,md5('category-'||(i%3))::uuid,CASE i%3 WHEN 0"
                  + " THEN 'Gaming RTX Laptop ' WHEN 1 THEN 'Samsung Smartphone ' ELSE 'Apple"
                  + " Tablet ' END||i,'Sanitized synthetic electronics fixture','PUBLISHED' FROM"
                  + " generate_series(1,1500) i");
          jdbc.execute(
              "INSERT INTO product_variants(id,product_id,sku,option_combination,selling_price)"
                  + " SELECT"
                  + " md5('rec-variant-'||i)::uuid,md5('rec-product-'||i)::uuid,'REC-'||i,'{}',5000000+i*10000"
                  + " FROM generate_series(1,1500) i");
        });
    UUID source =
        jdbc.queryForObject("SELECT id FROM products WHERE name='Gaming RTX Laptop 3'", UUID.class);
    Actor owner = actor(), other = actor();
    assertThat(
            call(
                    "POST",
                    "/api/me/behavior/events",
                    owner.token(),
                    Map.of(
                        "eventType",
                        "PRODUCT_VIEW",
                        "productId",
                        source,
                        "clientEventId",
                        UUID.randomUUID()))
                .statusCode())
        .isEqualTo(201);
    assertThat(
            call(
                    "POST",
                    "/api/me/behavior/events",
                    owner.token(),
                    Map.of(
                        "eventType",
                        "SEARCH",
                        "searchQuery",
                        "gaming rtx",
                        "clientEventId",
                        UUID.randomUUID()))
                .statusCode())
        .isEqualTo(201);
    long catalogStart = System.nanoTime();
    var snapshot = store.catalog();
    double catalogMs = (System.nanoTime() - catalogStart) / 1e6;
    assertThat(snapshot).hasSize(1500);
    var times = new ArrayList<Double>();
    timed("/api/me/recommendations?limit=10", owner.token(), times);
    var personalized = ok(call("GET", "/api/me/recommendations?limit=10", owner.token(), null));
    assertThat(personalized.get("items").size()).isEqualTo(10);
    for (var item : personalized.get("items")) {
      assertThat(item.get("category").get("name").asString()).isEqualTo("Laptop");
      assertThat(item.has("score")).isFalse();
      assertThat(item.has("reason")).isFalse();
    }
    var captor = org.mockito.ArgumentCaptor.forClass(RecommendationContract.Request.class);
    verify(ai, atLeastOnce()).rank(captor.capture());
    var actual = captor.getValue();
    assertThat(actual.candidates()).hasSize(1500);
    assertThat(actual.recentEvents()).hasSize(2);
    String wire = json.writeValueAsString(actual);
    for (String forbidden :
        List.of("customerId", "email", "password", "Authorization", "refreshToken"))
      assertThat(wire).doesNotContain(forbidden);
    assertThat(store.events(other.id(), Instant.now())).isEmpty();
    var general = ok(call("GET", "/api/recommendations?limit=10", null, null));
    assertThat(general.get("items").size()).isEqualTo(10);
    assertThat(
            ok(call("GET", "/api/me/recommendations?limit=10", other.token(), null)).get("items"))
        .isEqualTo(general.get("items"));
    var similar = ok(call("GET", "/api/products/" + source + "/similar?limit=10", null, null));
    assertThat(similar.get("items").size()).isEqualTo(10);
    for (var item : similar.get("items")) {
      assertThat(item.get("id").asString()).isNotEqualTo(source.toString());
      assertThat(item.get("category").get("name").asString()).isEqualTo("Laptop");
    }
    var unauthorized =
        HTTP.send(
            HttpRequest.newBuilder(URI.create(aiUrl() + "/internal/recommendations/personalized"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(unauthorized.statusCode()).isEqualTo(401);
    var inferenceTimes = new ArrayList<Double>();
    var hydrateTimes = new ArrayList<Double>();
    for (int i = 0; i < 8; i++) {
      timed("/api/me/recommendations?limit=10", owner.token(), times);
      long start = System.nanoTime();
      var ranked = ai.rank(actual);
      inferenceTimes.add((System.nanoTime() - start) / 1e6);
      start = System.nanoTime();
      assertThat(hydrator.hydrate(ranked, 10)).hasSize(10);
      hydrateTimes.add((System.nanoTime() - start) / 1e6);
    }
    aiProcess.destroy();
    assertThat(aiProcess.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
    var outageTimes = new ArrayList<Double>();
    timed("/api/me/recommendations?limit=10", owner.token(), outageTimes);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM user_behavior_events", Long.class))
        .isEqualTo(2);
    String report =
        "1500 synthetic Backend-owned products; serial HTTP samples\\nCatalog preparation ms="
            + catalogMs
            + "\\nFull HTTP cold/warm ms="
            + times
            + "\\nAI HTTP inference warm ms="
            + inferenceTimes
            + "\\nHydration ms="
            + hydrateTimes
            + "\\nOutage fallback HTTP ms="
            + outageTimes
            + "\\n"
            + "Verified: owner events, minimized payload, laptop personalization, guest/cold start,"
            + " similar/source exclusion, internal 401, outage fallback.\\n";
    Files.writeString(
        Path.of("target/recommendation-http-evidence.txt"), report.replace("\\n", "\n"));
  }
}
