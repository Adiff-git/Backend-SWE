-- bootstrap_rows is a transaction-local parameterized staging relation.
-- No conflict/upsert clauses: a first import must never adopt existing business rows.
INSERT INTO categories(id,name,parent_category_id)
SELECT r.id,r.name,r.parent_category_id FROM bootstrap_rows s
CROSS JOIN LATERAL jsonb_populate_record(NULL::categories,s.body) r
WHERE s.kind='categories' AND r.parent_category_id IS NULL ORDER BY r.id;
INSERT INTO categories(id,name,parent_category_id)
SELECT r.id,r.name,r.parent_category_id FROM bootstrap_rows s
CROSS JOIN LATERAL jsonb_populate_record(NULL::categories,s.body) r
WHERE s.kind='categories' AND r.parent_category_id IS NOT NULL ORDER BY r.id;
INSERT INTO brands(id,code,name)
SELECT r.id,r.code,r.name FROM bootstrap_rows s CROSS JOIN LATERAL jsonb_populate_record(NULL::brands,s.body) r
WHERE s.kind='brands' ORDER BY r.id;
INSERT INTO products(id,category_id,brand_id,name,description,status,sale_percentage,sale_enabled,sale_starts_at,sale_ends_at)
SELECT r.id,r.category_id,r.brand_id,r.name,r.description,r.status,r.sale_percentage,r.sale_enabled,r.sale_starts_at,r.sale_ends_at
FROM bootstrap_rows s CROSS JOIN LATERAL jsonb_populate_record(NULL::products,s.body) r
WHERE s.kind='products' ORDER BY r.id;
INSERT INTO product_variants(id,product_id,sku,option_combination,selling_price,base_price,technical_specifications)
SELECT r.id,r.product_id,r.sku,r.option_combination,r.base_price,r.base_price,r.technical_specifications
FROM bootstrap_rows s CROSS JOIN LATERAL jsonb_populate_record(NULL::product_variants,s.body) r
WHERE s.kind='product_variants' ORDER BY r.id;
INSERT INTO product_images(id,product_id,object_key,alt_text,content_type,byte_size,width,height,display_order,is_primary,created_at)
SELECT r.id,r.product_id,r.object_key,r.alt_text,r.content_type,r.byte_size,r.width,r.height,r.display_order,r.is_primary,r.created_at
FROM bootstrap_rows s CROSS JOIN LATERAL jsonb_populate_record(NULL::product_images,s.body) r
WHERE s.kind='product_images' ORDER BY r.id;
INSERT INTO catalog_source_links(id,source,source_key,source_url,source_record_id,product_variant_id,
 source_payload_sha256,normalized_content_sha256,normalization_version,approved_manifest_sha256,updated_at)
SELECT r.id,r.source,r.source_key,r.source_url,r.source_record_id,r.product_variant_id,r.source_payload_sha256,
 r.normalized_content_sha256,r.normalization_version,r.approved_manifest_sha256,r.updated_at
FROM bootstrap_rows s CROSS JOIN LATERAL jsonb_populate_record(NULL::catalog_source_links,s.body) r
WHERE s.kind='catalog_source_links' ORDER BY r.id;
