package com.example.demo.order.repository;

import com.example.demo.order.dto.response.OrderReadSeed;
import com.example.demo.order.entity.OrderStatus;
import com.example.demo.order.exception.InvalidOrderRequestException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class OrderPageRepositoryImpl implements OrderPageRepository {
    private final EntityManager entities;

    @Override
    public List<OrderReadSeed.Header> findOwnedHeadersLong(Long owner, OrderStatus status, long offset, int size, String sort) {
        String ordering = switch (sort) {
            case "placedAt,asc" -> "placed_at ASC";
            case "placedAt,desc" -> "placed_at DESC";
            case "payableTotal,asc" -> "payable_total ASC";
            case "payableTotal,desc" -> "payable_total DESC";
            default -> throw new InvalidOrderRequestException();
        };
        Query query = entities.createNativeQuery("SELECT id,order_number,placed_at,status,payable_total,merchandise_total,recipient_name_snapshot,recipient_phone_snapshot,delivery_address_snapshot,cancelled_at,completed_at,cancellation_reason FROM orders WHERE customer_id=:owner AND (CAST(:status AS TEXT) IS NULL OR status=CAST(:status AS TEXT)) ORDER BY " + ordering + ",id ASC LIMIT :size OFFSET :offset");
        query.setParameter("owner",owner).setParameter("status",status == null ? null : status.name())
                .setParameter("size",size).setParameter("offset",offset);
        List<OrderReadSeed.Header> result = new ArrayList<>();
        for (Object value : query.getResultList()) {
            Object[] row = (Object[]) value;
            result.add(new OrderReadSeed.Header(((Number)row[0]).longValue(),(String)row[1],instant(row[2]),
                    OrderStatus.valueOf((String)row[3]),(BigDecimal)row[4],(BigDecimal)row[5],(String)row[6],
                    (String)row[7],(String)row[8],instant(row[9]),instant(row[10]),(String)row[11]));
        }
        return List.copyOf(result);
    }

    private Instant instant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        if (value instanceof OffsetDateTime time) return time.toInstant();
        return ((java.sql.Timestamp)value).toInstant();
    }
}
