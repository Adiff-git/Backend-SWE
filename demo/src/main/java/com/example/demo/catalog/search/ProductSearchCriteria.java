package com.example.demo.catalog.search;

import com.example.demo.catalog.dto.request.ProductSearchRequest.SpecificationGroup;
import java.math.BigInteger;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Value;

/** Foundation for parsed criteria, not proof of registry/category authorization or executable SQL. */
@Value
@Builder
public class ProductSearchCriteria {
    ProductSearchNormalizer.Query keyword;
    int page;
    @Builder.Default
    int size = 20;
    UUID categoryId;
    List<UUID> brandIds;
    BigInteger minPrice;
    BigInteger maxPrice;
    ProductSearchSort sort;
    List<SpecificationGroup> specificationGroups;
}
