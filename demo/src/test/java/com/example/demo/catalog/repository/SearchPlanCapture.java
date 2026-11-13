package com.example.demo.catalog.repository;

import com.example.demo.catalog.dto.request.ProductSearchRequest;
import com.example.demo.catalog.search.ProductSearchCriteria;
import com.example.demo.catalog.search.ProductSearchNormalizer;
import com.example.demo.catalog.search.SpecificationFilterRegistry;
import jakarta.persistence.EntityManager;
import java.util.UUID;

/** Test-only instrumentation of the exact production compiler and bound parameters. */
public final class SearchPlanCapture {
    private SearchPlanCapture(){}
    public static String explain(EntityManager em,ProductSearchRequest r){
        var c=ProductSearchCriteria.builder().keyword(new ProductSearchNormalizer().normalizeQuery(r.getQ())).page(r.getPage()).size(r.getSize()).categoryId(r.getCategoryId()==null?null:UUID.fromString(r.getCategoryId())).brandIds(r.getBrandIds()==null?null:r.getBrandIds().stream().map(UUID::fromString).toList()).minPrice(r.getMinPrice()).maxPrice(r.getMaxPrice()).sort(r.getSort()).specificationGroups(r.getSpecificationGroups()).build();
        em.createNativeQuery("SELECT set_config('pg_trgm.strict_word_similarity_threshold','0.32',true)").getSingleResult();
        var statement=new CatalogSearchSql(new SpecificationFilterRegistry()).compile(c,.32,true);
        var query=em.createNativeQuery("EXPLAIN(ANALYZE,BUFFERS,VERBOSE,SETTINGS,FORMAT JSON) "+statement.sql());statement.parameters().forEach(query::setParameter);
        return query.getSingleResult().toString();
    }
}
