-- Fresh-import verification only. Completed normal startup must not re-enforce these counts.
WITH review_stats AS (
 SELECT p.id,COUNT(r.id) n,ROUND(AVG(r.rating),2) average FROM products p
 LEFT JOIN product_reviews r ON r.product_id=p.id GROUP BY p.id
), counts AS (
 SELECT CASE WHEN n=0 THEN 0 WHEN n<=10 THEN 1 WHEN n<=50 THEN 2 WHEN n<=100 THEN 3 ELSE 4 END band,
 COUNT(*) n FROM review_stats GROUP BY 1
), ratings AS (
 SELECT CASE WHEN average<=2 THEN 0 WHEN average<=3 THEN 1 WHEN average<=4 THEN 2
 WHEN average<=4.5 THEN 3 ELSE 4 END band,COUNT(*) n FROM review_stats WHERE n>0 GROUP BY 1
)
SELECT jsonb_build_object(
 'review_count_groups',(SELECT jsonb_agg(COALESCE(c.n,0) ORDER BY b.band) FROM generate_series(0,4) b(band) LEFT JOIN counts c USING(band)),
 'average_rating_groups',(SELECT jsonb_agg(COALESCE(r.n,0) ORDER BY b.band) FROM generate_series(0,4) b(band) LEFT JOIN ratings r USING(band)),
 'review_ownership_errors',(SELECT COUNT(*) FROM product_reviews r JOIN order_items i ON i.id=r.order_item_id
   JOIN orders o ON o.id=i.order_id JOIN users u ON u.id=r.author_id WHERE o.status<>'COMPLETED'
   OR o.customer_id<>r.author_id OR i.product_id<>r.product_id OR u.account_status<>'ACTIVE'
   OR r.created_at<o.completed_at OR r.updated_at<r.created_at),
 'address_errors',(SELECT COUNT(*) FROM (SELECT u.id,COUNT(a.id) FILTER(WHERE a.is_default) n FROM users u
   LEFT JOIN addresses a ON a.user_id=u.id GROUP BY u.id) a WHERE a.n<>1),
 'order_temporal_errors',(SELECT COUNT(*) FROM orders o JOIN users u ON u.id=o.customer_id WHERE o.placed_at<u.joined_at),
 'order_size_errors',(SELECT COUNT(*) FROM (SELECT o.id,COUNT(i.id) n,COUNT(DISTINCT i.product_variant_id) d
   FROM orders o LEFT JOIN order_items i ON i.order_id=o.id GROUP BY o.id) o WHERE n NOT BETWEEN 1 AND 4 OR n<>d),
 'price_errors',(SELECT COUNT(*) FROM order_items i JOIN orders o ON o.id=i.order_id WHERE i.pricing_instant_snapshot IS DISTINCT FROM o.placed_at
   OR i.original_unit_price_snapshot IS NULL OR i.line_total<>i.unit_price_snapshot*i.quantity
   OR NOT order_sale_snapshot_is_valid(i.original_unit_price_snapshot,i.unit_price_snapshot,i.direct_unit_discount_snapshot,
      i.direct_sale_percentage_snapshot,i.direct_sale_starts_at_snapshot,i.direct_sale_ends_at_snapshot,i.pricing_instant_snapshot)),
 'inventory_errors',(SELECT COUNT(*) FROM inventory_balances b WHERE b.reserved<>
   (SELECT COALESCE(SUM(r.quantity),0) FROM inventory_reservations r WHERE r.inventory_balance_id=b.id AND r.state='ACTIVE')),
 'dimension_errors',(SELECT COUNT(*) FROM (SELECT product_id,COUNT(DISTINCT keys) n FROM (
   SELECT product_id,(SELECT jsonb_agg(k ORDER BY k) FROM jsonb_object_keys(option_combination) k) keys FROM product_variants) v GROUP BY product_id) q WHERE n>1),
 'review_count_bound_errors',(SELECT COUNT(*) FROM review_stats WHERE n>1000),
 'original_variant_errors',(SELECT COUNT(*) FROM bootstrap_rows s LEFT JOIN product_variants v ON v.id=(s.body->>'id')::uuid
   WHERE s.kind='original_variants' AND (v.id IS NULL OR v.sku<>s.body->>'sku' OR v.base_price<>(s.body->>'base_price')::numeric)),
 'available_stock_errors',(SELECT COUNT(*) FROM bootstrap_rows s LEFT JOIN inventory_balances b ON b.original_variant_id=(s.body->>'variant_id')::uuid
   WHERE s.kind='stock' AND (b.id IS NULL OR b.stock-b.reserved<>(s.body->>'available')::bigint)),
 'purchase_coverage_errors',(SELECT COUNT(*) FROM bootstrap_rows s WHERE s.kind='coverage' AND
   ((SELECT COUNT(*) FROM order_items i JOIN orders o ON o.id=i.order_id WHERE i.product_id=(s.body->>'product_id')::uuid AND o.status='COMPLETED')<>(s.body->>'completed_items')::bigint
    OR (SELECT COUNT(*) FROM product_reviews r WHERE r.product_id=(s.body->>'product_id')::uuid)<>(s.body->>'review_count')::bigint
    OR (SELECT COALESCE(SUM(r.rating),0) FROM product_reviews r WHERE r.product_id=(s.body->>'product_id')::uuid)<>(s.body->>'expected_rating_sum')::bigint
    OR (s.body->>'tier'='never-purchased' AND EXISTS(SELECT 1 FROM order_items i WHERE i.product_id=(s.body->>'product_id')::uuid)))),
 'cart_errors',(SELECT COUNT(*) FROM (SELECT c.id,COUNT(i.id) n,COUNT(i.id) FILTER(WHERE i.selected) selected,
   COUNT(i.id) FILTER(WHERE NOT i.selected) unselected FROM carts c LEFT JOIN cart_items i ON i.cart_id=c.id GROUP BY c.id) c
   WHERE n NOT BETWEEN 2 AND 6 OR selected=0 OR unselected=0),
 'credential_errors',ABS((SELECT COUNT(*) FROM users WHERE password IS NOT NULL)-12),
 'voucher_errors',ABS((SELECT COUNT(*) FROM voucher_entitlements WHERE state='AVAILABLE')-96)
   +ABS(COALESCE((SELECT redeemed_count FROM vouchers WHERE code='DEMO_GLOBAL'),0)-20)
   +ABS(COALESCE((SELECT u.redeemed_count FROM voucher_customer_usages u JOIN vouchers v ON v.id=u.voucher_id
                 WHERE v.code='DEMO_CUSTOMER' AND u.customer_id=1),0)-1),
 'sale_groups',(SELECT jsonb_build_array(COUNT(*) FILTER(WHERE p.sale_percentage IS NULL),
   COUNT(*) FILTER(WHERE p.sale_enabled AND p.sale_starts_at<=a.anchor_at AND p.sale_ends_at>a.anchor_at),
   COUNT(*) FILTER(WHERE p.sale_enabled AND p.sale_starts_at>a.anchor_at),
   COUNT(*) FILTER(WHERE p.sale_enabled AND p.sale_ends_at<=a.anchor_at),
   COUNT(*) FILTER(WHERE NOT p.sale_enabled AND p.sale_percentage IS NOT NULL))
   FROM products p CROSS JOIN bootstrap_import_runs a WHERE a.id=1),
 'stock_groups',(SELECT jsonb_build_array(COUNT(*) FILTER(WHERE stock-reserved BETWEEN 20 AND 200),
   COUNT(*) FILTER(WHERE stock-reserved BETWEEN 1 AND 5),COUNT(*) FILTER(WHERE stock=reserved)) FROM inventory_balances),
 'review_total',(SELECT COUNT(*) FROM product_reviews),
 'status_counts',(SELECT jsonb_object_agg(status,n) FROM (SELECT status,COUNT(*) n FROM orders GROUP BY status) o)
) AS verification;
