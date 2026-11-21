package com.example.demo.order.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.demo.order.entity.Order;
import com.example.demo.order.entity.OrderItem;
import com.example.demo.order.entity.OrderStatus;
import com.example.demo.order.repository.OrderItemRepository;
import com.example.demo.order.repository.OrderRepository;
import com.example.demo.order.service.impl.OrderReviewReadServiceImpl;
import com.example.demo.testdata.order.OrderTestData;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class OrderReviewReadServiceImplTest {
    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PENDING", "CONFIRMED", "SHIPPING", "CANCELLED", "FAILED"})
    void getReviewPurchase_shouldRejectNonCompletedState(OrderStatus status) {
        OrderRepository orders = mock(OrderRepository.class);
        OrderItemRepository items = mock(OrderItemRepository.class);
        Order order = OrderTestData.order(1L, status).id(2L).build();
        OrderItem item = OrderTestData.item(order).id(3L).build();
        when(orders.findOwnedForUpdate(1L, 2L)).thenReturn(Optional.of(order));
        when(items.findReviewItemForUpdate(2L, 3L)).thenReturn(Optional.of(item));
        assertThatThrownBy(() -> new OrderReviewReadServiceImpl(orders, items).getReviewPurchase(1L, 2L, 3L, true))
                .isInstanceOf(com.example.demo.order.exception.OrderConflictException.class);
    }
}
