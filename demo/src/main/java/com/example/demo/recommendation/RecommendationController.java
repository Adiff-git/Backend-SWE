package com.example.demo.recommendation;

import com.example.demo.catalog.dto.response.ProductListingItem;
import com.example.demo.catalog.exception.InvalidCatalogRequestException;
import com.example.demo.common.identity.CurrentUserIdProvider;
import io.swagger.v3.oas.annotations.Operation;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class RecommendationController {
  private final RecommendationService service;
  private final CurrentUserIdProvider identity;

  public record Recommendations(List<ProductListingItem> items) {}

  private int limit(MultiValueMap<String, String> params) {
    params.forEach(
        (key, values) -> {
          if (!key.equals("limit") || values.size() != 1)
            throw new InvalidCatalogRequestException("Unsupported or duplicate parameter");
        });
    try {
      int n = params.containsKey("limit") ? Integer.parseInt(params.getFirst("limit")) : 10;
      if (n < 1 || n > 20) throw new NumberFormatException();
      return n;
    } catch (NumberFormatException e) {
      throw new InvalidCatalogRequestException("limit must be an integer from 1 to 20");
    }
  }

  private ResponseEntity<Recommendations> response(
      Long owner, UUID source, MultiValueMap<String, String> parameters) {
    return ResponseEntity.ok()
        .header("Cache-Control", "no-store")
        .body(new Recommendations(service.recommend(owner, source, limit(parameters))));
  }

  @io.swagger.v3.oas.annotations.Parameter(
      name = "limit",
      in = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY,
      schema =
          @io.swagger.v3.oas.annotations.media.Schema(
              type = "integer",
              minimum = "1",
              maximum = "20",
              defaultValue = "10"))
  @GetMapping("/api/recommendations")
  @Operation(
      summary = "General published product recommendations",
      description =
          "Public; aggregate popularity and diversity. limit 1..20, default 10. No guest tracking.")
  public ResponseEntity<Recommendations> general(
      @io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestParam
          MultiValueMap<String, String> parameters) {
    return response(null, null, parameters);
  }

  @io.swagger.v3.oas.annotations.Parameter(
      name = "limit",
      in = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY,
      schema =
          @io.swagger.v3.oas.annotations.media.Schema(
              type = "integer",
              minimum = "1",
              maximum = "20",
              defaultValue = "10"))
  @GetMapping("/api/me/recommendations")
  @Operation(
      summary = "Current Customer recommendations",
      description =
          "Customer JWT required. Uses only authenticated owner's recent interests and current"
              + " wishlist; no customerId parameter.")
  @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
  public ResponseEntity<Recommendations> personalized(
      @io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestParam
          MultiValueMap<String, String> parameters) {
    return response(identity.getCurrentUserId(), null, parameters);
  }

  @io.swagger.v3.oas.annotations.Parameter(
      name = "limit",
      in = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY,
      schema =
          @io.swagger.v3.oas.annotations.media.Schema(
              type = "integer",
              minimum = "1",
              maximum = "20",
              defaultValue = "10"))
  @GetMapping("/api/products/{productId}/similar")
  @Operation(
      summary = "Similar published products",
      description = "Public; source must be published. Excludes source. limit 1..20, default 10.")
  public ResponseEntity<Recommendations> similar(
      @PathVariable UUID productId,
      @io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestParam
          MultiValueMap<String, String> parameters) {
    return response(null, productId, parameters);
  }
}
