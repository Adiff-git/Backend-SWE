package com.example.demo.cart.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class UpdateCartQuantityRequest {
    @NotNull @Min(1) @Max(999) private Integer quantity;
}
