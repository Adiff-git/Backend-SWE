package com.example.demo.cart.service;

import com.example.demo.cart.dto.request.AddCartItemRequest;
import com.example.demo.cart.dto.request.UpdateCartItemRequest;
import com.example.demo.cart.exception.CartItemNotFoundException;
import com.example.demo.cart.repository.CartItemRepository;
import com.example.demo.cart.service.impl.CartServiceImpl;
import com.example.demo.cart.validator.CartValidator;
import com.example.demo.catalog.service.CatalogCartReadService;
import com.example.demo.storage.service.ImageUrlResolver;
import com.example.demo.storage.service.PreparedImageUrls;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CartServiceImplTest {
    CartTransactionService transactions = mock(CartTransactionService.class);
    CartItemRepository items = mock(CartItemRepository.class);
    CatalogCartReadService catalog = mock(CatalogCartReadService.class);
    ImageUrlResolver resolver = mock(ImageUrlResolver.class);
    CartService service = new CartServiceImpl(transactions, items, catalog, resolver, new CartValidator());

    @Test void addItem_shouldPrepareResourceFreeUrlsBeforeEnteringWriteTransaction() {
        UUID variant = UUID.randomUUID();
        AddCartItemRequest request = new AddCartItemRequest();
        request.setVariantId(variant);
        request.setQuantity(2);
        PreparedImageUrls urls = key -> "url";
        when(items.findVariantIdsForOwner(1L)).thenReturn(List.of());
        when(catalog.hasImageCandidates(List.of(variant))).thenReturn(true);
        when(resolver.prepare()).thenReturn(urls);
        service.addItem(1L, request);
        InOrder order = inOrder(resolver, transactions);
        order.verify(resolver).prepare();
        order.verify(transactions).addItem(1L, request, urls);
    }

    @Test void getCart_shouldAvoidImagePreparationForMissingCart() {
        when(items.findVariantIdsForOwner(1L)).thenReturn(List.of());
        service.getCart(1L);
        verifyNoInteractions(catalog, resolver);
    }

    @Test void updateItem_shouldRejectForeignSourceBeforeCatalogPreflight() {
        UpdateCartItemRequest request = new UpdateCartItemRequest();
        request.setQuantity(1); request.setVariantId(UUID.randomUUID());
        assertThatThrownBy(() -> service.updateItem(1L, 9L, request)).isInstanceOf(CartItemNotFoundException.class);
        verifyNoInteractions(catalog, resolver, transactions);
    }
}
