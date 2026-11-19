package com.example.demo.checkout.controller;

import com.example.demo.checkout.dto.response.CheckoutReceiptResponse;
import com.example.demo.checkout.service.CheckoutService;
import com.example.demo.common.identity.CurrentUserIdProvider;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CheckoutController {
    private final CurrentUserIdProvider identity;
    private final CheckoutRequestReader reader;
    private final CheckoutService service;

    @PostMapping("/api/me/cart/checkout")
    public ResponseEntity<CheckoutReceiptResponse> checkout(HttpServletRequest request) throws HttpMediaTypeNotSupportedException {
        String key = reader.readKey(request);
        CheckoutReceiptResponse receipt = service.checkout(identity.getCurrentUserId(),key,reader.readRequest(request));
        return ResponseEntity.created(URI.create("/api/me/orders/"+receipt.getOrder().getId()))
                .header("Cache-Control","private, no-store").body(receipt);
    }
}
