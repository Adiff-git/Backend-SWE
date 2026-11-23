package com.example.demo.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import com.example.demo.catalog.entity.*;
import com.example.demo.catalog.validator.*;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CatalogFixturesTest {
    @Test void readyFactoryAddsExactlyOneExplicitImage_withoutChangingGenericGalleryFactories() {
        EntityManager entities = mock(EntityManager.class);
        var fixture = CatalogFixtures.persistReadyProduct(entities);
        ArgumentCaptor<Object> persisted = ArgumentCaptor.forClass(Object.class);
        verify(entities, times(4)).persist(persisted.capture());
        ProductImage image = persisted.getAllValues().stream().filter(ProductImage.class::isInstance)
                .map(ProductImage.class::cast).findFirst().orElseThrow();
        assertThat(image.getProduct()).isSameAs(fixture.product());
        assertThat(image.isActive()).isTrue();
        assertThat(fixture.variant().isEnabled()).isTrue();
        var validator = new ProductPublicationValidator(new AdminCatalogValidator(new CatalogDataValidator(),
                new TechnicalSpecificationValidator(), new ProductImageContentValidator()));
        assertThat(validator.violations(fixture.product(), List.of(fixture.variant()), List.of(image), Set.of())).isEmpty();
        reset(entities);
        CatalogFixtures.persistProduct(entities, ProductStatus.DRAFT);
        verify(entities, times(3)).persist(any());
    }
}
