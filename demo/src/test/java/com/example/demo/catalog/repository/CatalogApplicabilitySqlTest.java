package com.example.demo.catalog.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.catalog.search.ProductSearchCriteria;
import com.example.demo.catalog.search.SpecificationFilterRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogApplicabilitySqlTest {
    @Test
    void registryCtes_shouldPreserveV27GlobalAmbiguityAndAncestryBoundaries() throws IOException {
        // Snapshot of the approved pre-extraction resolver, not an independent Java tree algorithm.
        String baseline;
        try (java.io.InputStream input = getClass().getResourceAsStream("/catalog/applicability-v27.sql")) {
            baseline = new String(java.util.Objects.requireNonNull(input).readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThat(normalize(CatalogApplicabilitySql.registryCtes())).isEqualTo(normalize(baseline));
    }

    @Test
    void projection_shouldUseOnlyV27Categories_andTheSameResolverAsSearch() {
        String projection = CatalogApplicabilitySql.projection();
        assertThat(projection).startsWith("WITH RECURSIVE ")
                .contains(CatalogApplicabilitySql.registryCtes())
                .endsWith("SELECT family, id AS category_id FROM registry_categories ORDER BY family, id");
        for (UUID category : new UUID[] {null, UUID.fromString("00000000-0000-0000-0000-000000000001")}) {
            String search = new CatalogSearchSql(new SpecificationFilterRegistry())
                    .compile(ProductSearchCriteria.builder().categoryId(category).build(), 0.3, false).sql();
            assertThat(search).contains(CatalogApplicabilitySql.registryCtes() + ", selected_products AS MATERIALIZED (");
        }
        assertThat(projection).doesNotContain("catalog_taxonomy_state", "enabled", "active", "last_restored_at",
                "expectedVersion", "FOR UPDATE", "FOR SHARE", "INSERT", "UPDATE", "DELETE");
    }

    private static String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
