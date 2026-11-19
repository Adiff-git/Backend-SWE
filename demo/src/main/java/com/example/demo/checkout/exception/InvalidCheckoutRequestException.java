package com.example.demo.checkout.exception;

import com.example.demo.common.exception.ApplicationException;

public class InvalidCheckoutRequestException extends ApplicationException {
    public InvalidCheckoutRequestException() { super(400, "Invalid checkout request"); }
    @Override public String getCode() { return "INVALID_CHECKOUT_REQUEST"; }
}
