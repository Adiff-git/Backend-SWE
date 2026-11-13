package com.example.demo.cart.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class UpdateCartItemRequest {
    @NotNull @Min(1) @Max(1000) private Integer quantity;
    private UUID variantId;
    private boolean variantIdPresent;

    public void setVariantId(UUID variantId) {
        this.variantId = variantId;
        this.variantIdPresent = true;
    }
}
