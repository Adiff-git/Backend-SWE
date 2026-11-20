package com.example.demo.inventory.service;

import com.example.demo.inventory.dto.response.InventoryAvailability;
import com.example.demo.inventory.dto.request.ReservationCommand;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface InventoryService {
    Map<UUID, InventoryAvailability> getAvailability(Collection<UUID> variantIds);
    Map<UUID, InventoryAvailability> lockAvailability(Collection<UUID> variantIds);
    void reserveReservations(ReservationCommand command);
    void releaseReservations(com.example.demo.inventory.dto.request.ResolutionCommand command);
    void consumeReservations(com.example.demo.inventory.dto.request.ResolutionCommand command);
}
