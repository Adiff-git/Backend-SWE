package com.example.demo.catalog.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Recorded-environment SC-003 check; local results are not a production guarantee. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
		"spring.jpa.properties.hibernate.generate_statistics=true",
		"logging.level.org.hibernate.stat=OFF"
})
@Import(CatalogPublicReadPerformanceIT.PerformanceStorageConfiguration.class)
class CatalogPublicReadPerformanceIT {

	private static final int PUBLISHED_COUNT = 1_000;
	private static final int HIDDEN_COUNT = 40;
	private static final int WARMUP_ROUNDS = 20;
	private static final int MEASURED_ROUNDS = 100;
	private static final long ONE_SECOND_NANOS = 1_000_000_000L;

	@Autowired private MockMvc mvc;
	@Autowired private ObjectMapper json;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private TransactionTemplate transactions;
	@Autowired private EntityManagerFactory entityManagerFactory;

	@BeforeEach
	@AfterEach
	void clearCommittedCatalog() {
		jdbc.execute("TRUNCATE TABLE product_image_uploads, product_images, product_variants, products, categories CASCADE");
	}

	@Test
	void publicReads_shouldMeetRecordedP95AndBoundedQueriesWithLargeCatalog() throws Exception {
		SeededCatalog catalog = seedCatalog();
		List<UUID> publishedIds = catalog.publishedIds();
		SessionFactory factory = entityManagerFactory.unwrap(SessionFactory.class);
		List<UUID> imageBearingIds = catalog.imageBearingIds().subList(0, 10);
		assertThat(imageBearingIds).hasSize(10);

		// Keep setup, JVM/Spring startup, and first-use path initialization out of latency samples.
		for (int index = 0; index < WARMUP_ROUNDS; index++) {
			requestPage(index % 3 == 0 ? 0 : index % 3 == 1 ? 24 : 49, factory);
			requestDetail(imageBearingIds.get(index % imageBearingIds.size()), factory);
		}

		long[] pageNanos = new long[MEASURED_ROUNDS];
		long[] detailNanos = new long[MEASURED_ROUNDS];
		long maxPageQueries = 0;
		long maxDetailQueries = 0;
		for (int index = 0; index < MEASURED_ROUNDS; index++) {
			Sample page = requestPage(index % 3 == 0 ? 0 : index % 3 == 1 ? 24 : 49, factory);
			Sample detail = requestDetail(imageBearingIds.get(index % imageBearingIds.size()), factory);
			pageNanos[index] = page.nanos();
			detailNanos[index] = detail.nanos();
			maxPageQueries = Math.max(maxPageQueries, page.queries());
			maxDetailQueries = Math.max(maxDetailQueries, detail.queries());
		}

		Sample hundredItems = requestPageOfHundred(factory);
		assertThat(hundredItems.queries()).isLessThanOrEqualTo(6);
		assertThat(maxPageQueries).isLessThanOrEqualTo(6);
		assertThat(maxDetailQueries).isLessThanOrEqualTo(5);
		JsonNode maximumGallery = json.readTree(requestDetail(imageBearingIds.get(0), factory).body());
		assertThat(maximumGallery.get("variants").size()).isEqualTo(2);
		assertThat(maximumGallery.get("images").size()).isEqualTo(10);
		assertThat(maximumGallery.get("images").get(0).get("isPrimary").asBoolean()).isTrue();

		String firstPage = requestPage(0, factory).body();
		String repeatedPage = requestPage(0, factory).body();
		assertThat(pageIds(firstPage)).containsExactlyElementsOf(pageIds(repeatedPage));
		assertThat(pageIds(firstPage)).containsExactlyElementsOf(publishedIds.subList(0, 20));
		assertThat(json.readTree(firstPage).get("totalItems").asInt()).isEqualTo(PUBLISHED_COUNT);

		long pageP95 = percentile95(pageNanos);
		long detailP95 = percentile95(detailNanos);
		String postgresVersion = jdbc.queryForObject("SHOW server_version", String.class);
		System.out.printf(Locale.ROOT,
				"T064_RESULT published=%d hidden=%d warmup=%d samples=%d page20_p95_ms=%.3f "
				+ "detail_p95_ms=%.3f page20_max_queries=%d detail_max_queries=%d "
				+ "page100_queries=%d java=%s postgres=%s cpus=%d%n",
				PUBLISHED_COUNT, HIDDEN_COUNT, WARMUP_ROUNDS, MEASURED_ROUNDS,
				pageP95 / 1_000_000.0, detailP95 / 1_000_000.0,
				maxPageQueries, maxDetailQueries, hundredItems.queries(),
				System.getProperty("java.version"), postgresVersion,
				Runtime.getRuntime().availableProcessors());
		assertThat(pageP95).isLessThan(ONE_SECOND_NANOS);
		assertThat(detailP95).isLessThan(ONE_SECOND_NANOS);
	}

