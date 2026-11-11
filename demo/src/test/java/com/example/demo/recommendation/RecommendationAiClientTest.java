package com.example.demo.recommendation;

import static com.example.demo.recommendation.RecommendationContract.*;
import static org.assertj.core.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class RecommendationAiClientTest {
  HttpServer server;
  String response;
  int status = 200;
  long delay;
  UUID id = UUID.randomUUID();
  String key = "test-only-" + UUID.randomUUID();

  @BeforeEach
  void start() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/internal/recommendations/personalized",
        exchange -> {
          assertThat(exchange.getRequestHeaders().getFirst("X-Recommendation-Key")).isEqualTo(key);
          try {
            Thread.sleep(delay);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(status, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  RecommendationAiClient client(long timeout) {
    return new RecommendationAiClient(
        "http://127.0.0.1:" + server.getAddress().getPort(),
        key,
        500,
        timeout,
        JsonMapper.builder().build());
  }

  Request request() {
    return new Request(
        List.of(new Candidate(id, "Laptop", "Laptop", null, BigDecimal.TEN, "", 0)),
        List.of(),
        List.of(),
        null,
        Instant.now(),
        10);
  }

  @Test
  void preservesIds() throws Exception {
    response = "{\"items\":[{\"productId\":\"" + id + "\",\"score\":0.8}]}";
    assertThat(client(1000).rank(request())).containsExactly(id);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}",
        "{\"items\":[],\"extra\":true}",
        "{\"items\":[{\"productId\":\"bad\",\"score\":1}]}",
        "{\"items\":[{\"productId\":\"00000000-0000-0000-0000-000000000001\",\"score\":1}]}",
        "{\"items\":null}",
        "null"
      })
  void rejectsMalformedOrUnauthorized(String body) {
    response = body;
    assertThatThrownBy(() -> client(1000).rank(request())).isInstanceOf(Exception.class);
  }

  @Test
  void rejectsDuplicateUnknownFieldsNegativeScoresAndSource() {
    for (String item :
        List.of(
            "{\"productId\":\"" + id + "\",\"score\":1,\"reason\":\"bad\"}",
            "{\"productId\":\"" + id + "\",\"score\":-1}")) {
      response = "{\"items\":[" + item + "]}";
      assertThatThrownBy(() -> client(1000).rank(request())).isInstanceOf(Exception.class);
    }
    String item = "{\"productId\":\"" + id + "\",\"score\":1}";
    response = "{\"items\":[" + item + "," + item + "]}";
    assertThatThrownBy(() -> client(1000).rank(request())).isInstanceOf(Exception.class);
  }

  @Test
  void timeoutAndServiceAuthFailure() {
    response = "{\"items\":[]}";
    status = 401;
    assertThatThrownBy(() -> client(1000).rank(request())).hasMessageContaining("401");
    status = 503;
    assertThatThrownBy(() -> client(1000).rank(request())).hasMessageContaining("503");
    status = 200;
    delay = 200;
    assertThatThrownBy(() -> client(20).rank(request()))
        .isInstanceOf(java.net.http.HttpTimeoutException.class);
  }

  @Test
  void responseBound() {
    response = "x".repeat(70000);
    assertThatThrownBy(() -> client(1000).rank(request())).isInstanceOf(Exception.class);
  }

  @Test
  void rejectsDuplicateJsonKeysAndTrailingDocuments() {
    for (String body : List.of("{\"items\":[],\"items\":[]}", "{\"items\":[]} {}")) {
      response = body;
      assertThatThrownBy(() -> client(1000).rank(request())).isInstanceOf(Exception.class);
    }
  }
}
