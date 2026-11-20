package com.example.demo.order.service;

import com.example.demo.inventory.service.InventoryService;
import com.example.demo.order.converter.OrderConverter;
import com.example.demo.order.entity.Order;
import com.example.demo.order.entity.OrderStatus;
import com.example.demo.order.exception.OrderConflictException;
import com.example.demo.order.exception.OrderNotFoundException;
import com.example.demo.order.repository.OrderItemRepository;
import com.example.demo.order.repository.OrderRepository;
import com.example.demo.order.service.impl.OrderCompletionServiceImpl;
import com.example.demo.order.validator.OrderValidator;
import com.example.demo.testdata.order.OrderTestData;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OrderCompletionServiceImplTest {
    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderItemRepository items = mock(OrderItemRepository.class);
    private final InventoryService inventory = mock(InventoryService.class);
    private final Clock clock = mock(Clock.class);
    private final OrderCompletionService service = new OrderCompletionServiceImpl(orders, items, inventory,
            new OrderConverter(), new OrderValidator(), clock);

    @Test void firstCompletion_shouldConsumeExactOwnedLinesAndOneMicrosecondEvent() {
        Order order = OrderTestData.order(1L, OrderStatus.SHIPPING).inventoryManaged(true).id(7L).build();
        when(orders.findOwnedForUpdate(1L, 7L)).thenReturn(Optional.of(order));
        when(clock.instant()).thenReturn(OrderTestData.PLACED.plusSeconds(1).plusNanos(789));
        OrderItemRepository.ItemView row = mock(OrderItemRepository.ItemView.class);
        UUID variant = UUID.randomUUID();
        when(row.getId()).thenReturn(9L);
        when(row.getProductVariantId()).thenReturn(variant);
        when(row.getQuantity()).thenReturn(3);
        when(items.findDetails(7L)).thenReturn(List.of(row));
        service.completeOrder(1L, 7L);
        var command = org.mockito.ArgumentCaptor.forClass(com.example.demo.inventory.dto.request.ResolutionCommand.class);
        org.mockito.Mockito.verify(inventory).consumeReservations(command.capture());
        assertThat(command.getValue().getOrderId()).isEqualTo(7L);
        assertThat(command.getValue().getLines().getFirst().getOrderItemId()).isEqualTo(9L);
        assertThat(command.getValue().getLines().getFirst().getOriginalVariantId()).isEqualTo(variant);
        assertThat(command.getValue().getLines().getFirst().getQuantity()).isEqualTo(3);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(order.getCompletedAt()).isEqualTo(command.getValue().getResolvedAt()).isEqualTo(OrderTestData.PLACED.plusSeconds(1));
        org.mockito.Mockito.verify(clock).instant();
    }

    @Test void completedRepeat_shouldBypassClockItemsAndInventoryEvenWhenClockIsEarlier() {
        Order completed = OrderTestData.order(1L, OrderStatus.COMPLETED).inventoryManaged(true).id(7L).build();
        when(orders.findOwnedForUpdate(1L, 7L)).thenReturn(Optional.of(completed));
        service.completeOrder(1L, 7L);
        assertThat(completed.getCompletedAt()).isEqualTo(OrderTestData.PLACED.plusSeconds(1));
        verifyNoInteractions(clock, items, inventory);
    }

    @Test void legacyCompletionAndInvalidTime_shouldNeverBorrowAggregateInventory() {
        Order legacy = OrderTestData.order(1L, OrderStatus.SHIPPING).id(7L).build();
        when(orders.findOwnedForUpdate(1L, 7L)).thenReturn(Optional.of(legacy));
        when(clock.instant()).thenReturn(OrderTestData.PLACED);
        service.completeOrder(1L, 7L);
        assertThat(legacy.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        verifyNoInteractions(items, inventory);
        Order managed = OrderTestData.order(1L, OrderStatus.SHIPPING).inventoryManaged(true).id(8L).build();
        when(orders.findOwnedForUpdate(1L, 8L)).thenReturn(Optional.of(managed));
        when(clock.instant()).thenReturn(OrderTestData.PLACED.minusSeconds(1));
        assertThatThrownBy(() -> service.completeOrder(1L, 8L)).isInstanceOf(OrderConflictException.class);
        assertThat(managed.getStatus()).isEqualTo(OrderStatus.SHIPPING);
        verifyNoInteractions(items, inventory);
    }

    @ParameterizedTest @EnumSource(value = OrderStatus.class, names = {"SHIPPING", "COMPLETED"}, mode = EnumSource.Mode.EXCLUDE)
    void firstCompletion_shouldRejectEveryOtherState(OrderStatus status) {
        when(orders.findOwnedForUpdate(1L, 7L)).thenReturn(Optional.of(OrderTestData.order(1L, status).id(7L).build()));
        assertThatThrownBy(() -> service.completeOrder(1L, 7L)).isInstanceOf(OrderConflictException.class);
        verifyNoInteractions(clock, items, inventory);
    }

    @Test void completion_shouldKeepMissingAndForeignOrdersIndistinguishableAndRejectInvalidIds() {
        when(orders.findOwnedForUpdate(1L, 7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.completeOrder(1L, 7L)).isInstanceOf(OrderNotFoundException.class);
        when(orders.findOwnedForUpdate(1L, 7L)).thenReturn(Optional.of(OrderTestData.order(2L, OrderStatus.SHIPPING).id(7L).build()));
        assertThatThrownBy(() -> service.completeOrder(1L, 7L)).isInstanceOf(OrderNotFoundException.class);
        assertThatThrownBy(() -> service.completeOrder(1L, 0L)).isInstanceOf(com.example.demo.order.exception.InvalidOrderRequestException.class);
        verifyNoInteractions(clock, items, inventory);
    }
}
