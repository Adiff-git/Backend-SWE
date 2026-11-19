package com.example.demo.user.dto.response;

import lombok.Value;

@Value
public class CheckoutAddressSnapshot {
    String recipientName;
    String recipientPhone;
    String deliveryAddress;
}
