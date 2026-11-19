package com.example.demo.cart.service;

import com.example.demo.cart.dto.response.CheckoutCartSeed;
import java.util.List;

public interface CartCheckoutService {
    CheckoutCartSeed getCheckoutCartSeed(Long owner);
    void consumeCheckoutItems(Long owner, CheckoutCartSeed seed, List<CheckoutCartSeed.Row> included);
}
