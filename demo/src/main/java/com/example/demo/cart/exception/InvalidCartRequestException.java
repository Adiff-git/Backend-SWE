package com.example.demo.cart.exception;

import com.example.demo.common.exception.ApplicationException;

public final class InvalidCartRequestException extends ApplicationException {
    public InvalidCartRequestException() { super(400, "Invalid cart request"); }
    @Override public String getCode() { return "INVALID_CART_REQUEST"; }
}
