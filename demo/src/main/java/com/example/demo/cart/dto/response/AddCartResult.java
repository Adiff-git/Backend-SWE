package com.example.demo.cart.dto.response;

import lombok.Value;

@Value
public class AddCartResult {
    boolean created;
    Long itemId;
    CartResponse response;
}
