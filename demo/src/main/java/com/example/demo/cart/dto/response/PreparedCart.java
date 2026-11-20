package com.example.demo.cart.dto.response;

import com.example.demo.catalog.dto.response.CartVariantState;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Value;

@Value
public class PreparedCart {
    List<CartReadSeed> items;
    Map<UUID, CartVariantState> states;
    Map<Long, BigInteger> subtotals;
    Map<Long, String> ineligibleReasons;
    long selectedItemCount;
    long selectedTotalQuantity;
    BigInteger selectedSubtotal;

    public PreparedCart(List<CartReadSeed> items, Map<UUID, CartVariantState> states,
            Map<Long, BigInteger> subtotals, Map<Long, String> ineligibleReasons, long selectedItemCount, long selectedTotalQuantity,
            BigInteger selectedSubtotal) {
        this.items = List.copyOf(items);
        this.states = Map.copyOf(states);
        this.subtotals = Map.copyOf(subtotals);
        this.ineligibleReasons = Map.copyOf(ineligibleReasons);
        this.selectedItemCount = selectedItemCount;
        this.selectedTotalQuantity = selectedTotalQuantity;
        this.selectedSubtotal = selectedSubtotal;
    }
}
