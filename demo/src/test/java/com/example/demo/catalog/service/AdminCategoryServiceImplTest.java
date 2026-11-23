package com.example.demo.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import com.example.demo.catalog.CatalogFixtures;
import com.example.demo.catalog.converter.AdminCatalogConverter;
import com.example.demo.catalog.dto.request.CatalogField;
import com.example.demo.catalog.dto.request.AdminTaxonomyRequests.CategoryCreate;
import com.example.demo.catalog.dto.request.AdminTaxonomyRequests.CategoryPatch;
import com.example.demo.catalog.entity.Category;
import com.example.demo.catalog.exception.AdminCatalogException;
import com.example.demo.catalog.repository.CategoryRepository;
import com.example.demo.catalog.repository.CatalogMutationRepository;
import com.example.demo.catalog.repository.CatalogMutationRepository.Resource;
import com.example.demo.catalog.repository.CatalogTaxonomyQueries;
import com.example.demo.catalog.repository.CatalogTaxonomyQueries.BindingScope;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class AdminCategoryServiceImplTest {
    final CategoryRepository categories=mock(CategoryRepository.class);
    final CatalogTaxonomyQueries queries=mock(CatalogTaxonomyQueries.class);
    final CatalogMutationRepository revisions=mock(CatalogMutationRepository.class);
    final CatalogAdminAggregateReader aggregates=mock(CatalogAdminAggregateReader.class);
    final PlatformTransactionManager manager=mock(PlatformTransactionManager.class);
    final AdminCategoryServiceImpl service=new AdminCategoryServiceImpl(categories,queries,revisions,aggregates,new AdminCatalogConverter(),manager);
    final UUID id=UUID.randomUUID();final Category category=CatalogFixtures.category();
    @BeforeEach void setup(){
        ReflectionTestUtils.setField(category,"id",id);when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(categories.findById(id)).thenReturn(java.util.Optional.of(category));when(revisions.lockVersions(eq(Resource.CATEGORY),any(),eq(true))).thenReturn(Map.of(id,0L));
        when(queries.normalizeName(anyString())).thenAnswer(call->call.getArgument(0));when(queries.bindings()).thenReturn(Map.of());
        when(categories.saveAndFlush(any())).thenAnswer(call->call.getArgument(0));
    }
    @Test void create_shouldUseAuthoritativeNormalizedSiblingOrRootEquality_withoutMerging() {
        when(queries.normalizeName("\u00a0Laptop\u00a0")).thenReturn("Laptop");when(queries.categoryNameExists("Laptop",null,null)).thenReturn(true);
        assertThatThrownBy(()->service.create(new CategoryCreate(0,"\u00a0Laptop\u00a0",null))).isInstanceOfSatisfying(AdminCatalogException.class,e->assertThat(e.getCode()).isEqualTo("CATALOG_NAME_CONFLICT"));
        verify(categories,never()).saveAndFlush(any());
    }
    @Test void update_shouldRejectSelfAndDescendantParent_underExclusiveTaxonomyGate() {
        UUID descendant=UUID.randomUUID();when(revisions.lockVersions(eq(Resource.CATEGORY),any(),eq(true))).thenReturn(Map.of(id,0L,descendant,0L));
        when(queries.descendants(id)).thenReturn(Set.of(id,descendant));
        assertThatThrownBy(()->service.update(id,new CategoryPatch(0,0,CatalogField.absent(),CatalogField.supplied(descendant)))).isInstanceOf(AdminCatalogException.class);
        verify(revisions).lockTaxonomy(true);verify(revisions,never()).claimTaxonomy(anyLong());assertThat(category.getParentCategory()).isNull();
    }
    @Test void delete_shouldCheckAllStateReferences_andNeverCascadeOrReassign() {
        when(queries.categoryReferenced(id)).thenReturn(true);
        assertThatThrownBy(()->service.delete(id,0,0)).isInstanceOfSatisfying(AdminCatalogException.class,e->assertThat(e.getStatus()).isEqualTo(409));
        verify(categories,never()).delete(any());verify(revisions,never()).claimTaxonomy(anyLong());
    }
    @Test void staleTaxonomy_shouldRejectBeforeCategoryWork_orVersionClaim() {
        when(revisions.lockTaxonomy(true)).thenReturn(3L);
        assertThatThrownBy(()->service.delete(id,0,0)).isInstanceOfSatisfying(AdminCatalogException.class,e->assertThat(e.getCode()).isEqualTo("CATALOG_STALE_VERSION"));
        verifyNoInteractions(categories);verify(revisions,never()).claimTaxonomy(anyLong());
    }
    @Test void rename_shouldValidateNewlyApplicablePublishedProductsOutsideChangedSubtree_andRequestRollback() {
        UUID outsideCategory=UUID.randomUUID(),product=UUID.randomUUID();
        when(queries.bindings()).thenReturn(Map.of("laptop",new BindingScope(2,null,Set.of())),Map.of("laptop",new BindingScope(1,id,Set.of(outsideCategory))));
        when(queries.publishedInCategories(Set.of(outsideCategory))).thenReturn(List.of(product));
        var published=CatalogFixtures.publishedProduct().product();when(revisions.freshProduct(product)).thenReturn(published);
        doThrow(new AdminCatalogException(422,"CATALOG_PUBLICATION_INVALID","Wrong applicable fact type/unit")).when(aggregates).requirePublication(published);
        assertThatThrownBy(()->service.update(id,new CategoryPatch(0,0,CatalogField.supplied("Renamed"),CatalogField.absent()))).isInstanceOf(AdminCatalogException.class);
        verify(revisions).lockVersions(Resource.PRODUCT,List.of(product),true);verify(aggregates).requirePublication(published);verify(manager).rollback(any());
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.MethodSource("outerWhitespace")
    void create_shouldSendEveryApprovedOuterWhitespaceToDatabaseNormalizer_withoutJavaEquality(char whitespace) {
        String raw=whitespace+"Laptop"+whitespace;when(queries.normalizeName(raw)).thenReturn("Laptop");
        service.create(new CategoryCreate(0,raw,null));verify(queries).normalizeName(raw);verify(queries).categoryNameExists("Laptop",null,null);
    }
    static java.util.stream.Stream<Character> outerWhitespace(){return java.util.stream.IntStream.of(9,10,11,12,13,32,133,160,5760,8192,8193,8194,8195,8196,8197,8198,8199,8200,8201,8202,8232,8233,8239,8287,12288).mapToObj(value->(char)value);}
    @Test void delete_shouldRevalidateOutsidePublishedProducts_whenRemovingGlobalAmbiguity() {
        UUID outside=UUID.randomUUID(),product=UUID.randomUUID();
        when(queries.bindings()).thenReturn(Map.of("laptop",new BindingScope(2,null,Set.of())),Map.of("laptop",new BindingScope(1,outside,Set.of(outside))));
        when(queries.publishedInCategories(Set.of(outside))).thenReturn(List.of(product));
        var published=CatalogFixtures.publishedProduct().product();when(revisions.freshProduct(product)).thenReturn(published);
        doThrow(new AdminCatalogException(422,"CATALOG_PUBLICATION_INVALID","Applicable type/unit rejected")).when(aggregates).requirePublication(published);
        assertThatThrownBy(()->service.delete(id,0,0)).isInstanceOf(AdminCatalogException.class);
        verify(categories).delete(category);verify(aggregates).requirePublication(published);verify(manager).rollback(any());
    }

}
