package com.example.demo.catalog.dto.request;

import lombok.Value;

/** Omission and explicit null are different values; endpoint readers decide which fields may clear. */
@Value
public class CatalogField<T> {
    boolean supplied;
    T value;
    public static <T> CatalogField<T> absent() { return new CatalogField<>(false, null); }
    public static <T> CatalogField<T> supplied(T value) { return new CatalogField<>(true, value); }
}
