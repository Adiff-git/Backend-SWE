package com.example.demo.voucher.exception;
import com.example.demo.common.exception.ApplicationException;
import java.util.Map;
import org.springframework.http.HttpStatus;
public class VoucherNotApplicableException extends ApplicationException {
    private final String reason;
    public VoucherNotApplicableException(String reason) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "The selected voucher is not applicable.");
        this.reason = reason;
    }
    @Override public String getCode() { return "VOUCHER_NOT_APPLICABLE"; }
    @Override public Map<String,Object> getProperties() { return Map.of("reason", reason); }
}
