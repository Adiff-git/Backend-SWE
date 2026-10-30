package com.example.demo.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.catalog.CatalogFixtures.ProductFixture;
import com.example.demo.catalog.entity.ProductStatus;
import com.example.demo.catalog.entity.ProductVariant;
import com.example.demo.catalog.storage.ProductImageStorage;
import com.example.demo.catalog.storage.ProductImageStorage.PreparedReadSigner;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Public GET slice of the shared four-route OpenAPI contract gate in T063. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(CatalogContractIT.ContractStorageConfiguration.class)
class CatalogContractIT {

	private static final Pattern CANONICAL_UUID = Pattern.compile(
			"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
	private static final Set<String> PAGE_FIELDS = Set.of("items", "page", "size", "totalItems", "totalPages");
	private static final Set<String> SUMMARY_FIELDS = Set.of("id", "name", "category", "primaryImageUrl");
	private static final Set<String> DETAIL_FIELDS = Set.of(
			"id", "name", "category", "primaryImageUrl", "description", "variants", "images");
	private static final Set<String> CATEGORY_FIELDS = Set.of("id", "name");
	private static final Set<String> VARIANT_FIELDS = Set.of("id", "sku", "options", "sellingPrice");
	private static final Set<String> PRICE_FIELDS = Set.of("amount", "currency");
	private static final Set<String> IMAGE_FIELDS = Set.of(
			"id", "url", "altText", "width", "height", "displayOrder", "isPrimary");
	private static final Set<String> SENSITIVE_FIELDS = Set.of(
			"objectKey", "stagingKey", "durableObjectKey", "claimToken", "claimExpiresAt",
			"preparerSubject", "proposedContentType", "proposedByteSize", "credentials");

	@Autowired private MockMvc mvc;
	@Autowired private ObjectMapper json;
	@Autowired private EntityManager entityManager;
	@Autowired private TransactionTemplate transactions;
	@Autowired private JdbcTemplate jdbc;

	@BeforeEach
	void clearBefore() {
		clearCommittedData();
	}

	@AfterEach
	void clearAfter() {
		clearCommittedData();
	}

	@Test
	void listProducts_shouldMatchOpenApiContract_forAnonymousPublishedPage() throws Exception {
		ContractFixture publishedWithImage = persistDetailedProduct(ProductStatus.PUBLISHED, true);
		UUID publishedWithoutImage = persistBasicProduct(ProductStatus.PUBLISHED);
		UUID draft = persistBasicProduct(ProductStatus.DRAFT);
		UUID archived = persistBasicProduct(ProductStatus.ARCHIVED);

		JsonNode response = performJson("/api/products?page=0&size=20", 200, MediaType.APPLICATION_JSON);

		assertExactFields(response, PAGE_FIELDS);
		assertThat(response.get("items").isArray()).isTrue();
		assertThat(response.get("items").size()).isEqualTo(2);
		assertThat(response.get("page").intValue()).isZero();
		assertThat(response.get("size").intValue()).isEqualTo(20);
		assertThat(response.get("totalItems").isIntegralNumber()).isTrue();
		assertThat(response.get("totalItems").longValue()).isEqualTo(2);
		assertThat(response.get("totalPages").intValue()).isEqualTo(1);

		Set<String> returnedIds = new HashSet<>();
		for (JsonNode item : response.get("items")) {
			assertExactFields(item, SUMMARY_FIELDS);
			assertCanonicalUuid(item.get("id"));
			assertThat(item.get("name").asText()).isNotBlank();
			assertCategory(item.get("category"));
			assertNoSensitiveFields(item);
			returnedIds.add(item.get("id").asText());
		}
		assertThat(returnedIds).containsExactlyInAnyOrder(
				publishedWithImage.productId().toString(), publishedWithoutImage.toString());
		assertThat(returnedIds).doesNotContain(draft.toString(), archived.toString());

		JsonNode withImage = findById(response.get("items"), publishedWithImage.productId());
		assertSignedUrl(withImage.get("primaryImageUrl"));
		JsonNode withoutImage = findById(response.get("items"), publishedWithoutImage);
		assertThat(withoutImage.get("primaryImageUrl").isNull()).isTrue();
	}

	@Test
	void listProducts_shouldEnforceOpenApiPaginationAndApprovedParameters() throws Exception {
		persistBasicProduct(ProductStatus.PUBLISHED);
		persistBasicProduct(ProductStatus.PUBLISHED);

		JsonNode defaults = performJson("/api/products", 200, MediaType.APPLICATION_JSON);
		assertThat(defaults.get("page").intValue()).isZero();
		assertThat(defaults.get("size").intValue()).isEqualTo(20);

		JsonNode boundary = performJson("/api/products?page=1&size=1", 200, MediaType.APPLICATION_JSON);
		assertThat(boundary.get("items").size()).isEqualTo(1);
		assertThat(boundary.get("page").intValue()).isEqualTo(1);
		assertThat(boundary.get("size").intValue()).isEqualTo(1);
		assertThat(boundary.get("totalItems").longValue()).isEqualTo(2);
		assertThat(boundary.get("totalPages").intValue()).isEqualTo(2);

		for (String query : List.of("page=-1", "page=x", "size=0", "size=101", "size=x",
				"page=0&page=1", "size=1&size=2", "sort=name", "filter=status")) {
			assertProblem(performJson("/api/products?" + query, 400, MediaType.APPLICATION_PROBLEM_JSON), 400);
		}
	}

	@Test
	void getProduct_shouldMatchOpenApiContract_forPublishedProduct() throws Exception {
		ContractFixture fixture = persistDetailedProduct(ProductStatus.PUBLISHED, true);

		JsonNode response = performJson("/api/products/" + fixture.productId(), 200, MediaType.APPLICATION_JSON);

		assertExactFields(response, DETAIL_FIELDS);
		assertCanonicalUuid(response.get("id"));
		assertThat(response.get("id").asText()).isEqualTo(fixture.productId().toString());
		assertThat(response.get("name").asText()).isNotBlank();
		assertThat(response.get("description").asText()).isEqualTo("Contract product description");
		assertCategory(response.get("category"));
		assertSignedUrl(response.get("primaryImageUrl"));

		assertThat(response.get("variants").size()).isEqualTo(2);
		for (JsonNode variant : response.get("variants")) {
			assertExactFields(variant, VARIANT_FIELDS);
			assertCanonicalUuid(variant.get("id"));
			assertThat(variant.get("sku").asText()).isNotBlank();
			assertThat(variant.get("options").isObject()).isTrue();
			assertExactFields(variant.get("sellingPrice"), PRICE_FIELDS);
			assertThat(variant.get("sellingPrice").get("amount").isIntegralNumber()).isTrue();
			assertThat(variant.get("sellingPrice").get("currency").asText()).isEqualTo("VND");
		}
		JsonNode expensive = findById(response.get("variants"), fixture.expensiveVariantId());
		assertThat(expensive.get("options").get("color").asText()).isEqualTo("blue");
		assertThat(expensive.get("sellingPrice").get("amount").bigIntegerValue())
				.isEqualTo(new BigDecimal("123456789012345678901234567890").toBigIntegerExact());

		assertThat(response.get("images").size()).isEqualTo(2);
		assertThat(response.get("images").get(0).get("displayOrder").intValue()).isEqualTo(2);
		assertThat(response.get("images").get(0).get("isPrimary").booleanValue()).isTrue();
		assertThat(response.get("images").get(1).get("displayOrder").intValue()).isEqualTo(7);
		assertThat(response.get("images").get(1).get("isPrimary").booleanValue()).isFalse();
		for (JsonNode image : response.get("images")) {
			assertExactFields(image, IMAGE_FIELDS);
			assertCanonicalUuid(image.get("id"));
			assertSignedUrl(image.get("url"));
			assertThat(image.get("altText").asText()).isNotBlank();
			assertThat(image.get("width").intValue()).isPositive();
			assertThat(image.get("height").intValue()).isPositive();
		}
		assertThat(response.get("primaryImageUrl").asText())
				.isEqualTo(response.get("images").get(0).get("url").asText());
		assertNoSensitiveFields(response);
	}

	@Test
	void getProduct_shouldReturnNullAndEmptyOptionalRepresentations_withoutImages() throws Exception {
		UUID productId = persistBasicProduct(ProductStatus.PUBLISHED);

		JsonNode response = performJson("/api/products/" + productId, 200, MediaType.APPLICATION_JSON);

		assertExactFields(response, DETAIL_FIELDS);
		assertThat(response.get("description").isNull()).isTrue();
		assertThat(response.get("primaryImageUrl").isNull()).isTrue();
		assertThat(response.get("images").isArray()).isTrue();
		assertThat(response.get("images").isEmpty()).isTrue();
		assertThat(response.get("variants").size()).isEqualTo(1);
	}

	@Test
	void getProduct_shouldReturnRfc9457Problems_forInvalidHiddenAndMissingIds() throws Exception {
		UUID draft = persistBasicProduct(ProductStatus.DRAFT);
		UUID archived = persistBasicProduct(ProductStatus.ARCHIVED);

		for (String invalid : List.of("not-a-uuid", "123E4567-E89B-12D3-A456-426614174000",
				"123e4567e89b12d3a456426614174000")) {
			assertProblem(performJson("/api/products/" + invalid, 400,
					MediaType.APPLICATION_PROBLEM_JSON), 400);
		}
		for (UUID hiddenOrMissing : List.of(draft, archived, UUID.randomUUID())) {
			assertProblem(performJson("/api/products/" + hiddenOrMissing, 404,
					MediaType.APPLICATION_PROBLEM_JSON), 404);
		}
	}

	private JsonNode performJson(String path, int expectedStatus, MediaType mediaType) throws Exception {
		String body = mvc.perform(get(path))
				.andExpect(status().is(expectedStatus))
				.andExpect(content().contentTypeCompatibleWith(mediaType))
				.andReturn().getResponse().getContentAsString();
		return json.readTree(body);
	}

	private void assertCategory(JsonNode category) {
		assertExactFields(category, CATEGORY_FIELDS);
		assertCanonicalUuid(category.get("id"));
		assertThat(category.get("name").asText()).isNotBlank();
	}

	private void assertProblem(JsonNode problem, int expectedStatus) {
		assertThat(fieldNames(problem)).contains("type", "title", "status", "detail");
		assertThat(problem.get("type").isTextual()).isTrue();
		assertThat(problem.get("title").isTextual()).isTrue();
		assertThat(problem.get("status").intValue()).isEqualTo(expectedStatus);
		assertThat(problem.get("detail").asText()).isNotBlank();
		assertNoSensitiveFields(problem);
	}

	private void assertCanonicalUuid(JsonNode value) {
		assertThat(value.isTextual()).isTrue();
		assertThat(value.asText()).matches(CANONICAL_UUID);
	}

	private void assertSignedUrl(JsonNode value) {
		assertThat(value.isTextual()).isTrue();
		URI uri = URI.create(value.asText());
		assertThat(uri.isAbsolute()).isTrue();
		assertThat(uri.getScheme()).isEqualTo("https");
		assertThat(uri.getQuery()).contains("X-Amz-Expires=300");
	}

	private void assertNoSensitiveFields(JsonNode node) {
		for (String field : SENSITIVE_FIELDS) {
			assertThat(node.findValues(field)).isEmpty();
		}
		assertThat(node.toString()).doesNotContain("secret", "credential", "staging/", "final/");
	}

	private void assertExactFields(JsonNode node, Set<String> expected) {
		assertThat(node.isObject()).isTrue();
		assertThat(fieldNames(node)).containsExactlyInAnyOrderElementsOf(expected);
	}

	private Set<String> fieldNames(JsonNode node) {
		Set<String> names = new HashSet<>();
		node.properties().forEach(entry -> names.add(entry.getKey()));
		return names;
	}

	private JsonNode findById(JsonNode values, UUID id) {
		for (JsonNode value : values) {
			if (id.toString().equals(value.get("id").asText())) {
				return value;
			}
		}
		throw new AssertionError("Missing id " + id);
	}

	private UUID persistBasicProduct(ProductStatus status) {
		return transactions.execute(transaction -> {
			ProductFixture fixture = CatalogFixtures.persistProduct(entityManager, status);
			entityManager.flush();
			return fixture.product().getId();
		});
	}

	private ContractFixture persistDetailedProduct(ProductStatus status, boolean images) {
		return transactions.execute(transaction -> {
			ProductFixture fixture = CatalogFixtures.persistProduct(entityManager, status);
			fixture.product().setDescription("Contract product description");
			fixture.variant().setOptionCombination(Map.of("color", "red"));
			fixture.variant().setSellingPrice(BigDecimal.valueOf(1000));
			ProductVariant expensive = CatalogFixtures.variant(fixture.product(), Map.of("color", "blue"),
					new BigDecimal("123456789012345678901234567890"));
			entityManager.persist(expensive);
			if (images) {
				entityManager.persist(CatalogFixtures.image(fixture.product(), 7, false));
				entityManager.persist(CatalogFixtures.image(fixture.product(), 2, true));
			}
			entityManager.flush();
			return new ContractFixture(fixture.product().getId(), expensive.getId());
		});
	}

	private void clearCommittedData() {
		jdbc.execute("TRUNCATE TABLE product_image_uploads, product_images, product_variants, products, categories CASCADE");
	}

	private record ContractFixture(UUID productId, UUID expensiveVariantId) {
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class ContractStorageConfiguration {
		@Bean
		@Primary
		ProductImageStorage contractImageStorage() {
			return () -> new PreparedReadSigner() {
				@Override
				public String signReadUrl(String privateObjectKey) {
					return "https://images.test/catalog/" + UUID.randomUUID() + "?X-Amz-Expires=300";
				}

				@Override
				public void close() {
				}
			};
		}
	}
}
