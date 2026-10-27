package com.example.demo.catalog.dto.response;

import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public final class ProductPage {
	private final List<ProductSummary> items;
	private final int page;
	private final int size;
	private final long totalItems;
	private final int totalPages;
}
