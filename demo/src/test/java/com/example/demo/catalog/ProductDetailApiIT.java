package com.example.demo.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.catalog.CatalogFixtures.ProductFixture;
import com.example.demo.catalog.entity.ProductImage;
import com.example.demo.catalog.entity.ProductStatus;
import com.example.demo.catalog.entity.ProductVariant;
import com.example.demo.catalog.storage.ProductImageStorage;
import com.example.demo.catalog.storage.ProductImageStorage.PreparedReadSigner;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.hibernate.SessionFactory;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Import(ProductDetailApiIT.FakeStorageConfiguration.class)
class ProductDetailApiIT {

	private static final BigDecimal LARGE_WHOLE_VND = new BigDecimal("123456789012345678901234567890");

	@Autowired private MockMvc mvc;
	@Autowired private ObjectMapper json;
	@Autowired private EntityManager entityManager;
	@Autowired private EntityManagerFactory entityManagerFactory;
	@Autowired private TransactionTemplate transactions;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private FakeStorage storage;

	@BeforeEach
	void setup() {
		clearCommittedData();
		storage.reset();
	}

	@AfterEach
	void cleanup() {
		storage.reset();
		clearCommittedData();
	}

	private void clearCommittedData() {
		jdbc.execute("TRUNCATE TABLE product_image_uploads, product_images, product_variants, products, categories CASCADE");
	}

	@Test
	void publishedDetailContainsOnlyOwnCategoryVariantsAndOrderedSignedImages() throws Exception {
		DetailFixture fixture = persistDetailedProduct();
		UUID other = persistProduct(ProductStatus.PUBLISHED);
		UUID otherVariant = jdbc.queryForObject(
				"SELECT id FROM product_variants WHERE product_id = ?", UUID.class, other);
		SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
		sessionFactory.getStatistics().clear();
		String body = mvc.perform(get("/api/products/{id}", fixture.productId()))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.id").value(fixture.productId().toString()))
				.andExpect(jsonPath("$.category.id").value(fixture.categoryId().toString()))
				.andExpect(jsonPath("$.description").value("Detailed description"))
				.andExpect(jsonPath("$.variants.length()").value(2))
				.andExpect(jsonPath("$.images.length()").value(2))
				.andExpect(jsonPath("$.images[0].displayOrder").value(2))
				.andExpect(jsonPath("$.images[0].isPrimary").value(true))
				.andExpect(jsonPath("$.images[1].displayOrder").value(7))
				.andExpect(jsonPath("$.images[1].isPrimary").value(false))
				.andReturn().getResponse().getContentAsString();
		JsonNode detail = json.readTree(body);
		assertThat(detail.get("primaryImageUrl").asText()).isEqualTo(detail.get("images").get(0).get("url").asText());
		assertThat(detail.get("images").get(1).get("url").asText()).isNotEqualTo(detail.get("primaryImageUrl").asText());
		assertThat(detail.get("images").get(0).get("url").asText()).contains("X-Amz-Expires=300");
		assertThat(body).contains(fixture.firstVariantId().toString(), fixture.secondVariantId().toString());
		assertThat(body).doesNotContain(other.toString(), otherVariant.toString(), "objectKey", "stagingKey", "claimToken",
				"contentType", "byteSize", "createdAt", "\"status\"", "weight");
		JsonNode expensiveVariant = null;
		for (JsonNode variant : detail.get("variants")) {
			assertThat(variant.get("sellingPrice").get("currency").asText()).isEqualTo("VND");
			assertThat(variant.get("options").isObject()).isTrue();
			if (fixture.secondVariantId().toString().equals(variant.get("id").asText())) {
				expensiveVariant = variant;
			}
		}
		assertThat(expensiveVariant).isNotNull();
		assertThat(expensiveVariant.get("options").get("color").asText()).isEqualTo("blue");
		assertThat(expensiveVariant.get("sellingPrice").get("amount").isIntegralNumber()).isTrue();
		assertThat(expensiveVariant.get("sellingPrice").get("amount").bigIntegerValue())
				.isEqualTo(LARGE_WHOLE_VND.toBigIntegerExact());
		assertThat(storage.signCalls.get()).isEqualTo(2);
		assertThat(sessionFactory.getStatistics().getPrepareStatementCount()).isLessThanOrEqualTo(5);
	}

