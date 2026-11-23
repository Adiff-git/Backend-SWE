-- ADDITIVE V27 READ-ONLY preflight supplement for Analyze finding M02.
-- Contract addition only; not a migration; NOT EXECUTED during remediation.
-- Run alongside data-preflight.sql on the same identified immutable snapshot.
-- Every violation result set below must be empty; keep identity-only output private.
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
SET LOCAL statement_timeout = '60s';
SET LOCAL lock_timeout = '2s';

SELECT CURRENT_DATABASE() AS database_name, CURRENT_TIMESTAMP AS checked_at,
       CURRENT_SETTING('transaction_read_only') AS transaction_read_only;

-- Mirror validate_inventory_order: managed orders need items and exact allocations.
WITH expected_orders AS (
    SELECT id, status, placed_at, cancelled_at, completed_at, inventory_managed,
           CASE status WHEN 'CANCELLED' THEN 'RELEASED'
                       WHEN 'COMPLETED' THEN 'CONSUMED' ELSE 'ACTIVE' END AS expected_state,
           CASE status WHEN 'CANCELLED' THEN cancelled_at
                       WHEN 'COMPLETED' THEN completed_at ELSE NULL END AS expected_time
    FROM orders
), violations AS (
    SELECT 'MANAGED_ORDER_WITHOUT_ITEMS'::TEXT AS reason, o.id AS order_id,
           NULL::BIGINT AS order_item_id, NULL::BIGINT AS reservation_id
    FROM expected_orders o
    WHERE o.inventory_managed AND NOT EXISTS (
        SELECT 1 FROM order_items i WHERE i.order_id = o.id
    )
    UNION ALL
    SELECT 'MANAGED_ITEM_WITHOUT_ALLOCATION', o.id, i.id, NULL::BIGINT
    FROM expected_orders o JOIN order_items i ON i.order_id = o.id
    WHERE o.inventory_managed AND NOT EXISTS (
        SELECT 1 FROM inventory_reservations r WHERE r.order_item_id = i.id
    )
    UNION ALL
    SELECT 'LEGACY_ORDER_HAS_ALLOCATION', o.id, r.order_item_id, r.id
    FROM expected_orders o JOIN inventory_reservations r ON r.order_id = o.id
    WHERE NOT o.inventory_managed
    UNION ALL
    SELECT 'ALLOCATION_ORDER_OR_ITEM_MISMATCH', r.order_id, r.order_item_id, r.id
    FROM inventory_reservations r
    LEFT JOIN expected_orders o ON o.id = r.order_id
    LEFT JOIN order_items i ON i.id = r.order_item_id
    WHERE o.id IS NULL OR i.id IS NULL OR i.order_id IS DISTINCT FROM r.order_id
    UNION ALL
    SELECT 'ALLOCATION_QUANTITY_MISMATCH', r.order_id, r.order_item_id, r.id
    FROM inventory_reservations r JOIN order_items i ON i.id = r.order_item_id
    WHERE r.quantity IS DISTINCT FROM i.quantity OR r.quantity NOT BETWEEN 1 AND 999
    UNION ALL
    SELECT 'ALLOCATION_LIFECYCLE_MISMATCH', r.order_id, r.order_item_id, r.id
    FROM inventory_reservations r JOIN expected_orders o ON o.id = r.order_id
    WHERE r.state IS DISTINCT FROM o.expected_state
       OR r.reserved_at IS DISTINCT FROM o.placed_at
       OR r.resolved_at IS DISTINCT FROM o.expected_time
       OR r.state NOT IN ('ACTIVE', 'CONSUMED', 'RELEASED')
       OR (r.state = 'ACTIVE') IS DISTINCT FROM (r.resolved_at IS NULL)
       OR r.resolved_at < r.reserved_at
       OR (o.status IN ('CANCELLED', 'COMPLETED') AND o.expected_time IS NULL)
    UNION ALL
    SELECT 'ALLOCATION_BALANCE_ATTRIBUTION_MISMATCH', r.order_id, r.order_item_id, r.id
    FROM inventory_reservations r
    LEFT JOIN inventory_balances b ON b.id = r.inventory_balance_id
    WHERE b.id IS NULL OR b.original_variant_id IS DISTINCT FROM r.original_variant_id
    UNION ALL
    SELECT 'ALLOCATION_ITEM_VARIANT_MISMATCH', r.order_id, r.order_item_id, r.id
    FROM inventory_reservations r JOIN order_items i ON i.id = r.order_item_id
    WHERE i.product_variant_id IS NOT NULL
      AND i.product_variant_id IS DISTINCT FROM r.original_variant_id
    UNION ALL
    SELECT 'ACTIVE_ALLOCATION_WITHOUT_LIVE_VARIANT', r.order_id, r.order_item_id, r.id
    FROM inventory_reservations r
    LEFT JOIN order_items i ON i.id = r.order_item_id AND i.order_id = r.order_id
    LEFT JOIN inventory_balances b ON b.id = r.inventory_balance_id
    LEFT JOIN product_variants v ON v.id = r.original_variant_id
    WHERE r.state = 'ACTIVE' AND (
        i.product_variant_id IS NULL OR v.id IS NULL
        OR b.product_variant_id IS DISTINCT FROM r.original_variant_id
    )
)
SELECT reason, order_id, order_item_id, reservation_id FROM violations
ORDER BY reason, order_id, order_item_id, reservation_id;

-- All-row cardinality: duplicate attribution must never disappear in aggregation.
SELECT 'DUPLICATE_ITEM_ALLOCATION'::TEXT AS reason, order_item_id,
       ARRAY_AGG(id ORDER BY id) AS reservation_ids
FROM inventory_reservations GROUP BY order_item_id HAVING COUNT(*) <> 1;
SELECT 'DUPLICATE_ORDER_VARIANT_ALLOCATION'::TEXT AS reason, order_id, original_variant_id,
       ARRAY_AGG(id ORDER BY id) AS reservation_ids
FROM inventory_reservations GROUP BY order_id, original_variant_id HAVING COUNT(*) <> 1;

-- Preserve retained terminal balances; only nonnull live references must match.
WITH active_units AS (
    SELECT inventory_balance_id, SUM(quantity::NUMERIC) AS units
    FROM inventory_reservations WHERE state = 'ACTIVE' GROUP BY inventory_balance_id
)
SELECT b.id AS balance_id, b.original_variant_id, b.product_variant_id
FROM inventory_balances b LEFT JOIN active_units a ON a.inventory_balance_id = b.id
LEFT JOIN product_variants v ON v.id = b.product_variant_id
WHERE b.stock < 0 OR b.reserved < 0 OR b.reserved > b.stock
   OR b.reserved::NUMERIC IS DISTINCT FROM COALESCE(a.units, 0)
   OR (b.product_variant_id IS NOT NULL AND (
       b.product_variant_id IS DISTINCT FROM b.original_variant_id OR v.id IS NULL
   ));
SELECT original_variant_id, ARRAY_AGG(id ORDER BY id) AS balance_ids
FROM inventory_balances GROUP BY original_variant_id HAVING COUNT(*) <> 1;
SELECT product_variant_id, ARRAY_AGG(id ORDER BY id) AS balance_ids
FROM inventory_balances WHERE product_variant_id IS NOT NULL
GROUP BY product_variant_id HAVING COUNT(*) <> 1;
ROLLBACK;
