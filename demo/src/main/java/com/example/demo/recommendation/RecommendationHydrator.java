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

  @Transactional
  public List<ProductListingItem> hydrate(List<UUID> ordered, int limit) {
    return hydrate(ordered, limit, null);
  }

  @Transactional
  public List<ProductListingItem> hydrate(List<UUID> ordered, int limit, UUID source) {
    var primaryImages =
        ordered.isEmpty()
            ? List.<com.example.demo.catalog.entity.ProductImage>of()
            : images.findPrimaryImagesForProducts(ordered);
    // Follow catalog policy: credential resolution must precede publication locks.
    try (var signer = primaryImages.isEmpty() ? null : storage.prepareReadSigner()) {
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
          products.findAllByIdsAndStatusWithCategory(ordered, ProductStatus.PUBLISHED);
      Map<UUID, com.example.demo.catalog.entity.Product> byId = new HashMap<>();
      currentProducts.forEach(product -> byId.put(product.getId(), product));
      Map<UUID, RecommendationStore.Row> fresh = new HashMap<>();
      store.refresh(ordered).forEach(row -> fresh.put(row.candidate().productId(), row));
      Map<UUID, String> urls = new HashMap<>();
      for (var image : primaryImages) {
        UUID id = image.getProduct().getId();
        if (published.contains(id)) urls.put(id, signer.signReadUrl(image.getObjectKey()));
      }
      List<ProductListingItem> result = new ArrayList<>();
      for (UUID id : ordered) {
        var product = byId.get(id);
        var row = fresh.get(id);
        if (published.contains(id) && product != null && row != null) {
          result.add(
              converter.toListingItem(
                  converter.toSummary(product, urls.get(id)),
                  row.candidate().price() == null
                      ? null
                      : row.candidate().price().toBigIntegerExact(),
                  row.variantId()));
          if (result.size() == limit) break;
        }
      }
      return List.copyOf(result);
    }
  }
}
