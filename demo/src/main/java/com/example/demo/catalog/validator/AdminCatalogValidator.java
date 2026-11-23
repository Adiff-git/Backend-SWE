package com.example.demo.catalog.validator;

import com.example.demo.catalog.entity.Product;
import com.example.demo.catalog.entity.ProductImage;
import com.example.demo.catalog.entity.ProductVariant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Basic integrity for all lifecycle states, independent of publication completeness and stock. */
@Component
@RequiredArgsConstructor
public class AdminCatalogValidator {
    private final CatalogDataValidator basic;
    private final TechnicalSpecificationValidator specifications;
    private final ProductImageContentValidator images;

    public void validateBasic(Product product, List<ProductVariant> variants, List<ProductImage> gallery, Set<String> families) {
        basic.validateProduct(product, variants);
        if (families == null) throw new IllegalArgumentException("Resolved category context is required");
        for (ProductVariant variant : variants) specifications.validatePersisted(variant.getTechnicalSpecifications(), families);
        if (gallery == null) throw new IllegalArgumentException("Prepared gallery is required");
        Set<Integer> positions = new HashSet<>();
        int active = 0;
        int primary = 0;
        for (ProductImage image : gallery) {
            if (image == null || image.getProduct() == null || image.getProduct() != product
                    && (product.getId() == null || !product.getId().equals(image.getProduct().getId()))) {
                throw new IllegalArgumentException("Image must belong to Product");
            }
            images.validateMetadata(image.getContentType(), image.getByteSize(), image.getAltText());
            if (image.getObjectKey() == null || image.getObjectKey().isBlank() || image.getWidth() <= 0
                    || image.getHeight() <= 0 || image.getDisplayOrder() < 0 || !image.isActive() && image.isPrimary()) {
                throw new IllegalArgumentException("Invalid gallery metadata");
            }
            if (!image.isActive()) continue;
            active++;
            if (image.isPrimary()) primary++;
            if (!positions.add(image.getDisplayOrder())) throw new IllegalArgumentException("Duplicate active gallery position");
        }
        if (active > 10 || active > 0 && primary != 1) throw new IllegalArgumentException("Invalid active gallery");
    }
}
