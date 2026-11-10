package com.example.demo.catalog.controller;

import com.example.demo.catalog.dto.response.ProductPage;
import com.example.demo.catalog.dto.response.ProductDetail;
import com.example.demo.catalog.exception.InvalidCatalogRequestException;
import com.example.demo.catalog.service.ProductCatalogService;
import com.example.demo.catalog.search.ProductSearchParameterParser;
import jakarta.validation.Validator;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
public class ProductCatalogController {
	private static final Pattern CANONICAL_UUID = Pattern.compile(
			"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

	private final ProductCatalogService service;
	private final ProductSearchParameterParser parser;
	@org.springframework.beans.factory.annotation.Value("${catalog.search.fuzzy.enabled:true}")
	private boolean fuzzyEnabled = true;

	public ProductCatalogController(ProductCatalogService service, Validator validator) {
		this.service = service;
		this.parser = new ProductSearchParameterParser(validator);
	}

	@GetMapping("/{id}")
	public ProductDetail getProduct(@PathVariable String id) {
		if (!CANONICAL_UUID.matcher(id).matches()) {
			throw new InvalidCatalogRequestException("Invalid product identifier");
		}
		return service.getProduct(UUID.fromString(id));
	}

	@GetMapping
	public ProductPage listProducts(@RequestParam MultiValueMap<String, String> parameters,
            jakarta.servlet.http.HttpServletResponse response) {
        var request = parser.parse(parameters);
        ProductPage page = service.listProducts(request);
        if (!new com.example.demo.catalog.search.ProductSearchNormalizer().normalizeQuery(request.getQ()).getTokens().isEmpty()) {
            response.setHeader("X-Catalog-Search-Mode",fuzzyEnabled ? "fuzzy-enabled" : "exact-only");
        }
        return page;
	}
}
