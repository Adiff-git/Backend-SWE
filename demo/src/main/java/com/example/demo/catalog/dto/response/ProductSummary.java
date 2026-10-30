package com.example.demo.catalog.dto.response;

import java.util.UUID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class ProductSummary {
	private final UUID id;
	private final String name;
	private final CategorySummary category;
	private final String primaryImageUrl;
}
