package com.example.demo.cart.exception;

import com.example.demo.common.exception.ApplicationException;

public final class CartConflictException extends ApplicationException {
    public CartConflictException() { super(409, "Concurrent cart update; you may retry"); }
    @Override public String getCode() { return "CART_CONFLICT"; }
}
