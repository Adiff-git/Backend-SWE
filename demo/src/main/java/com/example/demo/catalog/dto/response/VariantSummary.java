package com.example.demo.catalog.dto.response;

import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public final class VariantSummary {
	private final UUID id;
	private final String sku;
	private final Map<String, String> options;
	private final SellingPrice sellingPrice;
	private final Map<String, Object> technicalSpecifications;
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
    private final com.example.demo.inventory.dto.response.InventoryAvailability availability;
}
