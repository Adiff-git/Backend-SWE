package com.example.demo.catalog.controller;

import com.example.demo.catalog.dto.response.BrandListItem;
import com.example.demo.catalog.service.BrandCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Brands", description = "Brand list and published product counts")
@RequestMapping("/api/brands")
@RequiredArgsConstructor
public class BrandCatalogController {
    private final BrandCatalogService service;

    @Operation(summary = "List brands", description = "All brands with published product counts, sorted by name, then code. Includes brands with zero published products. Returns an empty array when no brands exist. No authentication required. Query a brand's products using GET /api/products?brandId={id}.")
    @GetMapping
    public List<BrandListItem> listBrands() {
        return service.listBrands();
    }
}
