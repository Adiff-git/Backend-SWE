package com.example.demo.catalog.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import com.example.demo.catalog.CatalogFixtures;
import com.example.demo.catalog.CatalogFixtures.ProductFixture;
import com.example.demo.catalog.entity.Product;
import com.example.demo.catalog.entity.ProductImage;
import com.example.demo.catalog.entity.ProductImageUpload;
import com.example.demo.catalog.entity.ProductStatus;
import com.example.demo.catalog.entity.ProductVariant;

@DataJpaTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CatalogConstraintsIT {

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private TransactionTemplate transactions;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ProductRepository products;

	@Autowired
	private CategoryRepository categories;

	@Autowired
	private ProductVariantRepository variants;

	@Autowired
	private ProductImageRepository images;

	@Autowired
	private ProductImageUploadRepository uploads;

	@BeforeEach
	void clearBeforeTest() {
		clearCommittedData();
	}

	@AfterEach
	void clearAfterTest() {
		clearCommittedData();
	}

	private void clearCommittedData() {
		jdbc.execute("TRUNCATE TABLE product_image_uploads, product_images, product_variants, "
				+ "products, categories CASCADE");
	}

	@Test
	void repositories_shouldFindOwnedAndBatchedCatalogRows() {
		UUID firstId = persistProduct(ProductStatus.PUBLISHED);
		UUID secondId = persistProduct(ProductStatus.DRAFT);
		UUID firstImageId = transactions.execute(status -> {
			Product first = products.findById(firstId).orElseThrow();
			ProductImage image = CatalogFixtures.image(first, 0, true);
			entityManager.persist(image);
			ProductImageUpload upload = CatalogFixtures.upload(first);
			entityManager.persist(upload);
			entityManager.flush();
			return image.getId();
		});

		transactions.executeWithoutResult(status -> {
			assertThat(products.findByIdAndStatus(firstId, ProductStatus.PUBLISHED)).isPresent();
			assertThat(products.findByIdAndStatus(secondId, ProductStatus.PUBLISHED)).isEmpty();
			assertThat(products.findAllByIdsAndStatus(List.of(firstId, secondId), ProductStatus.PUBLISHED))
					.extracting(Product::getId).containsExactly(firstId);
			assertThat(categories.findAllById(List.of(products.findById(firstId).orElseThrow()
					.getCategory().getId()))).hasSize(1);
			assertThat(variants.findAllByProductIds(List.of(firstId, secondId))).hasSize(2);
			assertThat(variants.findByProduct_IdOrderByIdAsc(firstId)).hasSize(1);
			assertThat(images.findAllByProductIds(List.of(firstId, secondId)))
					.extracting(ProductImage::getId).containsExactly(firstImageId);
			assertThat(images.findByProduct_IdAndPrimaryTrue(firstId)).isPresent();
			assertThat(images.countByProduct_Id(firstId)).isEqualTo(1);
			UUID uploadId = uploads.findAll().getFirst().getId();
			assertThat(uploads.findByIdAndProduct_Id(uploadId, firstId)).isPresent();
			assertThat(uploads.findByIdAndProduct_Id(uploadId, secondId)).isEmpty();
			assertThat(products.lockStatusForShare(firstId)).contains("PUBLISHED");
			assertThat(products.lockIdForNoKeyUpdate(firstId)).contains(firstId);
			assertThat(uploads.lockByIdAndProductId(uploadId, firstId)).isPresent();
		});
	}

	@Test
	void database_shouldEnforceUuidReferencesRequiredFieldsAndStatus() {
		UUID productId = persistProduct(ProductStatus.PUBLISHED);
		assertThat(productId).isNotNull();
		assertConstraint(() -> jdbc.update("UPDATE products SET category_id = ? WHERE id = ?",
				UUID.randomUUID(), productId), "23503", "fk_products_category_id");
		assertNotNull(() -> jdbc.update("UPDATE products SET status = NULL WHERE id = ?", productId),
				"status");
		assertConstraint(() -> jdbc.update("UPDATE products SET status = 'HIDDEN' WHERE id = ?", productId),
				"23514", "ck_products_status");
		assertNotNull(() -> jdbc.update("UPDATE product_variants SET selling_price = NULL "
				+ "WHERE product_id = ?", productId), "selling_price");
	}

	@Test
	void database_shouldEnforceSkuOptionsAndWholePrice() {
		UUID firstId = persistProduct(ProductStatus.PUBLISHED);
		UUID secondId = persistProduct(ProductStatus.DRAFT);
		String firstSku = jdbc.queryForObject("SELECT sku FROM product_variants WHERE product_id = ?",
				String.class, firstId);
		assertConstraint(() -> insertVariant(secondId, firstSku.toUpperCase(), "{\"color\":\"blue\"}",
				"1"), "23505", "uk_product_variants_sku_ci");
		insertVariant(firstId, "sku-" + UUID.randomUUID(), "{\"color\":\"blue\",\"size\":\"M\"}",
				"1000");
		assertConstraint(() -> insertVariant(firstId, "sku-" + UUID.randomUUID(),
				"{\"size\":\"M\",\"color\":\"blue\"}", "1000"),
				"23505", "uk_product_variants_product_id_option_combination");
		assertConstraint(() -> insertVariant(firstId, "sku-" + UUID.randomUUID(),
				"{\"color\":\"red\"}", "1.5"),
				"23514", "ck_product_variants_price_whole_finite");
		assertConstraint(() -> insertVariant(firstId, "sku-" + UUID.randomUUID(),
				"{\"color\":\"green\"}", "NaN"),
				"23514", "ck_product_variants_price_whole_finite");
		assertConstraint(() -> transactions.executeWithoutResult(status -> {
			insertVariant(firstId, "sku-" + UUID.randomUUID(), "{\"color\":{\"nested\":\"x\"}}", "1");
		}), "23514", "ct_product_variants_integrity");
		UUID variantId = variants.findByProduct_IdOrderByIdAsc(firstId).getFirst().getId();
		assertConstraint(() -> transactions.executeWithoutResult(status ->
				jdbc.update("UPDATE product_variants SET option_combination = '{}'::jsonb WHERE id = ?",
						variantId)), "23514", "ct_product_variants_integrity");
	}

	@Test
	void database_shouldCheckVariantMinimumAtRealCommit() {
		assertConstraint(() -> transactions.executeWithoutResult(status -> {
			ProductFixture fixture = CatalogFixtures.publishedProduct();
			entityManager.persist(fixture.category());
			entityManager.persist(fixture.product());
		}), "23514", "ct_products_variant_integrity");
		UUID productId = persistProduct(ProductStatus.PUBLISHED);
		assertConstraint(() -> transactions.executeWithoutResult(status -> {
			variants.deleteAll(variants.findByProduct_IdOrderByIdAsc(productId));
			entityManager.flush();
		}), "23514", "ct_products_variant_integrity");
		assertThat(variants.findByProduct_IdOrderByIdAsc(productId)).hasSize(1);
	}

	@Test
	void database_shouldEnforceImageGalleryAndUploadPublicationTime() {
		UUID productId = persistProduct(ProductStatus.PUBLISHED);
		UUID firstImageId = transactions.execute(status -> {
			Product product = products.findById(productId).orElseThrow();
			ProductImage image = CatalogFixtures.image(product, 0, true);
			entityManager.persist(image);
			entityManager.flush();
			return image.getId();
		});
		assertConstraint(() -> transactions.executeWithoutResult(status -> {
			Product product = products.findById(productId).orElseThrow();
			entityManager.persist(CatalogFixtures.image(product, 0, false));
			entityManager.flush();
		}), "23505", "uk_product_images_product_id_display_order");
		assertConstraint(() -> transactions.executeWithoutResult(status -> {
			Product product = products.findById(productId).orElseThrow();
			entityManager.persist(CatalogFixtures.image(product, 1, true));
			entityManager.flush();
		}), "23505", "uk_product_images_product_id_primary");
		assertConstraint(() -> transactions.executeWithoutResult(status -> {
			ProductImage first = images.findById(firstImageId).orElseThrow();
			first.setPrimary(false);
		}), "23514", "ct_product_images_gallery_integrity");

		transactions.executeWithoutResult(status -> {
			Product product = products.findById(productId).orElseThrow();
			for (int order = 1; order < 10; order++) {
				entityManager.persist(CatalogFixtures.image(product, order, false));
			}
		});
		UUID anotherImageId = images.findByProduct_IdOrderByDisplayOrderAsc(productId).get(1).getId();
		String firstKey = images.findById(firstImageId).orElseThrow().getObjectKey();
		assertConstraint(() -> jdbc.update("UPDATE product_images SET object_key = ? WHERE id = ?",
				firstKey, anotherImageId), "23505", "uk_product_images_object_key");
		assertConstraint(() -> transactions.executeWithoutResult(status -> {
			Product product = products.findById(productId).orElseThrow();
			entityManager.persist(CatalogFixtures.image(product, 10, false));
		}), "23514", "ct_product_images_gallery_integrity");
		assertThat(images.countByProduct_Id(productId)).isEqualTo(10);

		UUID uploadId = transactions.execute(status -> {
			Product product = products.findById(productId).orElseThrow();
			ProductImageUpload upload = CatalogFixtures.upload(product);
			entityManager.persist(upload);
			entityManager.flush();
			return upload.getId();
		});
		assertConstraint(() -> jdbc.update("UPDATE product_image_uploads SET state = 'PUBLISHED', "
				+ "product_image_id = ?, published_at = expires_at WHERE id = ?", firstImageId, uploadId),
				"23514", "ck_product_image_uploads_published");
	}

	@Test
	void concurrentVariantDeletes_shouldRejectOnlyExpectedIntegrityConflict() throws Exception {
		UUID productId = persistProduct(ProductStatus.PUBLISHED);
		transactions.executeWithoutResult(status -> {
			Product product = products.findById(productId).orElseThrow();
			ProductVariant second = CatalogFixtures.variant(product, Map.of("color", "blue"),
					BigDecimal.TEN);
			entityManager.persist(second);
		});
		List<UUID> variantIds = variants.findByProduct_IdOrderByIdAsc(productId).stream()
				.map(ProductVariant::getId).toList();
		assertThat(raceCommits(
				() -> variants.deleteById(variantIds.get(0)),
				() -> variants.deleteById(variantIds.get(1)),
				"ct_products_variant_integrity")).isEqualTo(1);
		assertThat(variants.findByProduct_IdOrderByIdAsc(productId)).hasSize(1);
	}

	@Test
	void concurrentGalleryInserts_shouldRejectOnlyExpectedIntegrityConflict() throws Exception {
		UUID productId = persistProduct(ProductStatus.PUBLISHED);
		transactions.executeWithoutResult(status -> {
			Product product = products.findById(productId).orElseThrow();
			for (int order = 0; order < 9; order++) {
				entityManager.persist(CatalogFixtures.image(product, order, order == 0));
			}
		});
		assertThat(raceCommits(
				() -> entityManager.persist(CatalogFixtures.image(products.findById(productId).orElseThrow(),
						9, false)),
				() -> entityManager.persist(CatalogFixtures.image(products.findById(productId).orElseThrow(),
						10, false)),
				"ct_product_images_gallery_integrity")).isEqualTo(1);
		assertThat(images.countByProduct_Id(productId)).isEqualTo(10);
	}

	@Test
	void productShareLock_shouldBlockConcurrentStatusUpdate() throws Exception {
		UUID productId = persistProduct(ProductStatus.PUBLISHED);
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<?> holder = executor.submit(() -> transactions.executeWithoutResult(status -> {
				assertThat(products.lockStatusForShare(productId)).contains("PUBLISHED");
				locked.countDown();
				try {
					if (!release.await(15, TimeUnit.SECONDS)) {
						throw new AssertionError("Share lock was not released");
					}
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
					throw new AssertionError("Share lock holder interrupted", interrupted);
				}
			}));
			assertThat(locked.await(15, TimeUnit.SECONDS)).isTrue();
			Future<?> writer = executor.submit(() -> transactions.executeWithoutResult(status -> {
				jdbc.execute("SET LOCAL lock_timeout = '200ms'");
				jdbc.update("UPDATE products SET status = 'ARCHIVED' WHERE id = ?", productId);
			}));
			assertThatThrownBy(() -> writer.get(10, TimeUnit.SECONDS)).satisfies(error ->
					assertThat(postgresError(error).getSQLState()).isEqualTo("55P03"));
			release.countDown();
			holder.get(10, TimeUnit.SECONDS);
		} finally {
			release.countDown();
			executor.shutdownNow();
		}
		assertThat(products.findById(productId).orElseThrow().getStatus())
				.isEqualTo(ProductStatus.PUBLISHED);
	}

	private UUID persistProduct(ProductStatus status) {
		return transactions.execute(transaction -> {
			ProductFixture fixture = CatalogFixtures.persistProduct(entityManager, status);
			entityManager.flush();
			return fixture.product().getId();
		});
	}

	private void insertVariant(UUID productId, String sku, String options, String price) {
		jdbc.update("INSERT INTO product_variants (id, product_id, sku, option_combination, selling_price) "
				+ "VALUES (?, ?, ?, ?::jsonb, ?::numeric)", UUID.randomUUID(), productId, sku, options, price);
	}

	private void assertConstraint(Runnable work, String state, String constraint) {
		assertThatThrownBy(work::run).satisfies(error -> {
			PSQLException sqlError = postgresError(error);
			assertThat(sqlError.getSQLState()).isEqualTo(state);
			assertThat(sqlError.getServerErrorMessage()).isNotNull();
			assertThat(sqlError.getServerErrorMessage().getConstraint()).isEqualTo(constraint);
		});
	}

	private void assertNotNull(Runnable work, String column) {
		assertThatThrownBy(work::run).satisfies(error -> {
			PSQLException sqlError = postgresError(error);
			assertThat(sqlError.getSQLState()).isEqualTo("23502");
			assertThat(sqlError.getServerErrorMessage()).isNotNull();
			assertThat(sqlError.getServerErrorMessage().getColumn()).isEqualTo(column);
		});
	}

	private PSQLException postgresError(Throwable error) {
		for (Throwable cause = error; cause != null; cause = cause.getCause()) {
			if (cause instanceof PSQLException postgres) {
				return postgres;
			}
			if (cause instanceof SQLException sql && sql.getNextException() instanceof PSQLException postgres) {
				return postgres;
			}
		}
		throw new AssertionError("Expected a PostgreSQL error, got " + error, error);
	}

	private int raceCommits(Runnable first, Runnable second, String expectedConstraint) throws Exception {
		CountDownLatch staged = new CountDownLatch(2);
		CountDownLatch release = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<Boolean> firstResult = executor.submit(() -> commitRacing(first, staged, release,
					expectedConstraint));
			Future<Boolean> secondResult = executor.submit(() -> commitRacing(second, staged, release,
					expectedConstraint));
			assertThat(staged.await(15, TimeUnit.SECONDS)).isTrue();
			release.countDown();
			return (firstResult.get(30, TimeUnit.SECONDS) ? 1 : 0)
					+ (secondResult.get(30, TimeUnit.SECONDS) ? 1 : 0);
		} finally {
			release.countDown();
			executor.shutdownNow();
		}
	}

	private boolean commitRacing(Runnable change, CountDownLatch staged, CountDownLatch release,
			String expectedConstraint) throws Exception {
		try {
			transactions.executeWithoutResult(status -> {
				change.run();
				entityManager.flush();
				staged.countDown();
				try {
					if (!release.await(15, TimeUnit.SECONDS)) {
						throw new AssertionError("Concurrent work was not released");
					}
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
					throw new AssertionError("Concurrent work interrupted", interrupted);
				}
			});
			return true;
		} catch (RuntimeException conflict) {
			PSQLException sqlError = postgresError(conflict);
			assertThat(sqlError.getSQLState()).isEqualTo("23514");
			assertThat(sqlError.getServerErrorMessage()).isNotNull();
			assertThat(sqlError.getServerErrorMessage().getConstraint()).isEqualTo(expectedConstraint);
			return false;
		}
	}
}
