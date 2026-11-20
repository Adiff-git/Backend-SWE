package com.example.demo.order.service;

/** Trusted internal lifecycle boundary. Exposing customer receipt confirmation requires a separate feature. */
public interface OrderCompletionService {
    void completeOrder(Long ownerId, Long orderId);
}
