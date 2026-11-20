package com.example.demo.fixture.inventory;

import java.util.UUID;
import java.time.Instant;
import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;

/** Explicit test-only writes; callers own transactions and final integrity checks. */
public final class InventoryFixtures {
    private InventoryFixtures() { }

    public static void supplyStock(jakarta.persistence.EntityManager entities, UUID variantId, long stock) {
        if (stock < 0) throw new IllegalArgumentException("Test stock must be nonnegative");
        int changed = entities.createNativeQuery("UPDATE inventory_balances SET stock=:stock WHERE original_variant_id=:variant")
                .setParameter("stock", stock).setParameter("variant", variantId).executeUpdate();
        if (changed != 1) throw new IllegalStateException("Expected one provisioned inventory balance");
    }

    public static long order(JdbcTemplate jdbc, long ownerId, boolean managed, UUID variantId, int quantity) {
        Long orderId = jdbc.queryForObject("""
                INSERT INTO orders(customer_id,order_number,placed_at,status,recipient_name_snapshot,
                  recipient_phone_snapshot,delivery_address_snapshot,merchandise_total,payable_total,inventory_managed)
                VALUES (?,?,?,'PENDING','Inventory recipient','0123456789','Inventory address',?,?,?) RETURNING id
                """, Long.class, ownerId, "INVENTORY-" + UUID.randomUUID(),
                Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")), quantity * 100L, quantity * 100L, managed);
        jdbc.update("""
                INSERT INTO order_items(order_id,product_variant_id,product_name_snapshot,variant_sku_snapshot,
                  variant_options_snapshot,quantity,unit_price_snapshot,line_total)
                VALUES (?,?,'Inventory product','INVENTORY-SKU','{}',?,100,?)
                """, orderId, variantId, quantity, quantity * 100L);
        return orderId;
    }

    public static long activeReservation(JdbcTemplate jdbc, long orderId, long itemId,
                                        long balanceId, UUID originalVariantId, int quantity) {
        return jdbc.queryForObject("""
                INSERT INTO inventory_reservations(order_id,order_item_id,inventory_balance_id,
                  original_variant_id,quantity,state,reserved_at)
                VALUES (?,?,?,?,?,'ACTIVE',?) RETURNING id
                """, Long.class, orderId, itemId, balanceId, originalVariantId, quantity,
                Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")));
    }

    public static void supplyStock(JdbcTemplate jdbc, UUID variantId, long stock) {
        if (stock < 0) throw new IllegalArgumentException("Test stock must be nonnegative");
        int changed = jdbc.update("UPDATE inventory_balances SET stock=? WHERE original_variant_id=?", stock, variantId);
        if (changed != 1) throw new IllegalStateException("Expected one provisioned inventory balance");
    }

    public static void removeUnusedBalance(JdbcTemplate jdbc, UUID variantId) {
        int changed = jdbc.update("DELETE FROM inventory_balances WHERE original_variant_id=? AND stock=0 AND reserved=0", variantId);
        if (changed != 1) throw new IllegalStateException("Expected one unused zero balance");
    }

    public static void cleanRetainedInventory(JdbcTemplate jdbc) {
        jdbc.execute("TRUNCATE TABLE inventory_reservations,inventory_balances,users,categories CASCADE");
    }
}
