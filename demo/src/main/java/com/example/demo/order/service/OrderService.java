package com.example.demo.order.service;

import com.example.demo.order.dto.request.OrderListRequest;
import com.example.demo.order.dto.request.CancelOrderRequest;
import com.example.demo.order.dto.response.OrderPage;
import com.example.demo.order.dto.response.OrderDetailResponse;

public interface OrderService {
    OrderPage listOrders(Long owner, OrderListRequest request);
    OrderDetailResponse getOrder(Long owner, Long id);
    void cancelOrder(Long owner, Long id, CancelOrderRequest request);
}
