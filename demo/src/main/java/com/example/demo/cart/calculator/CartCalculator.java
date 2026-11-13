package com.example.demo.cart.calculator;

import com.example.demo.cart.dto.response.CartReadSeed;
import com.example.demo.cart.dto.response.PreparedCart;
import com.example.demo.cart.exception.CartQuantityLimitException;
import com.example.demo.catalog.dto.response.CartVariantState;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CartCalculator {
    public PreparedCart calculate(List<CartReadSeed> rows, Map<UUID, CartVariantState> states) {
        Map<Long, BigInteger> subtotals = new HashMap<>();
        BigInteger total = BigInteger.ZERO;
        long selectedRows = 0;
        long selectedQuantity = 0;
        for (CartReadSeed row : rows) {
            CartVariantState state = states.get(row.getVariantId());
            if (state == null || !state.isPurchasable()) continue;
            BigInteger subtotal = state.getAmount().multiply(BigInteger.valueOf(row.getQuantity()));
            subtotals.put(row.getId(), subtotal);
            if (row.isSelected()) {
                total = total.add(subtotal);
                try {
                    selectedRows = Math.addExact(selectedRows, 1L);
                    selectedQuantity = Math.addExact(selectedQuantity, row.getQuantity());
                } catch (ArithmeticException overflow) { throw new CartQuantityLimitException(); }
            }
        }
        return new PreparedCart(rows, states, subtotals, selectedRows, selectedQuantity, total);
    }
}
