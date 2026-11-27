package com.example.demo.order.dto.response;

import com.example.demo.catalog.dto.response.SellingPrice;
import java.util.Map;
import java.util.UUID;
import lombok.Value;

@Value
@lombok.AllArgsConstructor
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
    SellingPrice originalUnitPriceSnapshot;
    SellingPrice directUnitDiscountSnapshot;
    com.example.demo.order.dto.response.DirectSaleSnapshot directSaleSnapshot;
    java.time.Instant pricingInstantSnapshot;
    public OrderItemResponse(Long id, UUID productId, UUID variantId, String name, String sku, Map<String,String> options,
            int quantity, SellingPrice price, SellingPrice line, String image) {
        this(id,productId,variantId,name,sku,options,quantity,price,line,image,null,null,null,null);
    }
}
