package com.example.demo.catalog.converter;

import com.example.demo.catalog.dto.response.CategorySummary;
import com.example.demo.catalog.dto.response.BrandSummary;
import com.example.demo.catalog.entity.Brand;
import com.example.demo.catalog.dto.response.ProductDetail;
import com.example.demo.catalog.dto.response.ProductImageSummary;
import com.example.demo.catalog.dto.response.ProductSummary;
import com.example.demo.catalog.dto.response.ProductListingItem;
import java.math.BigInteger;
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

    public ProductDetail toPreparedDetail(Product product,List<com.example.demo.catalog.repository.CatalogDetailVariantRow> rows,
            List<ProductImage> images,Map<UUID,String> urls,
            Map<UUID,com.example.demo.inventory.dto.response.InventoryAvailability> availability,
            List<com.example.demo.catalog.dto.response.OptionDimension> dimensions,
            Map<UUID,com.example.demo.catalog.dto.response.VariantPricing> prices,
            com.example.demo.catalog.dto.response.ProductPriceRange range) {
        ProductDetail header=toDetail(product,List.of(),images,urls,availability,dimensions,prices,range);
        List<VariantSummary> summaries=rows.stream().map(row -> {
            var state=row.pricing();
            Map<String,String> selection=row.canonicalLabels().entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                    Map.Entry::getKey,entry -> entry.getValue().get("value")));
            return new VariantSummary(state.variantId(),state.sku(),state.options(),prices.get(state.variantId()).getEffectivePrice(),
                    row.specifications(),availability.get(state.variantId()),selection,prices.get(state.variantId()));
        }).toList();
        return new ProductDetail(header.getId(),header.getName(),header.getCategory(),header.getPrimaryImageUrl(),header.getDescription(),
                header.getBrand(),summaries,header.getImages(),dimensions,range);
    }

	public ProductSummary toSummary(Product product, String primaryImageUrl) {
		Category category = product.getCategory();
		return new ProductSummary(product.getId(), product.getName(),
				new CategorySummary(category.getId(), category.getName()), primaryImageUrl);
	}

    /** All values have already been selected/loaded by the owning workflow; no variant selection here. */
    public ProductListingItem toListingItem(ProductSummary summary, BigInteger displayAmount,
            UUID representativeVariantId, com.example.demo.inventory.dto.response.InventoryAvailability availability) {
        return toListingItem(summary, displayAmount, representativeVariantId, availability, null,
                new com.example.demo.catalog.dto.response.ProductPriceRange(null, null));
    }

    public ProductListingItem toListingItem(ProductSummary summary, BigInteger displayAmount,
            UUID representativeVariantId, com.example.demo.inventory.dto.response.InventoryAvailability availability,
            com.example.demo.catalog.dto.response.VariantPricing pricing,
            com.example.demo.catalog.dto.response.ProductPriceRange range) {
        SellingPrice price = displayAmount == null ? null : new SellingPrice(displayAmount, "VND");
        return new ProductListingItem(summary.getId(), summary.getName(), summary.getCategory(),
                summary.getPrimaryImageUrl(), price, representativeVariantId, availability, pricing, range);
    }

	public ProductDetail toDetail(Product product, List<ProductVariant> variants,
			List<ProductImage> images, Map<UUID, String> approvedImageUrls,
            Map<UUID, com.example.demo.inventory.dto.response.InventoryAvailability> availability) {
        return toDetail(product, variants, images, approvedImageUrls, availability, List.of());
    }

    public ProductDetail toDetail(Product product, List<ProductVariant> variants,
            List<ProductImage> images, Map<UUID, String> approvedImageUrls,
            Map<UUID, com.example.demo.inventory.dto.response.InventoryAvailability> availability,
            List<com.example.demo.catalog.dto.response.OptionDimension> optionDimensions) {
        return toDetail(product, variants, images, approvedImageUrls, availability, optionDimensions, Map.of(),
                new com.example.demo.catalog.dto.response.ProductPriceRange(null, null));
    }

    public ProductDetail toDetail(Product product, List<ProductVariant> variants,
            List<ProductImage> images, Map<UUID, String> approvedImageUrls,
            Map<UUID, com.example.demo.inventory.dto.response.InventoryAvailability> availability,
            List<com.example.demo.catalog.dto.response.OptionDimension> optionDimensions,
            Map<UUID, com.example.demo.catalog.dto.response.VariantPricing> prices,
            com.example.demo.catalog.dto.response.ProductPriceRange range) {
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
                    prices.containsKey(variant.getId()) ? prices.get(variant.getId()).getEffectivePrice()
                            : new SellingPrice(variant.getSellingPrice().toBigIntegerExact(), "VND"),
					Map.copyOf(variant.getTechnicalSpecifications()), availability.get(variant.getId()),
                    variant.getSelectionOptions(), prices.get(variant.getId())));
		}
		ProductSummary summary = toSummary(product, primaryImageUrl);
		Brand brand = product.getBrand();
		BrandSummary brandSummary = brand == null ? null
				: new BrandSummary(brand.getId(), brand.getCode(), brand.getName());
		return new ProductDetail(summary.getId(), summary.getName(), summary.getCategory(),
				summary.getPrimaryImageUrl(), product.getDescription(), brandSummary, variantSummaries, imageSummaries, optionDimensions, range);
	}
}
