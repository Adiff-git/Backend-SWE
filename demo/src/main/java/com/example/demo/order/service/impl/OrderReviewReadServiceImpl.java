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
    private final tools.jackson.databind.json.JsonMapper json=tools.jackson.databind.json.JsonMapper.builder().build();
    @Override @Transactional(readOnly=true)
    public java.util.Map<Long,java.util.Map<String,String>> getReviewOptionSnapshots(java.util.Collection<Long> itemIds) {
        if(itemIds==null || itemIds.size()>100 || itemIds.stream().anyMatch(id -> id==null || id<=0))
            throw new IllegalArgumentException("Review option snapshot batch must contain at most100 positive item IDs");
        if(itemIds.isEmpty()) return java.util.Map.of();
        java.util.Map<Long,java.util.Map<String,String>> result=new java.util.HashMap<>();
        for(Object[] row:items.findReviewOptionSnapshots(itemIds)) {
            if(row[1]!=null) result.put(((Number)row[0]).longValue(),java.util.Map.copyOf(json.readValue(row[1].toString(),
                    new tools.jackson.core.type.TypeReference<java.util.Map<String,String>>() {})));
        }
        return java.util.Map.copyOf(result);
    }
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
