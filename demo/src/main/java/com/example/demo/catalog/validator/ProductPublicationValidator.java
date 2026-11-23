package com.example.demo.catalog.validator;

import com.example.demo.catalog.entity.Product;
import com.example.demo.catalog.entity.ProductImage;
import com.example.demo.catalog.entity.ProductVariant;
import com.example.demo.catalog.exception.AdminCatalogException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Central proposed-aggregate readiness. Call on Publish and every resulting Published mutation. */
@Component
@RequiredArgsConstructor
public class ProductPublicationValidator {
    public record Violation(String path, String code, String message) { }
    private final AdminCatalogValidator basic;

    public List<Violation> violations(Product product, List<ProductVariant> variants, List<ProductImage> images, Set<String> families) {
        List<Violation> result = new ArrayList<>();
        try {
            basic.validateBasic(product, variants, images, families);
        } catch (IllegalArgumentException | com.example.demo.catalog.exception.InvalidProductImageException invalid) {
            result.add(new Violation("product", "BASIC_DATA_INVALID", "Correct the supplied catalog data"));
        }
        List<ProductVariant> preparedVariants = variants == null ? List.of() : variants;
        long enabled = 0;
        for (int index = 0; index < preparedVariants.size(); index++) {
            ProductVariant variant = preparedVariants.get(index);
            if (variant == null || !variant.isEnabled()) continue;
            enabled++;
            if (variant.getSellingPrice() == null || variant.getSellingPrice().signum() <= 0) {
                result.add(new Violation("variants[" + index + "].sellingPrice", "POSITIVE_PRICE_REQUIRED", "Enabled sale price must be positive"));
            }
        }
        if (enabled == 0) result.add(new Violation("variants", "ENABLED_VARIANT_REQUIRED", "At least one Enabled variant is required"));
        List<ProductImage> active = images == null ? List.of() : images.stream().filter(image -> image != null && image.isActive()).toList();
        if (active.isEmpty() || active.stream().filter(ProductImage::isPrimary).count() != 1) {
            result.add(new Violation("images", "PRIMARY_IMAGE_REQUIRED", "A valid gallery with exactly one primary image is required"));
        }
        return result.stream().sorted(Comparator.comparing(Violation::path).thenComparing(Violation::code)).toList();
    }

    public void requireValid(Product product, List<ProductVariant> variants, List<ProductImage> images, Set<String> families) {
        List<Violation> violations = violations(product, variants, images, families);
        if (!violations.isEmpty()) throw new AdminCatalogException(422, "CATALOG_PUBLICATION_INVALID", "Product is not publication-ready",
                Map.of("violations", violations));
    }
}
