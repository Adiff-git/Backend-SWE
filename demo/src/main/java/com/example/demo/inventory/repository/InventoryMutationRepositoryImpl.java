package com.example.demo.inventory.repository;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class InventoryMutationRepositoryImpl implements InventoryMutationRepository {
    private final EntityManager entities;

    @Override public int reserveUnits(long balanceId, int quantity) {
        return entities.createNativeQuery("UPDATE inventory_balances SET reserved=reserved+:q WHERE id=:id AND :q BETWEEN 1 AND 999 AND stock-reserved>=:q")
                .setParameter("q", quantity).setParameter("id", balanceId).executeUpdate();
    }

    @Override public int releaseUnits(long balanceId, int quantity) {
        return entities.createNativeQuery("UPDATE inventory_balances SET reserved=reserved-:q WHERE id=:id AND :q BETWEEN 1 AND 999 AND reserved>=:q")
                .setParameter("q", quantity).setParameter("id", balanceId).executeUpdate();
    }

    @Override public int consumeUnits(long balanceId, int quantity) {
        return entities.createNativeQuery("UPDATE inventory_balances SET stock=stock-:q,reserved=reserved-:q WHERE id=:id AND :q BETWEEN 1 AND 999 AND stock>=:q AND reserved>=:q")
                .setParameter("q", quantity).setParameter("id", balanceId).executeUpdate();
    }

    @Override public int insertReservation(long orderId, long itemId, long balanceId, UUID variantId, int quantity, Instant reservedAt) {
        return entities.createNativeQuery("INSERT INTO inventory_reservations(order_id,order_item_id,inventory_balance_id,original_variant_id,quantity,reserved_at) VALUES (:orderId,:itemId,:balanceId,:variantId,:q,:event)")
                .setParameter("orderId", orderId).setParameter("itemId", itemId).setParameter("balanceId", balanceId)
                .setParameter("variantId", variantId).setParameter("q", quantity).setParameter("event", reservedAt).executeUpdate();
    }

    @Override public int resolveReservation(long reservationId, String state, Instant resolvedAt) {
        return entities.createNativeQuery("UPDATE inventory_reservations SET state=:state,resolved_at=:event WHERE id=:id AND state='ACTIVE' AND :state IN ('CONSUMED','RELEASED') AND reserved_at<=:event")
                .setParameter("state", state).setParameter("event", resolvedAt).setParameter("id", reservationId).executeUpdate();
    }
}
