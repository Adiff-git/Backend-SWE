package com.example.demo.catalog.dto.response;

import java.util.UUID;
import lombok.Value;

@Value
public class ProductPriceRange {
    Endpoint minimum;
    Endpoint maximum;

    @Value
    public static class Endpoint {
        UUID variantId;
        VariantPricing pricing;
    }
}
