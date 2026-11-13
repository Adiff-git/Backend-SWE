package com.example.demo.cart.service.impl;

import com.example.demo.cart.calculator.CartCalculator;
import com.example.demo.cart.converter.CartConverter;
import com.example.demo.cart.dto.request.AddCartItemRequest;
import com.example.demo.cart.dto.request.UpdateCartItemRequest;
import com.example.demo.cart.dto.request.CartSelectionRequest;
import com.example.demo.cart.exception.InvalidCartRequestException;
import jakarta.annotation.PostConstruct;
import com.example.demo.cart.dto.response.AddCartResult;
import com.example.demo.cart.dto.response.CartReadSeed;
import com.example.demo.cart.dto.response.CartResponse;
import com.example.demo.cart.entity.Cart;
import com.example.demo.cart.entity.CartItem;
import com.example.demo.cart.repository.CartItemRepository;
import com.example.demo.cart.repository.CartRepository;
import com.example.demo.cart.service.CartTransactionService;
import com.example.demo.cart.validator.CartValidator;
import com.example.demo.catalog.dto.response.CartVariantState;
import com.example.demo.catalog.service.CatalogCartReadService;
import com.example.demo.common.identity.IdentityUnavailableException;
import com.example.demo.storage.service.PreparedImageUrls;
import com.example.demo.user.exception.ResourceNotFoundException;
import com.example.demo.user.service.UserService;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

@Service
@RequiredArgsConstructor
@Transactional(isolation = Isolation.READ_COMMITTED)
public class CartTransactionServiceImpl implements CartTransactionService {
    private final CartRepository carts;
    private final CartItemRepository items;
    private final UserService users;
    private final CatalogCartReadService catalog;
    private final CartValidator validator;
    private final CartCalculator calculator;
    private final CartConverter converter;
    private final Clock clock;
    @Value("${cart.lock-timeout-ms:5000}")
    private long lockTimeoutMs;

    @PostConstruct void validateConfiguration() {
        if (lockTimeoutMs <= 0) throw new IllegalArgumentException("Cart lock timeout must be positive");
    }

    @Override
    public CartResponse getCart(Long owner, PreparedImageUrls urls) {
        validator.owner(owner);
        return carts.findByUserId(owner).map(cart -> response(rows(cart), urls, null))
                .orElseGet(() -> response(List.of(), urls, null));
    }

    @Override
    public AddCartResult addItem(Long owner, AddCartItemRequest request, PreparedImageUrls urls) {
        validator.owner(owner);
        if (request == null || request.getVariantId() == null) throw new InvalidCartRequestException();
        int quantity = validator.quantity(request.getQuantity());
        Cart cart = lockCart(owner, true);
        List<CartItem> rows = rows(cart);
        CartVariantState state = catalog.getVariantStates(List.of(request.getVariantId())).get(request.getVariantId());
        validator.eligible(state);
        CartItem item = rows.stream().filter(row -> row.getProductVariantId().equals(request.getVariantId())).findFirst().orElse(null);
        boolean created = item == null;
        if (created) {
            item = new CartItem(cart.getId(), clock.instant());
            item.setQuantity(quantity);
            snapshot(item, state);
        } else {
            item.setQuantity(validator.addedQuantity(item.getQuantity(), quantity));
        }
        items.saveAndFlush(item);
        CartResponse response = response(rows(cart), urls, request.getVariantId());
        return new AddCartResult(created, item.getId(), response);
    }

    @Override public CartResponse updateItem(Long owner, Long itemId, UpdateCartItemRequest request, PreparedImageUrls urls) {
        validator.owner(owner);
        if (request == null || request.isVariantIdPresent() && request.getVariantId() == null) throw new InvalidCartRequestException();
        int quantity = validator.quantity(request.getQuantity());
        Cart cart = lockCart(owner, false);
        List<CartItem> rows = cart == null ? List.of() : rows(cart);
        CartItem source = validator.ownedItem(rows, itemId);
        UUID targetId = request.getVariantId() == null ? source.getProductVariantId() : request.getVariantId();
        CartVariantState target = catalog.getVariantStates(List.of(targetId)).get(targetId);
        validator.eligible(target);
        validator.sameProduct(source.getProductId(), target.getProductId());
        CartItem existing = rows.stream().filter(row -> row.getProductVariantId().equals(targetId)).findFirst().orElse(null);
        if (existing != null && existing != source) {
            int merged = validator.addedQuantity(existing.getQuantity(), quantity);
            existing.setQuantity(merged);
            snapshot(existing, target);
            items.delete(source);
        } else {
            source.setQuantity(quantity);
            snapshot(source, target);
        }
        items.flush();
        return response(rows(cart), urls, targetId);
    }

