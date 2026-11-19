package com.example.demo.order.validator;

import com.example.demo.common.identity.IdentityUnavailableException;
import com.example.demo.order.dto.request.CancelOrderRequest;
import com.example.demo.order.entity.Order;
import com.example.demo.order.exception.InvalidOrderRequestException;
import com.example.demo.order.exception.OrderConflictException;
import com.example.demo.order.exception.OrderNotFoundException;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class OrderValidator {
    public void validateRequest(Long owner, Long id, CancelOrderRequest request) {
        if (owner == null) throw new IdentityUnavailableException();
        if (id == null || id < 1 || request == null || !request.isReasonValid()) throw new InvalidOrderRequestException();
    }

    /** True means first transition; false is an idempotent repeat with no Clock sampling. */
    public boolean canCancel(Order order, Long owner) {
        if (order == null || !owner.equals(order.getCustomerId())) throw new OrderNotFoundException();
        return switch (order.getStatus()) {
            case PENDING, CONFIRMED -> true;
            case CANCELLED -> false;
            case SHIPPING, COMPLETED -> throw new OrderConflictException();
        };
    }

    public void validateTime(Order order, Instant at) {
        if (at.isBefore(order.getPlacedAt())) throw new OrderConflictException();
    }
}
