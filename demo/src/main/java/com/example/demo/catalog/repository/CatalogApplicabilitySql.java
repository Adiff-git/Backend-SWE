package com.example.demo.catalog.repository;

/**
 * Authoritative V27 named-family resolver shared by search and the future JDBC audit.
 * Missing/ambiguous global roots produce no binding. Inheritance stops at another
 * canonical family; UNION also bounds recursion on corrupt cyclic input.
 * These are trusted read-only SQL fragments, with no caller-supplied SQL or schema changes.
 */
public final class CatalogApplicabilitySql {
    private CatalogApplicabilitySql() { }

    private static final String REGISTRY_CTES = """
            registry_roots AS (
                SELECT catalog_search_safe_normalize_v2(name) AS family,min(id::text)::uuid AS id FROM categories
                WHERE catalog_search_safe_normalize_v2(name) IN ('laptop','tablet')
                GROUP BY catalog_search_safe_normalize_v2(name) HAVING count(*)=1
            ), registry_categories(family,id) AS (
                SELECT family,id FROM registry_roots UNION
                SELECT r.family,c.id FROM registry_categories r JOIN categories c ON c.parent_category_id=r.id
                WHERE catalog_search_safe_normalize_v2(c.name)=r.family OR catalog_search_safe_normalize_v2(c.name) NOT IN ('laptop','tablet','smartphone','computer accessories','headphone & earbuds','charger & power bank','speaker','phone accessories','smartwatch')
            )
            """.stripTrailing();

    /** CTE definitions without WITH RECURSIVE or a trailing comma, for query composition. */
    public static String registryCtes() {
        return REGISTRY_CTES;
    }

    /** All applicable (canonical family, category ID) pairs, usable with plain V27 JDBC. */
    public static String projection() {
        return "WITH RECURSIVE " + REGISTRY_CTES
                + " SELECT family, id AS category_id FROM registry_categories ORDER BY family, id";
    }
}
