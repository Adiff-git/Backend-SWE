package com.example.demo.catalog.search;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProductSearchSort {
    RELEVANCE("relevance"),
    PRICE_ASC("price-asc"),
    PRICE_DESC("price-desc"),
    NAME_ASC("name-asc"),
    NAME_DESC("name-desc");

    private final String parameterValue;
}
