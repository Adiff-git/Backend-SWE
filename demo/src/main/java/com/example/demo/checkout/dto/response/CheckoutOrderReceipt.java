package com.example.demo.checkout.dto.response;

import com.example.demo.catalog.dto.response.SellingPrice;
import com.example.demo.order.entity.OrderStatus;
import java.time.Instant;
import java.util.List;
import lombok.Value;

@Value
public class CheckoutOrderReceipt {
    Long id;
    String orderNumber;
    Instant placedAt;
    OrderStatus status;
    String recipientNameSnapshot;
    String recipientPhoneSnapshot;
    String deliveryAddressSnapshot;
    List<CheckoutItemReceipt> items;
    SellingPrice merchandiseTotal;
    SellingPrice payableTotal;

    public CheckoutOrderReceipt(Long id, String orderNumber, Instant placedAt, OrderStatus status,
            String name, String phone, String address, List<CheckoutItemReceipt> items,
            SellingPrice merchandiseTotal, SellingPrice payableTotal) {
        this.id = id;
        this.orderNumber = orderNumber;
        this.placedAt = placedAt;
        this.status = status;
        this.recipientNameSnapshot = name;
        this.recipientPhoneSnapshot = phone;
        this.deliveryAddressSnapshot = address;
        this.items = List.copyOf(items);
        this.merchandiseTotal = merchandiseTotal;
        this.payableTotal = payableTotal;
    }
}
