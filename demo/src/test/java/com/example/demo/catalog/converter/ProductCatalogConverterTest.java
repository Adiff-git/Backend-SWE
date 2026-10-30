package com.example.demo.catalog.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.catalog.CatalogFixtures;
import com.example.demo.catalog.CatalogFixtures.ProductFixture;
import com.example.demo.catalog.dto.response.ProductDetail;
import com.example.demo.catalog.dto.response.ProductSummary;
import com.example.demo.catalog.entity.Category;
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
	}
}
