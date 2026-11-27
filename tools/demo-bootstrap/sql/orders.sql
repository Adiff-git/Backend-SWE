-- Resolve immutable monetary snapshots in staging before inserting order headers.
CREATE TEMP TABLE bootstrap_priced_items ON COMMIT DROP AS
SELECT (s.body->>'id')::bigint id, (s.body->>'order_id')::bigint order_id,
 p.id product_id,v.id product_variant_id,p.name product_name_snapshot,v.sku variant_sku_snapshot,
 v.option_combination variant_options_snapshot,(s.body->>'quantity')::integer quantity,
 v.base_price original_unit_price_snapshot,
 catalog_effective_price_v1(v.base_price,p.sale_percentage,p.sale_enabled,p.sale_starts_at,p.sale_ends_at,
   (h.body->>'placed_at')::timestamptz) unit_price_snapshot,
 CASE WHEN p.sale_enabled AND p.sale_percentage IS NOT NULL
   AND (p.sale_starts_at IS NULL OR p.sale_starts_at<=(h.body->>'placed_at')::timestamptz)
   AND (p.sale_ends_at IS NULL OR p.sale_ends_at>(h.body->>'placed_at')::timestamptz)
   THEN p.sale_percentage ELSE NULL END direct_sale_percentage_snapshot,
 p.sale_starts_at,p.sale_ends_at,(h.body->>'placed_at')::timestamptz pricing_instant_snapshot
FROM bootstrap_rows s JOIN bootstrap_rows h ON h.kind='orders' AND (h.body->>'id')::bigint=(s.body->>'order_id')::bigint
JOIN product_variants v ON v.id=(s.body->>'product_variant_id')::uuid JOIN products p ON p.id=v.product_id
WHERE s.kind='order_items';
CREATE INDEX ON bootstrap_priced_items(order_id);
CREATE TEMP TABLE bootstrap_order_totals ON COMMIT DROP AS
SELECT order_id,SUM(unit_price_snapshot*quantity) merchandise_total FROM bootstrap_priced_items GROUP BY order_id;
CREATE TEMP TABLE bootstrap_redemptions (LIKE voucher_redemptions) ON COMMIT DROP;
INSERT INTO bootstrap_redemptions(id,voucher_id,voucher_entitlement_id,voucher_customer_usage_id,customer_id,
 order_id,redeemed_at,code_snapshot,title_snapshot,type_snapshot,value_snapshot,minimum_subtotal_snapshot,
 maximum_discount_snapshot,calculation_base,applied_discount)
SELECT r.id,r.voucher_id,r.voucher_entitlement_id,r.voucher_customer_usage_id,r.customer_id,r.order_id,r.redeemed_at,
 v.code,v.title,v.discount_type,v.discount_value,v.minimum_subtotal,v.maximum_discount,t.merchandise_total,
 CASE v.discount_type WHEN 'FIXED_AMOUNT' THEN LEAST(v.discount_value,t.merchandise_total)
 ELSE FLOOR(LEAST(t.merchandise_total*v.discount_value/100,COALESCE(v.maximum_discount,t.merchandise_total),t.merchandise_total)) END
FROM bootstrap_rows s CROSS JOIN LATERAL jsonb_populate_record(NULL::voucher_redemptions,s.body) r
JOIN vouchers v ON v.id=r.voucher_id JOIN bootstrap_order_totals t ON t.order_id=r.order_id
WHERE s.kind='voucher_redemptions';
INSERT INTO orders(id,customer_id,order_number,placed_at,status,recipient_name_snapshot,recipient_phone_snapshot,
 delivery_address_snapshot,merchandise_total,payable_total,cancelled_at,completed_at,cancellation_reason,
 inventory_managed,voucher_discount,voucher_snapshot)
SELECT r.id,r.customer_id,r.order_number,r.placed_at,r.status,r.recipient_name_snapshot,r.recipient_phone_snapshot,
 r.delivery_address_snapshot,t.merchandise_total,t.merchandise_total-COALESCE(d.applied_discount,0),
 r.cancelled_at,r.completed_at,r.cancellation_reason,TRUE,COALESCE(d.applied_discount,0),
 CASE WHEN d.id IS NOT NULL THEN voucher_order_snapshot(jsonb_populate_record(NULL::voucher_redemptions,to_jsonb(d))) ELSE NULL END
FROM bootstrap_rows s CROSS JOIN LATERAL jsonb_populate_record(NULL::orders,s.body) r
JOIN bootstrap_order_totals t ON t.order_id=r.id
LEFT JOIN bootstrap_redemptions d ON d.order_id=r.id WHERE s.kind='orders' ORDER BY r.id;
INSERT INTO order_items(id,order_id,product_id,product_variant_id,product_name_snapshot,variant_sku_snapshot,
 variant_options_snapshot,quantity,unit_price_snapshot,line_total,original_unit_price_snapshot,
 direct_unit_discount_snapshot,direct_sale_percentage_snapshot,direct_sale_starts_at_snapshot,
 direct_sale_ends_at_snapshot,pricing_instant_snapshot)
SELECT id,order_id,product_id,product_variant_id,product_name_snapshot,variant_sku_snapshot,
 variant_options_snapshot,quantity,unit_price_snapshot,quantity*unit_price_snapshot,original_unit_price_snapshot,
 original_unit_price_snapshot-unit_price_snapshot,direct_sale_percentage_snapshot,
 CASE WHEN direct_sale_percentage_snapshot IS NOT NULL THEN sale_starts_at END,
 CASE WHEN direct_sale_percentage_snapshot IS NOT NULL THEN sale_ends_at END,pricing_instant_snapshot
FROM bootstrap_priced_items ORDER BY id;
