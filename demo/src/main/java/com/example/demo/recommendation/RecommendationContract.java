package com.example.demo.recommendation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Contract v1. Canonical schema: AI-SWE recommendation/inference.py. */
public final class RecommendationContract {
  private RecommendationContract() {}

  public record Candidate(
      UUID productId,
      String name,
      String category,
      String brand,
      BigDecimal price,
      String description,
      double popularity) {}

  public record Event(String eventType, UUID productId, String query, Instant occurredAt) {}

  public record Request(
      List<Candidate> candidates,
      List<Event> recentEvents,
      List<UUID> wishlist,
      UUID sourceProductId,
      Instant asOf,
      int limit) {}
}
