package com.example.demo.catalog;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import org.springframework.beans.BeanUtils;
import com.example.demo.catalog.entity.Category;
import com.example.demo.catalog.entity.Product;
import com.example.demo.catalog.entity.ProductImage;
import com.example.demo.catalog.entity.ProductStatus;
import com.example.demo.catalog.entity.ProductVariant;

/** Fresh catalog test data. Callers own transactions and commit boundaries. */
public final class CatalogFixtures {

	private CatalogFixtures() {
	}

	public static Category category() {
		Category category = BeanUtils.instantiateClass(Category.class);
		category.setName("Category " + UUID.randomUUID());
		return category;
	}

	public static Category category(Category parent) {
		Category category = category();
		category.setParentCategory(parent);
		return category;
	}

	public static ProductFixture draftProduct() {
		return product(ProductStatus.DRAFT);
	}

	public static ProductFixture publishedProduct() {
		return product(ProductStatus.PUBLISHED);
	}

	public static ProductFixture archivedProduct() {
		return product(ProductStatus.ARCHIVED);
	}

	public static ProductFixture product(ProductStatus status) {
		Category category = category();
		Product product = BeanUtils.instantiateClass(Product.class);
		product.setCategory(category);
		product.setName("Product " + UUID.randomUUID());
		product.setStatus(status);
		ProductVariant variant = variant(product, Map.of("choice", UUID.randomUUID().toString()),
				BigDecimal.valueOf(1000));
		return new ProductFixture(category, product, variant);
	}

	/** Persists the parent and first variant in the caller's transaction. */
	public static ProductFixture persistProduct(EntityManager entityManager, ProductStatus status) {
		ProductFixture fixture = product(status);
		entityManager.persist(fixture.category());
		entityManager.persist(fixture.product());
		entityManager.persist(fixture.variant());
		return fixture;
	}

	public static ProductVariant variant(Product product, Map<String, String> options, BigDecimal price) {
		ProductVariant variant = BeanUtils.instantiateClass(ProductVariant.class);
		variant.setProduct(product);
		variant.setSku("SKU-" + UUID.randomUUID());
		variant.setOptionCombination(options);
		variant.setSellingPrice(price);
		return variant;
	}

	public static ProductImage image(Product product, int displayOrder, boolean primary) {
		ProductImage image = BeanUtils.instantiateClass(ProductImage.class);
		image.setProduct(product);
		image.setObjectKey("final/" + UUID.randomUUID());
		image.setAltText("Product image");
		image.setContentType("image/png");
		image.setByteSize(100);
		image.setWidth(10);
		image.setHeight(10);
		image.setDisplayOrder(displayOrder);
		image.setPrimary(primary);
		image.setCreatedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
		return image;
	}

	public record ProductFixture(Category category, Product product, ProductVariant variant) {
	}
}
