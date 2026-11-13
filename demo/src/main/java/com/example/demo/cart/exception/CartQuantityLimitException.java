package com.example.demo.cart.exception;

import com.example.demo.common.exception.ApplicationException;

public final class CartQuantityLimitException extends ApplicationException {
    public CartQuantityLimitException() { super(422, "Resulting quantity exceeds the cart item limit"); }
    @Override public String getCode() { return "CART_QUANTITY_LIMIT"; }
}
