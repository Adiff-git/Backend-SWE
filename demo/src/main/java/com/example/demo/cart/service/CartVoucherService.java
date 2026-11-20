package com.example.demo.cart.service;
import com.example.demo.cart.dto.response.CartResponse;
public interface CartVoucherService { CartResponse applyVoucher(Long owner, Long grant); void removeVoucher(Long owner);
    com.example.demo.voucher.dto.response.VoucherWalletPage getWalletPage(Long owner,int page,int size);
    com.example.demo.voucher.dto.response.VoucherWalletDetail getWalletEntitlement(Long owner,Long grant); }
