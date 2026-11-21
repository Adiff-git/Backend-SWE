package com.example.demo.recommendation;

import static com.example.demo.recommendation.RecommendationContract.*;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class RecommendationAiClient {
  public static final class OperationalFailure extends RuntimeException {
    public final String code;

    OperationalFailure(String code) {
      super(code);
      this.code = code;
    }
  }

  private final String url, key;
  private final Duration timeout;
  private final HttpClient http;
  private final JsonMapper json;
  private final JsonMapper responseJson =
      JsonMapper.builder()
          .enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
          .enable(tools.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
          .build();

  public RecommendationAiClient(
      @Value("${recommendation.ai.url:http://127.0.0.1:8001}") String url,
      @Value("${recommendation.ai.key:}") String key,
      @Value("${recommendation.ai.connect-timeout-ms:500}") long connect,
      @Value("${recommendation.ai.response-timeout-ms:3000}") long response,
      JsonMapper json) {
    var uri = URI.create(url);
    if (!Set.of("http", "https").contains(uri.getScheme())
        || uri.getHost() == null
        || uri.getUserInfo() != null
        || uri.getQuery() != null
        || uri.getFragment() != null
        || connect < 1
        || connect > 10000
        || response < 1
        || response > 30000)
      throw new IllegalArgumentException("Invalid recommendation AI configuration");
    this.url = url.replaceAll("/+$", "");
    this.key = key;
    this.json = json;
    this.timeout = Duration.ofMillis(response);
    this.http =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(connect))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
  }

  public List<UUID> rank(Request request) throws Exception {
    if (key.length() < 32) throw new OperationalFailure("SERVICE_KEY_NOT_CONFIGURED");
    byte[] body = json.writeValueAsBytes(request);
    if (body.length > 12_000_000)
      throw new IllegalStateException("Recommendation request capacity exceeded");
    var call =
        HttpRequest.newBuilder(
                URI.create(
                    url
                        + "/internal/recommendations/"
                        + (request.sourceProductId() == null ? "personalized" : "similar")))
            .timeout(timeout)
            .header("Content-Type", "application/json")
            .header("X-Recommendation-Key", key)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build();
    // Bounded subscriber rejects oversized responses before buffering them in memory.
    var response = http.send(call, info -> new BoundedBodySubscriber(65536));
    if (response.statusCode() != 200)
      throw new OperationalFailure("AI_HTTP_" + response.statusCode());
    var root = responseJson.readTree(response.body());
    if (!root.isObject()
        || root.size() != 1
        || !root.has("items")
        || !root.get("items").isArray()
        || root.get("items").size() > request.limit())
      throw new IllegalArgumentException("Invalid AI response envelope");
    Set<UUID> allowed = new HashSet<>();
    request.candidates().forEach(c -> allowed.add(c.productId()));
    List<UUID> result = new ArrayList<>();
    for (var item : root.get("items")) {
      if (!item.isObject()
          || item.size() != 2
          || !item.has("productId")
          || !item.has("score")
          || !item.get("productId").isString()
          || !item.get("score").isNumber()
          || !Double.isFinite(item.get("score").doubleValue())
          || item.get("score").doubleValue() < 0)
        throw new IllegalArgumentException("Invalid AI item");
      String raw = item.get("productId").asString();
      UUID id = UUID.fromString(raw);
      if (!id.toString().equalsIgnoreCase(raw)
          || !allowed.contains(id)
          || id.equals(request.sourceProductId())
          || result.contains(id))
        throw new IllegalArgumentException("Unauthorized or duplicate AI product");
      result.add(id);
    }
    return result;
  }

  private static final class BoundedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
    private final java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
    private final java.util.concurrent.CompletableFuture<byte[]> result =
        new java.util.concurrent.CompletableFuture<>();
    private final int max;
    private java.util.concurrent.Flow.Subscription subscription;

    BoundedBodySubscriber(int max) {
      this.max = max;
    }

    public java.util.concurrent.CompletionStage<byte[]> getBody() {
      return result;
    }

    public void onSubscribe(java.util.concurrent.Flow.Subscription s) {
      subscription = s;
      s.request(1);
    }

    public void onNext(List<java.nio.ByteBuffer> chunks) {
      for (var chunk : chunks) {
        if (chunk.remaining() > max - bytes.size()) {
          subscription.cancel();
          result.completeExceptionally(new IllegalArgumentException("AI response too large"));
          return;
        }
        byte[] part = new byte[chunk.remaining()];
        chunk.get(part);
        bytes.writeBytes(part);
      }
      subscription.request(1);
    }

    public void onError(Throwable t) {
      result.completeExceptionally(t);
    }

    public void onComplete() {
      result.complete(bytes.toByteArray());
    }
  }
}
