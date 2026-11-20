package com.example.demo.voucher.dto.response;
import java.math.BigInteger;
import java.time.Instant;
import lombok.Value;
@Value
public class CheckoutVoucherQuote {
    Long ownerId; Long usageId; BigInteger base; Instant event; VoucherSnapshot snapshot;
    public BigInteger getDiscount() { return snapshot == null ? BigInteger.ZERO : snapshot.getAppliedDiscount().getAmount(); }
}
