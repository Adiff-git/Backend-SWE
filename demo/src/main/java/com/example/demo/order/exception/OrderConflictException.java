package com.example.demo.order.exception;

import com.example.demo.common.exception.ApplicationException;

public final class OrderConflictException extends ApplicationException {
    public OrderConflictException() { super(409, "Order action conflicts with its current state"); }
    @Override public String getCode() { return "ORDER_CONFLICT"; }
}
