package com.example.demo.cart.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class CartSelectionRequest {
    @NotNull private Boolean selected;
}
