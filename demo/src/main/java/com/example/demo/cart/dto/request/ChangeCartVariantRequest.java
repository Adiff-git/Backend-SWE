package com.example.demo.cart.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ChangeCartVariantRequest {
    @NotNull private UUID productVariantId;
}
