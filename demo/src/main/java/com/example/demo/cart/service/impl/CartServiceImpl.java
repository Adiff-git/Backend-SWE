package com.example.demo.cart.service.impl;

import com.example.demo.cart.dto.request.AddCartItemRequest;
import com.example.demo.cart.dto.request.UpdateCartItemRequest;
import com.example.demo.cart.dto.request.CartSelectionRequest;
import com.example.demo.cart.dto.response.AddCartResult;
import com.example.demo.cart.dto.response.CartResponse;
import com.example.demo.cart.exception.InvalidCartRequestException;
import com.example.demo.cart.repository.CartItemRepository;
import com.example.demo.cart.service.CartService;
import com.example.demo.cart.service.CartTransactionService;
import com.example.demo.cart.validator.CartValidator;
import com.example.demo.catalog.service.CatalogCartReadService;
import com.example.demo.storage.service.ImageUrlResolver;
import com.example.demo.storage.service.PreparedImageUrls;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {
    private final CartTransactionService transactions;
    private final CartItemRepository items;
    private final CatalogCartReadService catalog;
    private final ImageUrlResolver imageUrls;
    private final CartValidator validator;

    @Override public CartResponse getCart(Long owner) {
        validator.owner(owner);
        return transactions.getCart(owner, prepare(items.findVariantIdsForOwner(owner)));
    }

    @Override public AddCartResult addItem(Long owner, AddCartItemRequest request) {
        validator.owner(owner);
        if (request == null || request.getVariantId() == null) throw new InvalidCartRequestException();
        validator.quantity(request.getQuantity());
        List<UUID> ids = new ArrayList<>(items.findVariantIdsForOwner(owner));
        ids.add(request.getVariantId());
        return transactions.addItem(owner, request, prepare(ids));
    }

    private PreparedImageUrls prepare(List<UUID> ids) {
        return ids.isEmpty() || !catalog.hasImageCandidates(ids) ? null : imageUrls.prepare();
    }

    @Override public CartResponse updateItem(Long owner, Long itemId, UpdateCartItemRequest request) {
        validator.owner(owner);
        if (request == null || request.isVariantIdPresent() && request.getVariantId() == null) throw new InvalidCartRequestException();
        validator.quantity(request.getQuantity());
        requireOwned(owner, itemId);
        List<UUID> ids = new ArrayList<>(items.findVariantIdsForOwner(owner));
        if (request.getVariantId() != null) ids.add(request.getVariantId());
        return transactions.updateItem(owner, itemId, request, prepare(ids));
    }

    @Override public void removeItem(Long owner, Long itemId) {
        validator.owner(owner);
        transactions.removeItem(owner, itemId);
    }

    private void requireOwned(Long owner, Long itemId) {
        validator.ownedItemExists(itemId != null && items.findOwnedVariantId(owner, itemId).isPresent());
    }

    @Override public CartResponse selectItem(Long owner, Long itemId, CartSelectionRequest request) {
        validator.owner(owner);
        if (request == null) throw new InvalidCartRequestException();
        validator.selection(request.getSelected());
        requireOwned(owner, itemId);
        return transactions.selectItem(owner, itemId, request, prepare(items.findVariantIdsForOwner(owner)));
    }

    @Override public CartResponse selectAll(Long owner, CartSelectionRequest request) {
        validator.owner(owner);
        if (request == null) throw new InvalidCartRequestException();
        validator.selection(request.getSelected());
        return transactions.selectAll(owner, request, prepare(items.findVariantIdsForOwner(owner)));
    }
}
