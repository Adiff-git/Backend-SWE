package com.example.demo.catalog.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.catalog.CatalogFixtures;
import com.example.demo.catalog.CatalogFixtures.ProductFixture;
import com.example.demo.catalog.entity.ProductImage;
import com.example.demo.catalog.entity.ProductStatus;
import com.example.demo.catalog.entity.ProductVariant;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
class ProductDetailRepositoryIT {

	@Autowired private EntityManager entityManager;
	@Autowired private TransactionTemplate transactions;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private ProductRepository products;
	@Autowired private ProductVariantRepository variants;
	@Autowired private ProductImageRepository images;

	@BeforeEach
	@AfterEach
	void clearData() {
		jdbc.execute("TRUNCATE TABLE product_image_uploads, product_images, product_variants, products, categories CASCADE");
	}

	@Test
	void publishedLookupIncludesImmediateCategoryAndOnlyOwnedVariantsAndOrderedImages() {
		UUID published = persistPublishedWithGallery();
		UUID other = persistSimple(ProductStatus.PUBLISHED);
		assertThat(products.findByIdAndStatusWithCategory(published, ProductStatus.PUBLISHED))
				.isPresent().get().extracting(product -> product.getCategory().getName())
				.asString().startsWith("Category ");
		List<ProductVariant> ownedVariants = variants.findByProduct_IdOrderByIdAsc(published);
		assertThat(ownedVariants).hasSize(2)
				.allSatisfy(variant -> assertThat(variant.getProduct().getId()).isEqualTo(published));
		assertThat(variants.findByProduct_IdOrderByIdAsc(other)).hasSize(1);
		List<ProductImage> gallery = images.findByProduct_IdOrderByDisplayOrderAsc(published);
		assertThat(gallery).extracting(ProductImage::getDisplayOrder).containsExactly(0, 3);
		assertThat(gallery).extracting(ProductImage::isPrimary).containsExactly(true, false);
	}

	@Test
	void unpublishedAndUnknownIdsShareAbsentPublishedLookup() {
		UUID draft = persistSimple(ProductStatus.DRAFT);
		UUID archived = persistSimple(ProductStatus.ARCHIVED);
		assertThat(products.findByIdAndStatusWithCategory(draft, ProductStatus.PUBLISHED)).isEmpty();
		assertThat(products.findByIdAndStatusWithCategory(archived, ProductStatus.PUBLISHED)).isEmpty();
		assertThat(products.findByIdAndStatusWithCategory(UUID.randomUUID(), ProductStatus.PUBLISHED)).isEmpty();
	}

	private UUID persistSimple(ProductStatus status) {
		return transactions.execute(transaction -> {
			ProductFixture fixture = CatalogFixtures.persistProduct(entityManager, status);
			entityManager.flush();
			return fixture.product().getId();
		});
	}

	private UUID persistPublishedWithGallery() {
		return transactions.execute(transaction -> {
			ProductFixture fixture = CatalogFixtures.persistProduct(entityManager, ProductStatus.PUBLISHED);
			ProductVariant second = CatalogFixtures.variant(fixture.product(), Map.of("color", "blue"),
					BigDecimal.valueOf(123456789));
			entityManager.persist(second);
			entityManager.persist(CatalogFixtures.image(fixture.product(), 3, false));
			entityManager.persist(CatalogFixtures.image(fixture.product(), 0, true));
			entityManager.flush();
			return fixture.product().getId();
		});
	}
}
