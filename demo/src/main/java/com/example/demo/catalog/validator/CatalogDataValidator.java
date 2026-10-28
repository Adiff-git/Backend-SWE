package com.example.demo.catalog.validator;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import com.example.demo.catalog.entity.Product;
import com.example.demo.catalog.entity.ProductVariant;

/** Checks trusted catalog input before it is committed; PostgreSQL remains the final guard. */
@Component
public class CatalogDataValidator {

	public void validateProduct(Product product, Collection<ProductVariant> variants) {
		if (product == null || product.getCategory() == null) {
			throw new IllegalArgumentException("Product requires a category");
		}
		if (product.getName() == null || product.getName().isBlank()) {
			throw new IllegalArgumentException("Product requires a nonblank name");
		}
		if (product.getStatus() == null) {
			throw new IllegalArgumentException("Product requires an explicit status");
		}
		if (variants == null || variants.isEmpty()) {
			throw new IllegalArgumentException("Product requires at least one variant");
		}

		Set<Map<String, String>> optionCombinations = new HashSet<>();
		for (ProductVariant variant : variants) {
			if (variant == null || variant.getProduct() == null
					|| (variant.getProduct() != product && (product.getId() == null
							|| !product.getId().equals(variant.getProduct().getId())))) {
				throw new IllegalArgumentException("Variant must belong to the product");
			}
			if (variant.getSku() == null || variant.getSku().isBlank()) {
				throw new IllegalArgumentException("Variant requires a nonblank SKU");
			}
			BigDecimal price = variant.getSellingPrice();
			if (price == null) {
				throw new IllegalArgumentException("Variant requires a selling price");
			}
			try {
				price.toBigIntegerExact();
			} catch (ArithmeticException invalidFraction) {
				throw new IllegalArgumentException("Selling price must be whole VND", invalidFraction);
			}

			Map<String, String> options = validOptions(variant.getOptionCombination());
			if (variants.size() > 1 && options.isEmpty()) {
				throw new IllegalArgumentException("Multiple variants require nonempty options");
			}
			if (!optionCombinations.add(options)) {
				throw new IllegalArgumentException("Variant option combinations must be distinct");
			}
		}
	}

	private Map<String, String> validOptions(Map<?, ?> source) {
		if (source == null) {
			throw new IllegalArgumentException("Variant requires an option map");
		}
		Map<String, String> copy = new HashMap<>();
		for (Map.Entry<?, ?> entry : source.entrySet()) {
			if (!(entry.getKey() instanceof String key) || key.isBlank()
					|| !(entry.getValue() instanceof String value) || value.isBlank()) {
				throw new IllegalArgumentException("Option names and values must be nonblank strings");
			}
			copy.put(key, value);
		}
		return Map.copyOf(copy);
	}
}
