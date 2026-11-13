package com.example.demo.cart.exception;

import com.example.demo.common.exception.ApplicationException;

public final class CartItemUnavailableException extends ApplicationException {
    public CartItemUnavailableException() { super(422, "Cart item is unavailable"); }
    @Override public String getCode() { return "CART_ITEM_UNAVAILABLE"; }
}
