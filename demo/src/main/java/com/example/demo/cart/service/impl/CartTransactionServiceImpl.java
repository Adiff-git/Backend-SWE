package com.example.demo.cart.service.impl;

import com.example.demo.cart.calculator.CartCalculator;
import com.example.demo.cart.converter.CartConverter;
import com.example.demo.cart.dto.request.AddCartItemRequest;
import com.example.demo.cart.dto.request.UpdateCartQuantityRequest;
import com.example.demo.cart.dto.request.ChangeCartVariantRequest;
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
    private final com.example.demo.cart.service.CartPreviewService previews;
    @Value("${cart.lock-timeout-ms:5000}")
    private long lockTimeoutMs;

    @PostConstruct void validateConfiguration() {
        if (lockTimeoutMs <= 0) throw new IllegalArgumentException("Cart lock timeout must be positive");
    }

    @Override
    public CartResponse getCart(Long owner, PreparedImageUrls urls) {
        validator.owner(owner);
        Cart cart = lockCart(owner, false);
        var rows = cart == null ? List.<CartItem>of() : rows(cart);
        var prepared=prepare(rows,null);
        return response(cart,rows,catalog.getCartPresentation(prepared,urls),prepared.getPricingInstant());
    }

    @Override
    public AddCartResult addItem(Long owner, AddCartItemRequest request, PreparedImageUrls urls) {
        validator.owner(owner);
        if (request == null || request.getProductVariantId() == null) throw new InvalidCartRequestException();
        int quantity = validator.quantity(request.getQuantity());
        Cart cart = lockCart(owner, true);
        List<CartItem> rows = rows(cart);
        var prepared = prepare(rows, request.getProductVariantId());
        CartVariantState state = catalog.getVariantStates(prepared).get(request.getProductVariantId());
        validator.eligible(state);
        CartItem item = rows.stream().filter(row -> row.getProductVariantId().equals(request.getProductVariantId())).findFirst().orElse(null);
        boolean created = item == null;
        int resultingQuantity = created ? quantity : validator.addedQuantity(item.getQuantity(), quantity);
        validator.stockEligible(state, resultingQuantity);
        if (created) {
            item = new CartItem(cart.getId(), clock.instant());
            item.setQuantity(quantity);
            snapshot(item, state);
        } else {
            item.setQuantity(resultingQuantity);
        }
        items.saveAndFlush(item);
        CartResponse response = response(cart, rows(cart), urls, request.getProductVariantId(), prepared);
        return new AddCartResult(created, item.getId(), response);
    }

    @Override public CartResponse updateQuantity(Long owner, Long itemId, UpdateCartQuantityRequest request, PreparedImageUrls urls) {
        validator.owner(owner);
        if (request == null) throw new InvalidCartRequestException();
        int quantity = validator.quantity(request.getQuantity());
        Cart cart = lockCart(owner, false);
        CartItem source = validator.ownedItem(cart == null ? List.of() : rows(cart), itemId);
        UUID variantId = source.getProductVariantId();
        var prepared = prepare(rows(cart), variantId);
        CartVariantState state = catalog.getVariantStates(prepared).get(variantId);
        validator.eligible(state);
        validator.stockEligible(state, quantity);
        source.setQuantity(quantity);
        items.flush();
        return response(cart, rows(cart), urls, variantId, prepared);
    }

    @Override public CartResponse changeVariant(Long owner, Long itemId, ChangeCartVariantRequest request, PreparedImageUrls urls) {
        validator.owner(owner);
        if (request == null || request.getProductVariantId() == null) throw new InvalidCartRequestException();
        Cart cart = lockCart(owner, false);
        List<CartItem> rows = cart == null ? List.of() : rows(cart);
        CartItem source = validator.ownedItem(rows, itemId);
        UUID targetId = request.getProductVariantId();
        var prepared = prepare(rows, targetId);
        CartVariantState target = catalog.getVariantStates(prepared).get(targetId);
        validator.eligible(target);
        validator.sameProduct(source.getProductId(), target.getProductId());
        CartItem existing = rows.stream().filter(row -> row.getProductVariantId().equals(targetId)).findFirst().orElse(null);
        validator.stockEligible(target, existing == null ? CartValidator.MIN_QUANTITY : existing.getQuantity());
        if (!source.getProductVariantId().equals(targetId)) {
            if (existing != null) {
                // Existing target is unchanged: quantity, selection, snapshots and time all survive.
                items.delete(source);
            } else {
                source.setQuantity(CartValidator.MIN_QUANTITY);
                snapshot(source, target);
            }
        }
        items.flush();
        return response(cart, rows(cart), urls, targetId, prepared);
    }

    @Override public void removeItem(Long owner, Long itemId) {
        validator.owner(owner);
        Cart cart = lockCart(owner, false);
        CartItem source = validator.ownedItem(cart == null ? List.of() : rows(cart), itemId);
        items.delete(source);
        items.flush();
        List<CartItem> remaining = rows(cart);
        var prepared=prepare(remaining,null);
        previews.getCartResponse(cart,remaining,catalog.getVariantStates(prepared),false,prepared.getPricingInstant());
    }

    @Override public CartResponse selectItem(Long owner, Long itemId, CartSelectionRequest request, PreparedImageUrls urls) {
        validator.owner(owner);
        boolean selected = validator.selection(request.getSelected());
        Cart cart = lockCart(owner, false);
        List<CartItem> rows = cart == null ? List.of() : rows(cart);
        CartItem item = validator.ownedItem(rows, itemId);
        var prepared = prepare(rows, null);
        Map<UUID, CartVariantState> states = catalog.getCartPresentation(prepared, urls);
        if (selected) {
            validator.selectable(states.get(item.getProductVariantId()));
            validator.stockEligible(states.get(item.getProductVariantId()), item.getQuantity());
        }
        item.setSelected(selected);
        items.flush();
        return selected ? response(cart, rows, urls, item.getProductVariantId(), prepared) : response(cart, rows, states,prepared.getPricingInstant());
    }

    @Override public CartResponse selectAll(Long owner, CartSelectionRequest request, PreparedImageUrls urls) {
        validator.owner(owner);
        boolean selected = validator.selection(request.getSelected());
        Cart cart = lockCart(owner, false);
        List<CartItem> rows = cart == null ? List.of() : rows(cart);
        var prepared = prepare(rows, null);
        Map<UUID, CartVariantState> states = catalog.getCartPresentation(prepared, urls);
        for (CartItem row : rows) {
            CartVariantState state = states.get(row.getProductVariantId());
            if (!selected || validator.ineligibleReason(state, row.getQuantity()) == null) row.setSelected(selected);
        }
        items.flush();
        return response(cart, rows, states,prepared.getPricingInstant());
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

    private CartResponse response(Cart cart, List<CartItem> rows, PreparedImageUrls urls, UUID target,
            com.example.demo.catalog.dto.response.PreparedOperationPricing prepared) {
        Map<UUID, CartVariantState> states = catalog.getCartPresentation(prepared, urls);
        if (target != null) {
            validator.eligible(states.get(target));
            CartItem affected = rows.stream().filter(row -> target.equals(row.getProductVariantId())).findFirst()
                    .orElseThrow(com.example.demo.cart.exception.CartStockConflictException::new);
            validator.stockEligible(states.get(target), affected.getQuantity());
        }
        return response(cart, rows, states,prepared.getPricingInstant());
    }

    private com.example.demo.catalog.dto.response.PreparedOperationPricing prepare(List<CartItem> rows, UUID extra) {
        var ids = new java.util.HashSet<UUID>();
        rows.forEach(row -> ids.add(row.getProductVariantId()));
        if (extra != null) ids.add(extra);
        return catalog.preparePricing(ids, clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
    }

    private CartResponse response(Cart cart, List<CartItem> rows, Map<UUID, CartVariantState> states,java.time.Instant at) {
        return previews.getCartResponse(cart, rows, states, false,at);
    }

    private void snapshot(CartItem item, CartVariantState state) {
        item.setProductId(state.getProductId());
        item.setProductVariantId(state.getVariantId());
        item.setPublicProductName(state.getProductName());
        item.setPublicSku(state.getSku());
        item.setPublicOptions(Map.copyOf(state.getOptions()));
    }
}
