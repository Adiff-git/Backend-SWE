INSERT INTO inventory_reservations(order_id,order_item_id,inventory_balance_id,original_variant_id,quantity,state,reserved_at,resolved_at)
SELECT i.order_id,i.id,b.id,i.product_variant_id,i.quantity,'ACTIVE',o.placed_at,NULL
FROM order_items i JOIN orders o ON o.id=i.order_id
JOIN inventory_balances b ON b.product_variant_id=i.product_variant_id ORDER BY i.id;
UPDATE inventory_reservations r SET state=CASE o.status WHEN 'COMPLETED' THEN 'CONSUMED' ELSE 'RELEASED' END,
 resolved_at=CASE o.status WHEN 'COMPLETED' THEN o.completed_at ELSE o.cancelled_at END
FROM orders o WHERE o.id=r.order_id AND o.status IN ('COMPLETED','CANCELLED');
UPDATE inventory_balances b SET reserved=a.reserved,stock=a.reserved+COALESCE((s.body->>'available')::bigint,0)
FROM (SELECT b0.id,COALESCE(SUM(r.quantity) FILTER(WHERE r.state='ACTIVE'),0) reserved
 FROM inventory_balances b0 LEFT JOIN inventory_reservations r ON r.inventory_balance_id=b0.id GROUP BY b0.id) a,
bootstrap_rows s WHERE b.id=a.id AND s.kind='stock' AND (s.body->>'variant_id')::uuid=b.original_variant_id;
UPDATE voucher_entitlements g SET state='CONSUMED',consumed_at=r.redeemed_at
FROM bootstrap_redemptions r WHERE r.voucher_entitlement_id=g.id;
INSERT INTO voucher_redemptions SELECT * FROM bootstrap_redemptions ORDER BY id;
UPDATE voucher_customer_usages u SET redeemed_count=(SELECT COUNT(*) FROM voucher_redemptions r WHERE r.voucher_customer_usage_id=u.id);
UPDATE vouchers v SET redeemed_count=(SELECT COUNT(*) FROM voucher_redemptions r WHERE r.voucher_id=v.id);
