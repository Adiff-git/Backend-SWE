package com.example.demo.catalog.service;

import com.example.demo.catalog.dto.response.OptionDimension;
import com.example.demo.catalog.entity.ProductVariant;
import java.util.List;

public interface ProductOptionService {
    List<OptionDimension> getPreparedDimensions(java.util.Map<java.util.UUID,java.util.Map<String,java.util.Map<String,String>>> labels);
    List<OptionDimension> getDimensions(List<ProductVariant> variants);
}
