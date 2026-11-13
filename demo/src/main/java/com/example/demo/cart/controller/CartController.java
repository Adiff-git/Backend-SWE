package com.example.demo.cart.controller;

import com.example.demo.cart.dto.request.AddCartItemRequest;
import com.example.demo.cart.dto.request.UpdateCartItemRequest;
import com.example.demo.cart.dto.request.CartSelectionRequest;
import com.example.demo.cart.dto.response.AddCartResult;
import com.example.demo.cart.dto.response.CartResponse;
import com.example.demo.cart.service.CartService;
import com.example.demo.common.identity.CurrentUserIdProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/cart")
@RequiredArgsConstructor
public class CartController {
    private final CartService service;
    private final CurrentUserIdProvider identity;
    private final CartRequestReader reader;

    @GetMapping public CartResponse getCart(HttpServletRequest request) {
        reader.noBody(request);
        return service.getCart(identity.getCurrentUserId());
    }

    @PostMapping(value = "/items", consumes = "application/json")
    public ResponseEntity<CartResponse> addItem(@Valid @RequestBody AddCartItemRequest body, HttpServletRequest request) {
        reader.noQuery(request);
        AddCartResult result = service.addItem(identity.getCurrentUserId(), body);
        return result.isCreated() ? ResponseEntity.created(URI.create("/api/me/cart/items/" + result.getItemId())).body(result.getResponse())
                : ResponseEntity.ok(result.getResponse());
    }

    @PutMapping(value = "/items/{itemId}", consumes = "application/json")
    public CartResponse updateItem(@PathVariable Long itemId, @Valid @RequestBody UpdateCartItemRequest body, HttpServletRequest request) {
        reader.noQuery(request);
        return service.updateItem(identity.getCurrentUserId(), itemId, body);
    }

    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<Void> removeItem(@PathVariable Long itemId, HttpServletRequest request) {
        reader.noBody(request);
        service.removeItem(identity.getCurrentUserId(), itemId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping(value = "/items/{itemId}/selection", consumes = "application/json")
    public CartResponse selectItem(@PathVariable Long itemId, @Valid @RequestBody CartSelectionRequest body, HttpServletRequest request) {
        reader.noQuery(request);
        return service.selectItem(identity.getCurrentUserId(), itemId, body);
    }

    @PutMapping(value = "/selection", consumes = "application/json")
    public CartResponse selectAll(@Valid @RequestBody CartSelectionRequest body, HttpServletRequest request) {
        reader.noQuery(request);
        return service.selectAll(identity.getCurrentUserId(), body);
    }
}
