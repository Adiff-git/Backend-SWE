package com.example.demo.catalog.dto.response;

import java.util.List;
import java.util.UUID;
import lombok.Getter;

@Getter
public final class ProductDetail extends ProductSummary {
	private final String description;
	private final BrandSummary brand;
	private final List<VariantSummary> variants;
	private final List<ProductImageSummary> images;
    private final List<OptionDimension> optionDimensions;
    private final ProductPriceRange priceRange;

	public ProductDetail(UUID id, String name, CategorySummary category, String primaryImageUrl,
			String description, BrandSummary brand, List<VariantSummary> variants, List<ProductImageSummary> images) {
        this(id, name, category, primaryImageUrl, description, brand, variants, images, List.of());
    }

    public ProductDetail(UUID id, String name, CategorySummary category, String primaryImageUrl,
            String description, BrandSummary brand, List<VariantSummary> variants, List<ProductImageSummary> images,
            List<OptionDimension> optionDimensions) {
        this(id, name, category, primaryImageUrl, description, brand, variants, images, optionDimensions,
                new ProductPriceRange(null, null));
    }

    public ProductDetail(UUID id, String name, CategorySummary category, String primaryImageUrl,
            String description, BrandSummary brand, List<VariantSummary> variants, List<ProductImageSummary> images,
            List<OptionDimension> optionDimensions, ProductPriceRange priceRange) {
		super(id, name, category, primaryImageUrl);
		this.description = description;
		this.brand = brand;
		this.variants = List.copyOf(variants);
		this.images = List.copyOf(images);
        this.optionDimensions = List.copyOf(optionDimensions);
        this.priceRange = priceRange;
	}
}
