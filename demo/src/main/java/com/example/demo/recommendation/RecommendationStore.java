package com.example.demo.recommendation;

import static com.example.demo.recommendation.RecommendationContract.*;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RecommendationStore {
  public static final int MAX_CANDIDATES = 3000;

  private static final class CapacityExceeded
      extends com.example.demo.common.exception.ApplicationException {
    CapacityExceeded() {
      super(503, "Recommendation catalog capacity exceeded");
    }
  }

  private final JdbcTemplate jdbc;

  public record Row(Candidate candidate, UUID variantId) {}

  private String text(String value, int max) {
    return value == null
        ? ""
        : value.substring(
            0, value.offsetByCodePoints(0, Math.min(value.codePointCount(0, value.length()), max)));
  }

  // Complete published snapshot up to explicit capacity. No arbitrary first-page ranking.
  public List<Row> catalog() {
    return rows(List.of());
  }

  public List<Row> refresh(Collection<UUID> ids) {
    if (ids.isEmpty()) return List.of();
    if (ids.size() > 100) throw new IllegalArgumentException("Too many hydration identifiers");
    return rows(ids);
  }

  private List<Row> rows(Collection<UUID> ids) {
    String scope =
        ids.isEmpty()
            ? ""
            : " AND p.id IN (" + String.join(",", Collections.nCopies(ids.size(), "?")) + ")";
    var rows =
        jdbc.query(
            """
            SELECT p.id,left(p.name,512) name,left(c.name,256) category,left(b.name,256) brand,
              left(p.description,2000) description,v.id variant_id,v.selling_price
            FROM products p JOIN categories c ON c.id=p.category_id LEFT JOIN brands b ON b.id=p.brand_id
            LEFT JOIN LATERAL (SELECT id,selling_price FROM product_variants WHERE product_id=p.id
              AND selling_price>=0 AND selling_price::text NOT IN ('NaN','Infinity','-Infinity')
              ORDER BY selling_price,id LIMIT 1) v ON true
            WHERE p.status='PUBLISHED'
            """
                + scope
                + " ORDER BY p.id LIMIT 3001",
            (rs, n) ->
                new Row(
                    new Candidate(
                        rs.getObject("id", UUID.class),
                        text(rs.getString("name"), 512),
                        text(rs.getString("category"), 256),
                        text(rs.getString("brand"), 256),
                        rs.getBigDecimal("selling_price"),
                        text(rs.getString("description"), 2000),
                        0),
                    rs.getObject("variant_id", UUID.class)),
            ids.toArray());
    if (rows.size() > MAX_CANDIDATES) throw new CapacityExceeded();
    return rows;
  }

  public Map<UUID, Double> popularity(Instant now) {
    Map<UUID, Double> scores = new HashMap<>();
    jdbc.query(
        """
        SELECT product_id,sum(least(n,3)*weight) score FROM (
          SELECT customer_id,product_id,event_type,count(*) n,
            CASE event_type WHEN 'WISHLIST_ADD' THEN 5 WHEN 'PRODUCT_CLICK' THEN 3 ELSE 2 END weight
          FROM user_behavior_events WHERE occurred_at>=? AND occurred_at<=?
            AND event_type IN ('PRODUCT_VIEW','PRODUCT_CLICK','WISHLIST_ADD')
          GROUP BY customer_id,product_id,event_type
        ) capped GROUP BY product_id
        """,
        (org.springframework.jdbc.core.RowCallbackHandler)
            rs -> scores.put(rs.getObject(1, UUID.class), rs.getDouble(2)),
        Timestamp.from(now.minus(Duration.ofDays(90))),
        Timestamp.from(now));
    return scores;
  }

  public List<Event> events(Long owner, Instant now) {
    return jdbc.query(
        """
        SELECT event_type,product_id,normalized_query,occurred_at FROM user_behavior_events
        WHERE customer_id=? AND occurred_at>=? AND occurred_at<=?
        ORDER BY occurred_at DESC,id DESC LIMIT 500
        """,
        (rs, n) ->
            new Event(
                rs.getString(1),
                rs.getObject(2, UUID.class),
                rs.getString(3),
                rs.getTimestamp(4).toInstant()),
        owner,
        Timestamp.from(now.minus(Duration.ofDays(90))),
        Timestamp.from(now));
  }

  public List<UUID> wishlist(Long owner, Set<UUID> candidates) {
    // Membership limited to snapshot IDs; bounded result with no unbounded entity materialization.
    if (candidates.isEmpty()) return List.of();
    return jdbc.query(
        "SELECT product_id FROM wishlist_items WHERE user_id=? AND product_id IN ("
            + String.join(",", Collections.nCopies(candidates.size(), "?"))
            + ") ORDER BY product_id",
        (rs, n) -> rs.getObject(1, UUID.class),
        arguments(owner, candidates));
  }

  private Object[] arguments(Long owner, Set<UUID> ids) {
    List<Object> args = new ArrayList<>();
    args.add(owner);
    args.addAll(ids);
    return args.toArray();
  }
}
