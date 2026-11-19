package com.example.demo.checkout.exception;

import com.example.demo.common.exception.ApplicationException;

public class CheckoutInProgressException extends ApplicationException {
    public CheckoutInProgressException() { super(409, "Checkout is still processing; you may retry"); }
    @Override public String getCode() { return "CHECKOUT_IN_PROGRESS"; }
}
