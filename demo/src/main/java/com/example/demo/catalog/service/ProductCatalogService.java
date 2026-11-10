package com.example.demo.catalog.service;

import com.example.demo.catalog.dto.response.ProductPage;
import com.example.demo.catalog.dto.response.ProductDetail;
import com.example.demo.catalog.dto.response.ProductSummary;
import com.example.demo.catalog.entity.Product;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface ProductCatalogService {

	int DEFAULT_PAGE = 0;
	int DEFAULT_SIZE = 20;
	int MAX_SIZE = 100;

	ProductPage listProducts(int page, int size);

	ProductPage listProducts(com.example.demo.catalog.dto.request.ProductSearchRequest request);

	ProductDetail getProduct(UUID id);

	/** Internal eligible reference; no detail/gallery loading. */
	Product getPublishedProductEntity(UUID id);

	/** Bounded, fully prepared public summaries; ineligible IDs are absent. */
	Map<UUID, ProductSummary> getProductSummaries(Collection<UUID> ids);
}
