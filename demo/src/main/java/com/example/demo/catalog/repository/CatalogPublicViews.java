package com.example.demo.catalog.repository;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/** Scalar current-read projections avoid stale persistence-context entities after signing preparation. */
public final class CatalogPublicViews {
    private CatalogPublicViews() { }
    public interface Product {
        UUID getId(); String getName(); UUID getCategoryId(); String getCategoryName();
        String getDescription(); UUID getBrandId(); String getBrandCode(); String getBrandName();
    }
    public interface Variant {
        UUID getId(); String getSku(); boolean getEnabled(); BigDecimal getPrice();
        Map<String,String> getOptions(); Map<String,Object> getSpecifications();
    }
    public interface Image {
        UUID getId(); String getObjectKey(); String getAltText(); int getWidth(); int getHeight();
        int getDisplayOrder(); boolean getPrimaryImage();
    }
}
