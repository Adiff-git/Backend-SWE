package com.example.demo.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.demo.catalog.entity.ProductStatus;
import com.example.demo.catalog.exception.CatalogConflictException;
import com.example.demo.catalog.repository.CartVariantView;
import com.example.demo.catalog.repository.ProductRepository;
import com.example.demo.catalog.repository.ProductVariantRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogCheckoutServiceImplTest {
    private final ProductRepository products=mock(ProductRepository.class);
    private final ProductVariantRepository variants=mock(ProductVariantRepository.class);
    private final CatalogCheckoutServiceImpl service=new CatalogCheckoutServiceImpl(products,variants);

    @Test void getCheckoutVariantStates_shouldReadEnabledAndCurrentPriceOnlyAfterParentAndChildLocks(){
        UUID id=UUID.randomUUID(),parent=UUID.randomUUID();
        when(variants.findCheckoutParents(List.of(id))).thenReturn(List.<Object[]>of(new Object[]{id,parent}));
        CartVariantView row=row(id,parent,false);
        when(variants.findCheckoutStates(List.of(id))).thenReturn(List.of(row));
        var current=service.getCheckoutVariantStates(List.of(id,id)).get(id);
        assertThat(current.isEnabled()).isFalse();assertThat(current.getPrice()).isEqualByComparingTo("25");
        var order=inOrder(products,variants);
        order.verify(variants).findCheckoutParents(List.of(id));order.verify(products).lockStatusesForShare(List.of(parent));
        order.verify(variants).lockCheckoutVariants(List.of(id));order.verify(variants).findCheckoutStates(List.of(id));
    }

    @Test void getCheckoutVariantStates_shouldRejectUnexpectedParentInsteadOfAcceptingUnprotectedRow(){
        UUID id=UUID.randomUUID(),parent=UUID.randomUUID();
        when(variants.findCheckoutParents(anyCollection())).thenReturn(List.<Object[]>of(new Object[]{id,parent}));
        var moved=row(id,UUID.randomUUID(),true);
        when(variants.findCheckoutStates(anyCollection())).thenReturn(List.of(moved));
        assertThatThrownBy(() -> service.getCheckoutVariantStates(List.of(id))).isInstanceOf(CatalogConflictException.class);
    }

    private CartVariantView row(UUID id,UUID parent,boolean enabled){
        var row=mock(CartVariantView.class);
        when(row.getVariantId()).thenReturn(id);when(row.getProductId()).thenReturn(parent);
        when(row.getStatus()).thenReturn(ProductStatus.PUBLISHED);when(row.getEnabled()).thenReturn(enabled);
        when(row.getPrice()).thenReturn(BigDecimal.valueOf(25));when(row.getOptions()).thenReturn(Map.of());
        return row;
    }
}
