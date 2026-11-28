package com.example.demo.catalog.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.catalog.CatalogFixtures;
import com.example.demo.catalog.entity.Brand;
import com.example.demo.catalog.entity.ProductStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class BrandRepositoryIT {
    @Autowired private EntityManager em;
    @Autowired private BrandRepository brands;

    @Test
    void countsPublishedProductsOnceAndKeepsEmptyBrands() {
        var populated = new Brand("count-z", "Count Z");
        var empty = new Brand("count-a", "Count A");
        em.persist(populated);
        em.persist(empty);
        for (var status : ProductStatus.values()) {
            var fixture = CatalogFixtures.persistProduct(em, status);
            fixture.product().setBrand(populated);
            if (status == ProductStatus.PUBLISHED) {
                em.persist(CatalogFixtures.variant(fixture.product(), java.util.Map.of("color", "blue"), new java.math.BigDecimal("100")));
            }
        }
        em.flush();
        var result = brands.findAllWithProductCounts().stream()
                .filter(item -> item.getId().equals(populated.getId()) || item.getId().equals(empty.getId()))
                .toList();
        assertThat(result).extracting(item -> item.getCode()).containsExactly("count-a", "count-z");
        assertThat(result).extracting(item -> item.getProductCount()).containsExactly(0L, 1L);
    }
}
