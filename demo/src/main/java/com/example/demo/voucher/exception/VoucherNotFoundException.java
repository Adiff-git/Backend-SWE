package com.example.demo.voucher.exception;
import com.example.demo.common.exception.ApplicationException;
import org.springframework.http.HttpStatus;
public class VoucherNotFoundException extends ApplicationException {
    public VoucherNotFoundException() { super(HttpStatus.NOT_FOUND, "Voucher entitlement not found."); }
    @Override public String getCode() { return "VOUCHER_NOT_FOUND"; }
}
