package com.example.demo.inventory.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;
import lombok.Value;

@Value
public class ReservationLine {
    @NotNull @Positive Long orderItemId;
    @NotNull UUID originalVariantId;
    @Min(1) @Max(999) int quantity;
}
