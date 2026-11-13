package com.example.demo.cart.exception;

import com.example.demo.common.exception.ApplicationException;

public final class CartItemNotFoundException extends ApplicationException {
    public CartItemNotFoundException() { super(404, "Cart item not found"); }
    @Override public String getCode() { return "CART_ITEM_NOT_FOUND"; }
}
