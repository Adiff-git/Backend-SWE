package com.example.demo.voucher.exception;
import com.example.demo.common.exception.ApplicationException;
import org.springframework.http.HttpStatus;
public class VoucherConflictException extends ApplicationException {
    public VoucherConflictException() { super(HttpStatus.CONFLICT, "Voucher state changed or is inconsistent."); }
    @Override public String getCode() { return "VOUCHER_CONFLICT"; }
}
