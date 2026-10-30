package com.example.demo.catalog.dto.response;

import java.util.UUID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public final class ProductImageSummary {
	private final UUID id;
	private final String url;
	private final String altText;
	private final int width;
	private final int height;
	private final int displayOrder;
	private final Boolean isPrimary;
}
