package com.example.demo.cart.service;

import com.example.demo.cart.dto.request.AddCartItemRequest;
import com.example.demo.cart.dto.request.UpdateCartItemRequest;
import com.example.demo.cart.dto.request.CartSelectionRequest;
import com.example.demo.cart.dto.response.AddCartResult;
import com.example.demo.cart.dto.response.CartResponse;

public interface CartService {
    CartResponse getCart(Long owner);
    AddCartResult addItem(Long owner, AddCartItemRequest request);
    CartResponse updateItem(Long owner, Long itemId, UpdateCartItemRequest request);
    void removeItem(Long owner, Long itemId);
    CartResponse selectItem(Long owner, Long itemId, CartSelectionRequest request);
    CartResponse selectAll(Long owner, CartSelectionRequest request);
}
