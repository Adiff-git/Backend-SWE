package com.example.demo.catalog.controller;

import com.example.demo.catalog.dto.response.ProductFilterOptions;
import com.example.demo.catalog.exception.InvalidCatalogRequestException;
import com.example.demo.catalog.repository.ProductFilterOptionsRepository;
import com.example.demo.catalog.search.ProductSearchCriteria;
import com.example.demo.catalog.search.ProductSearchParameterParser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Product filters")
public class ProductFilterOptionsController {
    private final ProductFilterOptionsRepository repository;
    private final ProductSearchParameterParser parser;

    public ProductFilterOptionsController(ProductFilterOptionsRepository repository, Validator validator) {
        this.repository = repository;
        this.parser = new ProductSearchParameterParser(validator);
    }

    @GetMapping("/api/product-filter-options")
    @Operation(summary = "List available specification filter options", description = "No parameters required. Returns global observed values and distinct published product counts for physical_ram (GB), installed_storage_gb (GB), cpu_family, display_diagonal (inch), and display_resolution across all categories. Optional categoryId and repeated brandId narrow the options. Missing or invalid facts are omitted. Groups are sorted by key; numeric values numerically, text values alphabetically and normalized. Different options can count the same product. Use each key/value/unit in GET /api/products specFilters, without requiring categoryId. Returns [] if no valid options exist.")
    public List<ProductFilterOptions> options(@RequestParam MultiValueMap<String, String> parameters) {
        if (!Set.of("categoryId", "brandId").containsAll(parameters.keySet())) {
            throw new InvalidCatalogRequestException("Filter options accepts only categoryId and brandId");
        }
        var request = parser.parse(parameters);
        return repository.findOptions(ProductSearchCriteria.builder()
                .categoryId(request.getCategoryId() == null ? null : UUID.fromString(request.getCategoryId()))
                .brandIds(request.getBrandIds() == null ? null : request.getBrandIds().stream().map(UUID::fromString).toList())
                .build());
    }
}
