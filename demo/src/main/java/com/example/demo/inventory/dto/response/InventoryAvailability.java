package com.example.demo.inventory.dto.response;

import lombok.Value;

@Value
public class InventoryAvailability {
    long availableQuantity;
    String status;
}
