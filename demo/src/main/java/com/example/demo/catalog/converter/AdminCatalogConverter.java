package com.example.demo.catalog.converter;

import com.example.demo.catalog.dto.response.AdminCatalogResponses;
import com.example.demo.catalog.dto.response.SellingPrice;
import com.example.demo.catalog.entity.Product;
import com.example.demo.catalog.entity.ProductImage;
import com.example.demo.catalog.entity.ProductVariant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Services prepare references, active gallery, readiness and signed URLs; mapping performs no fetching/signing/validation. */
@Component
public class AdminCatalogConverter {
    public AdminCatalogResponses.ProductRow productRow(Product product, AdminCatalogResponses.CategoryReference category,
            AdminCatalogResponses.Brand brand, long variants, long enabled) {
        return new AdminCatalogResponses.ProductRow(product.getId(), product.getName(), product.getStatus().name(),
                Long.toString(product.getVersion()), category, brand, variants, enabled);
    }

    public AdminCatalogResponses.Variant variant(ProductVariant variant, UUID productId, long productVersion) {
        return new AdminCatalogResponses.Variant(variant.getId(), productId, Long.toString(productVersion), variant.getSku(),
                variant.isEnabled(), Map.copyOf(variant.getOptionCombination()), new SellingPrice(variant.getSellingPrice().toBigIntegerExact(), "VND"),
                Map.copyOf(variant.getTechnicalSpecifications()));
    }

    public AdminCatalogResponses.Image image(ProductImage image, UUID productId, String preparedUrl) {
        return new AdminCatalogResponses.Image(image.getId(), productId, preparedUrl, image.getAltText(), image.getWidth(),
                image.getHeight(), image.getDisplayOrder(), image.isPrimary());
    }
    public AdminCatalogResponses.Category category(com.example.demo.catalog.entity.Category category,UUID preparedParent) {
        return new AdminCatalogResponses.Category(category.getId(),category.getName(),preparedParent,Long.toString(category.getVersion()));
    }
    public AdminCatalogResponses.Brand brand(com.example.demo.catalog.entity.Brand brand) {
        return new AdminCatalogResponses.Brand(brand.getId(),brand.getCode(),brand.getName(),Long.toString(brand.getVersion()));
    }

}
