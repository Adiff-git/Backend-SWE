package com.example.demo.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.catalog.CatalogFixtures.ProductFixture;
import com.example.demo.catalog.entity.Category;
import com.example.demo.catalog.entity.ProductImage;
import com.example.demo.catalog.entity.ProductStatus;
import com.example.demo.catalog.entity.ProductVariant;
import com.example.demo.catalog.storage.ProductImageStorage;
import com.example.demo.catalog.storage.ProductImageStorage.PreparedReadSigner;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(CatalogRelationshipApiIT.FakeStorageConfiguration.class)
class CatalogRelationshipApiIT {

	private static final BigDecimal EXACT_PRICE = new BigDecimal("987654321012345678901234567890");

	@Autowired private MockMvc mvc;
	@Autowired private ObjectMapper json;
	@Autowired private EntityManager entityManager;
	@Autowired private TransactionTemplate transactions;
	@Autowired private JdbcTemplate jdbc;

	@BeforeEach
	@AfterEach
	void clearCommittedData() {
		jdbc.execute("TRUNCATE TABLE product_image_uploads, product_images, product_variants, products, categories CASCADE");
	}

	@Test
	void listingAndDetailExposeOnlyOwnedImmediateCategoryVariantsAndGallery() throws Exception {
		RelatedProducts fixtures = persistRelatedProducts();
		JsonNode listing = json.readTree(mvc.perform(get("/api/products?page=0&size=20"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
		assertThat(listing.get("totalItems").asLong()).isEqualTo(2);
		JsonNode firstSummary = findById(listing.get("items"), fixtures.firstProductId());
		JsonNode secondSummary = findById(listing.get("items"), fixtures.secondProductId());
		assertThat(firstSummary.get("category").get("id").asText())
				.isEqualTo(fixtures.firstCategoryId().toString());
		assertThat(secondSummary.get("category").get("id").asText())
				.isEqualTo(fixtures.secondCategoryId().toString());
		assertThat(firstSummary.get("category").properties()).extracting(Map.Entry::getKey)
				.containsExactlyInAnyOrder("id", "name");
		assertThat(firstSummary.get("primaryImageUrl").asText()).contains("X-Amz-Expires=300");
		assertThat(secondSummary.get("primaryImageUrl").isNull()).isTrue();

		JsonNode first = json.readTree(mvc.perform(get("/api/products/{id}", fixtures.firstProductId()))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
		JsonNode second = json.readTree(mvc.perform(get("/api/products/{id}", fixtures.secondProductId()))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
		assertThat(first.get("category").get("id").asText()).isEqualTo(firstSummary.get("category").get("id").asText());
		assertThat(second.get("category").get("id").asText()).isEqualTo(secondSummary.get("category").get("id").asText());
		assertThat(first.get("variants").size()).isEqualTo(2);
		assertThat(second.get("variants").size()).isEqualTo(1);
		JsonNode expensive = findById(first.get("variants"), fixtures.expensiveVariantId());
		assertThat(expensive.get("options").get("color").asText()).isEqualTo("blue");
		assertThat(expensive.get("options").get("size").asText()).isEqualTo("L");
		assertThat(expensive.get("sellingPrice").get("amount").isIntegralNumber()).isTrue();
		assertThat(expensive.get("sellingPrice").get("amount").bigIntegerValue())
				.isEqualTo(EXACT_PRICE.toBigIntegerExact());
		assertThat(expensive.get("sellingPrice").get("currency").asText()).isEqualTo("VND");
		assertThat(second.get("variants").get(0).get("options").isEmpty()).isTrue();
		assertThat(first.get("images").size()).isEqualTo(2);
		assertThat(first.get("images").get(0).get("displayOrder").asInt()).isEqualTo(2);
		assertThat(first.get("images").get(0).get("isPrimary").asBoolean()).isTrue();
		assertThat(first.get("images").get(1).get("displayOrder").asInt()).isEqualTo(7);
		assertThat(first.get("images").get(1).get("isPrimary").asBoolean()).isFalse();
		assertThat(firstSummary.get("primaryImageUrl").asText())
				.isEqualTo(first.get("primaryImageUrl").asText())
				.isEqualTo(first.get("images").get(0).get("url").asText());
		assertThat(second.get("images").isEmpty()).isTrue();
		assertThat(second.get("primaryImageUrl").isNull()).isTrue();
		String firstBody = first.toString();
		assertThat(firstBody).doesNotContain(fixtures.secondVariantId().toString(),
				fixtures.secondCategoryId().toString(), "objectKey", "stagingKey", "claimToken",
				"contentType", "byteSize", "preparerSubject", "\"status\"");
		assertThat(second.toString()).doesNotContain(fixtures.firstImageId().toString());
	}

	@Test
	void unpublishedProductsAndTheirImagesStayAbsentFromBothReads() throws Exception {
		UUID draft = persistHiddenProduct(ProductStatus.DRAFT);
		UUID archived = persistHiddenProduct(ProductStatus.ARCHIVED);
		JsonNode listing = json.readTree(mvc.perform(get("/api/products"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
		assertThat(listing.get("items").isEmpty()).isTrue();
		for (UUID hiddenId : List.of(draft, archived)) {
			mvc.perform(get("/api/products/{id}", hiddenId)).andExpect(status().isNotFound());
		}
	}

	private JsonNode findById(JsonNode array, UUID id) {
		for (JsonNode item : array) {
			if (id.toString().equals(item.get("id").asText())) {
				return item;
			}
		}
		throw new AssertionError("Expected ID missing: " + id);
	}

	private RelatedProducts persistRelatedProducts() {
		return transactions.execute(transaction -> {
			Category parent = CatalogFixtures.category();
			entityManager.persist(parent);
			ProductFixture first = CatalogFixtures.persistProduct(entityManager, ProductStatus.PUBLISHED);
			first.category().setParentCategory(parent);
			first.variant().setOptionCombination(Map.of("color", "red", "size", "M"));
			ProductVariant expensive = CatalogFixtures.variant(first.product(),
					Map.of("size", "L", "color", "blue"), EXACT_PRICE);
			entityManager.persist(expensive);
			ProductImage later = CatalogFixtures.image(first.product(), 7, false);
			ProductImage primary = CatalogFixtures.image(first.product(), 2, true);
			entityManager.persist(later);
			entityManager.persist(primary);
			ProductFixture second = CatalogFixtures.persistProduct(entityManager, ProductStatus.PUBLISHED);
			second.variant().setOptionCombination(Map.of());
			entityManager.flush();
			return new RelatedProducts(first.product().getId(), second.product().getId(),
					first.category().getId(), second.category().getId(), expensive.getId(),
					second.variant().getId(), primary.getId());
		});
	}

	private UUID persistHiddenProduct(ProductStatus status) {
		return transactions.execute(transaction -> {
			ProductFixture fixture = CatalogFixtures.persistProduct(entityManager, status);
			entityManager.persist(CatalogFixtures.image(fixture.product(), 0, true));
			entityManager.flush();
			return fixture.product().getId();
		});
	}

	private record RelatedProducts(UUID firstProductId, UUID secondProductId,
			UUID firstCategoryId, UUID secondCategoryId, UUID expensiveVariantId,
			UUID secondVariantId, UUID firstImageId) {
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class FakeStorageConfiguration {
		@Bean
		@Primary
		ProductImageStorage relationshipReadStorage() {
			return () -> new PreparedReadSigner() {
				@Override
				public String signReadUrl(String privateObjectKey) {
					return "https://images.test/read/" + Integer.toUnsignedString(privateObjectKey.hashCode())
							+ "?X-Amz-Expires=300";
				}

				@Override
				public void close() {
				}
			};
		}
	}
}
