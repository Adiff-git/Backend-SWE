package com.example.demo.checkout.exception;

import com.example.demo.common.exception.ApplicationException;

public class CheckoutAmountLimitException extends ApplicationException {
    public CheckoutAmountLimitException() { super(422, "Checkout amount exceeds supported limits"); }
    @Override public String getCode() { return "CHECKOUT_AMOUNT_LIMIT"; }
}
