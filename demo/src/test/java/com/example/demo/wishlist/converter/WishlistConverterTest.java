package com.example.demo.wishlist.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.catalog.dto.response.CategorySummary;
import com.example.demo.catalog.dto.response.ProductSummary;
import com.example.demo.wishlist.dto.response.WishlistItemResponse;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class WishlistConverterTest {
    @Test
    void toItem_shouldPreservePreparedSummaryAndTimestamp_whenImageIsAbsent() {
        ProductSummary summary = new ProductSummary(UUID.randomUUID(), "Product",
                new CategorySummary(UUID.randomUUID(), "Category"), null);
        Instant addedAt = Instant.parse("2026-10-31T00:00:00Z");
        WishlistItemResponse response = new WishlistConverter().toItem(addedAt, summary);
        assertThat(response.getProduct()).isSameAs(summary);
        assertThat(response.getAddedAt()).isEqualTo(addedAt);
        JsonNode json = JsonMapper.builder().findAndAddModules().build().valueToTree(response);
        assertThat(json.propertyNames()).containsExactlyInAnyOrderElementsOf(Set.of("addedAt", "product"));
        assertThat(json.get("product").propertyNames())
                .containsExactlyInAnyOrder("id", "name", "category", "primaryImageUrl");
        assertThat(json.get("product").get("primaryImageUrl").isNull()).isTrue();
    }

    @Test
    void toPage_shouldPreserveOrderAndMetadataWithoutMutableAlias_whenItemsArePrepared() {
        WishlistConverter converter = new WishlistConverter();
        WishlistItemResponse a = converter.toItem(Instant.parse("2026-10-31T00:00:00Z"),
                new ProductSummary(UUID.randomUUID(), "A", null, null));
        WishlistItemResponse b = converter.toItem(a.getAddedAt(),
                new ProductSummary(UUID.randomUUID(), "B", null, null));
        ArrayList<WishlistItemResponse> source = new ArrayList<>(List.of(a, b));
        com.example.demo.wishlist.dto.response.WishlistPage page = converter.toPage(source, 1, 2, 5, 3);
        source.clear();
        assertThat(page.getItems()).containsExactly(a, b);
        assertThat(page.getPage()).isEqualTo(1);
        assertThat(page.getSize()).isEqualTo(2);
        assertThat(page.getTotalItems()).isEqualTo(5);
        assertThat(page.getTotalPages()).isEqualTo(3);
    }
}
