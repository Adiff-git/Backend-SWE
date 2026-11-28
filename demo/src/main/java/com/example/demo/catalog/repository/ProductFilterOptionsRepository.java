package com.example.demo.catalog.repository;

import com.example.demo.catalog.dto.response.ProductFilterOptions;
import com.example.demo.catalog.search.ProductSearchCriteria;
import com.example.demo.catalog.search.SpecificationFilterRegistry;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class ProductFilterOptionsRepository {
    private final EntityManager entityManager;
    private final SpecificationFilterRegistry registry = new SpecificationFilterRegistry();

    @Transactional(readOnly = true)
    public List<ProductFilterOptions> findOptions(ProductSearchCriteria criteria) {
        var statement = new CatalogSearchSql(registry).compileOptions(criteria);
        var query = entityManager.createNativeQuery(statement.sql());
        statement.parameters().forEach(query::setParameter);
        var groups = new LinkedHashMap<String, List<ProductFilterOptions.Option>>();
        for (Object result : query.getResultList()) {
            Object[] row = (Object[]) result;
            String key = (String) row[0];
            Object value = row[1] != null ? row[1] : row[2];
            groups.computeIfAbsent(key, ignored -> new ArrayList<>())
                    .add(new ProductFilterOptions.Option(value, ((Number) row[4]).longValue()));
        }
        return groups.entrySet().stream().map(entry -> {
            var definition = registry.get(entry.getKey());
            return new ProductFilterOptions(entry.getKey(), definition.type(), definition.unit(), List.copyOf(entry.getValue()));
        }).toList();
    }
}
