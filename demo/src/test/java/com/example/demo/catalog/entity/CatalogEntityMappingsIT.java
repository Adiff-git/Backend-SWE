package com.example.demo.catalog.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Persistence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class CatalogEntityMappingsIT {

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private TransactionTemplate transactions;

	@Autowired
	private JdbcTemplate jdbc;

	@AfterEach
	void clearCommittedCatalogData() {
		jdbc.execute("TRUNCATE TABLE product_image_uploads, product_images, product_variants, "
				+ "products, categories CASCADE");
	}

	@Test
	void entityMappings_shouldRoundTripAgainstFlywaySchema() {
		Instant preparedAt = Instant.parse("2026-10-27T04:00:00Z");
		UUID claimToken = UUID.randomUUID();
		Ids ids = transactions.execute(status -> {
			Category parent = new Category();
			parent.setName("Parent category");
			entityManager.persist(parent);

			Category category = new Category();
			category.setName("Category");
			category.setParentCategory(parent);
			entityManager.persist(category);

			Product product = new Product();
			product.setCategory(category);
			product.setName("Product");
			product.setStatus(ProductStatus.PUBLISHED);
			entityManager.persist(product);

			ProductVariant variant = new ProductVariant();
			variant.setProduct(product);
			variant.setSku("SKU-" + UUID.randomUUID());
			variant.setOptionCombination(Map.of("color", "blue", "size", "M"));
			variant.setSellingPrice(new BigDecimal("12345"));
			entityManager.persist(variant);

			ProductImage image = new ProductImage();
			image.setProduct(product);
			image.setObjectKey("final/" + UUID.randomUUID());
			image.setAltText("Front view");
			image.setContentType("image/webp");
			image.setByteSize(2048);
			image.setWidth(640);
			image.setHeight(480);
			image.setDisplayOrder(0);
			image.setPrimary(true);
			image.setCreatedAt(preparedAt);
			entityManager.persist(image);

			ProductImageUpload published = new ProductImageUpload("admin-subject");
			published.setProduct(product);
			published.setStagingKey("staging/" + UUID.randomUUID());
			published.setDurableKey(image.getObjectKey());
			published.setClaimToken(claimToken);
			published.setClaimExpiresAt(preparedAt.plus(2, ChronoUnit.MINUTES));
			published.setProposedContentType("image/webp");
			published.setProposedByteSize(2048);
			published.setAltText("Front view");
			published.setState(ProductImageUploadState.PUBLISHED);
			published.setPreparedAt(preparedAt);
			published.setExpiresAt(preparedAt.plus(15, ChronoUnit.MINUTES));
			published.setVerifiedAt(preparedAt.plus(30, ChronoUnit.SECONDS));
			published.setPublishedAt(preparedAt.plus(1, ChronoUnit.MINUTES));
			published.setProductImage(image);
			entityManager.persist(published);

			ProductImageUpload prepared = new ProductImageUpload("admin-subject");
			prepared.setProduct(product);
			prepared.setStagingKey("staging/" + UUID.randomUUID());
			prepared.setProposedContentType("image/png");
			prepared.setProposedByteSize(100);
			prepared.setAltText("Side view");
			prepared.setState(ProductImageUploadState.PREPARED);
			prepared.setPreparedAt(preparedAt);
			prepared.setExpiresAt(preparedAt.plus(15, ChronoUnit.MINUTES));
			entityManager.persist(prepared);

			entityManager.flush();
			return new Ids(category.getId(), product.getId(), variant.getId(), image.getId(),
					published.getId(), prepared.getId());
		});

		assertThat(ids).isNotNull();
		assertThat(jdbc.queryForObject("SELECT status FROM products WHERE id = ?", String.class, ids.productId()))
				.isEqualTo("PUBLISHED");
		assertThat(jdbc.queryForObject("SELECT state FROM product_image_uploads WHERE id = ?", String.class,
				ids.publishedUploadId())).isEqualTo("PUBLISHED");
		assertThat(jdbc.queryForObject("SELECT pg_typeof(option_combination)::text FROM product_variants "
				+ "WHERE id = ?", String.class, ids.variantId())).isEqualTo("jsonb");

		transactions.executeWithoutResult(status -> {
			entityManager.clear();
			Category category = entityManager.find(Category.class, ids.categoryId());
			assertThat(Persistence.getPersistenceUtil().isLoaded(category, "parentCategory")).isFalse();
			assertThat(category.getParentCategory().getName()).isEqualTo("Parent category");

			entityManager.clear();
			Product product = entityManager.find(Product.class, ids.productId());
			assertThat(Persistence.getPersistenceUtil().isLoaded(product, "category")).isFalse();
			assertThat(product.getCategory().getId()).isEqualTo(ids.categoryId());
			assertThat(product.getStatus()).isEqualTo(ProductStatus.PUBLISHED);
			assertThat(product.getDescription()).isNull();

			entityManager.clear();
			ProductVariant variant = entityManager.find(ProductVariant.class, ids.variantId());
			assertThat(Persistence.getPersistenceUtil().isLoaded(variant, "product")).isFalse();
			assertThat(variant.getProduct().getId()).isEqualTo(ids.productId());
			assertThat(variant.getOptionCombination()).containsEntry("color", "blue")
					.containsEntry("size", "M");
			assertThat(variant.getSellingPrice()).isEqualByComparingTo("12345");

			entityManager.clear();
			ProductImage image = entityManager.find(ProductImage.class, ids.imageId());
			assertThat(Persistence.getPersistenceUtil().isLoaded(image, "product")).isFalse();
			assertThat(image.getProduct().getId()).isEqualTo(ids.productId());
			assertThat(image.getCreatedAt()).isEqualTo(preparedAt);
			assertThat(image.isPrimary()).isTrue();
			assertThat(image.getObjectKey()).startsWith("final/");

			entityManager.clear();
			ProductImageUpload published = entityManager.find(ProductImageUpload.class, ids.publishedUploadId());
			assertThat(Persistence.getPersistenceUtil().isLoaded(published, "product")).isFalse();
			assertThat(published.getProduct().getId()).isEqualTo(ids.productId());
			assertThat(published.getProductImage().getId()).isEqualTo(ids.imageId());
			assertThat(published.getState()).isEqualTo(ProductImageUploadState.PUBLISHED);
			assertThat(published.getClaimToken()).isEqualTo(claimToken);
			assertThat(published.getClaimExpiresAt()).isEqualTo(preparedAt.plus(2, ChronoUnit.MINUTES));
			assertThat(published.getPublishedAt()).isEqualTo(preparedAt.plus(1, ChronoUnit.MINUTES));

			entityManager.clear();
			ProductImageUpload pending = entityManager.find(ProductImageUpload.class, ids.preparedUploadId());
			assertThat(pending.getProductImage()).isNull();
			assertThat(pending.getDurableKey()).isNull();
			assertThat(pending.getClaimToken()).isNull();
			assertThat(pending.getState()).isEqualTo(ProductImageUploadState.PREPARED);
		});
	}

	private record Ids(UUID categoryId, UUID productId, UUID variantId, UUID imageId,
			UUID publishedUploadId, UUID preparedUploadId) {
	}
}
