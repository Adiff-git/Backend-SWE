package com.example.demo.cart.service;
import java.math.BigInteger;
public interface CartWalletReadService { BigInteger getEligibleSubtotal(Long owner); com.example.demo.cart.dto.response.CartWalletContext getPricingContext(Long owner); }
