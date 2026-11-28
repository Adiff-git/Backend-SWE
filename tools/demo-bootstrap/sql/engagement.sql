INSERT INTO carts(id,user_id,voucher_entitlement_id,voucher_selection_revision)
SELECT r.id,r.user_id,r.voucher_entitlement_id,0 FROM bootstrap_rows s
CROSS JOIN LATERAL jsonb_populate_record(NULL::carts,s.body) r WHERE s.kind='carts' ORDER BY r.id;
INSERT INTO cart_items(id,cart_id,product_id,product_variant_id,quantity,selected,added_at,public_product_name,public_sku,public_options)
SELECT r.id,r.cart_id,p.id,v.id,r.quantity,r.selected,r.added_at,p.name,v.sku,v.option_combination
FROM bootstrap_rows s CROSS JOIN LATERAL jsonb_populate_record(NULL::cart_items,s.body) r
JOIN product_variants v ON v.id=r.product_variant_id JOIN products p ON p.id=v.product_id WHERE s.kind='cart_items' ORDER BY r.id;
INSERT INTO product_reviews(id,order_item_id,author_id,product_id,rating,comment,created_at,updated_at)
SELECT r.id,r.order_item_id,r.author_id,r.product_id,r.rating,r.comment,r.created_at,r.updated_at
FROM bootstrap_rows s CROSS JOIN LATERAL jsonb_populate_record(NULL::product_reviews,s.body) r
WHERE s.kind='product_reviews' ORDER BY r.id;
INSERT INTO wishlist_items(id,user_id,product_id,added_at)
SELECT r.id,r.user_id,r.product_id,r.added_at FROM bootstrap_rows s
CROSS JOIN LATERAL jsonb_populate_record(NULL::wishlist_items,s.body) r WHERE s.kind='wishlist_items' ORDER BY r.id;
INSERT INTO user_behavior_events(id,customer_id,event_type,product_id,search_query,normalized_query,client_event_id,source_context,occurred_at)
SELECT r.id,r.customer_id,r.event_type,r.product_id,r.search_query,r.normalized_query,r.client_event_id,r.source_context,r.occurred_at
FROM bootstrap_rows s CROSS JOIN LATERAL jsonb_populate_record(NULL::user_behavior_events,s.body) r
WHERE s.kind='user_behavior_events' ORDER BY r.id;
