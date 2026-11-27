package com.example.demo.inventory.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

class AdminInventoryQueryBudgetIT {
    @Test void scalarReadsUseOneDomainQueryAndKeepTotalsBeyondTheLastPage() {
        try (var database = new PostgreSQLContainer<>("postgres:17-alpine")
                .withCommand("postgres", "-c", "shared_preload_libraries=pg_stat_statements")) {
            database.start();
            var source = new DriverManagerDataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword());
            Flyway.configure().dataSource(source).target("32").load().migrate();
            var jdbc = new JdbcTemplate(source);
            jdbc.execute("CREATE EXTENSION pg_stat_statements");
            var repository = new AdminInventoryReadRepository(JdbcClient.create(source));
            assertThat(repository.list(null, Set.of(), 0, 20).totalItems()).isZero();
            UUID category = UUID.randomUUID(), product = UUID.randomUUID(), variant = UUID.randomUUID();
            jdbc.update("INSERT INTO categories(id,name) VALUES (?, 'Budget')", category);
            jdbc.update("INSERT INTO products(id,category_id,name,status) VALUES (?,?,'Budget','DRAFT')", product, category);
            jdbc.update("INSERT INTO product_variants(id,product_id,sku,option_combination,base_price) VALUES (?,?,?,?::jsonb,100)",
                    variant, product, "Budget%SKU", "{\"model\":\"primary\"}");
            for (int n = 1; n <= 2; n++) {
                jdbc.update("INSERT INTO product_variants(id,product_id,sku,option_combination,base_price) VALUES (?,?,?,?::jsonb,100)",
                        UUID.randomUUID(), product, "Budget%SKU-" + n, "{\"model\":\"" + n + "\"}");
            }
            for (int size : new int[]{1, 20, 100}) {
                var list = repository.list("%SKU", Set.of(), 0, size);
                assertThat(list.items()).hasSize(Math.min(size, 3));
                assertThat(list.totalItems()).isEqualTo(3);
                var out = repository.list("%SKU", Set.of(), 99, size);
                assertThat(out.totalItems()).isEqualTo(3);
                assertThat(out.items()).isEmpty();
                assertThat(repository.history(variant, 99, size).items()).isEmpty();
            }
            var ordered = repository.list("%SKU", Set.of(), 0, 100).items();
            assertThat(repository.list("%SKU", Set.of(), 1, 1).items()).containsExactly(ordered.get(1));
            assertThat(repository.list("%SKU", Set.of(), 2, 1).items()).containsExactly(ordered.get(2));
            assertThat(repository.list("%SKU", Set.of(com.example.demo.catalog.entity.ProductStatus.ARCHIVED), 0, 100)
                    .totalItems()).isZero();
            assertThat(repository.detail(variant).available()).isEqualTo("0");
            assertThat(calls(jdbc, "WITH filtered AS (%FROM product_variants v%")).isEqualTo(11);
            assertThat(calls(jdbc, "WITH filtered AS (%FROM admin_inventory_operations%")).isEqualTo(3);
            assertThat(calls(jdbc, "SELECT v.id AS variant_id%WHERE v.id =%")).isEqualTo(1);
        }
    }

    private long calls(JdbcTemplate jdbc, String pattern) {
        return jdbc.queryForObject("SELECT coalesce(sum(calls),0) FROM pg_stat_statements WHERE query LIKE ?", Long.class, pattern);
    }
}
