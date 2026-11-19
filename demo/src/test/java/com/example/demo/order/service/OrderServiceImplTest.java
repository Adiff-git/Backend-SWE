package com.example.demo.order.service;

import com.example.demo.catalog.service.CatalogOrderReadService;
import com.example.demo.order.converter.OrderConverter;
import com.example.demo.order.dto.request.OrderListRequest;
import com.example.demo.order.dto.response.OrderReadSeed;
import com.example.demo.order.service.impl.OrderServiceImpl;
import com.example.demo.order.repository.OrderRepository;
import com.example.demo.order.validator.OrderValidator;
import com.example.demo.order.dto.request.CancelOrderRequest;
import com.example.demo.order.entity.Order;
import com.example.demo.order.entity.OrderStatus;
import com.example.demo.testdata.order.OrderTestData;
import java.time.Clock;
import java.util.Optional;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OrderServiceImplTest {
    private final OrderReadService reads = mock(OrderReadService.class);
    private final CatalogOrderReadService images = mock(CatalogOrderReadService.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final Clock clock = mock(Clock.class);
    private final OrderService service = new OrderServiceImpl(reads, images, new OrderConverter(),orders,new OrderValidator(),clock);

    @Test void listOrders_shouldNeverPrepareImagesOnCountShortcut() {
        OrderListRequest request = new OrderListRequest(Integer.MAX_VALUE,100,null,"placedAt,desc");
        when(reads.getOrderPageSeed(1L,request)).thenReturn(new OrderReadSeed.Page(List.of(),List.of(),Integer.MAX_VALUE,100,125));
        assertThat(service.listOrders(1L,request).getTotalPages()).isEqualTo(2);
        verifyNoInteractions(images);
    }

    @Test void getOrder_shouldNeverPrepareImagesBeforeOwnerLookup() {
        when(reads.getOrderSeed(1L,7L)).thenThrow(new com.example.demo.order.exception.OrderNotFoundException());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.getOrder(1L,7L))
                .isInstanceOf(com.example.demo.order.exception.OrderNotFoundException.class);
        verifyNoInteractions(images);
    }

    @Test void cancelOrder_shouldPreserveWinningReasonAndNeverSampleClockForRepeat() {
        Order order = OrderTestData.order(1L,OrderStatus.CANCELLED).id(7L).cancellationReason("first").build();
        when(orders.findOwnedForUpdate(1L,7L)).thenReturn(Optional.of(order));
        service.cancelOrder(1L,7L,new CancelOrderRequest("second"));
        assertThat(order.getCancellationReason()).isEqualTo("first");
        verifyNoInteractions(clock,images,reads);
    }

    @Test void cancelOrder_shouldValidateTimeBeforeChangingAnything() {
        Order order = OrderTestData.order(1L,OrderStatus.CONFIRMED).id(7L).build();
        when(orders.findOwnedForUpdate(1L,7L)).thenReturn(Optional.of(order));
        when(clock.instant()).thenReturn(OrderTestData.PLACED.minusSeconds(1));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.cancelOrder(1L,7L,new CancelOrderRequest("reason")))
                .isInstanceOf(com.example.demo.order.exception.OrderConflictException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getCancelledAt()).isNull();
        verifyNoInteractions(images,reads);
    }
}
