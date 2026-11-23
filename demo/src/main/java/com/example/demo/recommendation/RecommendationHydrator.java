package com.example.demo.recommendation;

import com.example.demo.catalog.converter.ProductCatalogConverter;
import com.example.demo.catalog.dto.response.ProductListingItem;
import com.example.demo.catalog.entity.ProductStatus;
import com.example.demo.catalog.exception.ProductNotFoundException;
import com.example.demo.catalog.repository.ProductImageRepository;
import com.example.demo.catalog.repository.ProductRepository;
import com.example.demo.catalog.storage.ProductImageStorage;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reuses catalog batch repositories, signing boundary and public converter. */
@Service
@RequiredArgsConstructor
public class RecommendationHydrator {
  private final ProductRepository products;
  private final ProductImageRepository images;
  private final ProductImageStorage storage;
  private final ProductCatalogConverter converter;
  private final RecommendationStore store;
  private final com.example.demo.inventory.service.InventoryService inventory;

  @Transactional
  public List<ProductListingItem> hydrate(List<UUID> ordered, int limit) {
    return hydrate(ordered, limit, null);
  }

  @Transactional
  public List<ProductListingItem> hydrate(List<UUID> ordered, int limit, UUID source) {
    boolean pictured=!ordered.isEmpty() && !images.findCartPrimaryImages(ordered).isEmpty();
    // Only a scalar peek precedes credential resolution. Current membership is reloaded under locks.
    try (var signer = pictured ? storage.prepareReadSigner() : null) {
      var locks = new ArrayList<>(ordered);
      if (source != null) locks.add(source);
      if (locks.isEmpty()) return List.of();
      Set<UUID> published = new HashSet<>();
      for (Object[] row : products.lockStatusesForShare(locks)) {
        if (ProductStatus.PUBLISHED.name().equals(row[1])) published.add((UUID) row[0]);
      }
      if (source != null && !published.contains(source)) throw new ProductNotFoundException();
      if (ordered.isEmpty()) return List.of();
      var currentProducts =
          products.findPublicSummaries(ordered);
      Map<UUID, com.example.demo.catalog.repository.CatalogPublicViews.Product> byId = new HashMap<>();
      currentProducts.forEach(product -> byId.put(product.getId(), product));
      Map<UUID, RecommendationStore.Row> fresh = new HashMap<>();
      store.refresh(ordered).forEach(row -> fresh.put(row.candidate().productId(), row));
      var variantIds = fresh.values().stream().map(RecommendationStore.Row::variantId)
          .filter(Objects::nonNull).distinct().sorted(Comparator.comparing(UUID::toString)).toList();
      var availability = variantIds.isEmpty()
          ? Map.<UUID, com.example.demo.inventory.dto.response.InventoryAvailability>of()
          : inventory.getAvailability(variantIds);
      Map<UUID, String> urls = new HashMap<>();
      for (Object[] image : images.findCartPrimaryImages(ordered)) {
        UUID id=(UUID)image[0];
        if (published.contains(id) && fresh.containsKey(id)) {
          if(signer==null)throw new com.example.demo.catalog.exception.CatalogStorageUnavailableException();
          urls.put(id,signer.signReadUrl((String)image[1]));
        }
      }
      List<ProductListingItem> result = new ArrayList<>();
      for (UUID id : ordered) {
        var product = byId.get(id);
        var row = fresh.get(id);
        if (published.contains(id) && product != null && row != null && row.variantId()!=null
                && row.candidate().price()!=null && row.candidate().price().signum()>0) {
          result.add(
              converter.toListingItem(
                  converter.toSummary(product, urls.get(id)),
                  row.candidate().price() == null
                      ? null
                      : row.candidate().price().toBigIntegerExact(),
                  row.variantId(),
                  row.variantId() == null ? null : availability.get(row.variantId())));
          if (result.size() == limit) break;
        }
      }
      return List.copyOf(result);
    }
  }
}
