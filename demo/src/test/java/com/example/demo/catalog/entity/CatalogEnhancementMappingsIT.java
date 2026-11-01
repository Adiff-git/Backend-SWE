package com.example.demo.catalog.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.catalog.CatalogFixtures;
import com.example.demo.catalog.repository.BrandRepository;
import com.example.demo.catalog.repository.CatalogSourceLinkRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Map;
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
class CatalogEnhancementMappingsIT {
    @Autowired private EntityManager entities;
    @Autowired private BrandRepository brands;
    @Autowired private CatalogSourceLinkRepository links;

    @Test
    void repositories_shouldRoundTripBrandSpecificationAndReceiptWithoutChangingOptions() {
        CatalogFixtures.ProductFixture fixture = CatalogFixtures.persistProduct(entities, ProductStatus.PUBLISHED);
        Brand brand = brands.save(new Brand("mapping-brand", "Mapping Brand"));
        fixture.product().setBrand(brand);
        Map<String, String> originalOptions = fixture.variant().getOptionCombination();
        fixture.variant().setTechnicalSpecifications(Map.of("schema_version", 1, "values", Map.of("nfc_supported", false)));
        CatalogSourceLink receipt = new CatalogSourceLink();
        receipt.setSource("test"); receipt.setSourceKey("https://example.test/item");
        receipt.setSourceUrl("https://example.test/item"); receipt.setSourceRecordId("source-id");
        receipt.setProductVariant(fixture.variant()); receipt.setSourcePayloadSha256("a".repeat(64));
        receipt.setNormalizedContentSha256("b".repeat(64)); receipt.setNormalizationVersion("1");
        receipt.setApprovedManifestSha256("c".repeat(64)); receipt.setUpdatedAt(Instant.parse("2026-10-29T00:00:00Z"));
        links.saveAndFlush(receipt);
        entities.clear();
        CatalogSourceLink found = links.findBySourceAndSourceKey("test", "https://example.test/item").orElseThrow();
        assertThat(found.getProductVariant().getId()).isEqualTo(fixture.variant().getId());
        assertThat(found.getProductVariant().getOptionCombination()).isEqualTo(originalOptions);
        assertThat(found.getProductVariant().getTechnicalSpecifications()).isEqualTo(Map.of("schema_version", 1, "values", Map.of("nfc_supported", false)));
        assertThat(found.getProductVariant().getProduct().getBrand().getId()).isEqualTo(brand.getId());
        assertThat(brands.findByCode("mapping-brand").orElseThrow().getName()).isEqualTo("Mapping Brand");
    }
}
