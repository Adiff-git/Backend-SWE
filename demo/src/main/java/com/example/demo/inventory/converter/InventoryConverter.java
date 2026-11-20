package com.example.demo.inventory.converter;

import com.example.demo.inventory.dto.response.InventoryAvailability;
import org.springframework.stereotype.Component;

@Component
public class InventoryConverter {
    public com.example.demo.inventory.dto.response.InventoryAllocationSeed toAllocationSeed(
            com.example.demo.inventory.repository.InventoryReservationRepository.AllocationRow row) {
        return new com.example.demo.inventory.dto.response.InventoryAllocationSeed(row.getId(), row.getOrderId(),
                row.getOrderItemId(), row.getInventoryBalanceId(), row.getOriginalVariantId(), row.getQuantity(),
                row.getState(), row.getReservedAt(), row.getResolvedAt());
    }
    public InventoryAvailability toAvailability(boolean known, long availableQuantity) {
        return new InventoryAvailability(availableQuantity,
                known ? (availableQuantity > 0 ? "IN_STOCK" : "OUT_OF_STOCK") : "INVENTORY_UNAVAILABLE");
    }
}
