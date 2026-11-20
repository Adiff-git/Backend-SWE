package com.example.demo.checkout.exception;

import com.example.demo.common.exception.ApplicationException;

public class CheckoutKeyConflictException extends ApplicationException {
    public CheckoutKeyConflictException() { super(409, "Key has already been used for a different checkout request"); }
    @Override public String getCode() { return "CHECKOUT_KEY_CONFLICT"; }
}
