package com.example.demo.order.dto.request;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Value;

@Value
public class CreateOrderCommand {
    Long customerId;
    Instant placedAt;
    String recipientName;
    String recipientPhone;
    String deliveryAddress;
    List<Item> items;
    BigInteger merchandiseTotal;
    BigInteger voucherDiscount;
    BigInteger payableTotal;
    com.example.demo.voucher.dto.response.VoucherSnapshot voucherSnapshot;

    public CreateOrderCommand(Long customerId, Instant placedAt, String name, String phone, String address,
            List<Item> items, BigInteger total) {
        this(customerId, placedAt, name, phone, address, items, total, BigInteger.ZERO, total, null);
    }
    public CreateOrderCommand(Long customerId, Instant placedAt, String name, String phone, String address,
            List<Item> items, BigInteger total, BigInteger discount, BigInteger payable,
            com.example.demo.voucher.dto.response.VoucherSnapshot snapshot) {
        this.voucherDiscount=discount;
        this.payableTotal=payable;
        this.voucherSnapshot=snapshot;
        this.customerId = customerId;
        this.placedAt = placedAt;
        this.recipientName = name;
        this.recipientPhone = phone;
        this.deliveryAddress = address;
        this.items = List.copyOf(items);
        this.merchandiseTotal = total;
    }

    @Value
    public static class Item {
        Long cartItemId;
        UUID productId;
        UUID variantId;
        String productName;
        String sku;
        Map<String, String> options;
        int quantity;
        BigInteger unitPrice;
        BigInteger lineTotal;

        public Item(Long cartItemId, UUID productId, UUID variantId, String productName, String sku,
                Map<String, String> options, int quantity, BigInteger unitPrice, BigInteger lineTotal) {
            this.cartItemId = cartItemId;
            this.productId = productId;
            this.variantId = variantId;
            this.productName = productName;
            this.sku = sku;
            this.options = Map.copyOf(options);
            this.quantity = quantity;
            this.unitPrice = unitPrice;
            this.lineTotal = lineTotal;
        }
    }
}
