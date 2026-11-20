package com.example.demo.order.exception;

import com.example.demo.common.exception.ApplicationException;

public final class OrderNotFoundException extends ApplicationException {
    public OrderNotFoundException() { super(404, "Order not found"); }
    @Override public String getCode() { return "ORDER_NOT_FOUND"; }
}
