package com.example.demo.checkout.dto.response;

import com.example.demo.catalog.dto.response.SellingPrice;
import java.util.Map;
import lombok.Value;

@Value
@lombok.experimental.NonFinal
public class CheckoutItemReceipt {
    Long id;
    String productNameSnapshot;
    String variantSkuSnapshot;
    Map<String, String> variantOptionsSnapshot;
    int quantity;
    SellingPrice unitPriceSnapshot;
    SellingPrice lineTotal;

    public CheckoutItemReceipt(Long id, String name, String sku, Map<String, String> options, int quantity,
            SellingPrice price, SellingPrice line) {
        this.id = id;
        this.productNameSnapshot = name;
        this.variantSkuSnapshot = sku;
        this.variantOptionsSnapshot = Map.copyOf(options);
        this.quantity = quantity;
        this.unitPriceSnapshot = price;
        this.lineTotal = line;
    }
}
