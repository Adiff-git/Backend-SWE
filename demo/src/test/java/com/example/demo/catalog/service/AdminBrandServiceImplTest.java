package com.example.demo.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import com.example.demo.catalog.converter.AdminCatalogConverter;
import com.example.demo.catalog.dto.request.AdminTaxonomyRequests.BrandCreate;
import com.example.demo.catalog.dto.request.AdminTaxonomyRequests.BrandRename;
import com.example.demo.catalog.entity.Brand;
import com.example.demo.catalog.exception.AdminCatalogException;
import com.example.demo.catalog.repository.BrandRepository;
import com.example.demo.catalog.repository.CatalogTaxonomyQueries;
import com.example.demo.catalog.repository.CatalogMutationRepository;
import com.example.demo.catalog.repository.CatalogMutationRepository.Resource;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class AdminBrandServiceImplTest {
    final BrandRepository brands=mock(BrandRepository.class);final CatalogTaxonomyQueries queries=mock(CatalogTaxonomyQueries.class);
    final CatalogMutationRepository revisions=mock(CatalogMutationRepository.class);final PlatformTransactionManager manager=mock(PlatformTransactionManager.class);
    final AdminBrandServiceImpl service=new AdminBrandServiceImpl(brands,queries,revisions,new AdminCatalogConverter(),manager);
    final UUID id=UUID.randomUUID();final Brand brand=new Brand("immutable-code","Display");
    @BeforeEach void setup(){when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());when(brands.findById(id)).thenReturn(Optional.of(brand));when(revisions.lockVersions(Resource.BRAND,java.util.List.of(id),true)).thenReturn(Map.of(id,0L));when(queries.normalizeName(anyString())).thenAnswer(call->call.getArgument(0));}
    @Test void create_shouldRejectNormalizedNameConflict_withoutGuessingOrMergingIdentity(){
        when(queries.normalizeName("  Brand  ")).thenReturn("Brand");when(queries.brandNameExists("Brand",null)).thenReturn(true);
        assertThatThrownBy(()->service.create(new BrandCreate("code","  Brand  "))).isInstanceOfSatisfying(AdminCatalogException.class,e->assertThat(e.getCode()).isEqualTo("CATALOG_NAME_CONFLICT"));verify(brands,never()).saveAndFlush(any());
    }
    @Test void rename_shouldPreserveImmutableCode_andUseOnlyOwnRevision(){
        service.update(id,new BrandRename(0,"Renamed"));assertThat(brand.getCode()).isEqualTo("immutable-code");assertThat(brand.getName()).isEqualTo("Renamed");verify(revisions).claim(Resource.BRAND,id,0);verify(revisions,never()).lockTaxonomy(anyBoolean());
    }
    @Test void delete_shouldRejectArchivedOrOtherProductReferences_withoutLockingProducts(){
        when(queries.brandReferenced(id)).thenReturn(true);assertThatThrownBy(()->service.delete(id,0)).isInstanceOfSatisfying(AdminCatalogException.class,e->assertThat(e.getStatus()).isEqualTo(409));verify(brands,never()).delete(any());verify(revisions,never()).claim(any(),any(),anyLong());
    }
    @Test void update_shouldRejectStaleOwnRevision_beforeChangingName(){
        when(revisions.lockVersions(Resource.BRAND,java.util.List.of(id),true)).thenReturn(Map.of(id,3L));
        assertThatThrownBy(()->service.update(id,new BrandRename(0,"Lost edit"))).isInstanceOf(AdminCatalogException.class);assertThat(brand.getName()).isEqualTo("Display");
    }
}
