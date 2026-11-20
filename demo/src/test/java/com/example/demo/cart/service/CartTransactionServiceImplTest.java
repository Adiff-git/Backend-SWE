package com.example.demo.cart.service;

import com.example.demo.cart.calculator.CartCalculator;
import com.example.demo.cart.converter.CartConverter;
import com.example.demo.cart.dto.request.AddCartItemRequest;
import com.example.demo.cart.dto.request.ChangeCartVariantRequest;
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
    @Test void addItem_shouldRejectTheAccumulatedStockDemandBeforeChangingTheExistingRow() {
        CartRepository carts = mock(CartRepository.class);
        CartItemRepository items = mock(CartItemRepository.class);
        CatalogCartReadService catalog = mock(CatalogCartReadService.class);
        Cart cart = mock(Cart.class);
        when(cart.getVoucherEntitlementId()).thenReturn(null);
        when(cart.getId()).thenReturn(7L);
        when(carts.findOwnedForUpdate(1L)).thenReturn(Optional.of(cart));
        UUID variant = UUID.randomUUID();
        CartItem existing = new CartItem(7L, java.time.Instant.EPOCH);
        ReflectionTestUtils.setField(existing, "id", 9L);
        existing.setProductVariantId(variant);
        existing.setQuantity(2);
        when(items.findByCartIdOrderByAddedAtDescIdAsc(7L)).thenReturn(List.of(existing));
        var state = new CartVariantState(variant, UUID.randomUUID(), true, BigInteger.TEN, "P", "S", Map.of(), null,
                new com.example.demo.inventory.dto.response.InventoryAvailability(3, "IN_STOCK"));
        when(catalog.getVariantStates(anyCollection())).thenReturn(Map.of(variant, state));
        var service = new CartTransactionServiceImpl(carts, items, mock(UserService.class), catalog,
                new CartValidator(), new CartCalculator(), new CartConverter(), Clock.systemUTC(), previewService());
        ReflectionTestUtils.setField(service, "lockTimeoutMs", 5000L);
        var request = new AddCartItemRequest(); request.setProductVariantId(variant); request.setQuantity(2);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.addItem(1L, request, null))
                .isInstanceOf(com.example.demo.cart.exception.CartStockConflictException.class);
        assertThat(existing.getQuantity()).isEqualTo(2);
        verify(items, never()).saveAndFlush(any());
    }

    @Test void bulkSelection_shouldPreserveShortagePreferencesAndAllowDeselectAll() {
        CartRepository carts = mock(CartRepository.class);
        CartItemRepository items = mock(CartItemRepository.class);
        CatalogCartReadService catalog = mock(CatalogCartReadService.class);
        Cart cart = mock(Cart.class);
        when(cart.getVoucherEntitlementId()).thenReturn(null);
        when(cart.getId()).thenReturn(7L);
        when(carts.findOwnedForUpdate(1L)).thenReturn(Optional.of(cart));
        UUID product = UUID.randomUUID(), a = UUID.randomUUID(), b = UUID.randomUUID();
        CartItem shortage = new CartItem(7L, java.time.Instant.EPOCH);
        ReflectionTestUtils.setField(shortage, "id", 8L);
        shortage.setProductId(product); shortage.setProductVariantId(a); shortage.setQuantity(5); shortage.setSelected(true);
        shortage.setPublicProductName("P"); shortage.setPublicSku("A"); shortage.setPublicOptions(Map.of());
        CartItem eligible = new CartItem(7L, java.time.Instant.EPOCH);
        ReflectionTestUtils.setField(eligible, "id", 9L);
        eligible.setProductId(product); eligible.setProductVariantId(b); eligible.setQuantity(1); eligible.setSelected(false);
        eligible.setPublicProductName("P"); eligible.setPublicSku("B"); eligible.setPublicOptions(Map.of());
        when(items.findByCartIdOrderByAddedAtDescIdAsc(7L)).thenReturn(List.of(shortage, eligible));
        var availability = new com.example.demo.inventory.dto.response.InventoryAvailability(3, "IN_STOCK");
        when(catalog.getCartPresentation(anyCollection(), org.mockito.ArgumentMatchers.isNull())).thenReturn(Map.of(
                a, new CartVariantState(a, product, true, BigInteger.TEN, "P", "A", Map.of(), null, availability),
                b, new CartVariantState(b, product, true, BigInteger.TEN, "P", "B", Map.of(), null, availability)));
        var service = new CartTransactionServiceImpl(carts, items, mock(UserService.class), catalog,
                new CartValidator(), new CartCalculator(), new CartConverter(), Clock.systemUTC(), previewService());
        ReflectionTestUtils.setField(service, "lockTimeoutMs", 5000L);
        var selection = new com.example.demo.cart.dto.request.CartSelectionRequest(); selection.setSelected(true);
        var response = service.selectAll(1L, selection, null);
        assertThat(shortage.isSelected()).isTrue();
        assertThat(eligible.isSelected()).isTrue();
        assertThat(response.getSelectedItemCount()).isEqualTo(1);
        selection.setSelected(false);
        assertThat(service.selectAll(1L, selection, null).getSelectedItemCount()).isZero();
        assertThat(shortage.isSelected()).isFalse();
        assertThat(eligible.isSelected()).isFalse();
    }
    @Test void addItem_shouldRecheckAfterUserCoordinationAndUseExistingWinner() {
        CartRepository carts = mock(CartRepository.class);
        CartItemRepository items = mock(CartItemRepository.class);
        UserService users = mock(UserService.class);
        CatalogCartReadService catalog = mock(CatalogCartReadService.class);
        Cart winner = mock(Cart.class);
        when(winner.getVoucherEntitlementId()).thenReturn(null);
        when(winner.getId()).thenReturn(7L);
        when(carts.findOwnedForUpdate(1L)).thenReturn(Optional.empty(), Optional.of(winner));
        List<CartItem> rows = new java.util.ArrayList<>();
        when(items.findByCartIdOrderByAddedAtDescIdAsc(7L)).thenReturn(rows);
        when(items.saveAndFlush(any(CartItem.class))).thenAnswer(call -> {
            CartItem row = call.getArgument(0); ReflectionTestUtils.setField(row, "id", 44L); rows.add(row); return row;
        });
        UUID variant = UUID.randomUUID();
        var state = new CartVariantState(variant, UUID.randomUUID(), true, BigInteger.TEN, "P", "S", Map.of(), null, new com.example.demo.inventory.dto.response.InventoryAvailability(999, "IN_STOCK"));
        when(catalog.getVariantStates(anyCollection())).thenReturn(Map.of(variant, state));
        when(catalog.getCartPresentation(anyCollection(), org.mockito.ArgumentMatchers.isNull())).thenReturn(Map.of(variant,state));
        var service = new CartTransactionServiceImpl(carts, items, users, catalog,
                new CartValidator(), new CartCalculator(), new CartConverter(), Clock.systemUTC(), previewService());
        ReflectionTestUtils.setField(service, "lockTimeoutMs", 5000L);
        var request = new AddCartItemRequest(); request.setProductVariantId(variant); request.setQuantity(2);
        assertThat(service.addItem(1L, request, null).getResponse().getItems()).hasSize(1);
        InOrder order = inOrder(carts, users, items);
        order.verify(carts).setLockTimeout("5000ms"); order.verify(carts).findOwnedForUpdate(1L);
        order.verify(users).getUserEntityForUpdate(1L);
        order.verify(carts).setLockTimeout("5000ms"); order.verify(carts).findOwnedForUpdate(1L);
        order.verify(items).findByCartIdOrderByAddedAtDescIdAsc(7L);
        verify(carts, never()).saveAndFlush(any(Cart.class));
    }
    @Test void changeVariant_shouldRemoveSourceWithoutChangingExistingTarget() {
        CartRepository carts = mock(CartRepository.class);
        CartItemRepository items = mock(CartItemRepository.class);
        CatalogCartReadService catalog = mock(CatalogCartReadService.class);
        Cart cart = mock(Cart.class);
        when(cart.getVoucherEntitlementId()).thenReturn(null);
        when(cart.getId()).thenReturn(7L);
        when(carts.findOwnedForUpdate(1L)).thenReturn(Optional.of(cart));
        UUID parent = UUID.randomUUID(), first = UUID.randomUUID(), second = UUID.randomUUID();
        CartItem source = new CartItem(7L, java.time.Instant.EPOCH);
        ReflectionTestUtils.setField(source, "id", 8L);
        source.setProductId(parent); source.setProductVariantId(first); source.setQuantity(900);
        CartItem target = new CartItem(7L, java.time.Instant.EPOCH.plusSeconds(1));
        ReflectionTestUtils.setField(target, "id", 9L);
        target.setProductId(parent); target.setProductVariantId(second); target.setQuantity(600); target.setSelected(false);
        target.setPublicProductName("Prior target name"); target.setPublicSku("Prior target SKU"); target.setPublicOptions(Map.of());
        when(items.findByCartIdOrderByAddedAtDescIdAsc(7L)).thenReturn(List.of(source, target), List.of(target));
        CartVariantState state = new CartVariantState(second, parent, true, BigInteger.TEN, "P", "S", Map.of(), null, new com.example.demo.inventory.dto.response.InventoryAvailability(999, "IN_STOCK"));
        when(catalog.getVariantStates(anyCollection())).thenReturn(Map.of(second, state));
        when(catalog.getCartPresentation(anyCollection(), org.mockito.ArgumentMatchers.isNull())).thenReturn(Map.of(second,state));
        CartTransactionServiceImpl service = new CartTransactionServiceImpl(carts, items, mock(UserService.class), catalog,
                new CartValidator(), new CartCalculator(), new CartConverter(), Clock.systemUTC(), previewService());
        ReflectionTestUtils.setField(service, "lockTimeoutMs", 5000L);
        ChangeCartVariantRequest request = new ChangeCartVariantRequest(); request.setProductVariantId(second);
        var response = service.changeVariant(1L, 8L, request, null);
        assertThat(target.getQuantity()).isEqualTo(600);
        assertThat(target.getPublicProductName()).isEqualTo("Prior target name");
        assertThat(target.getPublicSku()).isEqualTo("Prior target SKU");
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
        when(cart.getVoucherEntitlementId()).thenReturn(null);
        when(cart.getId()).thenReturn(7L);
        when(carts.findOwnedForUpdate(1L)).thenReturn(Optional.of(cart));
        java.util.List<CartItem> persisted = new java.util.ArrayList<>();
        when(items.findByCartIdOrderByAddedAtDescIdAsc(7L)).thenReturn(persisted);
        when(items.saveAndFlush(any(CartItem.class))).thenAnswer(call -> {
            CartItem saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 9L);
            persisted.add(saved);
            return saved;
        });
        UUID variant = UUID.randomUUID();
        CartVariantState state = new CartVariantState(variant, UUID.randomUUID(), true, BigInteger.TEN, "P", "S", Map.of(), null, new com.example.demo.inventory.dto.response.InventoryAvailability(999, "IN_STOCK"));
        when(catalog.getVariantStates(anyCollection())).thenReturn(Map.of(variant,state));
        when(catalog.getCartPresentation(anyCollection(), org.mockito.ArgumentMatchers.isNull())).thenReturn(Map.of(variant,state));
        CartTransactionServiceImpl service = new CartTransactionServiceImpl(carts, items, mock(UserService.class),
                catalog, new CartValidator(), new CartCalculator(), new CartConverter(), Clock.systemUTC(), previewService());
        ReflectionTestUtils.setField(service, "lockTimeoutMs", 5000L);
        AddCartItemRequest request = new AddCartItemRequest();
        request.setProductVariantId(variant);
        request.setQuantity(1);
        service.addItem(1L, request, null);
        InOrder order = inOrder(carts, items);
        order.verify(carts).setLockTimeout("5000ms");
        order.verify(carts).findOwnedForUpdate(1L);
        order.verify(items).findByCartIdOrderByAddedAtDescIdAsc(7L);
    }
    private com.example.demo.cart.service.CartPreviewService previewService() {
        return new com.example.demo.cart.service.impl.CartPreviewServiceImpl(new CartCalculator(), new CartConverter(),
                new CartValidator(), mock(com.example.demo.voucher.service.VoucherService.class), Clock.systemUTC());
    }
}
