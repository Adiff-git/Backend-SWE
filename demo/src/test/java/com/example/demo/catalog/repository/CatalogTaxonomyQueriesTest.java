package com.example.demo.catalog.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.example.demo.catalog.dto.request.AdminTaxonomyRequests.Ordering;
import jakarta.persistence.EntityManager;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;

/** Bound SQL/mapping checks only; PostgreSQL collation and full taxonomy races remain T049/T070. */
class CatalogTaxonomyQueriesTest {
    final EntityManager entities=mock(EntityManager.class);
    final jakarta.persistence.Query query=mock(jakarta.persistence.Query.class);
    final CatalogTaxonomyQueries repository=new CatalogTaxonomyQueries(entities);
    void setup(){when(entities.createNativeQuery(anyString())).thenReturn(query);when(query.setParameter(anyString(),org.mockito.ArgumentMatchers.any())).thenReturn(query);}
    @Test void categories_shouldKeepEmptyPageTotals_andBindRootLiteralSearch_withoutHiddenCap(){
        setup();when(query.getResultList()).thenReturn(Collections.singletonList(new Object[]{null,null,null,null,201L,4L}));
        var page=repository.categories(new com.example.demo.catalog.dto.request.AdminTaxonomyRequests.Query(PageRequest.of(99,100),"%_!",Ordering.NAME_ASC,true,null));
        assertThat(page.getItems()).isEmpty();assertThat(page.getTotalItems()).isEqualTo(201);assertThat(page.getTotalPages()).isEqualTo(3);assertThat(page.getTaxonomyVersion()).isEqualTo("4");
        var sql=ArgumentCaptor.forClass(String.class);verify(entities).createNativeQuery(sql.capture());assertThat(sql.getValue()).contains("parent_category_id IS NULL","FROM selected","ORDER BY p.name ASC, p.id ASC").doesNotContain("%_!");
        verify(query).setParameter("q","%!%!_!!%");verify(query).setParameter("limit",100);verify(query).setParameter("offset",9900L);
    }
    @Test void names_shouldUseDatabaseNormalizerAndScopedEquality_forNullRootAndExcludedIdentity(){
        setup();when(query.getSingleResult()).thenReturn(true);UUID id=UUID.randomUUID();
        assertThat(repository.categoryNameExists("Laptop",null,id)).isTrue();
        var sql=ArgumentCaptor.forClass(String.class);verify(entities).createNativeQuery(sql.capture());assertThat(sql.getValue()).contains("catalog_name_key(:name)","parent_category_id IS NOT DISTINCT FROM CAST(:parent AS uuid)");
        verify(query).setParameter("exclude",id);verify(query).setParameter("parent",null);
    }
    @Test void bindings_shouldUseSameSharedAncestryProjection_andRetainGlobalAmbiguity(){
        setup();UUID id=UUID.randomUUID();when(query.getResultList()).thenReturn(List.of(new Object[]{"laptop",2L,null,null},new Object[]{"tablet",1L,id,id}));
        var result=repository.bindings();assertThat(result.get("laptop").matches()).isEqualTo(2);assertThat(result.get("laptop").categories()).isEmpty();assertThat(result.get("tablet").categories()).containsExactly(id);
        var sql=ArgumentCaptor.forClass(String.class);verify(entities).createNativeQuery(sql.capture());assertThat(sql.getValue()).contains(CatalogApplicabilitySql.registryCtes());
    }
}
