package com.example.demo.catalog.repository;

import com.example.demo.catalog.search.ProductCandidatePage;
import com.example.demo.catalog.search.ProductSearchCriteria;

public interface ProductSearchRepository {
    ProductCandidatePage findPublishedCandidates(ProductSearchCriteria criteria);
}
