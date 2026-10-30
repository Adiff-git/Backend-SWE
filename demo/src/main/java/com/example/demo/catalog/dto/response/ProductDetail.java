package com.example.demo.catalog.dto.response;

import java.util.List;
import java.util.UUID;
import lombok.Getter;

@Getter
public final class ProductDetail extends ProductSummary {
	private final String description;
	private final List<VariantSummary> variants;
	private final List<ProductImageSummary> images;

	public ProductDetail(UUID id, String name, CategorySummary category, String primaryImageUrl,
			String description, List<VariantSummary> variants, List<ProductImageSummary> images) {
		super(id, name, category, primaryImageUrl);
		this.description = description;
		this.variants = List.copyOf(variants);
		this.images = List.copyOf(images);
	}
}
