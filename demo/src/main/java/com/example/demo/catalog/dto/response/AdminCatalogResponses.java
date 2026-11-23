package com.example.demo.catalog.dto.response;

import com.example.demo.catalog.validator.ProductPublicationValidator.Violation;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Value;

/** Administrative wire types only; no public catalog DTO acquires lifecycle/revision fields. */
public final class AdminCatalogResponses {
    private AdminCatalogResponses() { }
    @Value public static class CategoryReference { UUID id; String name; String version; }
    @Value public static class Category { UUID id; String name; UUID parentCategoryId; String version; }
    @Value public static class Brand { UUID id; String code; String name; String version; }
    @Value public static class Readiness { boolean eligible; List<Violation> violations; }
    @Value public static class Variant {
        UUID id; UUID productId; String productVersion; String sku; boolean enabled;
        Map<String, String> options; SellingPrice sellingPrice; Map<String, Object> technicalSpecifications;
    }
    @Value public static class Image {
        UUID id; UUID productId; String url; String altText; int width; int height; int displayOrder; boolean isPrimary;
        // The wire contract is isPrimary rather than Lombok's inferred primary property.
        @com.fasterxml.jackson.annotation.JsonProperty("isPrimary") public boolean isPrimary() { return isPrimary; }
    }
    @Getter @RequiredArgsConstructor public static class ProductRow {
        private final UUID id; private final String name; private final String status; private final String version;
        private final CategoryReference category; private final Brand brand;
        private final long variantCount; private final long enabledVariantCount;
    }
    @Getter public static final class ProductDetail extends ProductRow {
        private final String description; private final Instant lastRestoredAt;
        private final List<Variant> variants; private final List<Image> images;
        private final String taxonomyVersion; private final Readiness publicationReadiness;
        public ProductDetail(ProductRow row, String description, Instant lastRestoredAt, List<Variant> variants,
                List<Image> images, String taxonomyVersion, Readiness publicationReadiness) {
            super(row.getId(), row.getName(), row.getStatus(), row.getVersion(), row.getCategory(), row.getBrand(), row.getVariantCount(), row.getEnabledVariantCount());
            this.description = description; this.lastRestoredAt = lastRestoredAt;
            this.variants = List.copyOf(variants); this.images = List.copyOf(images);
            this.taxonomyVersion = taxonomyVersion; this.publicationReadiness = publicationReadiness;
        }
    }
    @Value public static class ProductPage {
        List<ProductRow> items; int page; int size; long totalItems; int totalPages; String taxonomyVersion;
    }
    @Value public static class Page<T> { List<T> items; int page; int size; long totalItems; int totalPages; }
    @Value public static class CategoryPage { List<Category> items; int page; int size; long totalItems; int totalPages; String taxonomyVersion; }
    @Value public static class VariantList { List<Variant> items; String productVersion; String taxonomyVersion; }
    @Value public static class VariantMutation { Variant variant; String productVersion; String taxonomyVersion; }
    @Value public static class Gallery { List<Image> items; String productVersion; String taxonomyVersion; }
    @Value public static class Attachment { Image image; boolean replay; String productVersion; String taxonomyVersion; }
    @Value public static class Specifications { Map<String, Object> technicalSpecifications; String productVersion; String taxonomyVersion; }
    @Value public static class Binding { String family; String state; String reason; }
    @Value public static class CategoryDetail { Category category; String taxonomyVersion; }
    @Value public static class CategoryMutation { Category category; String taxonomyVersion; List<Binding> affectedBindings; }
    @Value public static class LegacyAttachment {
        UUID imageId;UUID productId;String altText;int width;int height;int displayOrder;boolean isPrimary;
        String productVersion;String taxonomyVersion;boolean replay;
        @com.fasterxml.jackson.annotation.JsonProperty("isPrimary") public boolean isPrimary(){return isPrimary;}
    }

}
