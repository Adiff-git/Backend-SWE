package com.example.demo.checkout.dto.response;

import com.example.demo.cart.dto.response.CheckoutCartSeed;
import com.example.demo.catalog.dto.response.CheckoutVariantState;
import java.math.BigInteger;
import java.util.List;
import lombok.Value;

@Value
public class CheckoutCalculation {
    List<Line> lines;
    BigInteger total;

    public CheckoutCalculation(List<Line> lines, BigInteger total) {
        this.lines = List.copyOf(lines);
        this.total = total;
    }

    @Value
    public static class Line {
        CheckoutCartSeed.Row row;
        CheckoutVariantState state;
        BigInteger unitPrice;
        BigInteger lineTotal;
    }
}
