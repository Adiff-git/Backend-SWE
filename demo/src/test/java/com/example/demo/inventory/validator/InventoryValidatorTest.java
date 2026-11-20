package com.example.demo.inventory.validator;

import com.example.demo.inventory.dto.request.ReservationCommand;
import com.example.demo.inventory.dto.request.ReservationLine;
import com.example.demo.inventory.exception.InventoryConflictException;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryValidatorTest {
    private final InventoryValidator validator = new InventoryValidator();
    private final Instant event = Instant.parse("2026-01-01T00:00:00Z");

    @Test void availableQuantity_shouldUseFullLongRangeWithoutCartLimitOrOverflow() {
        var zero = com.example.demo.testdata.inventory.InventoryTestData.zeroBalance();
        assertThat(validator.availableQuantity(zero.stock(), zero.reserved())).isZero();
        assertThat(validator.availableQuantity(Long.MAX_VALUE, 0)).isEqualTo(Long.MAX_VALUE);
        assertThat(validator.availableQuantity(Long.MAX_VALUE, Long.MAX_VALUE)).isZero();
        assertThat(validator.availableQuantity(10, 3)).isEqualTo(7);
        for (long[] invalid : new long[][]{{-1, 0}, {0, -1}, {0, 1}, {Long.MAX_VALUE, -1}}) {
            assertThatThrownBy(() -> validator.availableQuantity(invalid[0], invalid[1])).isInstanceOf(InventoryConflictException.class);
        }
    }

    @Test void reservation_shouldRejectNullInvalidAndDuplicateLinesBeforeAnyMutation() {
        ReservationLine line = new ReservationLine(1L, UUID.randomUUID(), 3);
        validator.validateReservation(new ReservationCommand(1L, event, List.of(line)));
        for (ReservationCommand invalid : new ReservationCommand[]{null,
                new ReservationCommand(0L, event, List.of(line)),
                new ReservationCommand(1L, null, List.of(line)),
                new ReservationCommand(1L, event, null),
                new ReservationCommand(1L, event, java.util.Arrays.asList((ReservationLine) null)),
                new ReservationCommand(1L, event, List.of()),
                new ReservationCommand(1L, event, List.of(line, line)),
                new ReservationCommand(1L, event, List.of(line, new ReservationLine(2L, line.getOriginalVariantId(), 1))),
                new ReservationCommand(1L, event, List.of(new ReservationLine(1L, UUID.randomUUID(), 1000))),
                new ReservationCommand(1L, event, List.of(new ReservationLine(1L, UUID.randomUUID(), 0))),
                new ReservationCommand(1L, event, List.of(new ReservationLine(-1L, UUID.randomUUID(), 1))),
                new ReservationCommand(1L, event, List.of(new ReservationLine(1L, null, 1)))}) {
            assertThatThrownBy(() -> validator.validateReservation(invalid)).isInstanceOf(InventoryConflictException.class);
        }
        validator.validateReservation(new ReservationCommand(1L, event, List.of(new ReservationLine(1L, UUID.randomUUID(), 999))));
    }

    @Test void reservationCommand_shouldCopyCallerInputAndExposeImmutableLines() {
        List<ReservationLine> input = new ArrayList<>();
        input.add(new ReservationLine(1L, UUID.randomUUID(), 1));
        ReservationCommand command = new ReservationCommand(1L, event, input);
        input.clear();
        assertThat(command.getLines()).hasSize(1);
        assertThatThrownBy(() -> command.getLines().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void event_shouldRejectBackwardTimeAndAcceptEqualUtcInstant() {
        validator.validateEvent(event, event);
        validator.validateEvent(event.plusNanos(1000), event);
        assertThatThrownBy(() -> validator.validateEvent(event.minusNanos(1000), event)).isInstanceOf(InventoryConflictException.class);
        assertThatThrownBy(() -> validator.validateEvent(null, event)).isInstanceOf(InventoryConflictException.class);
    }
}