	@Test
	void productWithoutImagesHasNullPrimaryUrlAndEmptyGallery() throws Exception {
		UUID id = persistProduct(ProductStatus.PUBLISHED);
		mvc.perform(get("/api/products/{id}", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.primaryImageUrl").value(org.hamcrest.Matchers.nullValue()))
				.andExpect(jsonPath("$.images").isEmpty())
				.andExpect(jsonPath("$.variants.length()").value(1));
		assertThat(storage.signCalls.get()).isZero();
	}

	@Test
	void draftArchivedAndUnknownProductsAreIndistinguishable404Problems() throws Exception {
		UUID draft = persistProduct(ProductStatus.DRAFT);
		UUID archived = persistProduct(ProductStatus.ARCHIVED);
		String expected = mvc.perform(get("/api/products/{id}", UUID.randomUUID()))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andReturn().getResponse().getContentAsString();
		for (UUID id : List.of(draft, archived)) {
			String hidden = mvc.perform(get("/api/products/{id}", id))
					.andExpect(status().isNotFound())
					.andReturn().getResponse().getContentAsString();
			assertThat(json.readTree(hidden).get("detail").asText())
					.isEqualTo(json.readTree(expected).get("detail").asText());
		}
	}

	@Test
	void committedUnpublishBeforeFinalLockPreventsGallerySigning() throws Exception {
		DetailFixture fixture = persistDetailedProduct();
		CountDownLatch signerPreparation = new CountDownLatch(1);
		CountDownLatch allowLock = new CountDownLatch(1);
		storage.pauseBeforeLock(signerPreparation, allowLock);
		try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
			Future<Integer> result = executor.submit(() -> mvc.perform(get("/api/products/{id}", fixture.productId()))
					.andReturn().getResponse().getStatus());
			assertThat(signerPreparation.await(10, TimeUnit.SECONDS)).isTrue();
			jdbc.update("UPDATE products SET status = 'ARCHIVED' WHERE id = ?", fixture.productId());
			allowLock.countDown();
			assertThat(result.get(10, TimeUnit.SECONDS)).isEqualTo(404);
			assertThat(storage.signCalls.get()).isZero();
		} finally {
			allowLock.countDown();
		}
	}

	private UUID persistProduct(ProductStatus status) {
		return transactions.execute(transaction -> {
			ProductFixture fixture = CatalogFixtures.persistProduct(entityManager, status);
			entityManager.flush();
			return fixture.product().getId();
		});
	}

	private DetailFixture persistDetailedProduct() {
		return transactions.execute(transaction -> {
			ProductFixture fixture = CatalogFixtures.persistProduct(entityManager, ProductStatus.PUBLISHED);
			fixture.product().setDescription("Detailed description");
			fixture.variant().setOptionCombination(Map.of("color", "red"));
			fixture.variant().setSellingPrice(BigDecimal.valueOf(1000));
			ProductVariant second = CatalogFixtures.variant(fixture.product(), Map.of("color", "blue"), LARGE_WHOLE_VND);
			entityManager.persist(second);
			ProductImage later = CatalogFixtures.image(fixture.product(), 7, false);
			ProductImage primary = CatalogFixtures.image(fixture.product(), 2, true);
			entityManager.persist(later);
			entityManager.persist(primary);
			entityManager.flush();
			return new DetailFixture(fixture.product().getId(), fixture.category().getId(),
					fixture.variant().getId(), second.getId());
		});
	}

	private record DetailFixture(UUID productId, UUID categoryId, UUID firstVariantId, UUID secondVariantId) {
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class FakeStorageConfiguration {
		@Bean
		@Primary
		FakeStorage fakeDetailStorage() {
			return new FakeStorage();
		}
	}

	static class FakeStorage implements ProductImageStorage {
		private final AtomicInteger signCalls = new AtomicInteger();
		private volatile CountDownLatch signerPreparation;
		private volatile CountDownLatch allowLock;

		void reset() {
			signCalls.set(0);
			signerPreparation = null;
			allowLock = null;
		}

		void pauseBeforeLock(CountDownLatch entered, CountDownLatch proceed) {
			signerPreparation = entered;
			allowLock = proceed;
		}

		@Override
		public PreparedReadSigner prepareReadSigner() {
			CountDownLatch entered = signerPreparation;
			CountDownLatch proceed = allowLock;
			if (entered != null) {
				entered.countDown();
				try {
					if (!proceed.await(10, TimeUnit.SECONDS)) {
						throw new IllegalStateException("Timed out waiting for status update");
					}
				} catch (InterruptedException exception) {
					Thread.currentThread().interrupt();
					throw new IllegalStateException(exception);
				}
			}
			return new PreparedReadSigner() {
				@Override
				public String signReadUrl(String privateObjectKey) {
					int ordinal = signCalls.incrementAndGet();
					return "https://images.test/read/" + ordinal + "?X-Amz-Expires=300";
				}

				@Override
				public void close() {
				}
			};
		}
	}
}
