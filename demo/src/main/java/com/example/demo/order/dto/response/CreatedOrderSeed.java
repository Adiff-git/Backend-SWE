package com.example.demo.order.dto.response;

import com.example.demo.order.dto.request.CreateOrderCommand;
import java.util.List;
import lombok.Value;

@Value
public class CreatedOrderSeed {
    Long id;
    String orderNumber;
    CreateOrderCommand command;
    List<Item> items;

    public CreatedOrderSeed(Long id, String orderNumber, CreateOrderCommand command, List<Item> items) {
        this.id = id;
        this.orderNumber = orderNumber;
        this.command = command;
        this.items = List.copyOf(items);
    }

    @Value
    public static class Item {
        Long id;
        CreateOrderCommand.Item snapshot;
    }
}
