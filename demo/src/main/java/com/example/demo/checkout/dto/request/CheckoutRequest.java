package com.example.demo.checkout.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Value;

@Value
public class CheckoutRequest {
    @NotNull @Positive Long addressId;
}
