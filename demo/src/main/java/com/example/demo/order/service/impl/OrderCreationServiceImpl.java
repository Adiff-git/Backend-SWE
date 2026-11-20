package com.example.demo.order.service.impl;

import com.example.demo.order.dto.request.CreateOrderCommand;
import com.example.demo.order.dto.response.CreatedOrderSeed;
import com.example.demo.order.repository.OrderRepository;
import com.example.demo.order.service.OrderCreationService;
import com.example.demo.order.validator.OrderValidator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderCreationServiceImpl implements OrderCreationService {
    private final OrderRepository orders;
    private final OrderValidator validator;

    @Override
    @Transactional
    public CreatedOrderSeed createOrder(CreateOrderCommand command) {
        validator.validateCreation(command);
        Long id = orders.nextOrderId();
        String number = "ORD-" + id;
        orders.insertPendingOrder(id,number,command);
        List<CreatedOrderSeed.Item> items = new ArrayList<>();
        for (CreateOrderCommand.Item item : command.getItems().stream().sorted(Comparator.comparing(CreateOrderCommand.Item::getCartItemId)).toList()) {
            items.add(new CreatedOrderSeed.Item(orders.insertHistoricalItem(id,item),item));
        }
        return new CreatedOrderSeed(id,number,command,items);
    }
}
