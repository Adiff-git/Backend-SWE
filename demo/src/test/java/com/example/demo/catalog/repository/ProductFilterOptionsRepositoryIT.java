package com.example.demo.catalog.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.catalog.CatalogFixtures;
import com.example.demo.catalog.entity.Brand;
import com.example.demo.catalog.entity.ProductStatus;
import com.example.demo.catalog.search.ProductSearchCriteria;
import com.example.demo.catalog.search.ProductSearchParameterParser;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ProductFilterOptionsRepository.class)
class ProductFilterOptionsRepositoryIT {
    @Autowired EntityManager em;
    @Autowired ProductFilterOptionsRepository options;
    @Autowired ProductRepository products;

    @Test
    void optionsAndSearchShareNormalizedFactsAndSameVariantRules() {
        var category = CatalogFixtures.category();
        category.setName("Laptop");
        em.persist(category);
        var brand = new Brand("filter-brand", "Filter Brand");
        em.persist(brand);
        var published = CatalogFixtures.persistProduct(em, ProductStatus.PUBLISHED);
        published.product().setCategory(category);
        published.product().setBrand(brand);
        published.variant().setTechnicalSpecifications(facts(16, "Core i5"));
        var otherVariant = CatalogFixtures.variant(published.product(), Map.of("color", "blue"), BigDecimal.TEN);
        otherVariant.setTechnicalSpecifications(facts(32, "Core i7"));
        em.persist(otherVariant);
        var sameFacts = CatalogFixtures.variant(published.product(), Map.of("color", "red"), BigDecimal.TEN);
        sameFacts.setTechnicalSpecifications(facts(16, "core i5"));
        em.persist(sameFacts);
        var draft = CatalogFixtures.persistProduct(em, ProductStatus.DRAFT);
        draft.product().setCategory(category);
        draft.product().setBrand(brand);
        draft.variant().setTechnicalSpecifications(facts(64, "secret cpu"));
        em.flush();

        var criteria = ProductSearchCriteria.builder().categoryId(category.getId()).brandIds(List.of(brand.getId())).build();
        var groups = options.findOptions(criteria);
        assertThat(groups).extracting(g -> g.key()).containsExactly("cpu_family", "display_diagonal", "display_resolution", "installed_storage_gb", "physical_ram");
        var cpu = groups.getFirst();
        assertThat(cpu.options()).extracting(o -> o.value()).containsExactly("core i5", "core i7");
        assertThat(cpu.options()).allSatisfy(o -> assertThat(o.productCount()).isEqualTo(1));
        var ram = groups.getLast();
        assertThat(ram.options()).extracting(o -> ((BigDecimal)o.value()).intValueExact()).containsExactly(16, 32);
        assertThat(ram.options()).allSatisfy(o -> assertThat(o.productCount()).isEqualTo(1));

        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var parser = new ProductSearchParameterParser(factory.getValidator());
            String filters = """
                    [{"key":"cpu_family","alternatives":[{"op":"eq","value":"CORE i5"}]},
                     {"key":"physical_ram","alternatives":[{"op":"eq","value":16,"unit":"GB"}]}]
                    """;
            var parsed = parser.parse(Map.of("categoryId", List.of(category.getId().toString()), "specFilters", List.of(filters)));
            assertThat(products.findPublishedCandidates(ProductSearchCriteria.builder().categoryId(category.getId())
                    .specificationGroups(parsed.getSpecificationGroups()).build()).getTotalElements()).isEqualTo(1);
            var mismatch = parser.parse(Map.of("categoryId", List.of(category.getId().toString()), "specFilters", List.of(filters.replace("\"value\":16", "\"value\":32"))));
            assertThat(products.findPublishedCandidates(ProductSearchCriteria.builder().categoryId(category.getId())
                    .specificationGroups(mismatch.getSpecificationGroups()).build()).getTotalElements()).isZero();
        }
        assertThat(options.findOptions(ProductSearchCriteria.builder().categoryId(category.getId())
                .brandIds(List.of(java.util.UUID.randomUUID())).build())).isEmpty();
    }

    private static Map<String, Object> facts(int ram, String cpu) {
        return Map.of("schema_version", 1, "values", Map.of(
                "physical_ram", Map.of("value", ram, "unit", "GB"),
                "cpu_family", cpu,
                "installed_storage_gb", Map.of("value", 512, "unit", "GB"),
                "display_diagonal", Map.of("value", 15.6, "unit", "inch"),
                "display_resolution", "1920 x 1080"));
    }

    @Test
    void globalOptionsAndFilteringIncludeFactsAcrossDifferentCategories() {
        var first = CatalogFixtures.persistProduct(em, ProductStatus.PUBLISHED);
        first.category().setName("Smartphone");
        first.variant().setTechnicalSpecifications(facts(8, "phone cpu"));
        var second = CatalogFixtures.persistProduct(em, ProductStatus.PUBLISHED);
        second.category().setName("Tablet");
        second.variant().setTechnicalSpecifications(facts(16, "tablet cpu"));
        em.flush();
        var global = options.findOptions(ProductSearchCriteria.builder().build());
        var ram = global.stream().filter(group -> group.key().equals("physical_ram")).findFirst().orElseThrow();
        assertThat(ram.options()).extracting(option -> ((BigDecimal) option.value()).intValueExact()).containsExactly(8, 16);
        var narrowed = options.findOptions(ProductSearchCriteria.builder().categoryId(first.category().getId()).build());
        assertThat(narrowed.stream().filter(group -> group.key().equals("physical_ram")).findFirst().orElseThrow().options())
                .extracting(option -> ((BigDecimal) option.value()).intValueExact()).containsExactly(8);
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var parsed = new ProductSearchParameterParser(factory.getValidator()).parse(Map.of("specFilters", List.of(
                    "[{\"key\":\"physical_ram\",\"alternatives\":[{\"op\":\"gte\",\"value\":8,\"unit\":\"GB\"}]}]")));
            com.example.demo.catalog.validator.ProductSearchValidator.validate(parsed);
            assertThat(products.findPublishedCandidates(ProductSearchCriteria.builder()
                    .specificationGroups(parsed.getSpecificationGroups()).build()).getTotalElements()).isEqualTo(2);
        }
    }
}
