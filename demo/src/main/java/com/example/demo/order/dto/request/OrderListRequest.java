package com.example.demo.order.dto.request;

import com.example.demo.order.entity.OrderStatus;
import com.example.demo.order.exception.InvalidOrderRequestException;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Set;
import lombok.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@Value
public class OrderListRequest {
    public static final Set<String> SORTS = Set.of("placedAt,asc","placedAt,desc","payableTotal,asc","payableTotal,desc");
    @Min(0) int page;
    @Min(1) @Max(100) int size;
    OrderStatus status;
    String sort;

    public OrderListRequest(int page, int size, OrderStatus status, String sort) {
        if (page < 0 || size < 1 || size > 100 || !SORTS.contains(sort)) throw new InvalidOrderRequestException();
        this.page = page; this.size = size; this.status = status; this.sort = sort;
    }

    public Pageable toPageable() {
        String[] parts = sort.split(",");
        return PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(parts[1]),parts[0]).and(Sort.by("id")));
    }
}
