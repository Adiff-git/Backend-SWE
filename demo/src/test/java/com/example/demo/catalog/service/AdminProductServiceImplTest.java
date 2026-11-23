package com.example.demo.catalog.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.demo.catalog.CatalogFixtures;
import com.example.demo.catalog.dto.request.AdminProductRequests.*;
import com.example.demo.catalog.dto.request.CatalogField;
import com.example.demo.catalog.entity.*;
import com.example.demo.catalog.exception.AdminCatalogException;
import com.example.demo.catalog.repository.*;
import com.example.demo.catalog.storage.ProductImageStorage;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class AdminProductServiceImplTest {
    private final ProductRepository products = mock(ProductRepository.class);
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final BrandRepository brands = mock(BrandRepository.class);
    private final CatalogMutationRepository revisions = mock(CatalogMutationRepository.class);
    private final CatalogAggregateMutation mutations = mock(CatalogAggregateMutation.class);
    private final CatalogAdminAggregateReader aggregates = mock(CatalogAdminAggregateReader.class);
    private final ProductImageStorage storage = mock(ProductImageStorage.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-22T00:00:00Z"), ZoneOffset.UTC);
    private final AdminProductServiceImpl service = new AdminProductServiceImpl(products, categories, brands, revisions, mutations, aggregates, storage, clock);
    private final UUID id = UUID.randomUUID();
    private Product current;

    @BeforeEach void setup() {
        current = CatalogFixtures.draftProduct().product();
        when(products.existsById(id)).thenReturn(true);
        doAnswer(call -> {
            CatalogAggregateMutation.ProductCommand<?> command = call.getArgument(3);
            return command.noOp(current) ? command.unchanged(current) : command.apply(current);
        }).when(mutations).product(eq(id), anyLong(), anyLong(), any());
    }

    @Test void create_shouldPersistVariantlessDraft_withoutRequiringOptionalFields() {
        var category = CatalogFixtures.category();
        UUID categoryId = UUID.randomUUID();
        when(categories.findById(categoryId)).thenReturn(Optional.of(category));
        when(products.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(mutations.create(any())).thenAnswer(call -> ((java.util.function.LongFunction<?>) call.getArgument(0)).apply(7));
        service.create(new Create("New product", categoryId, CatalogField.absent(), null, CatalogField.absent()));
        var captor = org.mockito.ArgumentCaptor.forClass(Product.class);
        verify(products).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ProductStatus.DRAFT);
        assertThat(captor.getValue().getLastRestoredAt()).isNull();
        verify(aggregates).validateNew(captor.getValue());
        verifyNoInteractions(storage, brands);
    }

    @Test void patch_shouldPreserveOmittedFields_andAllowExplicitOptionalClear() {
        current.setDescription("keep"); current.setBrand(new Brand("old", "Old"));
        service.update(id, new Patch(0, 0, CatalogField.supplied("Renamed"), CatalogField.absent(), CatalogField.absent(), null, CatalogField.absent()));
        assertThat(current.getName()).isEqualTo("Renamed"); assertThat(current.getDescription()).isEqualTo("keep");
        assertThat(current.getBrand()).isNotNull();
        service.update(id, new Patch(0, 0, CatalogField.absent(), CatalogField.absent(), CatalogField.supplied(null), null, CatalogField.supplied(null)));
        assertThat(current.getBrand()).isNull(); assertThat(current.getDescription()).isNull();
        verify(aggregates, times(2)).validateCurrent(current);
    }

    @Test void patch_shouldRejectArchivedEditsAndMissingObservedBrandVersion() {
        current.setStatus(ProductStatus.ARCHIVED);
        assertThatThrownBy(() -> service.update(id, new Patch(0,0,CatalogField.supplied("Edit"),CatalogField.absent(),CatalogField.absent(),null,CatalogField.absent())))
                .isInstanceOfSatisfying(AdminCatalogException.class, e -> assertThat(e.getStatus()).isEqualTo(409));
        current.setStatus(ProductStatus.DRAFT);
        assertThatThrownBy(() -> service.update(id, new Patch(0,0,CatalogField.absent(),CatalogField.absent(),CatalogField.supplied(UUID.randomUUID()),null,CatalogField.absent())))
                .isInstanceOfSatisfying(AdminCatalogException.class, e -> assertThat(e.getStatus()).isEqualTo(400));
    }

    @ParameterizedTest @EnumSource(ProductStatus.class)
    void transition_shouldRespectEverySourceAndCommand_withoutImplicitPublication(ProductStatus source) {
        for (Action action : Action.values()) {
            current.setStatus(source); current.setLastRestoredAt(null);
            ProductStatus target = switch(action) { case PUBLISH -> ProductStatus.PUBLISHED; case UNPUBLISH -> ProductStatus.UNPUBLISHED; case ARCHIVE -> ProductStatus.ARCHIVED; case RESTORE -> ProductStatus.DRAFT; };
            boolean valid = action == Action.ARCHIVE || action == Action.PUBLISH && source != ProductStatus.ARCHIVED
                    || action == Action.UNPUBLISH && Set.of(ProductStatus.PUBLISHED,ProductStatus.UNPUBLISHED).contains(source)
                    || action == Action.RESTORE && Set.of(ProductStatus.ARCHIVED,ProductStatus.DRAFT).contains(source);
            if (!valid) {
                assertThatThrownBy(() -> service.transition(id,0,0,action)).isInstanceOf(AdminCatalogException.class);
                assertThat(current.getStatus()).isEqualTo(source);
            } else {
                service.transition(id,0,0,action);
                assertThat(current.getStatus()).isEqualTo(target);
                assertThat(current.getLastRestoredAt()).isEqualTo(action == Action.RESTORE && source == ProductStatus.ARCHIVED ? clock.instant() : null);
            }
        }
    }

    @Test void publish_shouldValidateEvenCurrentTargetNoOp_andRejectInvalidReadiness() {
        current.setStatus(ProductStatus.PUBLISHED);
        doThrow(new AdminCatalogException(422,"CATALOG_PUBLICATION_INVALID","Not ready")).when(aggregates).requirePublication(current);
        assertThatThrownBy(() -> service.transition(id,0,0,Action.PUBLISH)).isInstanceOf(AdminCatalogException.class);
        verify(aggregates).requirePublication(current);
    }

    @Test void update_shouldLeaveStaleRejectionToAggregateBoundary_beforeApplyingFields() {
        reset(mutations);
        doThrow(new AdminCatalogException(409,"CATALOG_STALE_VERSION","Reload")).when(mutations).product(eq(id),eq(9L),eq(0L),any());
        String old = current.getName();
        assertThatThrownBy(() -> service.update(id,new Patch(9,0,CatalogField.supplied("lost"),CatalogField.absent(),CatalogField.absent(),null,CatalogField.absent())))
                .isInstanceOf(AdminCatalogException.class);
        assertThat(current.getName()).isEqualTo(old);
        verify(aggregates).hasImages(id);
        verifyNoMoreInteractions(aggregates);
    }
    @Test void brandAssignment_shouldCompareObservedRevision_underShareLock() {
        UUID brand=UUID.randomUUID();
        when(revisions.lockVersions(CatalogMutationRepository.Resource.BRAND,List.of(brand),false)).thenReturn(Map.of(brand,3L));
        assertThatThrownBy(()->service.update(id,new Patch(0,0,CatalogField.absent(),CatalogField.absent(),CatalogField.supplied(brand),2L,CatalogField.absent())))
                .isInstanceOfSatisfying(AdminCatalogException.class,e->{assertThat(e.getCode()).isEqualTo("CATALOG_STALE_VERSION");assertThat(e.getProperties()).containsEntry("currentVersion","3");});
        assertThat(current.getBrand()).isNull();
        verify(brands,never()).findById(any());
    }
    @Test void storageFailure_shouldPreventRevisionClaim_andNeverExposeStorageDetails() {
        when(aggregates.hasImages(id)).thenReturn(true);
        when(storage.prepareReadSigner()).thenThrow(new IllegalStateException("private-object-key"));
        assertThatThrownBy(()->service.transition(id,0,0,Action.ARCHIVE)).isInstanceOfSatisfying(AdminCatalogException.class,
                e->{assertThat(e.getStatus()).isEqualTo(503);assertThat(e.getMessage()).doesNotContain("private-object-key");});
        verifyNoInteractions(mutations);
        assertThat(current.getStatus()).isEqualTo(ProductStatus.DRAFT);
    }
    @Test void currentNoOp_shouldPreserveDataAndProvenance_withoutApplyingValidationWrites() {
        var timestamp=clock.instant();current.setLastRestoredAt(timestamp);
        service.update(id,new Patch(0,0,CatalogField.supplied(current.getName()),CatalogField.absent(),CatalogField.absent(),null,CatalogField.absent()));
        assertThat(current.getLastRestoredAt()).isSameAs(timestamp);
        verify(aggregates,never()).validateCurrent(any());
        verifyNoInteractions(categories,brands);
    }

}
