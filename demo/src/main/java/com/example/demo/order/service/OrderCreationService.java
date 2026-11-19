package com.example.demo.order.service;

import com.example.demo.order.dto.request.CreateOrderCommand;
import com.example.demo.order.dto.response.CreatedOrderSeed;

public interface OrderCreationService {
    CreatedOrderSeed createOrder(CreateOrderCommand command);
}
