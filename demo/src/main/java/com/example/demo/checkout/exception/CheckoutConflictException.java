package com.example.demo.checkout.exception;

import com.example.demo.common.exception.ApplicationException;

public class CheckoutConflictException extends ApplicationException {
    public CheckoutConflictException() { super(409, "Concurrent checkout change; you may retry"); }
    @Override public String getCode() { return "CHECKOUT_CONFLICT"; }
}
