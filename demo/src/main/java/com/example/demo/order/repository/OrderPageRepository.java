package com.example.demo.order.repository;

import com.example.demo.order.dto.response.OrderReadSeed;
import com.example.demo.order.entity.OrderStatus;
import java.util.List;

public interface OrderPageRepository {
    List<OrderReadSeed.Header> findOwnedHeadersLong(Long owner, OrderStatus status, long offset, int size, String sort);
}
