package com.example.demo.order.repository;

import com.example.demo.order.dto.request.CreateOrderCommand;

public interface OrderCreationRepository {
    Long nextOrderId();
    void insertPendingOrder(Long id, String number, CreateOrderCommand command);
    Long insertHistoricalItem(Long id, CreateOrderCommand.Item item);
}