    @Override public void removeItem(Long owner, Long itemId) {
        validator.owner(owner);
        Cart cart = lockCart(owner, false);
        CartItem source = validator.ownedItem(cart == null ? List.of() : rows(cart), itemId);
        items.delete(source);
        items.flush();
    }

    @Override public CartResponse selectItem(Long owner, Long itemId, CartSelectionRequest request, PreparedImageUrls urls) {
        validator.owner(owner);
        boolean selected = validator.selection(request.getSelected());
        Cart cart = lockCart(owner, false);
        List<CartItem> rows = cart == null ? List.of() : rows(cart);
        CartItem item = validator.ownedItem(rows, itemId);
        Map<UUID, CartVariantState> states = presentation(rows, urls);
        if (selected) validator.selectable(states.get(item.getProductVariantId()));
        item.setSelected(selected);
        items.flush();
        return response(rows, states);
    }

    @Override public CartResponse selectAll(Long owner, CartSelectionRequest request, PreparedImageUrls urls) {
        validator.owner(owner);
        boolean selected = validator.selection(request.getSelected());
        Cart cart = lockCart(owner, false);
        List<CartItem> rows = cart == null ? List.of() : rows(cart);
        Map<UUID, CartVariantState> states = presentation(rows, urls);
        for (CartItem row : rows) {
            CartVariantState state = states.get(row.getProductVariantId());
            if (!selected || state != null && state.isPurchasable()) row.setSelected(selected);
        }
        items.flush();
        return response(rows, states);
    }

    private Cart lockCart(Long owner, boolean create) {
        timeout();
        Cart cart = carts.findOwnedForUpdate(owner).orElse(null);
        if (cart != null || !create) return cart;
        try { users.getUserEntityForUpdate(owner); }
        catch (ResourceNotFoundException unavailable) { throw new IdentityUnavailableException(); }
        timeout();
        return carts.findOwnedForUpdate(owner).orElseGet(() -> carts.saveAndFlush(new Cart(owner)));
    }

    private void timeout() {
        if (lockTimeoutMs <= 0) throw new IllegalStateException("Cart lock timeout must be positive");
        // Must execute in the current transaction BEFORE the first blocking lock query.
        carts.setLockTimeout(lockTimeoutMs + "ms");
    }

    private List<CartItem> rows(Cart cart) { return items.findByCartIdOrderByAddedAtDescIdAsc(cart.getId()); }

    private CartResponse response(List<CartItem> rows, PreparedImageUrls urls, UUID target) {
        Map<UUID, CartVariantState> states = presentation(rows, urls);
        if (target != null) validator.eligible(states.get(target));
        return response(rows, states);
    }

    private Map<UUID, CartVariantState> presentation(List<CartItem> rows, PreparedImageUrls urls) {
        return catalog.getCartPresentation(rows.stream().map(CartItem::getProductVariantId).toList(), urls);
    }

    private CartResponse response(List<CartItem> rows, Map<UUID, CartVariantState> states) {
        List<CartReadSeed> seeds = rows.stream().map(converter::toSeed).toList();
        return converter.toResponse(calculator.calculate(seeds, states));
    }

    private void snapshot(CartItem item, CartVariantState state) {
        item.setProductId(state.getProductId());
        item.setProductVariantId(state.getVariantId());
        item.setPublicProductName(state.getProductName());
        item.setPublicSku(state.getSku());
        item.setPublicOptions(Map.copyOf(state.getOptions()));
    }
}
