package com.example.demo.catalog.service;

import com.example.demo.catalog.dto.response.CheckoutVariantState;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface CatalogCheckoutService {
    Map<UUID, CheckoutVariantState> getCheckoutVariantStates(Collection<UUID> ids);
}
