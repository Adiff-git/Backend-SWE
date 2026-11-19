package com.example.demo.order.controller;

import com.example.demo.common.identity.CurrentUserIdProvider;
import com.example.demo.order.dto.response.OrderPage;
import com.example.demo.order.dto.response.OrderDetailResponse;
import com.example.demo.order.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequestMapping("/api/me/orders") @RequiredArgsConstructor
public class OrderController {
    private final CurrentUserIdProvider identity;
    private final OrderService service;
    private final OrderRequestReader reader;

    @GetMapping
    public ResponseEntity<OrderPage> listOrders(HttpServletRequest request) {
        return ResponseEntity.ok().header("Cache-Control","private, no-store")
                .body(service.listOrders(identity.getCurrentUserId(),reader.readList(request)));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderDetailResponse> getOrder(@PathVariable String orderId, HttpServletRequest request) {
        reader.noQuery(request);
        reader.noBody(request);
        return ResponseEntity.ok().header("Cache-Control","private, no-store")
                .body(service.getOrder(identity.getCurrentUserId(),reader.readId(orderId)));
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<Void> cancelOrder(@PathVariable String orderId, HttpServletRequest request) throws HttpMediaTypeNotSupportedException {
        Long id = reader.readId(orderId);
        service.cancelOrder(identity.getCurrentUserId(),id,reader.readCancellation(request));
        return ResponseEntity.noContent().header("Cache-Control","private, no-store").build();
    }
}
