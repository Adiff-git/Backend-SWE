package com.example.demo.order.service;

import com.example.demo.order.dto.request.OrderListRequest;
import com.example.demo.order.dto.response.OrderReadSeed;

public interface OrderReadService {
    OrderReadSeed.Page getOrderPageSeed(Long owner, OrderListRequest request);
    OrderReadSeed.Detail getOrderSeed(Long owner, Long id);
}
