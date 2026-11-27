package com.example.demo.catalog.dto.response;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.Value;
/** Immutable pricing results owned by one operation. */
@Value
public class PreparedOperationPricing {
    Instant pricingInstant;
    Map<UUID, CartVariantState> states;
    public PreparedOperationPricing(Instant at, Map<UUID, CartVariantState> states) {
        this.pricingInstant = java.util.Objects.requireNonNull(at);
        this.states = Map.copyOf(states);
    }
}
