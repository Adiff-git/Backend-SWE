package com.example.demo.order.service.impl;

import com.example.demo.inventory.dto.request.ReservationLine;
import com.example.demo.inventory.dto.request.ResolutionCommand;
import com.example.demo.inventory.service.InventoryService;
import com.example.demo.order.converter.OrderConverter;
import com.example.demo.order.dto.response.OrderInventorySeed;
import com.example.demo.order.entity.Order;
import com.example.demo.order.repository.OrderItemRepository;
import com.example.demo.order.repository.OrderRepository;
import com.example.demo.order.service.OrderCompletionService;
import com.example.demo.order.validator.OrderValidator;
import jakarta.annotation.PostConstruct;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class OrderCompletionServiceImpl implements OrderCompletionService {
    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final InventoryService inventory;
    private final OrderConverter converter;
    private final OrderValidator validator;
    private final Clock clock;
    @Value("${order.lock-timeout-ms:5000}") private long lockTimeoutMs = 5000;

    @PostConstruct void validateConfiguration() {
        if (lockTimeoutMs <= 0) throw new IllegalArgumentException("Order lock timeout must be positive");
    }

    @Override @Transactional(isolation = Isolation.READ_COMMITTED)
    public void completeOrder(Long ownerId, Long orderId) {
        validator.validateCompletionRequest(ownerId, orderId);
        orders.setLockTimeout(lockTimeoutMs + "ms");
        Order order = orders.findOwnedForUpdate(ownerId, orderId).orElse(null);
        if (!validator.canComplete(order, ownerId)) return;
        Instant event = clock.instant().truncatedTo(ChronoUnit.MICROS);
        validator.validateTime(order, event);
        if (order.isInventoryManaged()) {
            OrderInventorySeed seed = converter.toInventorySeed(order, items.findDetails(orderId));
            inventory.consumeReservations(new ResolutionCommand(orderId, event, seed.getItems().stream()
                    .map(item -> new ReservationLine(item.getId(), item.getVariantId(), item.getQuantity())).toList()));
        }
        order.complete(event);
    }
}
