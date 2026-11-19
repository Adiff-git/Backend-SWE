package com.example.demo.order.service;

import com.example.demo.order.dto.request.OrderListRequest;
import com.example.demo.order.dto.response.OrderReadSeed;
import com.example.demo.order.entity.OrderStatus;
import com.example.demo.order.repository.OrderItemRepository;
import com.example.demo.order.repository.OrderRepository;
import com.example.demo.order.service.impl.OrderReadServiceImpl;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OrderReadServiceImplTest {
    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderItemRepository items = mock(OrderItemRepository.class);
    private final OrderReadService reads = new OrderReadServiceImpl(orders, items);

    @Test void getOrderPageSeed_shouldCountFirstAndSkipAllEmptyPageWork() {
        when(orders.countOwnedOrders(1L, null)).thenReturn(125L);
        OrderReadSeed.Page result = reads.getOrderPageSeed(1L, new OrderListRequest(Integer.MAX_VALUE, 100, null, "placedAt,desc"));
        assertThat(result.getHeaders()).isEmpty();
        assertThat(result.getTotalItems()).isEqualTo(125);
        assertThat(result.getPage()).isEqualTo(Integer.MAX_VALUE);
        verify(orders).countOwnedOrders(1L, null);
        org.mockito.Mockito.verifyNoMoreInteractions(orders);
        verifyNoInteractions(items);
    }

    @Test void getOrderPageSeed_shouldSkipContentAtExactEndAndForNoOrders() {
        for (long count : new long[]{0, 20}) {
            when(orders.countOwnedOrders(1L, null)).thenReturn(count);
            assertThat(reads.getOrderPageSeed(1L, new OrderListRequest(1, 20, null, "placedAt,desc")).getHeaders()).isEmpty();
        }
        verifyNoInteractions(items);
        verify(orders, org.mockito.Mockito.never()).findOwnedHeaders(any(), any(), any());
    }

    @Test void getOrderPageSeed_shouldDispatchLargeNonemptyOffsetWithoutNarrowing() {
        when(orders.countOwnedOrders(1L, null)).thenReturn(2147483649L);
        when(orders.findOwnedHeadersLong(eq(1L), eq(null), eq(2147483648L), eq(2), eq("placedAt,desc"))).thenReturn(List.of(header()));
        when(items.findPreviews(any())).thenReturn(List.of());
        OrderReadSeed.Page result = reads.getOrderPageSeed(1L, new OrderListRequest(1073741824, 2, null, "placedAt,desc"));
        assertThat(result.getTotalItems()).isEqualTo(2147483649L);
        org.mockito.InOrder sequence = inOrder(orders, items);
        sequence.verify(orders).countOwnedOrders(1L, null);
        sequence.verify(orders).findOwnedHeadersLong(1L, null, 2147483648L, 2, "placedAt,desc");
        sequence.verify(items).findPreviews(List.of(7L));
    }

    @Test void getOrderPageSeed_shouldUseJpaAtExactIntegerOffsetBoundary() {
        when(orders.countOwnedOrders(1L, null)).thenReturn(2147483649L);
        when(orders.findOwnedHeaders(eq(1L), eq(null), any(Pageable.class))).thenReturn(List.of());
        reads.getOrderPageSeed(1L, new OrderListRequest(Integer.MAX_VALUE, 1, null, "placedAt,desc"));
        verify(orders).findOwnedHeaders(eq(1L), eq(null), org.mockito.ArgumentMatchers.argThat(p -> p.getOffset() == Integer.MAX_VALUE));
        verifyNoInteractions(items);
    }

    static OrderReadSeed.Header header() {
        return new OrderReadSeed.Header(7L, "ORDER7", Instant.EPOCH, OrderStatus.PENDING,
                BigDecimal.valueOf(90), BigDecimal.valueOf(100), "Recipient", "Phone", "Old address", null, null, null);
    }

    @Test void getOrderSeed_shouldNotLoadItemsWhenOwnedHeaderMissing() {
        when(orders.findOwnedHeader(1L,7L)).thenReturn(java.util.Optional.empty());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> reads.getOrderSeed(1L,7L))
                .isInstanceOf(com.example.demo.order.exception.OrderNotFoundException.class);
        verifyNoInteractions(items);
    }
}
