package com.example.demo.order.exception;

import com.example.demo.common.exception.ApplicationException;

public final class InvalidOrderRequestException extends ApplicationException {
    public InvalidOrderRequestException() { super(400, "Invalid order request"); }
    @Override public String getCode() { return "INVALID_ORDER_REQUEST"; }
}
