package com.example.demo.catalog.repository;

import com.example.demo.catalog.search.ProductCandidatePage;
import com.example.demo.catalog.search.ProductSearchCriteria;

public interface ProductSearchRepository {
    record CurrentCandidate(java.util.UUID productId,java.util.UUID variantId,java.math.BigInteger amount,
            String name,java.util.UUID categoryId,String categoryName,String imageKey) {}
    java.util.List<CurrentCandidate> recheckPublishedCandidates(ProductSearchCriteria criteria,
            java.util.List<ProductCandidatePage.Candidate> candidates);
    ProductCandidatePage findPublishedCandidates(ProductSearchCriteria criteria);
}
