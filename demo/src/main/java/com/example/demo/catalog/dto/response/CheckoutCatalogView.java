package com.example.demo.catalog.dto.response;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.Value;

@Value
public class CheckoutCatalogView {
    Instant pricingInstant;
    Map<UUID, CheckoutVariantState> states;

    public CheckoutCatalogView(Instant pricingInstant, Map<UUID, CheckoutVariantState> states) {
        this.pricingInstant = java.util.Objects.requireNonNull(pricingInstant);
        this.states = Map.copyOf(states);
    }
}
