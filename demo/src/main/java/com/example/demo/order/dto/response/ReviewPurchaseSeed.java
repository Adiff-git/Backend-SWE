package com.example.demo.order.dto.response;

import java.util.UUID;
import lombok.Value;

@Value
public class ReviewPurchaseSeed {
    Long orderId;
    Long orderItemId;
    Long customerId;
    UUID productId;
}
