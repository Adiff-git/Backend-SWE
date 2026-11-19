package com.example.demo.cart.repository;

import com.example.demo.cart.dto.response.CheckoutCartSeed;
import java.util.List;

public interface CartCheckoutRepository {
    int deleteCheckoutRows(Long owner, Long cartId, List<CheckoutCartSeed.Row> rows);
}
