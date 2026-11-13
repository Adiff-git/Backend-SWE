package com.example.demo.cart.exception;

import com.example.demo.common.exception.ApplicationException;

public final class CartVariantSwitchException extends ApplicationException {
    public CartVariantSwitchException() { super(422, "Variant must belong to the same product"); }
    @Override public String getCode() { return "CART_VARIANT_SWITCH_INVALID"; }
}
