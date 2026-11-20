package com.example.demo.checkout.calculator;

import com.example.demo.cart.dto.response.CheckoutCartSeed;
import com.example.demo.catalog.dto.response.CheckoutVariantState;
import com.example.demo.checkout.dto.response.CheckoutCalculation;
import com.example.demo.checkout.exception.CheckoutAmountLimitException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CheckoutCalculator {
    public static final int MAX_INTEGER_DIGITS = 131072;
    private static final BigInteger AMOUNT_LIMIT = BigInteger.TEN.pow(MAX_INTEGER_DIGITS);

    public CheckoutCalculation calculate(List<CheckoutCartSeed.Row> included, Map<UUID, CheckoutVariantState> states) {
        List<CheckoutCalculation.Line> lines = new ArrayList<>();
        BigInteger total = BigInteger.ZERO;
        for (CheckoutCartSeed.Row row : included) {
            CheckoutVariantState state = states.get(row.getVariantId());
            BigInteger price = state.getPrice().toBigIntegerExact();
            validateAmount(price);
            BigInteger line = price.multiply(BigInteger.valueOf(row.getQuantity()));
            validateAmount(line);
            total = total.add(line);
            validateAmount(total);
            lines.add(new CheckoutCalculation.Line(row,state,price,line));
        }
        return new CheckoutCalculation(lines,total);
    }

    public void validateAmount(BigInteger amount) {
        if (amount == null || amount.signum() < 0 || amount.compareTo(AMOUNT_LIMIT) >= 0) throw new CheckoutAmountLimitException();
    }
}
