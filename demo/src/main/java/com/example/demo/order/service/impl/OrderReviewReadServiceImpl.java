package com.example.demo.order.service.impl;

import com.example.demo.order.dto.response.ReviewPurchaseSeed;
import com.example.demo.order.entity.Order;
import com.example.demo.order.entity.OrderItem;
import com.example.demo.order.entity.OrderStatus;
import com.example.demo.order.exception.OrderConflictException;
import com.example.demo.order.exception.OrderNotFoundException;
import com.example.demo.order.repository.OrderItemRepository;
import com.example.demo.order.repository.OrderRepository;
import com.example.demo.order.service.OrderReviewReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class OrderReviewReadServiceImpl implements OrderReviewReadService {
    private final OrderRepository orders;
    private final OrderItemRepository items;
    @org.springframework.beans.factory.annotation.Value("${order.lock-timeout-ms:5000}")
    private long lockTimeoutMs = 5000;
    @Override @Transactional
    public ReviewPurchaseSeed getReviewPurchase(Long actor, Long orderId, Long itemId, boolean creating) {
        if (lockTimeoutMs <= 0) throw new IllegalStateException("Order lock timeout must be positive");
        orders.setLockTimeout(lockTimeoutMs + "ms");
        Order order = orders.findOwnedForUpdate(actor, orderId).orElseThrow(OrderNotFoundException::new);
        OrderItem item = items.findReviewItemForUpdate(orderId, itemId).orElseThrow(OrderNotFoundException::new);
        if (order.getStatus() != OrderStatus.COMPLETED || creating && item.getProductId() == null) throw new OrderConflictException();
        return new ReviewPurchaseSeed(orderId, itemId, actor, item.getProductId());
    }
    @Override @Transactional(readOnly = true)
    public Long getReviewOrderId(Long actor, Long item) {
        return items.findOwnedReviewOrder(actor, item).orElseThrow(OrderNotFoundException::new);
    }
}
