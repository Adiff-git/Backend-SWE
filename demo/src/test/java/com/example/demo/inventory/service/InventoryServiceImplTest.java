package com.example.demo.inventory.service;

import com.example.demo.inventory.converter.InventoryConverter;
import com.example.demo.inventory.exception.InventoryConflictException;
import com.example.demo.inventory.repository.InventoryBalanceRepository;
import com.example.demo.inventory.service.impl.InventoryServiceImpl;
import com.example.demo.inventory.validator.InventoryValidator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InventoryServiceImplTest {
    private final InventoryBalanceRepository balances = mock(InventoryBalanceRepository.class);
    private final com.example.demo.inventory.repository.InventoryReservationRepository reservations = mock(com.example.demo.inventory.repository.InventoryReservationRepository.class);
    private final InventoryService service = new InventoryServiceImpl(balances, new InventoryValidator(), new InventoryConverter(), reservations);

    @Test void reservation_shouldIncreaseOnlyReservedAndRequireEveryAffectedCount() {
        UUID variant = UUID.randomUUID();
        var row = mock(InventoryBalanceRepository.BalanceRow.class);
        when(row.getId()).thenReturn(7L);
        when(row.getOriginalVariantId()).thenReturn(variant);
        when(row.getStock()).thenReturn(3L);
        when(row.getReserved()).thenReturn(0L);
        when(balances.lockLiveBalances(List.of(variant))).thenReturn(List.of(row));
        when(balances.reserveUnits(7L, 3)).thenReturn(1);
        java.time.Instant at = java.time.Instant.parse("2026-01-01T00:00:00Z");
        when(balances.insertReservation(1L, 2L, 7L, variant, 3, at)).thenReturn(1);
        var command = new com.example.demo.inventory.dto.request.ReservationCommand(1L, at,
                List.of(new com.example.demo.inventory.dto.request.ReservationLine(2L, variant, 3)));
        service.reserveReservations(command);
        verify(balances).reserveUnits(7L, 3);
        verify(balances).insertReservation(1L, 2L, 7L, variant, 3, at);
        org.mockito.Mockito.verify(balances, org.mockito.Mockito.never()).consumeUnits(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyInt());
        when(balances.reserveUnits(7L, 3)).thenReturn(0);
        assertThatThrownBy(() -> service.reserveReservations(command)).isInstanceOf(InventoryConflictException.class);
    }

    @Test void emptyObservation_shouldIssueNoSqlAndReturnImmutableEmptyResult() {
        assertThat(service.getAvailability(List.of())).isEmpty();
        assertThat(service.lockAvailability(List.of())).isEmpty();
        verifyNoInteractions(balances);
    }

    @Test void observations_shouldDeduplicateAndGloballySortBeforeBatching205Inputs() {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 205; i++) ids.add(UUID.randomUUID());
        ids.add(ids.getFirst());
        when(balances.findBalances(anyCollection())).thenReturn(List.of());
        var observed = service.getAvailability(ids);
        assertThat(observed).hasSize(205);
        assertThat(observed.values()).allSatisfy(value -> {
            assertThat(value.getAvailableQuantity()).isZero();
            assertThat(value.getStatus()).isEqualTo("INVENTORY_UNAVAILABLE");
        });
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<Collection<UUID>> batches = ArgumentCaptor.forClass((Class) Collection.class);
        verify(balances, times(3)).findBalances(batches.capture());
        assertThat(batches.getAllValues()).extracting(Collection::size).containsExactly(100, 100, 5);
        assertThat(batches.getAllValues().stream().flatMap(Collection::stream).toList())
                .containsExactlyElementsOf(ids.stream().distinct().sorted(Comparator.comparing(UUID::toString)).toList());
        assertThatThrownBy(observed::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void lockedObservations_shouldUseFreshCountersAndAbortOnCorruption() {
        var input = com.example.demo.testdata.inventory.InventoryTestData.balance(Long.MAX_VALUE, Long.MAX_VALUE);
        UUID variant = input.originalVariantId();
        InventoryBalanceRepository.BalanceRow row = mock(InventoryBalanceRepository.BalanceRow.class);
        when(row.getOriginalVariantId()).thenReturn(variant);
        when(row.getStock()).thenReturn(input.stock());
        when(row.getReserved()).thenReturn(input.reserved());
        when(balances.lockLiveBalances(List.of(variant))).thenReturn(List.of(row));
        assertThat(service.lockAvailability(List.of(variant)).get(variant).getStatus()).isEqualTo("OUT_OF_STOCK");
        when(row.getReserved()).thenReturn(-1L);
        assertThatThrownBy(() -> service.lockAvailability(List.of(variant))).isInstanceOf(InventoryConflictException.class);
    }
}
