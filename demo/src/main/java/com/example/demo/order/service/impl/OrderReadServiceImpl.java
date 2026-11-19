package com.example.demo.order.service.impl;

import com.example.demo.order.dto.request.OrderListRequest;
import com.example.demo.order.dto.response.OrderReadSeed;
import com.example.demo.order.exception.OrderNotFoundException;
import com.example.demo.order.repository.OrderItemRepository;
import com.example.demo.order.repository.OrderRepository;
import com.example.demo.order.service.OrderReadService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service @RequiredArgsConstructor
public class OrderReadServiceImpl implements OrderReadService {
    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final JsonMapper json = JsonMapper.builder().build();

    @Override @Transactional(readOnly = true)
    public OrderReadSeed.Page getOrderPageSeed(Long owner, OrderListRequest request) {
        long count = orders.countOwnedOrders(owner,request.getStatus());
        long offset = (long)request.getPage() * request.getSize();
        if (offset >= count) return new OrderReadSeed.Page(List.of(),List.of(),request.getPage(),request.getSize(),count);
        List<OrderReadSeed.Header> headers = offset <= Integer.MAX_VALUE
                ? orders.findOwnedHeaders(owner,request.getStatus(),request.toPageable())
                : orders.findOwnedHeadersLong(owner,request.getStatus(),offset,request.getSize(),request.getSort());
        List<OrderReadSeed.Item> previews = headers.isEmpty() ? List.of()
                : items.findPreviews(headers.stream().map(OrderReadSeed.Header::getId).toList()).stream().map(this::prepareItem).toList();
        return new OrderReadSeed.Page(headers,previews,request.getPage(),request.getSize(),count);
    }

    @Override @Transactional(readOnly = true)
    public OrderReadSeed.Detail getOrderSeed(Long owner, Long id) {
        OrderReadSeed.Header header = orders.findOwnedHeader(owner,id).orElseThrow(OrderNotFoundException::new);
        return new OrderReadSeed.Detail(header,items.findDetails(id).stream().map(this::prepareItem).toList());
    }

    private OrderReadSeed.Item prepareItem(OrderItemRepository.ItemView row) {
        Map<String, String> options = json.readValue(row.getOptionsJson(),new TypeReference<Map<String,String>>() { });
        return new OrderReadSeed.Item(row.getId(),row.getOrderId(),row.getProductId(),row.getProductVariantId(),
                row.getProductNameSnapshot(),row.getVariantSkuSnapshot(),options,row.getQuantity(),
                row.getUnitPriceSnapshot(),row.getLineTotal(),row.getRowCount());
    }
}
