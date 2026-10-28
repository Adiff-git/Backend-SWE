package com.example.demo.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.catalog.entity.ProductImage;
import com.example.demo.catalog.entity.ProductStatus;
import com.example.demo.catalog.storage.ProductImageStorage;
import com.example.demo.catalog.storage.ProductImageStorage.PreparedReadSigner;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
@Import(ProductListingApiIT.FakeStorageConfiguration.class)
class ProductListingApiIT {

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
	void anonymousListingShowsOnlyPublishedSummariesWithSignedOrNullPrimaryUrl() throws Exception {
		UUID published = persistProduct(ProductStatus.PUBLISHED, true);
		UUID noImage = persistProduct(ProductStatus.PUBLISHED, false);
		UUID draft = persistProduct(ProductStatus.DRAFT, true);
		UUID archived = persistProduct(ProductStatus.ARCHIVED, false);
		String body = mvc.perform(get("/api/products"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalItems").value(2))
				.andExpect(jsonPath("$.items.length()").value(2))
				.andReturn().getResponse().getContentAsString();
		assertThat(body).contains(published.toString(), noImage.toString(), "category", "primaryImageUrl");
		assertThat(body).doesNotContain(draft.toString(), archived.toString(), "objectKey", "stagingKey",
				"preparerSubject", "sellingPrice", "status");
		assertThat(body).contains("https://images.test/signed?X-Amz-Expires=300");
		assertThat(body).contains("\"primaryImageUrl\":null");
		assertThat(storage.signCalls.get()).isEqualTo(1);
	}

	@Test
	void emptyOutOfRangeAndRepeatedPagesHaveAccurateStableMetadata() throws Exception {
		persistProduct(ProductStatus.DRAFT, false);
		mvc.perform(get("/api/products"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items").isEmpty())
				.andExpect(jsonPath("$.totalItems").value(0))
				.andExpect(jsonPath("$.totalPages").value(0));
		persistProduct(ProductStatus.PUBLISHED, false);
		persistProduct(ProductStatus.PUBLISHED, false);
		persistProduct(ProductStatus.PUBLISHED, false);
		String first = mvc.perform(get("/api/products?page=0&size=2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalItems").value(3))
				.andExpect(jsonPath("$.totalPages").value(2))
				.andReturn().getResponse().getContentAsString();
		String repeated = mvc.perform(get("/api/products?page=0&size=2"))
				.andReturn().getResponse().getContentAsString();
		assertThat(repeated).isEqualTo(first);
		mvc.perform(get("/api/products?page=99&size=2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items").isEmpty())
				.andExpect(jsonPath("$.totalItems").value(3));
	}

	@Test
	void unchangedMixedCatalogHasStableOrderedIdsAndCountsAcrossEveryPage() throws Exception {
		List<UUID> expectedPublishedIds = new ArrayList<>();
		for (int index = 0; index < 23; index++) {
			expectedPublishedIds.add(persistProduct(ProductStatus.PUBLISHED, false));
		}
		UUID draft = persistProduct(ProductStatus.DRAFT, false);
		UUID archived = persistProduct(ProductStatus.ARCHIVED, false);
		expectedPublishedIds.sort(Comparator.comparing(UUID::toString));

		List<UUID> firstPass = new ArrayList<>();
		List<UUID> secondPass = new ArrayList<>();
		for (int page = 0; page < 4; page++) {
			JsonNode first = requestPage(page, 7);
			JsonNode repeated = requestPage(page, 7);
			assertThat(first.get("totalItems").longValue()).isEqualTo(23);
			assertThat(repeated.get("totalItems").longValue()).isEqualTo(23);
			assertThat(first.get("totalPages").intValue()).isEqualTo(4);
			assertThat(repeated.get("totalPages").intValue()).isEqualTo(4);
			assertThat(pageIds(first)).containsExactlyElementsOf(pageIds(repeated));
			firstPass.addAll(pageIds(first));
			secondPass.addAll(pageIds(repeated));
		}

		assertThat(firstPass).containsExactlyElementsOf(expectedPublishedIds);
		assertThat(secondPass).containsExactlyElementsOf(expectedPublishedIds);
		assertThat(firstPass).doesNotContain(draft, archived);
		JsonNode beyondEnd = requestPage(4, 7);
		assertThat(beyondEnd.get("items").isEmpty()).isTrue();
		assertThat(beyondEnd.get("totalItems").longValue()).isEqualTo(23);
		assertThat(beyondEnd.get("totalPages").intValue()).isEqualTo(4);
	}

	@Test
	void twentyImageSummariesUseBoundedRelationshipQueries() throws Exception {
		for (int index = 0; index < 21; index++) {
			persistProduct(ProductStatus.PUBLISHED, true);
		}
		SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
		sessionFactory.getStatistics().clear();
		mvc.perform(get("/api/products?size=20"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(20))
				.andExpect(jsonPath("$.totalItems").value(21));
		assertThat(sessionFactory.getStatistics().getPrepareStatementCount()).isLessThanOrEqualTo(6);
		assertThat(storage.signCalls.get()).isEqualTo(20);
	}

	@Test
	void committedUnpublishBeforeFinalLockPreventsNewSignedUrl() throws Exception {
		UUID productId = persistProduct(ProductStatus.PUBLISHED, true);
		CountDownLatch preparingSigner = new CountDownLatch(1);
		CountDownLatch allowLock = new CountDownLatch(1);
		storage.pauseBeforeLock(preparingSigner, allowLock);
		try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
			Future<String> response = executor.submit(() -> mvc.perform(get("/api/products"))
					.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
			assertThat(preparingSigner.await(10, TimeUnit.SECONDS)).isTrue();
			jdbc.update("UPDATE products SET status = 'ARCHIVED' WHERE id = ?", productId);
			allowLock.countDown();
			String body = response.get(10, TimeUnit.SECONDS);
			assertThat(body).doesNotContain("https://images.test/signed", productId.toString());
			assertThat(storage.signCalls.get()).isZero();
		} finally {
			allowLock.countDown();
		}
	}

	private UUID persistProduct(ProductStatus status, boolean image) {
		return transactions.execute(transaction -> {
			CatalogFixtures.ProductFixture fixture = CatalogFixtures.persistProduct(entityManager, status);
			if (image) {
				ProductImage primary = CatalogFixtures.image(fixture.product(), 0, true);
				entityManager.persist(primary);
			}
			entityManager.flush();
			return fixture.product().getId();
		});
	}

	private JsonNode requestPage(int page, int size) throws Exception {
		String body = mvc.perform(get("/api/products")
					.param("page", Integer.toString(page))
					.param("size", Integer.toString(size)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return json.readTree(body);
	}

	private List<UUID> pageIds(JsonNode page) {
		List<UUID> ids = new ArrayList<>();
		for (JsonNode item : page.get("items")) {
			ids.add(UUID.fromString(item.get("id").asText()));
		}
		return ids;
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class FakeStorageConfiguration {
		@Bean
		@Primary
		FakeStorage fakeStorage() {
			return new FakeStorage();
		}
	}

	static class FakeStorage implements ProductImageStorage {
		private final AtomicInteger signCalls = new AtomicInteger();
		private volatile CountDownLatch preparingSigner;
		private volatile CountDownLatch allowLock;

		void reset() {
			signCalls.set(0);
			preparingSigner = null;
			allowLock = null;
		}

		void pauseBeforeLock(CountDownLatch entered, CountDownLatch proceed) {
			preparingSigner = entered;
			allowLock = proceed;
		}

		@Override
		public PreparedReadSigner prepareReadSigner() {
			CountDownLatch entered = preparingSigner;
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
					signCalls.incrementAndGet();
					return "https://images.test/signed?X-Amz-Expires=300";
				}

				@Override
				public void close() {
				}
			};
		}
	}
}
