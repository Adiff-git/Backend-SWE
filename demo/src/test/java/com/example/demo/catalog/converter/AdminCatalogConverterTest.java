package com.example.demo.catalog.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.catalog.CatalogFixtures;
import com.example.demo.catalog.dto.response.AdminCatalogResponses;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class AdminCatalogConverterTest {
    private final AdminCatalogConverter converter = new AdminCatalogConverter();
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void mapping_shouldKeepRevisionStringsAndPrivateKeysOutOfAdminImages() {
        var fixture = CatalogFixtures.draftProduct();
        UUID category = UUID.randomUUID();
        var row = converter.productRow(fixture.product(), new AdminCatalogResponses.CategoryReference(category, "Category", "0"), null, 1, 1);
        assertThat(json.readTree(json.writeValueAsString(row)).get("version").isString()).isTrue();
        var image = converter.image(CatalogFixtures.image(fixture.product(), 0, true), UUID.randomUUID(), "https://example.test/signed");
        var wire = json.readTree(json.writeValueAsString(image));
        assertThat(wire.get("isPrimary").asBoolean()).isTrue();
        assertThat(wire.propertyNames()).doesNotContain("objectKey", "primary", "sourceImageId");
        var variant = converter.variant(fixture.variant(), UUID.randomUUID(), Long.MAX_VALUE);
        assertThat(variant.getProductVersion()).isEqualTo(Long.toString(Long.MAX_VALUE));
        assertThat(variant.getSellingPrice().getAmount()).isEqualTo(fixture.variant().getSellingPrice().toBigIntegerExact());
    }
}
