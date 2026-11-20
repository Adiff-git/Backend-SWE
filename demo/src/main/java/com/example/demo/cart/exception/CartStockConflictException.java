package com.example.demo.cart.exception;

import com.example.demo.common.exception.ApplicationException;

public final class CartStockConflictException extends ApplicationException {
    public CartStockConflictException() { super(409, "Requested quantity is unavailable"); }
    @Override public String getCode() { return "CART_STOCK_CONFLICT"; }
}
