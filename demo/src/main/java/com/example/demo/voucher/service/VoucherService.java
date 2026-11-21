package com.example.demo.voucher.service;
import com.example.demo.voucher.dto.response.VoucherPreview;
import java.math.BigInteger;
import java.time.Instant;
public interface VoucherService { VoucherPreview getPreview(Long owner, Long grant, BigInteger base, Instant event);
    com.example.demo.voucher.dto.response.CheckoutVoucherQuote lockCheckoutVoucher(Long owner, Long grant, BigInteger base);
    void redeemVoucher(com.example.demo.voucher.dto.response.CheckoutVoucherQuote quote, Long orderId);
    com.example.demo.voucher.dto.response.VoucherWalletPage getWalletPage(Long owner,int page,int size,BigInteger base);
    com.example.demo.voucher.dto.response.VoucherWalletDetail getWalletEntitlement(Long owner,Long grant,BigInteger base);
}
