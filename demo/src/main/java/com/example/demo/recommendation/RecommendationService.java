package com.example.demo.recommendation;

import static com.example.demo.recommendation.RecommendationContract.*;

import com.example.demo.catalog.dto.response.ProductListingItem;
import com.example.demo.catalog.exception.ProductNotFoundException;
import java.time.Clock;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RecommendationService {
  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(RecommendationService.class);
  private final RecommendationStore store;
  private final RecommendationAiClient ai;
  private final RecommendationHydrator hydrator;
  private final Clock clock;

  public List<ProductListingItem> recommend(Long owner, UUID source, int limit) {
    var now = clock.instant();
    var rows = store.catalog();
    Set<UUID> ids = new HashSet<>();
    rows.forEach(r -> ids.add(r.candidate().productId()));
    if (source != null && !ids.contains(source)) throw new ProductNotFoundException();
    if (rows.isEmpty()) return List.of();
    var popular = store.popularity(now);
    var candidates =
        rows.stream()
            .map(
                r -> {
                  var c = r.candidate();
                  return new Candidate(
                      c.productId(),
                      c.name(),
                      c.category(),
                      c.brand(),
                      c.price(),
                      c.description(),
                      popular.getOrDefault(c.productId(), 0.0));
                })
            .toList();
    var fallback = fallback(candidates, source);
    List<UUID> ranked = new ArrayList<>();
    if (owner != null || source != null) {
      var events =
          owner == null
              ? List.<Event>of()
              : store.events(owner, now).stream()
                  .filter(event -> event.productId() == null || ids.contains(event.productId()))
                  .toList();
      var wishlist = owner == null ? List.<UUID>of() : store.wishlist(owner, ids);
      // No history: general recommendations without an unnecessary inference call.
      if (source != null || !events.isEmpty() || !wishlist.isEmpty()) {
        try {
          var output = ai.rank(new Request(candidates, events, wishlist, source, now, limit));
          if (output.size() > limit
              || new HashSet<>(output).size() != output.size()
              || output.stream()
                  .anyMatch(id -> id == null || !ids.contains(id) || id.equals(source)))
            throw new IllegalArgumentException("Invalid AI ranking");
          ranked.addAll(output);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          log.warn("Recommendation AI interrupted; safe fallback");
        } catch (RecommendationAiClient.OperationalFailure e) {
          log.error(
              "Recommendation AI configuration/security failure: {}; safe public fallback remains"
                  + " active",
              e.code);
        } catch (Exception e) {
          log.warn(
              "Recommendation AI unavailable or invalid: {}; using safe fallback",
              e.getClass().getSimpleName());
        }
      }
    }
    for (UUID id : fallback) if (!ranked.contains(id)) ranked.add(id);
    // Reserve alternatives for races/unpublication; bounded batch <= catalog summary maximum.
    return hydrator.hydrate(
        ranked.stream().limit(Math.min(100, limit * 3L)).toList(), limit, source);
  }

  static List<UUID> fallback(List<Candidate> candidates, UUID source) {
    var remaining =
        new ArrayList<>(candidates.stream().filter(c -> !c.productId().equals(source)).toList());
    var result = new ArrayList<UUID>();
    var categories = new HashMap<String, Integer>();
    var brands = new HashMap<String, Integer>();
    double max = remaining.stream().mapToDouble(c -> Math.log1p(c.popularity())).max().orElse(0);
    while (!remaining.isEmpty() && result.size() < 100) {
      Candidate best =
          remaining.stream()
              .min(
                  Comparator.<Candidate>comparingDouble(
                          c ->
                              -((max == 0 ? 0 : Math.log1p(c.popularity()) / max)
                                  - .08 * categories.getOrDefault(c.category(), 0)
                                  - .08 * brands.getOrDefault(c.brand(), 0)))
                      .thenComparing(c -> c.productId().toString()))
              .orElseThrow();
      remaining.remove(best);
      result.add(best.productId());
      if (best.category() != null && !best.category().isBlank())
        categories.merge(best.category(), 1, Integer::sum);
      if (best.brand() != null && !best.brand().isBlank())
        brands.merge(best.brand(), 1, Integer::sum);
    }
    return result;
  }
}
