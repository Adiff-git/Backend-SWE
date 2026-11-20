package com.example.demo.inventory.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.List;
import lombok.Value;

@Value
public class ReservationCommand {
    @NotNull @Positive Long orderId;
    @NotNull Instant placedAt;
    @NotEmpty List<@NotNull @Valid ReservationLine> lines;

    public ReservationCommand(Long orderId, Instant placedAt, List<ReservationLine> lines) {
        this.orderId = orderId;
        this.placedAt = placedAt;
        this.lines = lines == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(lines));
    }
}
