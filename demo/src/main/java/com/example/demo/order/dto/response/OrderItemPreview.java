package com.example.demo.order.dto.response;

import java.util.Map;
import lombok.Value;

@Value
public class OrderItemPreview {
    Long id;
    String productNameSnapshot;
    String variantSkuSnapshot;
    Map<String, String> variantOptionsSnapshot;
    int quantity;
    String primaryImageUrl;
}
