package com.example.demo.cart.service;

import com.example.demo.cart.dto.request.AddCartItemRequest;
import com.example.demo.cart.dto.request.UpdateCartQuantityRequest;
import com.example.demo.cart.dto.request.ChangeCartVariantRequest;
import com.example.demo.cart.dto.request.CartSelectionRequest;
import com.example.demo.cart.dto.response.AddCartResult;
import com.example.demo.cart.dto.response.CartResponse;
import com.example.demo.storage.service.PreparedImageUrls;

public interface CartTransactionService {
    CartResponse getCart(Long owner, PreparedImageUrls urls);
    AddCartResult addItem(Long owner, AddCartItemRequest request, PreparedImageUrls urls);
    CartResponse updateQuantity(Long owner, Long itemId, UpdateCartQuantityRequest request, PreparedImageUrls urls);
    CartResponse changeVariant(Long owner, Long itemId, ChangeCartVariantRequest request, PreparedImageUrls urls);
    void removeItem(Long owner, Long itemId);
    CartResponse selectItem(Long owner, Long itemId, CartSelectionRequest request, PreparedImageUrls urls);
    CartResponse selectAll(Long owner, CartSelectionRequest request, PreparedImageUrls urls);
}
