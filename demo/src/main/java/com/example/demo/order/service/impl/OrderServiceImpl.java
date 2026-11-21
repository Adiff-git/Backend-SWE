package com.example.demo.order.service.impl;

import com.example.demo.catalog.service.CatalogOrderReadService;
import com.example.demo.order.converter.OrderConverter;
import com.example.demo.order.dto.request.OrderListRequest;
import com.example.demo.order.dto.request.CancelOrderRequest;
import com.example.demo.order.entity.Order;
import com.example.demo.order.repository.OrderRepository;
import com.example.demo.order.validator.OrderValidator;
import jakarta.annotation.PostConstruct;
import java.time.Clock;
import java.time.Instant;
import com.example.demo.order.dto.response.OrderPage;
import com.example.demo.order.dto.response.OrderDetailResponse;
import com.example.demo.order.dto.response.OrderReadSeed;
import com.example.demo.order.service.OrderReadService;
import com.example.demo.order.service.OrderService;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {
    private final OrderReadService reads;
    private final CatalogOrderReadService images;
    private final OrderConverter converter;
    private final OrderRepository orders;
    private final OrderValidator validator;
    private final Clock clock;
    private final com.example.demo.order.repository.OrderItemRepository items;
    private final com.example.demo.inventory.service.InventoryService inventory;
    @Value("${order.lock-timeout-ms:5000}")
    private long lockTimeoutMs = 5000;

    @PostConstruct void validateConfiguration() {
        if (lockTimeoutMs <= 0) throw new IllegalArgumentException("Order lock timeout must be positive");
    }

    @Override public OrderPage listOrders(Long owner, OrderListRequest request) {
        OrderReadSeed.Page seed = reads.getOrderPageSeed(owner,request);
        return converter.toPage(seed,imageUrls(seed.getPreviews()));
    }

    @Override public OrderDetailResponse getOrder(Long owner, Long id) {
        OrderReadSeed.Detail seed = reads.getOrderSeed(owner,id);
        return converter.toDetail(seed,imageUrls(seed.getItems()));
    }

    @Override @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void cancelOrder(Long owner, Long id, CancelOrderRequest request) {
        validator.validateRequest(owner,id,request);
        orders.setLockTimeout(lockTimeoutMs + "ms");
        Order order = orders.findOwnedForUpdate(owner,id).orElse(null);
        if (!validator.canCancel(order,owner)) return;
        Instant at = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        validator.validateTime(order,at);
        if (order.isInventoryManaged()) {
            com.example.demo.order.dto.response.OrderInventorySeed seed = converter.toInventorySeed(order, items.findDetails(order.getId()));
            inventory.releaseReservations(new com.example.demo.inventory.dto.request.ResolutionCommand(order.getId(), at,
                    seed.getItems().stream().map(item -> new com.example.demo.inventory.dto.request.ReservationLine(item.getId(), item.getVariantId(), item.getQuantity())).toList()));
        }
        order.cancel(at,request.getReason());
    }

    private Map<UUID,String> imageUrls(List<OrderReadSeed.Item> items) {
        List<UUID> ids = items.stream().map(OrderReadSeed.Item::getProductId).filter(Objects::nonNull).distinct().toList();
        return ids.isEmpty() ? Map.of() : images.getPrimaryImageUrls(ids);
    }
}
