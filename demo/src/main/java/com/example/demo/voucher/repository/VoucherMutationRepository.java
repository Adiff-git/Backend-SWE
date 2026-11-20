package com.example.demo.voucher.repository;
import java.time.Instant;
public interface VoucherMutationRepository {
    int incrementGlobal(long voucher);
    int incrementCustomer(long usage, long voucher, long owner);
    int consumeEntitlement(long grant, long voucher, long owner, Instant event);
    int insertRedemption(com.example.demo.voucher.dto.response.CheckoutVoucherQuote quote, long orderId);
}
