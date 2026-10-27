package com.example.demo.catalog.validator;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.example.demo.catalog.CatalogFixtures;
import com.example.demo.catalog.CatalogFixtures.ProductFixture;
import com.example.demo.catalog.entity.ProductVariant;

class CatalogDataValidatorTest {

	private final CatalogDataValidator validator = new CatalogDataValidator();

	@Test
	void validateProduct_shouldRejectMissingCategoryOrStatus() {
		ProductFixture fixture = CatalogFixtures.draftProduct();
		fixture.product().setCategory(null);
		assertInvalid(fixture, List.of(fixture.variant()), "category");
		fixture.product().setCategory(fixture.category());
		fixture.product().setStatus(null);
		assertInvalid(fixture, List.of(fixture.variant()), "status");
	}

	@Test
	void validateProduct_shouldRejectZeroVariants() {
		ProductFixture fixture = CatalogFixtures.publishedProduct();
		assertInvalid(fixture, List.of(), "at least one variant");
	}

	@Test
	void validateProduct_shouldRejectBlankSkuAndMissingPrice() {
		ProductFixture fixture = CatalogFixtures.publishedProduct();
		fixture.variant().setSku("  ");
		assertInvalid(fixture, List.of(fixture.variant()), "SKU");
		fixture.variant().setSku("SKU-valid");
		fixture.variant().setSellingPrice(null);
		assertInvalid(fixture, List.of(fixture.variant()), "selling price");
	}

	@Test
	void validateProduct_shouldRejectInvalidOptionKeysAndValues() {
		ProductFixture fixture = CatalogFixtures.publishedProduct();
		fixture.variant().setOptionCombination(Map.of(" ", "blue"));
		assertInvalid(fixture, List.of(fixture.variant()), "Option names and values");
		fixture.variant().setOptionCombination(Map.of("color", " "));
		assertInvalid(fixture, List.of(fixture.variant()), "Option names and values");
		fixture.variant().setOptionCombination(null);
		assertInvalid(fixture, List.of(fixture.variant()), "option map");
	}

	@Test
	void validateProduct_shouldRejectDuplicateOptionsIndependentOfKeyOrder() {
		ProductFixture fixture = CatalogFixtures.publishedProduct();
		fixture.variant().setOptionCombination(Map.of("color", "blue", "size", "M"));
		ProductVariant duplicate = CatalogFixtures.variant(fixture.product(),
				Map.of("size", "M", "color", "blue"), BigDecimal.TEN);
		assertInvalid(fixture, List.of(fixture.variant(), duplicate), "distinct");
	}

	@Test
	void validateProduct_shouldRejectEmptyOptionsWhenMultipleVariantsExist() {
		ProductFixture fixture = CatalogFixtures.publishedProduct();
		fixture.variant().setOptionCombination(Map.of());
		ProductVariant other = CatalogFixtures.variant(fixture.product(),
				Map.of("color", "blue"), BigDecimal.TEN);
		assertInvalid(fixture, List.of(fixture.variant(), other), "nonempty options");
	}

	@Test
	void validateProduct_shouldRejectFractionalPrice() {
		ProductFixture fixture = CatalogFixtures.publishedProduct();
		fixture.variant().setSellingPrice(new BigDecimal("1000.50"));
		assertInvalid(fixture, List.of(fixture.variant()), "whole VND");
	}

	@Test
	void validateProduct_shouldAcceptSingleEmptyMapAndExactWholePrices() {
		ProductFixture fixture = CatalogFixtures.publishedProduct();
		fixture.variant().setOptionCombination(Map.of());
		fixture.variant().setSellingPrice(new BigDecimal("1000.00"));
		assertThatCode(() -> validator.validateProduct(fixture.product(), List.of(fixture.variant())))
				.doesNotThrowAnyException();
		fixture.variant().setSellingPrice(BigDecimal.ZERO);
		assertThatCode(() -> validator.validateProduct(fixture.product(), List.of(fixture.variant())))
				.doesNotThrowAnyException();
	}

	private void assertInvalid(ProductFixture fixture, List<ProductVariant> variants, String message) {
		assertThatThrownBy(() -> validator.validateProduct(fixture.product(), variants))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining(message);
	}
}
