package com.example.demo.inventory.validator;

import com.example.demo.inventory.dto.request.ReservationCommand;
import com.example.demo.inventory.dto.request.ReservationLine;
import com.example.demo.inventory.exception.InventoryConflictException;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class InventoryValidator {
    private static final int MAX_LINE_QUANTITY = 999;

    public void validateResolutionCommand(com.example.demo.inventory.dto.request.ResolutionCommand command) {
        if (command == null) throw new InventoryConflictException();
        validateReservation(new ReservationCommand(command.getOrderId(), command.getResolvedAt(), command.getLines()));
    }

    public void validateResolution(com.example.demo.inventory.dto.request.ResolutionCommand command,
            java.util.List<com.example.demo.inventory.dto.response.InventoryAllocationSeed> allocations) {
        validateResolutionCommand(command);
        if (allocations.size() != command.getLines().size()) throw new InventoryConflictException();
        java.util.Map<Long, ReservationLine> expected = new java.util.HashMap<>();
        command.getLines().forEach(line -> expected.put(line.getOrderItemId(), line));
        for (com.example.demo.inventory.dto.response.InventoryAllocationSeed allocation : allocations) {
            ReservationLine line = expected.remove(allocation.getOrderItemId());
            if (line == null || !command.getOrderId().equals(allocation.getOrderId())
                    || !line.getOriginalVariantId().equals(allocation.getOriginalVariantId())
                    || line.getQuantity() != allocation.getQuantity() || !"ACTIVE".equals(allocation.getState())
                    || allocation.getResolvedAt() != null || allocation.getInventoryBalanceId() == null
                    || allocation.getId() == null) throw new InventoryConflictException();
            validateEvent(command.getResolvedAt(), allocation.getReservedAt());
        }
        if (!expected.isEmpty()) throw new InventoryConflictException();
    }

    public long availableQuantity(long stock, long reserved) {
        if (stock < 0 || reserved < 0 || reserved > stock) throw new InventoryConflictException();
        return Math.subtractExact(stock, reserved);
    }

    public void validateReservation(ReservationCommand command) {
        if (command == null || command.getOrderId() == null || command.getOrderId() <= 0
                || command.getPlacedAt() == null || command.getLines() == null || command.getLines().isEmpty()) {
            throw new InventoryConflictException();
        }
        Set<Long> items = new HashSet<>();
        Set<UUID> variants = new HashSet<>();
        for (ReservationLine line : command.getLines()) {
            if (line == null || line.getOrderItemId() == null || line.getOrderItemId() <= 0
                    || line.getOriginalVariantId() == null || line.getQuantity() < 1 || line.getQuantity() > MAX_LINE_QUANTITY
                    || !items.add(line.getOrderItemId()) || !variants.add(line.getOriginalVariantId())) {
                throw new InventoryConflictException();
            }
        }
    }

    public void validateEvent(Instant event, Instant placedAt) {
        if (event == null || placedAt == null || event.isBefore(placedAt)) throw new InventoryConflictException();
    }
}
