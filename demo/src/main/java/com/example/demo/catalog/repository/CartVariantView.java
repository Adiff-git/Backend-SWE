package com.example.demo.catalog.repository;

import com.example.demo.catalog.entity.ProductStatus;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/** Scalar repository projection; never exposed as an HTTP response. */
public interface CartVariantView {
    UUID getVariantId();
    UUID getProductId();
    ProductStatus getStatus();
    BigDecimal getPrice();
    String getProductName();
    String getSku();
    Map<String, String> getOptions();
}
