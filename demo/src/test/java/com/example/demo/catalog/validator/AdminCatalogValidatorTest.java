package com.example.demo.catalog.validator;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo.catalog.CatalogFixtures;
import com.example.demo.catalog.entity.ProductStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class AdminCatalogValidatorTest {
    private final AdminCatalogValidator validator = new AdminCatalogValidator(new CatalogDataValidator(),
            new TechnicalSpecificationValidator(), new ProductImageContentValidator());

    @ParameterizedTest
    @EnumSource(value = ProductStatus.class, names = {"DRAFT", "UNPUBLISHED", "ARCHIVED"})
    void basic_shouldAllowVariantlessPreparation_withoutInventingChildren(ProductStatus status) {
        CatalogFixtures.ProductFixture fixture = CatalogFixtures.product(status);
        assertThatCode(() -> validator.validateBasic(fixture.product(), List.of(), List.of(), Set.of()))
                .doesNotThrowAnyException();
    }

    @Test
    void basic_shouldStillRequireSkuPriceOptionsAndTypedFacts_onDisabledPreparation() {
        CatalogFixtures.ProductFixture fixture = CatalogFixtures.draftProduct();
        fixture.variant().setEnabled(false);
        fixture.variant().setSellingPrice(BigDecimal.ZERO);
        assertThatCode(() -> validator.validateBasic(fixture.product(), List.of(fixture.variant()), List.of(), Set.of()))
                .doesNotThrowAnyException();
        fixture.variant().setSku(" ");
        assertThatThrownBy(() -> validator.validateBasic(fixture.product(), List.of(fixture.variant()), List.of(), Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        fixture.variant().setSku("valid");
        fixture.variant().setSellingPrice(null);
        assertThatThrownBy(() -> validator.validateBasic(fixture.product(), List.of(fixture.variant()), List.of(), Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void basic_shouldPreserveGalleryIntegrity_evenWhenPublicationIsNotRequired() {
        CatalogFixtures.ProductFixture fixture = CatalogFixtures.draftProduct();
        var image = CatalogFixtures.image(fixture.product(), 0, false);
        assertThatThrownBy(() -> validator.validateBasic(fixture.product(), List.of(), List.of(image), Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        image.setPrimary(true);
        assertThatCode(() -> validator.validateBasic(fixture.product(), List.of(), List.of(image), Set.of()))
                .doesNotThrowAnyException();
        image.setActive(false);
        assertThatThrownBy(() -> validator.validateBasic(fixture.product(), List.of(), List.of(image), Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void basic_shouldApplySharedContextWithoutChangingGenericFalseZeroOrEmptyFacts() {
        CatalogFixtures.ProductFixture fixture = CatalogFixtures.draftProduct();
        fixture.variant().setTechnicalSpecifications(Map.of("schema_version", 1, "values", Map.of("physical_ram", "generic")));
        assertThatCode(() -> validator.validateBasic(fixture.product(), List.of(fixture.variant()), List.of(), Set.of("tablet")))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validateBasic(fixture.product(), List.of(fixture.variant()), List.of(), Set.of("laptop")))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test
    void basic_shouldAcceptTenButRejectElevenActiveImages_andInvalidPositions() {
        var fixture = CatalogFixtures.draftProduct();
        var images = new java.util.ArrayList<com.example.demo.catalog.entity.ProductImage>();
        for (int i = 0; i < 10; i++) images.add(CatalogFixtures.image(fixture.product(), i, i == 0));
        assertThatCode(() -> validator.validateBasic(fixture.product(), List.of(), images, Set.of())).doesNotThrowAnyException();
        images.add(CatalogFixtures.image(fixture.product(), 10, false));
        assertThatThrownBy(() -> validator.validateBasic(fixture.product(), List.of(), images, Set.of())).isInstanceOf(IllegalArgumentException.class);
        images.removeLast();
        images.getLast().setDisplayOrder(-1);
        assertThatThrownBy(() -> validator.validateBasic(fixture.product(), List.of(), images, Set.of())).isInstanceOf(IllegalArgumentException.class);
        images.getLast().setDisplayOrder(0);
        assertThatThrownBy(() -> validator.validateBasic(fixture.product(), List.of(), images, Set.of())).isInstanceOf(IllegalArgumentException.class);
        images.getLast().setDisplayOrder(9);
        images.getLast().setPrimary(true);
        assertThatThrownBy(() -> validator.validateBasic(fixture.product(), List.of(), images, Set.of())).isInstanceOf(IllegalArgumentException.class);
    }

}
