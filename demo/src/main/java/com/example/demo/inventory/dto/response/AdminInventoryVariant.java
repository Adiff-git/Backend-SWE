package com.example.demo.inventory.dto.response;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonInclude(JsonInclude.Include.ALWAYS)
public class AdminInventoryVariant {
    public enum StockStatus { OUT_OF_STOCK, IN_STOCK, UNAVAILABLE }

    UUID variantId;
    UUID productId;
    String productName;
    String sku;
    String productStatus;
    boolean variantEnabled;
    boolean catalogSaleEligible;
    String onHand;
    String reserved;
    String available;
    StockStatus stockStatus;
}
