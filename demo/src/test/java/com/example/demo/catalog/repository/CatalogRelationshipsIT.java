package com.example.demo.catalog.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.UUID;
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

@DataJpaTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CatalogRelationshipsIT {

	@Autowired private JdbcTemplate jdbc;
	@Autowired private TransactionTemplate transactions;

	@BeforeEach
	@AfterEach
	void clearCommittedData() {
		jdbc.execute("TRUNCATE TABLE product_image_uploads, product_images, product_variants, products, categories CASCADE");
	}

	@Test
	void singleVariantEmptyOptionsCommitsSuccessfully() {
		UUID category = insertCategory();
		UUID product = UUID.randomUUID();
		transactions.executeWithoutResult(status -> {
			insertProduct(product, category);
			insertVariant(product, "{}", "SKU-" + UUID.randomUUID());
		}); // Actual PostgreSQL commit runs deferred relationship triggers.
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_variants WHERE product_id = ?",
				Long.class, product)).isEqualTo(1L);
	}

	@Test
	void jsonbKeyOrderCannotCreateDuplicateOptionCombination() {
		UUID category = insertCategory();
		UUID product = UUID.randomUUID();
		transactions.executeWithoutResult(status -> {
			insertProduct(product, category);
			insertVariant(product, "{\"color\":\"blue\",\"size\":\"L\"}", "SKU-" + UUID.randomUUID());
		});
		assertSqlState("23505", () -> transactions.executeWithoutResult(status ->
				insertVariant(product, "{\"size\":\"L\",\"color\":\"blue\"}",
						"SKU-" + UUID.randomUUID())));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_variants WHERE product_id = ?",
				Long.class, product)).isEqualTo(1L);
	}

	@Test
	void distinctNonemptyOptionMapsCommitForMultiVariantProduct() {
		UUID category = insertCategory();
		UUID product = UUID.randomUUID();
		transactions.executeWithoutResult(status -> {
			insertProduct(product, category);
			insertVariant(product, "{\"color\":\"red\"}", "SKU-" + UUID.randomUUID());
			insertVariant(product, "{\"color\":\"blue\"}", "SKU-" + UUID.randomUUID());
		});
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_variants WHERE product_id = ?",
				Long.class, product)).isEqualTo(2L);
	}

	@Test
	void parentWithoutVariantFailsAtRealCommitAndInvalidForeignKeysFail() {
		UUID category = insertCategory();
		UUID product = UUID.randomUUID();
		assertSqlState("23514", () -> transactions.executeWithoutResult(status ->
				insertProduct(product, category))); // Trigger fires at commit, not at INSERT.
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE id = ?", Long.class, product)).isZero();
		assertSqlState("23503", () -> transactions.executeWithoutResult(status ->
				insertProduct(UUID.randomUUID(), UUID.randomUUID())));
		assertSqlState("23503", () -> transactions.executeWithoutResult(status ->
				insertVariant(UUID.randomUUID(), "{}", "SKU-" + UUID.randomUUID())));
	}

	private UUID insertCategory() {
		UUID id = UUID.randomUUID();
		jdbc.update("INSERT INTO categories (id, name) VALUES (?, ?)", id, "Category " + id);
		return id;
	}

	private void insertProduct(UUID id, UUID categoryId) {
		jdbc.update("INSERT INTO products (id, category_id, name, status) VALUES (?, ?, ?, 'PUBLISHED')",
				id, categoryId, "Product " + id);
	}

	private void insertVariant(UUID productId, String options, String sku) {
		jdbc.update("INSERT INTO product_variants (id, product_id, sku, option_combination, selling_price) "
				+ "VALUES (?, ?, ?, CAST(? AS JSONB), ?)", UUID.randomUUID(), productId, sku,
				options, BigDecimal.valueOf(1000));
	}

	private void assertSqlState(String expected, Runnable action) {
		assertThatThrownBy(action::run).satisfies(failure -> {
			SQLException sql = findSqlException(failure);
			assertThat((Object) sql).as("Expected a PostgreSQL constraint error").isNotNull();
			assertThat(sql.getSQLState()).isEqualTo(expected);
			assertThat((Object) sql).isInstanceOf(PSQLException.class);
		});
	}

	private SQLException findSqlException(Throwable failure) {
		Throwable current = failure;
		while (current != null) {
			if (current instanceof SQLException sql) {
				return sql;
			}
			current = current.getCause();
		}
		return null;
	}
}
