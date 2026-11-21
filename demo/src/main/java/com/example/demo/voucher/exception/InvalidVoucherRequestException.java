package com.example.demo.voucher.exception;
public class InvalidVoucherRequestException extends com.example.demo.common.exception.ApplicationException {
    public InvalidVoucherRequestException() {super(400,"Invalid voucher request.");}
    @Override public String getCode() {return "VOUCHER_INVALID_REQUEST";}
}
