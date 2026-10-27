package com.example.demo.catalog.converter;

import com.example.demo.catalog.dto.response.CategorySummary;
import com.example.demo.catalog.dto.response.ProductDetail;
import com.example.demo.catalog.dto.response.ProductImageSummary;
import com.example.demo.catalog.dto.response.ProductSummary;
import com.example.demo.catalog.dto.response.SellingPrice;
import com.example.demo.catalog.dto.response.VariantSummary;
import com.example.demo.catalog.entity.Category;
import com.example.demo.catalog.entity.Product;
import com.example.demo.catalog.entity.ProductImage;
import com.example.demo.catalog.entity.ProductVariant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Maps fully loaded catalog data; eligibility and signing belong to the service. */
@Component
public class ProductCatalogConverter {

	public ProductSummary toSummary(Product product, String primaryImageUrl) {
		Category category = product.getCategory();
		return new ProductSummary(product.getId(), product.getName(),
				new CategorySummary(category.getId(), category.getName()), primaryImageUrl);
	}

	public ProductDetail toDetail(Product product, List<ProductVariant> variants,
			List<ProductImage> images, Map<UUID, String> approvedImageUrls) {
		String primaryImageUrl = null;
		List<ProductImageSummary> imageSummaries = new ArrayList<>();
		for (ProductImage image : images) {
			String url = approvedImageUrls.get(image.getId());
			imageSummaries.add(new ProductImageSummary(image.getId(), url, image.getAltText(),
					image.getWidth(), image.getHeight(), image.getDisplayOrder(), image.isPrimary()));
			if (image.isPrimary()) {
				primaryImageUrl = url;
			}
		}
		List<VariantSummary> variantSummaries = new ArrayList<>();
		for (ProductVariant variant : variants) {
			variantSummaries.add(new VariantSummary(variant.getId(), variant.getSku(),
					Map.copyOf(variant.getOptionCombination()),
					new SellingPrice(variant.getSellingPrice().toBigIntegerExact(), "VND")));
		}
		ProductSummary summary = toSummary(product, primaryImageUrl);
		return new ProductDetail(summary.getId(), summary.getName(), summary.getCategory(),
				summary.getPrimaryImageUrl(), product.getDescription(), variantSummaries, imageSummaries);
	}
}
