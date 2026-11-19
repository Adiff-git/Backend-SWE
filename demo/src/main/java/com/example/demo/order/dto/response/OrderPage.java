package com.example.demo.order.dto.response;

import java.util.List;
import lombok.Value;

@Value
public class OrderPage {
    List<OrderSummaryResponse> items;
    int page;
    int size;
    long totalItems;
    int totalPages;
}
