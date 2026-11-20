package com.example.demo.order.dto.response;

import com.example.demo.catalog.dto.response.SellingPrice;
import com.example.demo.order.entity.OrderStatus;
import java.time.Instant;
import java.util.List;
import lombok.Value;

@Value
public class OrderDetailResponse {
    Long id;
    String orderNumber;
    Instant placedAt;
    OrderStatus status;
    String recipientNameSnapshot;
    String recipientPhoneSnapshot;
    String deliveryAddressSnapshot;
    List<OrderItemResponse> items;
    SellingPrice merchandiseTotal;
    SellingPrice payableTotal;
    Instant cancelledAt;
    Instant completedAt;
    String cancellationReason;
    SellingPrice voucherDiscount;
    com.example.demo.voucher.dto.response.VoucherSnapshot voucher;
}
