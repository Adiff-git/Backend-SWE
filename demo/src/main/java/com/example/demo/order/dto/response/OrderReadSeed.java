package com.example.demo.order.dto.response;

import com.example.demo.order.entity.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Value;

/** Fully materialized values, never persistence proxies. */
public final class OrderReadSeed {
    private OrderReadSeed() { }

    @Value @lombok.AllArgsConstructor
    public static class Header {
        Long id;
        String orderNumber;
        Instant placedAt;
        OrderStatus status;
        BigDecimal payableTotal;
        BigDecimal merchandiseTotal;
        String recipientNameSnapshot;
        String recipientPhoneSnapshot;
        String deliveryAddressSnapshot;
        Instant cancelledAt;
        Instant completedAt;
        String cancellationReason;
        BigDecimal voucherDiscount;
        com.example.demo.voucher.dto.response.VoucherSnapshot voucherSnapshot;
        String voucherSnapshotJson;
        public Header(Long id,String number,Instant placed,OrderStatus status,BigDecimal payable,BigDecimal merchandise,
                String name,String phone,String address,Instant cancelled,Instant completed,String reason,BigDecimal discount,
                com.example.demo.voucher.dto.response.VoucherSnapshot snapshot) {
            this(id,number,placed,status,payable,merchandise,name,phone,address,cancelled,completed,reason,discount,snapshot,null);
        }
        public Header(Long id,String number,Instant placed,OrderStatus status,BigDecimal payable,BigDecimal merchandise,
                String name,String phone,String address,Instant cancelled,Instant completed,String reason,BigDecimal discount,String json) {
            this(id,number,placed,status,payable,merchandise,name,phone,address,cancelled,completed,reason,discount,null,json);
        }
        public Header(Long id,String number,Instant placed,OrderStatus status,BigDecimal payable,BigDecimal merchandise,
                String name,String phone,String address,Instant cancelled,Instant completed,String reason) {
            this(id,number,placed,status,payable,merchandise,name,phone,address,cancelled,completed,reason,BigDecimal.ZERO,(com.example.demo.voucher.dto.response.VoucherSnapshot)null);
        }
    }

    @Value
    public static class Item {
        Long id;
        Long orderId;
        UUID productId;
        UUID productVariantId;
        String productNameSnapshot;
        String variantSkuSnapshot;
        Map<String, String> variantOptionsSnapshot;
        int quantity;
        BigDecimal unitPriceSnapshot;
        BigDecimal lineTotal;
        long rowCount;
        BigDecimal originalUnitPriceSnapshot;
        BigDecimal directUnitDiscountSnapshot;
        BigDecimal directSalePercentageSnapshot;
        Instant directSaleStartsAtSnapshot;
        Instant directSaleEndsAtSnapshot;
        Instant pricingInstantSnapshot;


        public Item(Long id, Long orderId, UUID productId, UUID productVariantId, String name, String sku,
                Map<String, String> options, int quantity, BigDecimal price, BigDecimal lineTotal, long rowCount) {
            this(id,orderId,productId,productVariantId,name,sku,options,quantity,price,lineTotal,rowCount,null,null,null,null,null,null);
        }
        public Item(Long id, Long orderId, UUID productId, UUID productVariantId, String name, String sku,
                Map<String,String> options, int quantity, BigDecimal price, BigDecimal lineTotal, long rowCount,
                BigDecimal originalUnitPriceSnapshot, BigDecimal directUnitDiscountSnapshot, BigDecimal directSalePercentageSnapshot, Instant directSaleStartsAtSnapshot, Instant directSaleEndsAtSnapshot, Instant pricingInstantSnapshot) {
            this.originalUnitPriceSnapshot=originalUnitPriceSnapshot;
            this.directUnitDiscountSnapshot=directUnitDiscountSnapshot;
            this.directSalePercentageSnapshot=directSalePercentageSnapshot;
            this.directSaleStartsAtSnapshot=directSaleStartsAtSnapshot;
            this.directSaleEndsAtSnapshot=directSaleEndsAtSnapshot;
            this.pricingInstantSnapshot=pricingInstantSnapshot;
            this.id = id; this.orderId = orderId; this.productId = productId; this.productVariantId = productVariantId;
            this.productNameSnapshot = name; this.variantSkuSnapshot = sku; this.variantOptionsSnapshot = Map.copyOf(options);
            this.quantity = quantity; this.unitPriceSnapshot = price; this.lineTotal = lineTotal; this.rowCount = rowCount;
        }
    }

    @Value
    public static class Page {
        List<Header> headers;
        List<Item> previews;
        int page;
        int size;
        long totalItems;
        public Page(List<Header> headers, List<Item> previews, int page, int size, long totalItems) {
            this.headers = List.copyOf(headers); this.previews = List.copyOf(previews);
            this.page = page; this.size = size; this.totalItems = totalItems;
        }
    }

    @Value
    public static class Detail {
        Header header;
        List<Item> items;
        public Detail(Header header, List<Item> items) { this.header = header; this.items = List.copyOf(items); }
    }
}
