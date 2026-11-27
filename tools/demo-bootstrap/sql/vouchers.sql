-- AVAILABLE insertion is mandatory even for historically consumed grants.
INSERT INTO vouchers(id,code,title,description,discount_type,discount_value,minimum_subtotal,maximum_discount,
 valid_from,valid_until,enabled,global_usage_limit,per_customer_usage_limit,redeemed_count)
SELECT r.id,r.code,r.title,r.description,r.discount_type,r.discount_value,r.minimum_subtotal,r.maximum_discount,
 r.valid_from,r.valid_until,r.enabled,r.global_usage_limit,r.per_customer_usage_limit,0
FROM bootstrap_rows s CROSS JOIN LATERAL jsonb_populate_record(NULL::vouchers,s.body) r
WHERE s.kind='vouchers' ORDER BY r.id;
INSERT INTO voucher_customer_usages(id,voucher_id,customer_id,redeemed_count)
SELECT r.id,r.voucher_id,r.customer_id,0 FROM bootstrap_rows s
CROSS JOIN LATERAL jsonb_populate_record(NULL::voucher_customer_usages,s.body) r
WHERE s.kind='voucher_customer_usages' ORDER BY r.id;
INSERT INTO voucher_entitlements(id,voucher_customer_usage_id,voucher_id,customer_id,granted_at,state,consumed_at)
SELECT r.id,r.voucher_customer_usage_id,r.voucher_id,r.customer_id,r.granted_at,'AVAILABLE',NULL
FROM bootstrap_rows s CROSS JOIN LATERAL jsonb_populate_record(NULL::voucher_entitlements,s.body) r
WHERE s.kind='voucher_entitlements' ORDER BY r.id;
