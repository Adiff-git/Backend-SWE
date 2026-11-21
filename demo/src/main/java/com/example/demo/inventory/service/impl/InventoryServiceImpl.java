package com.example.demo.inventory.service.impl;

import com.example.demo.inventory.converter.InventoryConverter;
import com.example.demo.inventory.dto.request.ReservationCommand;
import com.example.demo.inventory.dto.request.ReservationLine;
import com.example.demo.inventory.dto.response.InventoryAvailability;
import com.example.demo.inventory.exception.InventoryConflictException;
import com.example.demo.inventory.repository.InventoryBalanceRepository;
import com.example.demo.inventory.service.InventoryService;
import com.example.demo.inventory.validator.InventoryValidator;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
@Transactional(isolation = Isolation.READ_COMMITTED)
public class InventoryServiceImpl implements InventoryService {
    private static final int READ_BATCH_SIZE = 100;
    private final InventoryBalanceRepository balances;
    private final InventoryValidator validator;
    private final InventoryConverter converter;
    private final com.example.demo.inventory.repository.InventoryReservationRepository reservations;

    @Override @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public Map<UUID, InventoryAvailability> getAvailability(Collection<UUID> variantIds) {
        return observeAvailability(variantIds, false);
    }

    @Override public Map<UUID, InventoryAvailability> lockAvailability(Collection<UUID> variantIds) {
        return observeAvailability(variantIds, true);
    }

    @Override public void reserveReservations(ReservationCommand command) {
        validator.validateReservation(command);
        List<ReservationLine> lines = command.getLines().stream()
                .sorted(Comparator.comparing(line -> line.getOriginalVariantId().toString())).toList();
        List<UUID> ids = lines.stream().map(ReservationLine::getOriginalVariantId).toList();
        Map<UUID, InventoryBalanceRepository.BalanceRow> locked = new LinkedHashMap<>();
        for (int start = 0; start < ids.size(); start += READ_BATCH_SIZE) {
            balances.lockLiveBalances(ids.subList(start, Math.min(start + READ_BATCH_SIZE, ids.size())))
                    .forEach(row -> locked.put(row.getOriginalVariantId(), row));
        }
        for (ReservationLine line : lines) {
            InventoryBalanceRepository.BalanceRow row = locked.get(line.getOriginalVariantId());
            if (row == null || validator.availableQuantity(row.getStock(), row.getReserved()) < line.getQuantity()
                    || balances.reserveUnits(row.getId(), line.getQuantity()) != 1
                    || balances.insertReservation(command.getOrderId(), line.getOrderItemId(), row.getId(),
                            line.getOriginalVariantId(), line.getQuantity(), command.getPlacedAt()) != 1) {
                throw new InventoryConflictException();
            }
        }
    }

    @Override public void releaseReservations(com.example.demo.inventory.dto.request.ResolutionCommand command) {
        resolveOwnedReservations(command, false);
    }

    @Override public void consumeReservations(com.example.demo.inventory.dto.request.ResolutionCommand command) {
        resolveOwnedReservations(command, true);
    }

    private void resolveOwnedReservations(com.example.demo.inventory.dto.request.ResolutionCommand command, boolean consume) {
        validator.validateResolutionCommand(command);
        List<com.example.demo.inventory.dto.response.InventoryAllocationSeed> observed = reservations.findOwnedReservations(command.getOrderId())
                .stream().map(converter::toAllocationSeed).toList();
        validator.validateResolution(command, observed);
        List<UUID> ids = observed.stream().map(com.example.demo.inventory.dto.response.InventoryAllocationSeed::getOriginalVariantId)
                .distinct().sorted(Comparator.comparing(UUID::toString)).toList();
        Map<UUID, InventoryBalanceRepository.BalanceRow> locked = new LinkedHashMap<>();
        for (int start = 0; start < ids.size(); start += READ_BATCH_SIZE) {
            balances.lockBalances(ids.subList(start, Math.min(start + READ_BATCH_SIZE, ids.size())))
                    .forEach(row -> locked.put(row.getOriginalVariantId(), row));
        }
        List<com.example.demo.inventory.dto.response.InventoryAllocationSeed> allocations = reservations.lockOwnedReservations(command.getOrderId())
                .stream().map(converter::toAllocationSeed).toList();
        validator.validateResolution(command, allocations);
        for (com.example.demo.inventory.dto.response.InventoryAllocationSeed allocation : allocations) {
            InventoryBalanceRepository.BalanceRow row = locked.get(allocation.getOriginalVariantId());
            if (row == null || !row.getId().equals(allocation.getInventoryBalanceId())) throw new InventoryConflictException();
            validator.availableQuantity(row.getStock(), row.getReserved());
            int changed = consume ? balances.consumeUnits(row.getId(), allocation.getQuantity())
                    : balances.releaseUnits(row.getId(), allocation.getQuantity());
            if (changed != 1 || balances.resolveReservation(allocation.getId(), consume ? "CONSUMED" : "RELEASED", command.getResolvedAt()) != 1) {
                throw new InventoryConflictException();
            }
        }
    }

    private Map<UUID, InventoryAvailability> observeAvailability(Collection<UUID> variantIds, boolean locking) {
        if (variantIds == null || variantIds.stream().anyMatch(java.util.Objects::isNull)) throw new InventoryConflictException();
        List<UUID> ids = variantIds.stream().distinct().sorted(Comparator.comparing(UUID::toString)).toList();
        Map<UUID, InventoryAvailability> result = new LinkedHashMap<>();
        ids.forEach(id -> result.put(id, converter.toAvailability(false, 0)));
        for (int start = 0; start < ids.size(); start += READ_BATCH_SIZE) {
            List<UUID> batch = ids.subList(start, Math.min(start + READ_BATCH_SIZE, ids.size()));
            List<InventoryBalanceRepository.BalanceRow> rows = locking ? balances.lockLiveBalances(batch) : balances.findBalances(batch);
            for (InventoryBalanceRepository.BalanceRow row : rows) {
                long available = validator.availableQuantity(row.getStock(), row.getReserved());
                result.put(row.getOriginalVariantId(), converter.toAvailability(true, available));
            }
        }
        return java.util.Collections.unmodifiableMap(result);
    }
}