	private SeededCatalog seedCatalog() {
		UUID categoryId = UUID.randomUUID();
		List<UUID> published = new ArrayList<>();
		List<UUID> imageBearing = new ArrayList<>();
		List<Object[]> products = new ArrayList<>();
		List<Object[]> variants = new ArrayList<>();
		List<Object[]> images = new ArrayList<>();
		for (int index = 0; index < PUBLISHED_COUNT + HIDDEN_COUNT; index++) {
			UUID productId = UUID.randomUUID();
			String status = index < PUBLISHED_COUNT ? "PUBLISHED" : index % 2 == 0 ? "DRAFT" : "ARCHIVED";
			if (index < PUBLISHED_COUNT) {
				published.add(productId);
			}
			products.add(new Object[] {productId, categoryId, "Performance product " + index, status});
			for (int variant = 0; variant < 2; variant++) {
				variants.add(new Object[] {UUID.randomUUID(), productId,
						"PERF-" + productId + "-" + variant,
						"{\"color\":\"" + (variant == 0 ? "red" : "blue") + "\"}",
						BigDecimal.valueOf(100_000L + variant)});
			}
			if (index < PUBLISHED_COUNT && index % 4 == 0) {
				imageBearing.add(productId);
				int imageCount = index == 0 ? 10 : 2;
				for (int order = 0; order < imageCount; order++) {
					images.add(new Object[] {UUID.randomUUID(), productId,
							"final/performance/" + productId + "/" + order,
							"Product image " + order, "image/png", 1024L,
							640, 480, order, order == 0});
				}
			}
		}
		transactions.executeWithoutResult(status -> {
			jdbc.update("INSERT INTO categories(id, name) VALUES (?, ?)", categoryId, "Performance category");
			jdbc.batchUpdate("INSERT INTO products(id, category_id, name, status) VALUES (?, ?, ?, ?)", products);
			jdbc.batchUpdate("INSERT INTO product_variants(id, product_id, sku, option_combination, selling_price) "
					+ "VALUES (?, ?, ?, ?::jsonb, ?)", variants);
			jdbc.batchUpdate("INSERT INTO product_images(id, product_id, object_key, alt_text, content_type, "
					+ "byte_size, width, height, display_order, is_primary, created_at) "
					+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, clock_timestamp())", images);
		});
		// PostgreSQL UUID ordering follows unsigned bytes; canonical text has the same order.
		published.sort(Comparator.comparing(UUID::toString));
		return new SeededCatalog(List.copyOf(published), List.copyOf(imageBearing));
	}

	private Sample requestPage(int page, SessionFactory factory) throws Exception {
		factory.getStatistics().clear();
		long started = System.nanoTime();
		MvcResult result = mvc.perform(get("/api/products").param("page", Integer.toString(page))
				.param("size", "20")).andReturn();
		long nanos = System.nanoTime() - started;
		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		return new Sample(nanos, factory.getStatistics().getPrepareStatementCount(),
				result.getResponse().getContentAsString());
	}

	private Sample requestPageOfHundred(SessionFactory factory) throws Exception {
		factory.getStatistics().clear();
		long started = System.nanoTime();
		MvcResult result = mvc.perform(get("/api/products").param("page", "0")
				.param("size", "100")).andReturn();
		long nanos = System.nanoTime() - started;
		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		assertThat(json.readTree(result.getResponse().getContentAsString()).get("items").size()).isEqualTo(100);
		return new Sample(nanos, factory.getStatistics().getPrepareStatementCount(),
				result.getResponse().getContentAsString());
	}

	private Sample requestDetail(UUID id, SessionFactory factory) throws Exception {
		factory.getStatistics().clear();
		long started = System.nanoTime();
		MvcResult result = mvc.perform(get("/api/products/{id}", id)).andReturn();
		long nanos = System.nanoTime() - started;
		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		return new Sample(nanos, factory.getStatistics().getPrepareStatementCount(),
				result.getResponse().getContentAsString());
	}

	private List<UUID> pageIds(String body) {
		List<UUID> ids = new ArrayList<>();
		JsonNode items = json.readTree(body).get("items");
		for (JsonNode item : items) {
			ids.add(UUID.fromString(item.get("id").asText()));
		}
		return ids;
	}

	private long percentile95(long[] samples) {
		long[] ordered = samples.clone();
		Arrays.sort(ordered);
		return ordered[(int) Math.ceil(0.95 * ordered.length) - 1];
	}

	private record Sample(long nanos, long queries, String body) {
	}

	private record SeededCatalog(List<UUID> publishedIds, List<UUID> imageBearingIds) {
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class PerformanceStorageConfiguration {
		@Bean
		@Primary
		ProductImageStorage performanceImageStorage() {
			return new S3ProductImageStorage("catalog-images", "us-east-1", "http://localhost:4566",
					StaticCredentialsProvider.create(AwsBasicCredentials.create("performance-test", "performance-test")));
		}
	}
}
