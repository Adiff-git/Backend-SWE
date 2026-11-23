package com.example.demo.catalog.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo.catalog.CatalogFixtures;
import com.example.demo.catalog.entity.ProductStatus;
import com.example.demo.catalog.exception.AdminCatalogException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProductPublicationValidatorTest {
    private final ProductPublicationValidator validator = new ProductPublicationValidator(new AdminCatalogValidator(
            new CatalogDataValidator(), new TechnicalSpecificationValidator(), new ProductImageContentValidator()));

    @Test
    void readiness_shouldRequireEnabledVariantAndPrimary_butNeverStockBrandOrDescription() {
        var fixture = CatalogFixtures.draftProduct();
        assertThat(validator.violations(fixture.product(), List.of(), List.of(), Set.of()))
                .extracting(ProductPublicationValidator.Violation::code)
                .contains("ENABLED_VARIANT_REQUIRED", "PRIMARY_IMAGE_REQUIRED");
        assertThat(validator.violations(fixture.product(), List.of(fixture.variant()),
                List.of(CatalogFixtures.image(fixture.product(), 0, true)), Set.of())).isEmpty();
    }

    @Test
    void readiness_shouldValidateEveryEnabledVariant_andAllowBasicValidDisabledZeroPrice() {
        var fixture = CatalogFixtures.publishedProduct();
        var second = CatalogFixtures.variant(fixture.product(), Map.of("choice", "second"), BigDecimal.ZERO);
        second.setEnabled(false);
        var images = List.of(CatalogFixtures.image(fixture.product(), 0, true));
        assertThat(validator.violations(fixture.product(), List.of(fixture.variant(), second), images, Set.of())).isEmpty();
        second.setEnabled(true);
        assertThat(validator.violations(fixture.product(), List.of(fixture.variant(), second), images, Set.of()))
                .extracting(ProductPublicationValidator.Violation::code).contains("POSITIVE_PRICE_REQUIRED");
        second.setEnabled(false);
        fixture.variant().setEnabled(false);
        assertThat(validator.violations(fixture.product(), List.of(fixture.variant(), second), images, Set.of()))
                .extracting(ProductPublicationValidator.Violation::code).contains("ENABLED_VARIANT_REQUIRED");
    }

    @Test
    void readiness_shouldRejectWrongApplicableUnit_withoutInventingRequiredFacts() {
        var fixture = CatalogFixtures.draftProduct();
        fixture.variant().setTechnicalSpecifications(Map.of("schema_version", 1, "values",
                Map.of("physical_ram", Map.of("value", 8, "unit", "MB"))));
        assertThat(validator.violations(fixture.product(), List.of(fixture.variant()),
                List.of(CatalogFixtures.image(fixture.product(), 0, true)), Set.of("laptop"))).isNotEmpty();
        fixture.variant().setTechnicalSpecifications(Map.of("schema_version", 1, "values", Map.of("nfc_supported", false, "count", 0)));
        assertThat(validator.violations(fixture.product(), List.of(fixture.variant()),
                List.of(CatalogFixtures.image(fixture.product(), 0, true)), Set.of("laptop"))).isEmpty();
    }

    @Test
    void requireValid_shouldGiveSafeOrderedViolations_withoutMutatingAnInvalidPublishedProposal() {
        var fixture = CatalogFixtures.publishedProduct();
        fixture.variant().setSellingPrice(BigDecimal.ZERO);
        assertThatThrownBy(() -> validator.requireValid(fixture.product(), List.of(fixture.variant()), List.of(), Set.of()))
                .isInstanceOfSatisfying(AdminCatalogException.class, failure -> {
                    assertThat(failure.getStatus()).isEqualTo(422);
                    assertThat(failure.getCode()).isEqualTo("CATALOG_PUBLICATION_INVALID");
                    assertThat(failure.getProperties()).containsKey("violations");
                });
        assertThat(fixture.product().getStatus()).isEqualTo(ProductStatus.PUBLISHED);
        assertThat(fixture.product().getVersion()).isZero();
        assertThat(fixture.variant().getSellingPrice()).isEqualTo(BigDecimal.ZERO);
    }
}
