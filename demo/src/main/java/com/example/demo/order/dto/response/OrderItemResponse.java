package com.example.demo.order.dto.response;

import com.example.demo.catalog.dto.response.SellingPrice;
import java.util.Map;
import java.util.UUID;
import lombok.Value;

@Value
public class OrderItemResponse {
    Long id;
    UUID productId;
    UUID productVariantId;
    String productNameSnapshot;
    String variantSkuSnapshot;
    Map<String,String> variantOptionsSnapshot;
    int quantity;
    SellingPrice unitPriceSnapshot;
    SellingPrice lineTotal;
    String primaryImageUrl;
}
