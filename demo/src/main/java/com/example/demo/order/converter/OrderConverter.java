package com.example.demo.order.converter;

import com.example.demo.catalog.dto.response.SellingPrice;
import com.example.demo.order.dto.response.OrderItemPreview;
import com.example.demo.order.dto.response.OrderItemResponse;
import com.example.demo.order.dto.response.OrderDetailResponse;
import com.example.demo.order.dto.response.OrderPage;
import com.example.demo.order.dto.response.OrderReadSeed;
import com.example.demo.order.dto.response.OrderSummaryResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class OrderConverter {
    public com.example.demo.order.dto.response.OrderInventorySeed toInventorySeed(com.example.demo.order.entity.Order order,
            List<com.example.demo.order.repository.OrderItemRepository.ItemView> items) {
        return new com.example.demo.order.dto.response.OrderInventorySeed(order.isInventoryManaged(), order.getId(),
                order.getCustomerId(), order.getStatus(), order.getPlacedAt(), items.stream().map(item ->
                new com.example.demo.order.dto.response.OrderInventorySeed.Item(item.getId(), item.getProductVariantId(), item.getQuantity())).toList());
    }
    public OrderPage toPage(OrderReadSeed.Page seed, Map<UUID,String> urls) {
        Map<Long,List<OrderReadSeed.Item>> grouped = seed.getPreviews().stream().collect(Collectors.groupingBy(OrderReadSeed.Item::getOrderId));
        List<OrderSummaryResponse> summaries = seed.getHeaders().stream().map(header -> {
            List<OrderReadSeed.Item> rows = grouped.getOrDefault(header.getId(),List.of());
            long count = rows.isEmpty() ? 0 : rows.getFirst().getRowCount();
            return new OrderSummaryResponse(header.getId(),header.getOrderNumber(),header.getPlacedAt(),header.getStatus(),
                    money(header.getPayableTotal()),count,rows.stream().map(row -> new OrderItemPreview(row.getId(),
                    row.getProductNameSnapshot(),row.getVariantSkuSnapshot(),row.getVariantOptionsSnapshot(),row.getQuantity(),
                    row.getProductId() == null ? null : urls.get(row.getProductId()))).toList(),Math.max(count-3,0),money(header.getVoucherDiscount()),header.getVoucherSnapshot()==null ? null :
                    new com.example.demo.order.dto.response.OrderVoucherReference(header.getVoucherSnapshot().getVoucherId(),header.getVoucherSnapshot().getCode(),header.getVoucherSnapshot().getTitle()));
        }).toList();
        long pages = seed.getTotalItems() / seed.getSize() + (seed.getTotalItems() % seed.getSize() == 0 ? 0 : 1);
        return new OrderPage(summaries,seed.getPage(),seed.getSize(),seed.getTotalItems(),Math.toIntExact(pages));
    }

    public OrderDetailResponse toDetail(OrderReadSeed.Detail seed, Map<UUID,String> urls) {
        OrderReadSeed.Header header = seed.getHeader();
        List<OrderItemResponse> rows = seed.getItems().stream().map(row -> new OrderItemResponse(row.getId(),row.getProductId(),
                row.getProductVariantId(),row.getProductNameSnapshot(),row.getVariantSkuSnapshot(),row.getVariantOptionsSnapshot(),
                row.getQuantity(),money(row.getUnitPriceSnapshot()),money(row.getLineTotal()),
                row.getProductId() == null ? null : urls.get(row.getProductId()),
                row.getOriginalUnitPriceSnapshot()==null ? null : money(row.getOriginalUnitPriceSnapshot()),
                row.getDirectUnitDiscountSnapshot()==null ? null : money(row.getDirectUnitDiscountSnapshot()),
                row.getDirectSalePercentageSnapshot()==null ? null : new com.example.demo.order.dto.response.DirectSaleSnapshot(
                    row.getDirectSalePercentageSnapshot(),row.getDirectSaleStartsAtSnapshot(),row.getDirectSaleEndsAtSnapshot()),row.getPricingInstantSnapshot())).toList();
        return new OrderDetailResponse(header.getId(),header.getOrderNumber(),header.getPlacedAt(),header.getStatus(),
                header.getRecipientNameSnapshot(),header.getRecipientPhoneSnapshot(),header.getDeliveryAddressSnapshot(),rows,
                money(header.getMerchandiseTotal()),money(header.getPayableTotal()),header.getCancelledAt(),header.getCompletedAt(),header.getCancellationReason(),money(header.getVoucherDiscount()),header.getVoucherSnapshot());
    }

    private SellingPrice money(BigDecimal amount) { return new SellingPrice(amount.toBigIntegerExact(),"VND"); }
}
