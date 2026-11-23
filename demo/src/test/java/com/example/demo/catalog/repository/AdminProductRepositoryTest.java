package com.example.demo.catalog.repository;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.demo.catalog.dto.request.AdminProductRequests.*;
import com.example.demo.catalog.entity.ProductStatus;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

class AdminProductRepositoryTest {
    @Test void statement_shouldShareSelectionForCountAndPage_andBindLiteralAnySkuFilters() {
        var criteria=new Query(PageRequest.of(2,20),Set.of(ProductStatus.DRAFT,ProductStatus.ARCHIVED),UUID.randomUUID(),true,UUID.randomUUID(),"a%_!'",Ordering.NAME_DESC);
        var compiled=AdminProductRepositoryImpl.statement(criteria);
        assertThat(compiled.sql()).contains("WITH RECURSIVE", "UNION SELECT c.id", "FROM selected", "EXISTS (SELECT 1 FROM product_variants", "ORDER BY name DESC, id DESC", "ORDER BY p.name DESC, p.id DESC");
        assertThat(compiled.sql()).doesNotContain("v.enabled AND", "a%_!'", "ILIKE", "similarity(");
        assertThat(compiled.parameters()).containsEntry("offset",40L).containsEntry("keyword","%a!%!_!!'%");
    }
    @Test void findAdminPage_shouldKeepAccurateTotalsForOutOfRangePage_withoutPhantomItem() {
        EntityManager entities=mock(EntityManager.class); jakarta.persistence.Query query=mock(jakarta.persistence.Query.class);
        when(entities.createNativeQuery(anyString())).thenReturn(query); when(query.setParameter(anyString(),any())).thenReturn(query);
        Object[] row=new Object[15]; row[13]=23L;row[14]=7L;
        when(query.getResultList()).thenReturn(Collections.singletonList(row));
        var page=new AdminProductRepositoryImpl(entities).findAdminPage(new Query(PageRequest.of(9,20),Set.of(),null,false,null,null,Ordering.ID_ASC));
        assertThat(page.getItems()).isEmpty();assertThat(page.getTotalItems()).isEqualTo(23);assertThat(page.getTotalPages()).isEqualTo(2);assertThat(page.getTaxonomyVersion()).isEqualTo("7");
    }
}
