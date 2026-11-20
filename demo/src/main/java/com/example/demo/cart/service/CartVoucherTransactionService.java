package com.example.demo.cart.service;
import com.example.demo.cart.dto.response.CartResponse;
import com.example.demo.storage.service.PreparedImageUrls;
public interface CartVoucherTransactionService { CartResponse applyVoucher(Long owner, Long grant, PreparedImageUrls urls); void removeVoucher(Long owner); }
