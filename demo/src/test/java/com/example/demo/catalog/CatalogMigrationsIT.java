package com.example.demo.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.postgresql.util.PSQLException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CatalogMigrationsIT {

	private static final String JDBC_URL = "jdbc:tc:postgresql:17-alpine:///catalog_migration_test";
	private static final String USERNAME = "test";
	private static final String PASSWORD = "test";
	private Connection containerAnchor;

	@BeforeAll
	void migrateSchema() throws SQLException {
		containerAnchor = openConnection();
		Flyway.configure().dataSource(JDBC_URL, USERNAME, PASSWORD)
				.locations("classpath:db/migration").load().migrate();
	}

	@AfterAll
	void closeContainerAnchor() throws SQLException {
		if (containerAnchor != null) {
			containerAnchor.close();
		}
	}

	@BeforeEach
	void clearCatalogTables() throws SQLException {
		try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
			statement.execute("TRUNCATE TABLE product_image_uploads, product_images, product_variants, products, categories CASCADE");
		}
	}

	@Test
	void migrations_shouldCreateThreeVersionsAndDeferredTriggers() throws SQLException {
		try (Connection connection = openConnection()) {
			assertThat(count(connection, "SELECT COUNT(*) FROM flyway_schema_history WHERE success")).isEqualTo(3);
			assertThat(count(connection, "SELECT COUNT(*) FROM pg_trigger WHERE tgname IN "
					+ "('ct_products_variant_integrity', 'ct_product_variants_integrity', "
					+ "'ct_product_images_gallery_integrity') AND tgdeferrable AND tginitdeferred"))
					.isEqualTo(3);
		}
	}

	@Test
	void schema_shouldDeclareRequiredColumnsAndForeignKeys() throws SQLException {
		Map<String, List<String>> requiredColumns = Map.of(
				"categories", List.of("id", "name"),
				"products", List.of("id", "category_id", "name", "status"),
				"product_variants", List.of("id", "product_id", "sku", "option_combination", "selling_price"),
				"product_images", List.of("id", "product_id", "object_key", "alt_text", "content_type",
						"byte_size", "width", "height", "display_order", "is_primary", "created_at"),
				"product_image_uploads", List.of("id", "product_id", "preparer_subject", "staging_key",
						"proposed_content_type", "proposed_byte_size", "alt_text", "state", "prepared_at", "expires_at"));
		try (Connection connection = openConnection();
				PreparedStatement statement = connection.prepareStatement(
						"SELECT is_nullable, data_type FROM information_schema.columns "
								+ "WHERE table_schema = current_schema() AND table_name = ? AND column_name = ?")) {
			for (Map.Entry<String, List<String>> entry : requiredColumns.entrySet()) {
				for (String column : entry.getValue()) {
					statement.setString(1, entry.getKey());
					statement.setString(2, column);
					try (ResultSet result = statement.executeQuery()) {
						assertThat(result.next()).as(entry.getKey() + "." + column + " exists").isTrue();
						assertThat(result.getString("is_nullable"))
								.as(entry.getKey() + "." + column + " is required").isEqualTo("NO");
						if (column.equals("id")) {
							assertThat(result.getString("data_type")).isEqualTo("uuid");
						}
					}
				}
			}
			assertThat(count(connection, "SELECT COUNT(*) FROM pg_constraint WHERE contype = 'f' "
					+ "AND conname IN ('fk_categories_parent_category_id', 'fk_products_category_id', "
					+ "'fk_product_variants_product_id', 'fk_product_images_product_id', "
					+ "'fk_product_image_uploads_product_id', 'fk_product_image_uploads_product_image_id')"))
					.isEqualTo(6);
		}
	}

	@Test
	void product_shouldRequireVariantAtCommit() throws SQLException {
		UUID productId;
		try (Connection connection = openConnection()) {
			connection.setAutoCommit(false);
			UUID categoryId = insertCategory(connection);
			productId = insertProduct(connection, categoryId);
			assertConstraintFailure(connection::commit, "23514", "ct_products_variant_integrity");
		}
		try (Connection connection = openConnection()) {
			assertThat(count(connection, "SELECT COUNT(*) FROM products WHERE id = '" + productId + "'"))
					.isZero();
		}

		ProductSeed seed = seedProduct(1);
		try (Connection connection = openConnection()) {
			assertThat(count(connection, "SELECT COUNT(*) FROM product_variants WHERE product_id = '"
					+ seed.productId() + "'")).isEqualTo(1);
		}
	}

	@Test
	void productAndFirstVariant_shouldCommitTogetherWithSingleEmptyOptionMap() throws SQLException {
		UUID productId;
		try (Connection connection = openConnection()) {
			connection.setAutoCommit(false);
			productId = insertProduct(connection, insertCategory(connection));
			insertVariant(connection, productId, "sku-" + UUID.randomUUID(), "{}", BigDecimal.ZERO);
			connection.commit();
		}
		try (Connection connection = openConnection()) {
			assertThat(count(connection, "SELECT COUNT(*) FROM product_variants WHERE product_id = '"
					+ productId + "' AND option_combination = '{}'::JSONB")).isEqualTo(1);
		}
	}

	@Test
	void referencesAndRequiredFields_shouldRejectInvalidValues() throws SQLException {
		ProductSeed seed = seedProduct(1);
		UUID missingId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		try (Connection connection = openConnection()) {
			assertConstraintFailure(() -> execute(connection,
					"UPDATE categories SET parent_category_id = ? WHERE id = "
							+ "(SELECT category_id FROM products WHERE id = ?)", missingId, seed.productId()),
					"23503", "fk_categories_parent_category_id");
			assertConstraintFailure(() -> execute(connection,
					"UPDATE products SET category_id = ? WHERE id = ?", missingId, seed.productId()),
					"23503", "fk_products_category_id");
			assertConstraintFailure(() -> insertVariant(connection, missingId, "sku-" + UUID.randomUUID(),
					"{}", BigDecimal.ONE), "23503", "fk_product_variants_product_id");
			assertConstraintFailure(() -> insertImage(connection, missingId, 0, true),
					"23503", "fk_product_images_product_id");
			assertConstraintFailure(() -> insertUpload(connection, missingId, null,
					"PREPARED", now, now.plusMinutes(15), null),
					"23503", "fk_product_image_uploads_product_id");
			assertConstraintFailure(() -> insertUpload(connection, seed.productId(), missingId,
					"PUBLISHED", now, now.plusMinutes(15), now.plusMinutes(1)),
					"23503", "fk_product_image_uploads_product_image_id");

			assertNotNullFailure(() -> execute(connection,
					"UPDATE products SET status = NULL WHERE id = ?", seed.productId()), "status");
			assertNotNullFailure(() -> execute(connection,
					"UPDATE products SET category_id = NULL WHERE id = ?", seed.productId()), "category_id");
			assertNotNullFailure(() -> execute(connection,
					"UPDATE product_variants SET option_combination = NULL WHERE id = ?",
					seed.variantIds().get(0)), "option_combination");
			assertNotNullFailure(() -> execute(connection,
					"INSERT INTO categories (id, name) VALUES (?, NULL)", UUID.randomUUID()), "name");
			assertNotNullFailure(() -> execute(connection,
					"INSERT INTO product_image_uploads (id, product_id, staging_key, proposed_content_type, "
							+ "proposed_byte_size, alt_text, state, prepared_at, expires_at) "
							+ "VALUES (?, ?, ?, 'image/png', 100, 'alt', 'PREPARED', ?, ?)",
					UUID.randomUUID(), seed.productId(), "staging/" + UUID.randomUUID(), now,
					now.plusMinutes(15)), "preparer_subject");
		}
	}

	@Test
	void objectKeys_shouldBeUniqueAcrossProductsAndUploads() throws SQLException {
		ProductSeed first = seedProduct(1);
		ProductSeed second = seedProduct(1);
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		try (Connection connection = openConnection()) {
			connection.setAutoCommit(false);
			UUID firstImage = insertImage(connection, first.productId(), 0, true);
			connection.commit();
			connection.setAutoCommit(true);
			assertConstraintFailure(() -> execute(connection,
					"INSERT INTO product_images (id, product_id, object_key, alt_text, content_type, "
							+ "byte_size, width, height, display_order, is_primary, created_at) "
							+ "VALUES (?, ?, ?, 'alt', 'image/png', 100, 10, 10, 0, TRUE, ?)",
					UUID.randomUUID(), second.productId(), "final/" + firstImage, now),
					"23505", "uk_product_images_object_key");

			UUID firstUpload = insertUpload(connection, first.productId(), null,
					"PREPARED", now, now.plusMinutes(15), null);
			UUID secondUpload = insertUpload(connection, second.productId(), null,
					"PREPARED", now, now.plusMinutes(15), null);
			assertConstraintFailure(() -> execute(connection,
					"UPDATE product_image_uploads SET staging_key = ? WHERE id = ?",
					"staging/" + firstUpload, secondUpload),
					"23505", "uk_product_image_uploads_staging_key");
			execute(connection, "UPDATE product_image_uploads SET durable_key = ? WHERE id = ?",
				"final/shared-" + UUID.randomUUID(), firstUpload);
			assertConstraintFailure(() -> execute(connection,
					"UPDATE product_image_uploads SET durable_key = "
							+ "(SELECT durable_key FROM product_image_uploads WHERE id = ?) WHERE id = ?",
					firstUpload, secondUpload), "23505", "uk_product_image_uploads_durable_key");
		}
	}

	@Test
	void variants_shouldRejectInvalidOptionsSkuAndPrice() throws SQLException {
		ProductSeed seed = seedProduct(1);
		ProductSeed otherProduct = seedProduct(1);
		assertVariantCommitRejected(seed.productId(), "{}");
		assertVariantCommitRejected(seed.productId(), "{\"color\":{\"shade\":\"blue\"}}");
		assertVariantCommitRejected(seed.productId(), "{\"color\":5}");
		assertVariantCommitRejected(seed.productId(), "{\" \":\"blue\"}");
		assertVariantCommitRejected(seed.productId(), "{\"color\":\" \"}");

		try (Connection connection = openConnection()) {
			assertConstraintFailure(() -> insertVariant(connection, seed.productId(),
					"   ", "{\"color\":\"other\"}", BigDecimal.ONE),
					"23514", "ck_product_variants_sku_nonblank");
			assertConstraintFailure(() -> insertVariant(connection, otherProduct.productId(),
					seed.firstSku().toUpperCase(), "{\"color\":\"blue\"}", BigDecimal.ONE),
					"23505", "uk_product_variants_sku_ci");
			assertConstraintFailure(() -> insertVariant(connection, seed.productId(),
					"other-" + UUID.randomUUID(), "{\"color\":\"variant0\"}", BigDecimal.ONE),
					"23505", "uk_product_variants_product_id_option_combination");
			assertConstraintFailure(() -> execute(connection,
					"INSERT INTO product_variants (id, product_id, sku, option_combination, selling_price) "
							+ "VALUES (?, ?, ?, '{}'::JSONB, 1.5)",
					UUID.randomUUID(), seed.productId(), "fraction-" + UUID.randomUUID()),
					"23514", "ck_product_variants_price_whole_finite");
			assertConstraintFailure(() -> execute(connection,
					"INSERT INTO product_variants (id, product_id, sku, option_combination, selling_price) "
							+ "VALUES (?, ?, ?, '{}'::JSONB, 'NaN'::NUMERIC)",
					UUID.randomUUID(), seed.productId(), "nan-" + UUID.randomUUID()),
					"23514", "ck_product_variants_price_whole_finite");
			insertVariant(connection, seed.productId(), "negative-" + UUID.randomUUID(),
					"{\"color\":\"other\"}", BigDecimal.valueOf(-1));
		}
	}

	@Test
	void coreConstraints_shouldRejectInvalidStatusNamesAndKeyOrderDuplicates() throws SQLException {
		ProductSeed seed = seedProduct(1);
		try (Connection connection = openConnection()) {
			assertThatThrownBy(() -> execute(connection,
					"UPDATE products SET status = 'HIDDEN' WHERE id = ?", seed.productId()))
					.isInstanceOf(SQLException.class);
			assertThatThrownBy(() -> execute(connection,
					"UPDATE products SET name = '   ' WHERE id = ?", seed.productId()))
					.isInstanceOf(SQLException.class);
			assertThatThrownBy(() -> execute(connection,
					"INSERT INTO categories (id, name) VALUES (?, ?)", UUID.randomUUID(), "\t"))
					.isInstanceOf(SQLException.class);
			assertThatThrownBy(() -> execute(connection,
					"UPDATE product_variants SET option_combination = '[]'::JSONB WHERE id = ?",
					seed.variantIds().get(0))).isInstanceOf(SQLException.class);
			assertThatThrownBy(() -> execute(connection,
					"UPDATE product_variants SET selling_price = 'Infinity'::NUMERIC WHERE id = ?",
					seed.variantIds().get(0))).isInstanceOf(SQLException.class);
			execute(connection,
				"UPDATE product_variants SET option_combination = '{\"color\":\"red\",\"size\":\"m\"}'::JSONB "
						+ "WHERE id = ?", seed.variantIds().get(0));
			assertThatThrownBy(() -> insertVariant(connection, seed.productId(),
					"sku-" + UUID.randomUUID(), "{\"size\":\"m\",\"color\":\"red\"}", BigDecimal.ZERO))
					.isInstanceOf(SQLException.class);
		}
	}

	@Test
	void gallery_shouldEnforcePrimaryAndMaximumAtCommit() throws SQLException {
		ProductSeed seed = seedProduct(1);
		try (Connection connection = openConnection()) {
			connection.setAutoCommit(false);
			insertImage(connection, seed.productId(), 0, false);
			assertConstraintFailure(connection::commit, "23514", "ct_product_images_gallery_integrity");
		}

		UUID firstImage;
		try (Connection connection = openConnection()) {
			connection.setAutoCommit(false);
			firstImage = insertImage(connection, seed.productId(), 0, true);
			connection.commit();
		}
		try (Connection connection = openConnection()) {
			connection.setAutoCommit(false);
			execute(connection, "UPDATE product_images SET is_primary = FALSE WHERE id = ?", firstImage);
			insertImage(connection, seed.productId(), 1, true);
			connection.commit();
		}
		try (Connection connection = openConnection()) {
			connection.setAutoCommit(false);
			for (int order = 2; order < 10; order++) {
				insertImage(connection, seed.productId(), order, false);
			}
			connection.commit();
		}
		try (Connection connection = openConnection()) {
			connection.setAutoCommit(false);
			insertImage(connection, seed.productId(), 10, false);
			assertConstraintFailure(connection::commit, "23514", "ct_product_images_gallery_integrity");
		}
		try (Connection connection = openConnection()) {
			assertThat(count(connection, "SELECT COUNT(*) FROM product_images")).isEqualTo(10);
			assertThat(count(connection, "SELECT COUNT(*) FROM product_images WHERE is_primary")).isEqualTo(1);
		}
	}

	@Test
	void upload_shouldRejectInvalidMetadataAndCrossProductImage() throws SQLException {
		ProductSeed first = seedProduct(1);
		ProductSeed second = seedProduct(1);
		UUID imageId;
		try (Connection connection = openConnection()) {
			connection.setAutoCommit(false);
			imageId = insertImage(connection, first.productId(), 0, true);
			connection.commit();
		}
		OffsetDateTime preparedAt = OffsetDateTime.now(ZoneOffset.UTC);
		try (Connection connection = openConnection()) {
			assertConstraintFailure(() -> insertUpload(connection, second.productId(), imageId,
					"PUBLISHED", preparedAt, preparedAt.plusMinutes(15), preparedAt.plusMinutes(1)),
					"23503", "fk_product_image_uploads_product_image_id");
			assertConstraintFailure(() -> insertUpload(connection, first.productId(), imageId,
					"PUBLISHED", preparedAt, preparedAt.plusMinutes(15), preparedAt.plusMinutes(15)),
					"23514", "ck_product_image_uploads_published");
		}
	}

	@Test
	void uploadLifecycle_shouldRejectInvalidStateAndIncompletePublication() throws SQLException {
		ProductSeed seed = seedProduct(1);
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		try (Connection connection = openConnection()) {
			UUID uploadId = insertUpload(connection, seed.productId(), null,
					"PREPARED", now, now.plusMinutes(15), null);
			assertConstraintFailure(() -> execute(connection,
					"UPDATE product_image_uploads SET state = 'UNKNOWN' WHERE id = ?", uploadId),
					"23514", "ck_product_image_uploads_state");
			assertConstraintFailure(() -> execute(connection,
					"UPDATE product_image_uploads SET state = 'PUBLISHED' WHERE id = ?", uploadId),
					"23514", "ck_product_image_uploads_published");
			assertConstraintFailure(() -> execute(connection,
					"UPDATE product_image_uploads SET proposed_content_type = 'image/gif' WHERE id = ?", uploadId),
					"23514", "ck_product_image_uploads_content_type");
			assertConstraintFailure(() -> execute(connection,
					"UPDATE product_image_uploads SET proposed_byte_size = 10485761 WHERE id = ?", uploadId),
					"23514", "ck_product_image_uploads_byte_size");
			assertConstraintFailure(() -> execute(connection,
					"UPDATE product_image_uploads SET staging_key = ' ' WHERE id = ?", uploadId),
					"23514", "ck_product_image_uploads_staging_key_nonblank");
			assertConstraintFailure(() -> execute(connection,
					"UPDATE product_image_uploads SET durable_key = ' ' WHERE id = ?", uploadId),
					"23514", "ck_product_image_uploads_durable_key_nonblank");
			assertConstraintFailure(() -> execute(connection,
					"UPDATE product_image_uploads SET preparer_subject = ' ' WHERE id = ?", uploadId),
					"23514", "ck_product_image_uploads_preparer_nonblank");
			assertConstraintFailure(() -> execute(connection,
					"UPDATE product_image_uploads SET alt_text = ' ' WHERE id = ?", uploadId),
					"23514", "ck_product_image_uploads_alt_text_nonblank");
			assertConstraintFailure(() -> execute(connection,
					"UPDATE product_image_uploads SET claim_expires_at = ? WHERE id = ?",
					now.plusMinutes(5), uploadId), "23514", "ck_product_image_uploads_claim_pair");
		}
		UUID imageId;
		try (Connection connection = openConnection()) {
			connection.setAutoCommit(false);
			imageId = insertImage(connection, seed.productId(), 0, true);
			connection.commit();
		}
		try (Connection connection = openConnection()) {
			UUID publishedUpload = insertUpload(connection, seed.productId(), imageId,
					"PUBLISHED", now, now.plusMinutes(15), now.plusMinutes(1));
			execute(connection, "UPDATE product_image_uploads SET durable_key = ? WHERE id = ?",
					"final/" + imageId, publishedUpload);
			assertConstraintFailure(() -> insertUpload(connection, seed.productId(), imageId,
					"PUBLISHED", now, now.plusMinutes(15), now.plusMinutes(2)),
					"23505", "uk_product_image_uploads_product_image_id");
			assertThat(count(connection, "SELECT COUNT(*) FROM product_image_uploads WHERE state = 'PUBLISHED'"))
					.isEqualTo(1);
		}
	}

	@Test
	void imageAndUploadConstraints_shouldRejectInvalidFieldsAndClaims() throws SQLException {
		ProductSeed seed = seedProduct(1);
		UUID imageId;
		try (Connection connection = openConnection()) {
			connection.setAutoCommit(false);
			imageId = insertImage(connection, seed.productId(), 0, true);
			connection.commit();
		}
		OffsetDateTime preparedAt = OffsetDateTime.now(ZoneOffset.UTC);
		try (Connection connection = openConnection()) {
			assertThatThrownBy(() -> execute(connection,
					"UPDATE product_images SET content_type = 'image/gif' WHERE id = ?", imageId))
					.isInstanceOf(SQLException.class);
			assertThatThrownBy(() -> execute(connection,
					"UPDATE product_images SET byte_size = 10485761 WHERE id = ?", imageId))
					.isInstanceOf(SQLException.class);
			assertThatThrownBy(() -> execute(connection,
					"UPDATE product_images SET width = 0 WHERE id = ?", imageId))
					.isInstanceOf(SQLException.class);
			assertThatThrownBy(() -> execute(connection,
					"UPDATE product_images SET display_order = -1 WHERE id = ?", imageId))
					.isInstanceOf(SQLException.class);
			assertThatThrownBy(() -> execute(connection,
					"UPDATE product_images SET alt_text = ' ' WHERE id = ?", imageId))
					.isInstanceOf(SQLException.class);
			assertConstraintFailure(() -> insertImage(connection, seed.productId(), 0, false),
					"23505", "uk_product_images_product_id_display_order");
			assertConstraintFailure(() -> insertImage(connection, seed.productId(), 1, true),
					"23505", "uk_product_images_product_id_primary");

			UUID uploadId = insertUpload(connection, seed.productId(), null,
					"PREPARED", preparedAt, preparedAt.plusMinutes(15), null);
			assertConstraintFailure(() -> execute(connection,
					"UPDATE product_image_uploads SET claim_token = ? WHERE id = ?",
					UUID.randomUUID(), uploadId), "23514", "ck_product_image_uploads_claim_pair");
			assertThatThrownBy(() -> execute(connection,
					"UPDATE product_image_uploads SET proposed_byte_size = 0 WHERE id = ?", uploadId))
					.isInstanceOf(SQLException.class);
			assertConstraintFailure(() -> execute(connection,
					"UPDATE product_image_uploads SET expires_at = prepared_at WHERE id = ?", uploadId),
					"23514", "ck_product_image_uploads_expiry");
		}
	}

	@Test
	void concurrentVariantDeletes_shouldKeepAtLeastOneVariant() throws Exception {
		ProductSeed seed = seedProduct(2);
		assertThat(raceCommits(
				connection -> execute(connection, "DELETE FROM product_variants WHERE id = ?", seed.variantIds().get(0)),
				connection -> execute(connection, "DELETE FROM product_variants WHERE id = ?", seed.variantIds().get(1)),
				"ct_products_variant_integrity"))
				.isEqualTo(1);
		try (Connection connection = openConnection()) {
			assertThat(count(connection, "SELECT COUNT(*) FROM product_variants")).isEqualTo(1);
		}
	}

	@Test
	void concurrentGalleryInserts_shouldKeepAtMostTenImages() throws Exception {
		ProductSeed seed = seedProduct(1);
		try (Connection connection = openConnection()) {
			connection.setAutoCommit(false);
			for (int order = 0; order < 9; order++) {
				insertImage(connection, seed.productId(), order, order == 0);
			}
			connection.commit();
		}
		assertThat(raceCommits(
				connection -> insertImage(connection, seed.productId(), 9, false),
				connection -> insertImage(connection, seed.productId(), 10, false),
				"ct_product_images_gallery_integrity"))
				.isEqualTo(1);
		try (Connection connection = openConnection()) {
			assertThat(count(connection, "SELECT COUNT(*) FROM product_images")).isEqualTo(10);
		}
	}

	private void assertVariantCommitRejected(UUID productId, String options) throws SQLException {
		try (Connection connection = openConnection()) {
			connection.setAutoCommit(false);
			insertVariant(connection, productId, "sku-" + UUID.randomUUID(), options, BigDecimal.ZERO);
			assertConstraintFailure(connection::commit, "23514", "ct_product_variants_integrity");
		}
	}

	private void assertConstraintFailure(ThrowingCallable action, String sqlState, String constraint) {
		assertThatThrownBy(action).isInstanceOfSatisfying(PSQLException.class, exception -> {
			assertThat(exception.getSQLState()).isEqualTo(sqlState);
			assertThat(exception.getServerErrorMessage()).isNotNull();
			assertThat(exception.getServerErrorMessage().getConstraint()).isEqualTo(constraint);
		});
	}

	private void assertNotNullFailure(ThrowingCallable action, String column) {
		assertThatThrownBy(action).isInstanceOfSatisfying(PSQLException.class, exception -> {
			assertThat(exception.getSQLState()).isEqualTo("23502");
			assertThat(exception.getServerErrorMessage()).isNotNull();
			assertThat(exception.getServerErrorMessage().getColumn()).isEqualTo(column);
		});
	}

	private ProductSeed seedProduct(int variantCount) throws SQLException {
		try (Connection connection = openConnection()) {
			connection.setAutoCommit(false);
			UUID productId = insertProduct(connection, insertCategory(connection));
			List<UUID> variantIds = new ArrayList<>();
			String firstSku = null;
			for (int index = 0; index < variantCount; index++) {
				String sku = "sku-" + UUID.randomUUID();
				if (firstSku == null) {
					firstSku = sku;
				}
				variantIds.add(insertVariant(connection, productId, sku,
						"{\"color\":\"variant" + index + "\"}", BigDecimal.ZERO));
			}
			connection.commit();
			return new ProductSeed(productId, variantIds, firstSku);
		}
	}

	private UUID insertCategory(Connection connection) throws SQLException {
		UUID id = UUID.randomUUID();
		execute(connection, "INSERT INTO categories (id, name) VALUES (?, ?)", id, "Category");
		return id;
	}

	private UUID insertProduct(Connection connection, UUID categoryId) throws SQLException {
		UUID id = UUID.randomUUID();
		execute(connection, "INSERT INTO products (id, category_id, name, status) VALUES (?, ?, ?, ?)",
				id, categoryId, "Product", "PUBLISHED");
		return id;
	}

	private UUID insertVariant(Connection connection, UUID productId, String sku,
			String options, BigDecimal price) throws SQLException {
		UUID id = UUID.randomUUID();
		execute(connection, "INSERT INTO product_variants "
				+ "(id, product_id, sku, option_combination, selling_price) VALUES (?, ?, ?, ?::JSONB, ?)",
				id, productId, sku, options, price);
		return id;
	}

	private UUID insertImage(Connection connection, UUID productId, int order, boolean primary)
			throws SQLException {
		UUID id = UUID.randomUUID();
		execute(connection, "INSERT INTO product_images "
				+ "(id, product_id, object_key, alt_text, content_type, byte_size, width, height, "
				+ "display_order, is_primary, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
				id, productId, "final/" + id, "Product image", "image/png", 100L,
				10, 10, order, primary, OffsetDateTime.now(ZoneOffset.UTC));
		return id;
	}

	private UUID insertUpload(Connection connection, UUID productId, UUID imageId, String state,
			OffsetDateTime preparedAt, OffsetDateTime expiresAt, OffsetDateTime publishedAt)
			throws SQLException {
		UUID id = UUID.randomUUID();
		execute(connection, "INSERT INTO product_image_uploads "
				+ "(id, product_id, preparer_subject, staging_key, proposed_content_type, "
				+ "proposed_byte_size, alt_text, state, prepared_at, expires_at, published_at, product_image_id) "
				+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
				id, productId, "admin", "staging/" + id, "image/png", 100L,
				"Product image", state, preparedAt, expiresAt, publishedAt, imageId);
		return id;
	}

	private int raceCommits(SqlWork first, SqlWork second, String expectedConstraint) throws Exception {
		CountDownLatch staged = new CountDownLatch(2);
		CountDownLatch release = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<Boolean> firstResult = executor.submit(() -> commitRacingWork(first, staged, release,
					expectedConstraint));
			Future<Boolean> secondResult = executor.submit(() -> commitRacingWork(second, staged, release,
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

	private boolean commitRacingWork(SqlWork work, CountDownLatch staged, CountDownLatch release,
			String expectedConstraint)
			throws Exception {
		try (Connection connection = openConnection()) {
			connection.setAutoCommit(false);
			try {
				work.run(connection);
			} finally {
				staged.countDown();
			}
			release.await();
			try {
				connection.commit();
				return true;
			} catch (SQLException expectedConflict) {
				if (!(expectedConflict instanceof PSQLException postgresException)
						|| !"23514".equals(postgresException.getSQLState())
						|| postgresException.getServerErrorMessage() == null
						|| !expectedConstraint.equals(postgresException.getServerErrorMessage().getConstraint())) {
					throw expectedConflict;
				}
				return false;
			}
		}
	}

	private long count(Connection connection, String sql) throws SQLException {
		try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
			result.next();
			return result.getLong(1);
		}
	}

	private void execute(Connection connection, String sql, Object... values) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			for (int index = 0; index < values.length; index++) {
				statement.setObject(index + 1, values[index]);
			}
			statement.executeUpdate();
		}
	}

	private Connection openConnection() throws SQLException {
		return DriverManager.getConnection(JDBC_URL, USERNAME, PASSWORD);
	}

	private record ProductSeed(UUID productId, List<UUID> variantIds, String firstSku) {
	}

	@FunctionalInterface
	private interface SqlWork {
		void run(Connection connection) throws SQLException;
	}
}
