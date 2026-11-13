package com.example.demo.cart.service;

import com.example.demo.cart.calculator.CartCalculator;
import com.example.demo.cart.converter.CartConverter;
import com.example.demo.cart.dto.request.AddCartItemRequest;
import com.example.demo.cart.dto.request.UpdateCartItemRequest;
import com.example.demo.cart.entity.Cart;
import com.example.demo.cart.entity.CartItem;
import com.example.demo.cart.repository.CartItemRepository;
import com.example.demo.cart.repository.CartRepository;
import com.example.demo.cart.service.impl.CartTransactionServiceImpl;
import com.example.demo.cart.validator.CartValidator;
import com.example.demo.catalog.dto.response.CartVariantState;
import com.example.demo.catalog.service.CatalogCartReadService;
import com.example.demo.user.service.UserService;
import java.math.BigInteger;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThat;

class CartTransactionServiceImplTest {
    @Test void addItem_shouldRecheckAfterUserCoordinationAndUseExistingWinner() {
        CartRepository carts = mock(CartRepository.class);
        CartItemRepository items = mock(CartItemRepository.class);
        UserService users = mock(UserService.class);
        CatalogCartReadService catalog = mock(CatalogCartReadService.class);
        Cart winner = mock(Cart.class);
        when(winner.getId()).thenReturn(7L);
        when(carts.findOwnedForUpdate(1L)).thenReturn(Optional.empty(), Optional.of(winner));
        List<CartItem> rows = new java.util.ArrayList<>();
        when(items.findByCartIdOrderByAddedAtDescIdAsc(7L)).thenReturn(rows);
        when(items.saveAndFlush(any(CartItem.class))).thenAnswer(call -> {
            CartItem row = call.getArgument(0); ReflectionTestUtils.setField(row, "id", 44L); rows.add(row); return row;
        });
        UUID variant = UUID.randomUUID();
        var state = new CartVariantState(variant, UUID.randomUUID(), true, BigInteger.TEN, "P", "S", Map.of(), null);
        when(catalog.getVariantStates(anyCollection())).thenReturn(Map.of(variant, state));
        when(catalog.getCartPresentation(anyCollection(), org.mockito.ArgumentMatchers.isNull())).thenReturn(Map.of(variant,state));
        var service = new CartTransactionServiceImpl(carts, items, users, catalog,
                new CartValidator(), new CartCalculator(), new CartConverter(), Clock.systemUTC());
        ReflectionTestUtils.setField(service, "lockTimeoutMs", 5000L);
        var request = new AddCartItemRequest(); request.setVariantId(variant); request.setQuantity(2);
        assertThat(service.addItem(1L, request, null).getResponse().getItems()).hasSize(1);
        InOrder order = inOrder(carts, users, items);
        order.verify(carts).setLockTimeout("5000ms"); order.verify(carts).findOwnedForUpdate(1L);
        order.verify(users).getUserEntityForUpdate(1L);
        order.verify(carts).setLockTimeout("5000ms"); order.verify(carts).findOwnedForUpdate(1L);
        order.verify(items).findByCartIdOrderByAddedAtDescIdAsc(7L);
        verify(carts, never()).saveAndFlush(any(Cart.class));
    }
    @Test void updateItem_shouldMergeOnlyTargetAndRequestKeepingTargetSelectionAndTime() {
        CartRepository carts = mock(CartRepository.class);
        CartItemRepository items = mock(CartItemRepository.class);
        CatalogCartReadService catalog = mock(CatalogCartReadService.class);
        Cart cart = mock(Cart.class);
        when(cart.getId()).thenReturn(7L);
        when(carts.findOwnedForUpdate(1L)).thenReturn(Optional.of(cart));
        UUID parent = UUID.randomUUID(), first = UUID.randomUUID(), second = UUID.randomUUID();
        CartItem source = new CartItem(7L, java.time.Instant.EPOCH);
        ReflectionTestUtils.setField(source, "id", 8L);
        source.setProductId(parent); source.setProductVariantId(first); source.setQuantity(900);
        CartItem target = new CartItem(7L, java.time.Instant.EPOCH.plusSeconds(1));
        ReflectionTestUtils.setField(target, "id", 9L);
        target.setProductId(parent); target.setProductVariantId(second); target.setQuantity(600); target.setSelected(false);
        when(items.findByCartIdOrderByAddedAtDescIdAsc(7L)).thenReturn(List.of(source, target), List.of(target));
        CartVariantState state = new CartVariantState(second, parent, true, BigInteger.TEN, "P", "S", Map.of(), null);
        when(catalog.getVariantStates(anyCollection())).thenReturn(Map.of(second, state));
        when(catalog.getCartPresentation(anyCollection(), org.mockito.ArgumentMatchers.isNull())).thenReturn(Map.of(second,state));
        CartTransactionServiceImpl service = new CartTransactionServiceImpl(carts, items, mock(UserService.class), catalog,
                new CartValidator(), new CartCalculator(), new CartConverter(), Clock.systemUTC());
        ReflectionTestUtils.setField(service, "lockTimeoutMs", 5000L);
        UpdateCartItemRequest request = new UpdateCartItemRequest(); request.setVariantId(second); request.setQuantity(400);
        var response = service.updateItem(1L, 8L, request, null);
        assertThat(target.getQuantity()).isEqualTo(1000);
        assertThat(target.isSelected()).isFalse();
        assertThat(target.getAddedAt()).isEqualTo(java.time.Instant.EPOCH.plusSeconds(1));
        assertThat(response.getItems()).hasSize(1);
        verify(items).delete(source);
    }
    @Test void addItem_shouldSetTransactionLocalTimeoutBeforeLockAndReadChildrenAfterLock() {
        CartRepository carts = mock(CartRepository.class);
        CartItemRepository items = mock(CartItemRepository.class);
        CatalogCartReadService catalog = mock(CatalogCartReadService.class);
        Cart cart = mock(Cart.class);
        when(cart.getId()).thenReturn(7L);
        when(carts.findOwnedForUpdate(1L)).thenReturn(Optional.of(cart));
        when(items.findByCartIdOrderByAddedAtDescIdAsc(7L)).thenReturn(List.of());
        UUID variant = UUID.randomUUID();
        CartVariantState state = new CartVariantState(variant, UUID.randomUUID(), true, BigInteger.TEN, "P", "S", Map.of(), null);
        when(catalog.getVariantStates(anyCollection())).thenReturn(Map.of(variant,state));
        when(catalog.getCartPresentation(anyCollection(), org.mockito.ArgumentMatchers.isNull())).thenReturn(Map.of(variant,state));
        CartTransactionServiceImpl service = new CartTransactionServiceImpl(carts, items, mock(UserService.class),
                catalog, new CartValidator(), new CartCalculator(), new CartConverter(), Clock.systemUTC());
        ReflectionTestUtils.setField(service, "lockTimeoutMs", 5000L);
        AddCartItemRequest request = new AddCartItemRequest();
        request.setVariantId(variant);
        request.setQuantity(1);
        service.addItem(1L, request, null);
        InOrder order = inOrder(carts, items);
        order.verify(carts).setLockTimeout("5000ms");
        order.verify(carts).findOwnedForUpdate(1L);
        order.verify(items).findByCartIdOrderByAddedAtDescIdAsc(7L);
    }
}
