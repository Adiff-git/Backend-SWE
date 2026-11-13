package com.example.demo.catalog.service;

import com.example.demo.catalog.dto.response.CartVariantState;
import com.example.demo.catalog.entity.ProductStatus;
import com.example.demo.catalog.repository.CartVariantView;
import com.example.demo.catalog.repository.ProductImageRepository;
import com.example.demo.catalog.repository.ProductRepository;
import com.example.demo.catalog.repository.ProductVariantRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Collection;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

class CatalogCartReadServiceImplTest {
    ProductVariantRepository variants = mock(ProductVariantRepository.class);
    ProductRepository products = mock(ProductRepository.class);
    ProductImageRepository images = mock(ProductImageRepository.class);

    @Test void getVariantStates_shouldRetainHiddenIdentityWithoutHiddenDescriptions() {
        UUID variant = UUID.randomUUID();
        CartVariantView row = mock(CartVariantView.class);
        when(row.getVariantId()).thenReturn(variant);
        when(row.getProductId()).thenReturn(UUID.randomUUID());
        when(row.getStatus()).thenReturn(ProductStatus.ARCHIVED);
        when(row.getPrice()).thenReturn(BigDecimal.TEN);
        when(row.getProductName()).thenReturn("Hidden new name");
        when(variants.findCartStates(anyCollection())).thenReturn(List.of(row));
        CartVariantState state = new CatalogCartReadServiceImpl(variants, products, images).getVariantStates(List.of(variant)).get(variant);
        assertThat(state.isPurchasable()).isFalse();
        assertThat(state.getProductName()).isNull();
        assertThat(state.getOptions()).isEmpty();
        assertThat(state.getAmount()).isEqualTo(BigDecimal.TEN.toBigInteger());
        verifyNoInteractions(products, images);
    }

    @Test void getCartPresentation_shouldResolveOnlyAfterOrderedPublicationRecheck() {
        UUID variant = UUID.randomUUID();
        UUID product = UUID.randomUUID();
        CartVariantView row = mock(CartVariantView.class);
        when(row.getVariantId()).thenReturn(variant);
        when(row.getProductId()).thenReturn(product);
        when(row.getStatus()).thenReturn(ProductStatus.PUBLISHED);
        when(row.getPrice()).thenReturn(BigDecimal.ZERO);
        when(row.getOptions()).thenReturn(Map.of());
        when(variants.findCartStates(anyCollection())).thenReturn(List.of(row));
        when(images.findCartPrimaryImages(anyCollection())).thenReturn(List.<Object[]>of(new Object[]{product,"images/key"}));
        when(products.lockStatusesForShare(anyCollection())).thenReturn(List.<Object[]>of(new Object[]{product,"ARCHIVED"}));
        com.example.demo.storage.service.PreparedImageUrls urls = mock(com.example.demo.storage.service.PreparedImageUrls.class);
        CartVariantState state = new CatalogCartReadServiceImpl(variants, products, images).getCartPresentation(List.of(variant), urls).get(variant);
        assertThat(state.isPublished()).isFalse();
        assertThat(state.getPrimaryImageUrl()).isNull();
        verifyNoInteractions(urls);
        verify(products).lockStatusesForShare(List.of(product));
    }

    @Test void getCartPresentation_shouldBatchAndLockInGlobalPostgresUuidOrder() {
        Map<UUID, CartVariantView> rows = new java.util.HashMap<>();
        for (int index=0; index<105; index++) {
            UUID id = UUID.randomUUID();
            CartVariantView row = mock(CartVariantView.class);
            when(row.getVariantId()).thenReturn(id); when(row.getProductId()).thenReturn(id);
            when(row.getStatus()).thenReturn(ProductStatus.PUBLISHED); when(row.getPrice()).thenReturn(BigDecimal.TEN);
            when(row.getOptions()).thenReturn(Map.of()); rows.put(id, row);
        }
        when(variants.findCartStates(anyCollection())).thenAnswer(call -> ((Collection<UUID>)call.getArgument(0)).stream().map(rows::get).toList());
        when(images.findCartPrimaryImages(anyCollection())).thenAnswer(call -> ((Collection<UUID>)call.getArgument(0)).stream()
                .map(id -> new Object[]{id, "image/"+id}).toList());
        when(products.lockStatusesForShare(anyCollection())).thenAnswer(call -> ((Collection<UUID>)call.getArgument(0)).stream()
                .map(id -> new Object[]{id, "PUBLISHED"}).toList());
        var result = new CatalogCartReadServiceImpl(variants, products, images).getCartPresentation(rows.keySet(), key -> "signed:"+key);
        assertThat(result).hasSize(105);
        assertThat(result.values()).allMatch(row -> row.getPrimaryImageUrl() != null);
        ArgumentCaptor<Collection<UUID>> locks = ArgumentCaptor.forClass(Collection.class);
        verify(products, times(2)).lockStatusesForShare(locks.capture());
        List<String> ordered = locks.getAllValues().stream().flatMap(Collection::stream).map(UUID::toString).toList();
        assertThat(ordered).isSorted();
        assertThat(locks.getAllValues().getFirst()).hasSize(100);
        verify(variants, times(2)).findCartStates(anyCollection());
        verify(images, times(4)).findCartPrimaryImages(anyCollection());
        assertThat(new CatalogCartReadServiceImpl(variants, products, images).getCartPresentation(rows.keySet(), null).values())
                .allMatch(row -> row.getPrimaryImageUrl() == null);
    }

    @Test void getVariantStates_shouldDefensivelyRejectFractionalOrNegativePricesAndAcceptZero() {
        UUID id = UUID.randomUUID();
        CartVariantView row = mock(CartVariantView.class);
        when(row.getVariantId()).thenReturn(id); when(row.getProductId()).thenReturn(id);
        when(row.getStatus()).thenReturn(ProductStatus.PUBLISHED); when(row.getOptions()).thenReturn(Map.of());
        when(variants.findCartStates(anyCollection())).thenReturn(List.of(row));
        var service = new CatalogCartReadServiceImpl(variants, products, images);
        for (BigDecimal price : List.of(new BigDecimal("0.5"), BigDecimal.valueOf(-1))) {
            when(row.getPrice()).thenReturn(price);
            assertThat(service.getVariantStates(List.of(id)).get(id).isPurchasable()).isFalse();
        }
        when(row.getPrice()).thenReturn(BigDecimal.ZERO);
        assertThat(service.getVariantStates(List.of(id)).get(id).isPurchasable()).isTrue();
    }
}
