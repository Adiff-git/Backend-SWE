package com.example.demo.catalog.controller;

import com.example.demo.catalog.dto.response.ProductPage;
import com.example.demo.catalog.dto.response.ProductDetail;
import com.example.demo.catalog.exception.InvalidCatalogRequestException;
import com.example.demo.catalog.service.ProductCatalogService;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductCatalogController {
	private static final Pattern CANONICAL_UUID = Pattern.compile(
			"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

	private final ProductCatalogService service;

	@GetMapping("/{id}")
	public ProductDetail getProduct(@PathVariable String id) {
		if (!CANONICAL_UUID.matcher(id).matches()) {
			throw new InvalidCatalogRequestException("Invalid product identifier");
		}
		return service.getProduct(UUID.fromString(id));
	}

	@GetMapping
	public ProductPage listProducts(@RequestParam MultiValueMap<String, String> parameters) {
		for (Map.Entry<String, java.util.List<String>> entry : parameters.entrySet()) {
			if ((!"page".equals(entry.getKey()) && !"size".equals(entry.getKey()))
					|| entry.getValue().size() != 1) {
				throw new InvalidCatalogRequestException("Invalid or duplicate query parameter");
			}
		}
		int page = parseNonnegative(parameters.getFirst("page"), ProductCatalogService.DEFAULT_PAGE, "page");
		int size = parseNonnegative(parameters.getFirst("size"), ProductCatalogService.DEFAULT_SIZE, "size");
		if (size < 1 || size > ProductCatalogService.MAX_SIZE) {
			throw new InvalidCatalogRequestException("Invalid size parameter");
		}
		return service.listProducts(page, size);
	}

	private int parseNonnegative(String value, int defaultValue, String name) {
		if (value == null) {
			return defaultValue;
		}
		if (!value.matches("0|[1-9][0-9]*")) {
			throw new InvalidCatalogRequestException("Invalid " + name + " parameter");
		}
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException exception) {
			throw new InvalidCatalogRequestException("Invalid " + name + " parameter");
		}
	}
}
