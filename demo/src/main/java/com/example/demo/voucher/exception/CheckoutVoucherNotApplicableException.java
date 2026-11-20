package com.example.demo.voucher.exception;
import com.example.demo.common.exception.ApplicationException;
import java.util.Map;
import org.springframework.http.HttpStatus;
public class CheckoutVoucherNotApplicableException extends ApplicationException {
    private final String reason;
    public CheckoutVoucherNotApplicableException(String reason) { super(HttpStatus.CONFLICT, "The selected voucher is no longer applicable."); this.reason=reason; }
    @Override public String getCode() { return "VOUCHER_NOT_APPLICABLE"; }
    @Override public Map<String,Object> getProperties() { return Map.of("reason", reason); }
}
