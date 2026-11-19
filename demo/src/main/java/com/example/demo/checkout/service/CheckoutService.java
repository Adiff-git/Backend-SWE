package com.example.demo.checkout.service;

import com.example.demo.checkout.dto.request.CheckoutRequest;
import com.example.demo.checkout.dto.response.CheckoutReceiptResponse;

public interface CheckoutService {
    CheckoutReceiptResponse checkout(Long owner, String key, CheckoutRequest request);
}
