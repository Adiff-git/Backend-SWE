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

    public ProductSummary toSummary(com.example.demo.catalog.repository.CatalogPublicViews.Product product,String url){
        return new ProductSummary(product.getId(),product.getName(),new CategorySummary(product.getCategoryId(),product.getCategoryName()),url);
    }

    public ProductDetail toPublicDetail(com.example.demo.catalog.repository.CatalogPublicViews.Product product,
            List<com.example.demo.catalog.repository.CatalogPublicViews.Variant> variants,
            List<com.example.demo.catalog.repository.CatalogPublicViews.Image> images,Map<UUID,String> urls,
            Map<UUID,com.example.demo.inventory.dto.response.InventoryAvailability> availability){
        var gallery=images.stream().map(i -> new ProductImageSummary(i.getId(),urls.get(i.getId()),i.getAltText(),
                i.getWidth(),i.getHeight(),i.getDisplayOrder(),i.getPrimaryImage())).toList();
        var choices=variants.stream().map(v -> new VariantSummary(v.getId(),v.getSku(),Map.copyOf(v.getOptions()),
                new SellingPrice(v.getPrice().toBigIntegerExact(),"VND"),Map.copyOf(v.getSpecifications()),availability.get(v.getId()))).toList();
        String primary=images.stream().filter(com.example.demo.catalog.repository.CatalogPublicViews.Image::getPrimaryImage)
                .map(i -> urls.get(i.getId())).findFirst().orElse(null);
        var summary=toSummary(product,primary);
        BrandSummary brand=product.getBrandId()==null?null:new BrandSummary(product.getBrandId(),product.getBrandCode(),product.getBrandName());
        return new ProductDetail(summary.getId(),summary.getName(),summary.getCategory(),primary,product.getDescription(),brand,choices,gallery);
    }

	public ProductSummary toSummary(Product product, String primaryImageUrl) {
		Category category = product.getCategory();
		return new ProductSummary(product.getId(), product.getName(),
				new CategorySummary(category.getId(), category.getName()), primaryImageUrl);
	}

    /** All values have already been selected/loaded by the owning workflow; no variant selection here. */
    public ProductListingItem toListingItem(ProductSummary summary, BigInteger displayAmount,
            UUID representativeVariantId, com.example.demo.inventory.dto.response.InventoryAvailability availability) {
        SellingPrice price = displayAmount == null ? null : new SellingPrice(displayAmount, "VND");
        return new ProductListingItem(summary.getId(), summary.getName(), summary.getCategory(),
                summary.getPrimaryImageUrl(), price, representativeVariantId, availability);
    }

	public ProductDetail toDetail(Product product, List<ProductVariant> variants,
			List<ProductImage> images, Map<UUID, String> approvedImageUrls,
            Map<UUID, com.example.demo.inventory.dto.response.InventoryAvailability> availability) {
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
					new SellingPrice(variant.getSellingPrice().toBigIntegerExact(), "VND"),
					Map.copyOf(variant.getTechnicalSpecifications()), availability.get(variant.getId())));
		}
		ProductSummary summary = toSummary(product, primaryImageUrl);
		Brand brand = product.getBrand();
		BrandSummary brandSummary = brand == null ? null
				: new BrandSummary(brand.getId(), brand.getCode(), brand.getName());
		return new ProductDetail(summary.getId(), summary.getName(), summary.getCategory(),
				summary.getPrimaryImageUrl(), product.getDescription(), brandSummary, variantSummaries, imageSummaries);
	}
}
