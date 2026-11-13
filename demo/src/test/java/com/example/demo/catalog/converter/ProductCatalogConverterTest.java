package com.example.demo.catalog.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo.catalog.CatalogFixtures;
import com.example.demo.catalog.CatalogFixtures.ProductFixture;
import com.example.demo.catalog.dto.response.ProductDetail;
import com.example.demo.catalog.dto.response.ProductSummary;
import com.example.demo.catalog.dto.response.ProductListingItem;
import com.example.demo.catalog.dto.response.ProductPage;
import com.example.demo.catalog.entity.Category;
import com.example.demo.catalog.entity.Brand;
import com.example.demo.catalog.entity.ProductImage;
import com.example.demo.catalog.entity.ProductVariant;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ProductCatalogConverterTest {

	private final ProductCatalogConverter converter = new ProductCatalogConverter();
	private final JsonMapper json = JsonMapper.builder().build();

	@Test
	void summaryContainsOnlyApprovedFieldsAndImmediateCategory() throws Exception {
		ProductFixture fixture = CatalogFixtures.publishedProduct();
		Category parent = CatalogFixtures.category();
		ReflectionTestUtils.setField(parent, "id", UUID.randomUUID());
		fixture.category().setParentCategory(parent);
		UUID productId = UUID.randomUUID();
		UUID categoryId = UUID.randomUUID();
		ReflectionTestUtils.setField(fixture.product(), "id", productId);
		ReflectionTestUtils.setField(fixture.category(), "id", categoryId);
		ProductSummary summary = converter.toSummary(fixture.product(), "https://images.test/approved");
		JsonNode body = json.readTree(json.writeValueAsString(summary));
		assertThat(body.properties()).extracting(Map.Entry::getKey)
				.containsExactlyInAnyOrder("id", "name", "category", "primaryImageUrl");
		assertThat(body.get("id").asText()).isEqualTo(productId.toString());
		assertThat(body.get("category").get("id").asText()).isEqualTo(categoryId.toString());
		assertThat(body.get("category").properties()).extracting(Map.Entry::getKey)
				.containsExactlyInAnyOrder("id", "name");
		assertThat(body.toString()).doesNotContain(parent.getId().toString(), "status", "variants",
				"objectKey", "stagingKey", "sellingPrice");
	}

	@Test
	void detailMapsEachVariantAndImageOnceUsingOnlySuppliedUrls() throws Exception {
		ProductFixture fixture = CatalogFixtures.publishedProduct();
		ReflectionTestUtils.setField(fixture.product(), "id", UUID.randomUUID());
		ReflectionTestUtils.setField(fixture.category(), "id", UUID.randomUUID());
		ProductVariant first = fixture.variant();
		Brand brand = new Brand("test-brand", "Test Brand");
		ReflectionTestUtils.setField(brand, "id", UUID.randomUUID());
		fixture.product().setBrand(brand);
		Map<String, Object> specifications = Map.of("schema_version", 1, "values", Map.of(
				"nfc_supported", false, "count", 0, "cpu_model", "10110U",
				"physical_ram", Map.of("value", new BigDecimal("8.5"), "unit", "GB")));
		first.setTechnicalSpecifications(specifications);
		first.setOptionCombination(Map.of("color", "red", "size", "M"));
		first.setSellingPrice(new BigDecimal("123456789012345678901234567890"));
		ReflectionTestUtils.setField(first, "id", UUID.randomUUID());
		ProductVariant second = CatalogFixtures.variant(fixture.product(), Map.of("color", "blue"), BigDecimal.ZERO);
		ReflectionTestUtils.setField(second, "id", UUID.randomUUID());
		ProductImage primary = CatalogFixtures.image(fixture.product(), 1, true);
		ProductImage later = CatalogFixtures.image(fixture.product(), 4, false);
		ReflectionTestUtils.setField(primary, "id", UUID.randomUUID());
		ReflectionTestUtils.setField(later, "id", UUID.randomUUID());
		String primaryUrl = "https://images.test/approved-primary";
		String laterUrl = "https://images.test/approved-later";
		ProductDetail detail = converter.toDetail(fixture.product(), List.of(first, second),
				List.of(primary, later), Map.of(primary.getId(), primaryUrl, later.getId(), laterUrl));
		assertThat(detail.getBrand().getId()).isEqualTo(brand.getId());
		assertThat(detail.getBrand().getCode()).isEqualTo("test-brand");
		assertThat(detail.getBrand().getName()).isEqualTo("Test Brand");
		assertThat(json.readTree(json.writeValueAsString(detail.getVariants().get(0).getTechnicalSpecifications())))
				.isEqualTo(json.readTree(json.writeValueAsString(specifications)));
		assertThat(detail.getVariants().get(1).getTechnicalSpecifications())
				.isEqualTo(Map.of("schema_version", 1, "values", Map.of()));
		assertThat(detail.getVariants()).extracting(variant -> variant.getId())
				.containsExactly(first.getId(), second.getId());
		assertThat(detail.getVariants().get(0).getOptions())
				.containsExactlyInAnyOrderEntriesOf(Map.of("color", "red", "size", "M"));
		assertThat(detail.getVariants().get(0).getSellingPrice().getAmount())
				.isEqualTo(new BigInteger("123456789012345678901234567890"));
		assertThat(detail.getVariants().get(1).getSellingPrice().getAmount()).isEqualTo(BigInteger.ZERO);
		assertThat(detail.getVariants()).allSatisfy(variant ->
				assertThat(variant.getSellingPrice().getCurrency()).isEqualTo("VND"));
		assertThat(detail.getImages()).extracting(image -> image.getId())
				.containsExactly(primary.getId(), later.getId());
		assertThat(detail.getImages()).extracting(image -> image.getUrl())
				.containsExactly(primaryUrl, laterUrl);
		assertThat(detail.getPrimaryImageUrl()).isEqualTo(primaryUrl);
		String body = json.writeValueAsString(detail);
		assertThat(body).doesNotContain(primary.getObjectKey(), later.getObjectKey(), "objectKey",
				"stagingKey", "byteSize", "contentType", "createdAt", "status", "weight");
	}

	@Test
	void singleVariantEmptyOptionsAndNoImagesRemainRepresentable() {
		ProductFixture fixture = CatalogFixtures.publishedProduct();
		fixture.variant().setOptionCombination(Map.of());
		ProductDetail detail = converter.toDetail(fixture.product(), List.of(fixture.variant()),
				List.of(), Map.of());
		assertThat(detail.getVariants()).hasSize(1);
		assertThat(detail.getVariants().get(0).getOptions()).isEmpty();
		assertThat(detail.getImages()).isEmpty();
		assertThat(detail.getPrimaryImageUrl()).isNull();
		assertThat(detail.getBrand()).isNull();
		assertThat(detail.getVariants().get(0).getTechnicalSpecifications())
				.isEqualTo(Map.of("schema_version", 1, "values", Map.of()));
	}
    @Test
    void listingMapsOnlySelectedPairAndKeepsSharedSummaryUnchanged() {
        ProductSummary summary = converter.toSummary(CatalogFixtures.publishedProduct().product(), "signed-url");
        UUID selected = UUID.randomUUID();
        BigInteger amount = new BigInteger("1234567890123456789012345678901234567890");
        ProductListingItem item = converter.toListingItem(summary, amount, selected);
        assertThat(item.getDisplayPrice().getAmount()).isEqualTo(amount);
        assertThat(item.getDisplayPrice().getCurrency()).isEqualTo("VND");
        assertThat(item.getRepresentativeVariantId()).isEqualTo(selected);
        assertThat(item.getName()).isEqualTo(summary.getName());
        assertThat(item.getCategory()).isSameAs(summary.getCategory());
        assertThat(item.getPrimaryImageUrl()).isEqualTo(summary.getPrimaryImageUrl());
        JsonNode body = json.valueToTree(item);
        assertThat(body.properties()).extracting(Map.Entry::getKey).containsExactlyInAnyOrder(
                "id", "name", "category", "primaryImageUrl", "displayPrice", "representativeVariantId");
        assertThat(json.valueToTree(summary).has("displayPrice")).isFalse();
        ProductPage page = new ProductPage(
                List.of(item), 2, 20, 41, 3);
        JsonNode envelope = json.valueToTree(page);
        assertThat(envelope.properties()).extracting(Map.Entry::getKey)
                .containsExactlyInAnyOrder("items", "page", "size", "totalItems", "totalPages");
        assertThat(envelope.get("totalItems").asLong()).isEqualTo(41);
        assertThat(envelope.get("items").get(0).get("representativeVariantId").asString()).isEqualTo(selected.toString());
    }

    @Test
    void orphanListingHasExplicitNullPairAndMixedPairsAreRejected() {
        ProductSummary summary = converter.toSummary(CatalogFixtures.publishedProduct().product(), null);
        JsonNode item = json.valueToTree(converter.toListingItem(summary, null, null));
        assertThat(item.has("displayPrice")).isTrue();
        assertThat(item.get("displayPrice").isNull()).isTrue();
        assertThat(item.has("representativeVariantId")).isTrue();
        assertThat(item.get("representativeVariantId").isNull()).isTrue();
        assertThatThrownBy(() -> converter.toListingItem(summary, BigInteger.ZERO, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> converter.toListingItem(summary, null, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }

}
