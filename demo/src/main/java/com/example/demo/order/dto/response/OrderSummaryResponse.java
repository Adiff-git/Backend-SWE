package com.example.demo.order.dto.response;

import com.example.demo.catalog.dto.response.SellingPrice;
import com.example.demo.order.entity.OrderStatus;
import java.time.Instant;
import java.util.List;
import lombok.Value;

@Value
public class OrderSummaryResponse {
    Long id;
    String orderNumber;
    Instant placedAt;
    OrderStatus status;
    SellingPrice payableTotal;
    long itemCount;
    List<OrderItemPreview> itemPreview;
    long remainingItemCount;
    SellingPrice voucherDiscount;
    OrderVoucherReference voucher;
}
