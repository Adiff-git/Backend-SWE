package com.example.demo.catalog.repository;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** Caller holds the taxonomy gate; after proposed topology writes, flush then resolve in that same transaction. */
@Repository
@RequiredArgsConstructor
public class CatalogSpecificationContextRepository {
    private final EntityManager entities;
    static final String SQL = "WITH RECURSIVE " + CatalogApplicabilitySql.registryCtes() + """
             SELECT COALESCE(STRING_AGG(rc.family, ',' ORDER BY rc.family), '')
             FROM categories c LEFT JOIN registry_categories rc ON rc.id = c.id
             WHERE c.id = :categoryId GROUP BY c.id
            """;

    public Set<String> getApplicableFamilies(UUID categoryId) {
        if (categoryId == null) throw new IllegalArgumentException("Category is required");
        List<?> rows = entities.createNativeQuery(SQL).setParameter("categoryId", categoryId).getResultList();
        if (rows.size() != 1) throw new IllegalArgumentException("Category is unavailable");
        String families = (String) rows.getFirst();
        return families.isEmpty() ? Set.of() : Set.of(families.split(","));
    }
}
