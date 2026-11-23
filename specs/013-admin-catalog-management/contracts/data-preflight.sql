-- Admin Catalog Management: READ-ONLY baseline preflight for the V27 schema.
-- NOT a Flyway migration. No repair/normalization/schema command is included.
-- Run only against an identified authorized target with a READ-ONLY account.
-- Example: psql "$AUDIT_DATABASE_URL" -X -v ON_ERROR_STOP=1 -f <this-file>
-- Keep the URL/credentials out of logs. No connection was available during Plan.
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
SET LOCAL statement_timeout = '60s';
SET LOCAL lock_timeout = '2s';

-- 1. Identity/capability/history: compare checksums to repository, never run repair.
SELECT CURRENT_DATABASE() AS database_name, CURRENT_TIMESTAMP AS checked_at,
       CURRENT_SETTING('server_version') AS server_version,
       CURRENT_SETTING('server_encoding') AS encoding;
SELECT datcollate, datctype FROM pg_database WHERE datname = CURRENT_DATABASE();
SELECT installed_rank, version, description, script, checksum, success
FROM flyway_schema_history ORDER BY installed_rank;

-- 2. Proposed comparison semantics. This exact trim set must be shared by the
-- future immutable normalizer/index and repository input comparison.
-- One query groups brands globally and categories by parent (all NULL roots together).
WITH name_keys AS (
    SELECT 'brand'::TEXT AS kind, id, NULL::UUID AS parent_id,
           LOWER(BTRIM(name, U&'\0009\000A\000B\000C\000D\0020\0085\00A0\1680\2000\2001\2002\2003\2004\2005\2006\2007\2008\2009\200A\2028\2029\202F\205F\3000')) AS name_key
    FROM brands
    UNION ALL
    SELECT 'category', id, parent_category_id,
           LOWER(BTRIM(name, U&'\0009\000A\000B\000C\000D\0020\0085\00A0\1680\2000\2001\2002\2003\2004\2005\2006\2007\2008\2009\200A\2028\2029\202F\205F\3000'))
    FROM categories
), conflicts AS (
    SELECT kind, parent_id, name_key, COUNT(*) AS members,
           ARRAY_AGG(id ORDER BY id) AS identities
    FROM name_keys GROUP BY kind, parent_id, name_key
    HAVING COUNT(*) > 1 OR name_key = ''
)
SELECT c.*,
       (SELECT COUNT(*) FROM products p WHERE
            (c.kind = 'brand' AND p.brand_id = ANY(c.identities)) OR
            (c.kind = 'category' AND p.category_id = ANY(c.identities))) AS all_product_references,
       (SELECT COUNT(*) FROM products p WHERE p.status = 'ARCHIVED' AND
            ((c.kind = 'brand' AND p.brand_id = ANY(c.identities)) OR
             (c.kind = 'category' AND p.category_id = ANY(c.identities)))) AS archived_references
FROM conflicts c ORDER BY kind, parent_id NULLS FIRST, name_key;

-- 3. Cycle paths: bounded by visited identities, never infinite recursion.
WITH RECURSIVE ancestry AS (
    SELECT id AS origin, id, parent_category_id, ARRAY[id] AS path, FALSE AS cycle
    FROM categories
    UNION ALL
    SELECT a.origin, c.id, c.parent_category_id, a.path || c.id, c.id = ANY(a.path)
    FROM ancestry a JOIN categories c ON c.id = a.parent_category_id WHERE NOT a.cycle
)
SELECT DISTINCT origin, path FROM ancestry WHERE cycle ORDER BY origin;

-- Dynamic existing search-family bindings: missing/ambiguous is disabled, not
-- grounds for merging allowed names under different parents.
SELECT family,
       COUNT(c.id) AS matching_categories,
       ARRAY_AGG(c.id ORDER BY c.id) FILTER (WHERE c.id IS NOT NULL) AS identities
FROM (VALUES ('laptop'), ('tablet')) AS families(family)
LEFT JOIN categories c ON catalog_search_safe_normalize_v2(c.name) = family
GROUP BY family ORDER BY family;

-- 4. Statuses, variant counts and identities (all lifecycle rows included).
SELECT status, COUNT(*) FROM products GROUP BY status ORDER BY status;
SELECT id, status FROM products WHERE status NOT IN ('DRAFT','PUBLISHED','ARCHIVED');
SELECT p.id, p.status, COUNT(v.id) AS variants
FROM products p LEFT JOIN product_variants v ON v.product_id = p.id
GROUP BY p.id, p.status HAVING COUNT(v.id) = 0 ORDER BY p.id;
SELECT LOWER(sku) AS sku_key, ARRAY_AGG(id ORDER BY id) AS variants
FROM product_variants GROUP BY LOWER(sku) HAVING COUNT(*) > 1;
SELECT id, product_id FROM product_variants
WHERE sku IS NULL OR sku !~ '[^[:space:]]' OR selling_price IS NULL
   OR CASE WHEN selling_price::TEXT IN ('NaN','Infinity','-Infinity') THEN TRUE
           ELSE TRUNC(selling_price) <> selling_price END;
SELECT product_id, option_combination, ARRAY_AGG(id ORDER BY id) AS variants
FROM product_variants GROUP BY product_id, option_combination HAVING COUNT(*) > 1;
SELECT v.id, v.product_id FROM product_variants v
WHERE JSONB_TYPEOF(v.option_combination) IS DISTINCT FROM 'object'
   OR EXISTS (
       SELECT 1 FROM JSONB_EACH(CASE WHEN JSONB_TYPEOF(v.option_combination) = 'object'
                                   THEN v.option_combination ELSE '{}'::JSONB END) e
       WHERE e.key !~ '[^[:space:]]' OR JSONB_TYPEOF(e.value) <> 'string'
          OR COALESCE(e.value #>> '{}','') !~ '[^[:space:]]')
   OR (v.option_combination = '{}'::JSONB AND
       (SELECT COUNT(*) FROM product_variants sibling WHERE sibling.product_id = v.product_id) > 1);

-- Envelope only; the Java typed-value/applicability audit remains mandatory.
SELECT id, product_id FROM product_variants
WHERE technical_specifications IS NULL
   OR JSONB_TYPEOF(technical_specifications) IS DISTINCT FROM 'object'
   OR technical_specifications->'schema_version' IS DISTINCT FROM '1'::JSONB
   OR JSONB_TYPEOF(technical_specifications->'values') IS DISTINCT FROM 'object'
   OR technical_specifications - 'schema_version' - 'values' <> '{}'::JSONB;

-- 5. Published readiness baseline. All current variants will default Enabled.
-- No inventory positivity requirement; SQL metadata cannot prove object existence.
WITH variant_state AS (
    SELECT product_id, COUNT(*) AS variants,
           COUNT(*) FILTER (WHERE CASE
               WHEN selling_price IS NULL OR selling_price::TEXT IN ('NaN','Infinity','-Infinity') THEN TRUE
               ELSE selling_price <= 0 OR TRUNC(selling_price) <> selling_price END) AS invalid_sale_prices
    FROM product_variants GROUP BY product_id
), gallery_state AS (
    SELECT product_id, COUNT(*) AS images, COUNT(*) FILTER (WHERE is_primary) AS primaries,
           COUNT(*) FILTER (WHERE content_type NOT IN ('image/jpeg','image/png','image/webp')
               OR byte_size NOT BETWEEN 1 AND 10485760 OR width <= 0 OR height <= 0
               OR alt_text !~ '[^[:space:]]' OR object_key !~ '[^[:space:]]') AS invalid_metadata
    FROM product_images GROUP BY product_id
)
SELECT p.id, COALESCE(v.variants,0) AS variants, COALESCE(v.invalid_sale_prices,0) AS invalid_sale_prices,
       COALESCE(g.images,0) AS images, COALESCE(g.primaries,0) AS primaries,
       COALESCE(g.invalid_metadata,0) AS invalid_metadata
FROM products p LEFT JOIN variant_state v ON v.product_id = p.id
LEFT JOIN gallery_state g ON g.product_id = p.id
WHERE p.status = 'PUBLISHED' AND (COALESCE(v.variants,0) = 0 OR v.invalid_sale_prices > 0
    OR COALESCE(g.images,0) NOT BETWEEN 1 AND 10 OR COALESCE(g.primaries,0) <> 1 OR g.invalid_metadata > 0
    OR p.name !~ '[^[:space:]]' OR p.category_id IS NULL)
ORDER BY p.id;
SELECT product_id, COUNT(*) AS images, COUNT(*) FILTER (WHERE is_primary) AS primaries
FROM product_images GROUP BY product_id
HAVING COUNT(*) > 10 OR COUNT(*) FILTER (WHERE is_primary) <> 1;
SELECT product_id, display_order, COUNT(*) FROM product_images
GROUP BY product_id, display_order HAVING COUNT(*) > 1;
SELECT product_id, COUNT(*) AS legacy_source_images FROM product_images
WHERE source_image_id IS NULL GROUP BY product_id ORDER BY product_id;
SELECT product_id, product_image_id, state, COUNT(*) AS retained_upload_references
FROM product_image_uploads WHERE product_image_id IS NOT NULL
GROUP BY product_id, product_image_id, state ORDER BY product_id, product_image_id;

-- 6. Orphans. Return identities/reasons, no media keys/customer payloads.
SELECT 'product_category' AS kind, p.id AS identity FROM products p
LEFT JOIN categories c ON c.id = p.category_id WHERE c.id IS NULL
UNION ALL SELECT 'product_brand', p.id FROM products p
LEFT JOIN brands b ON b.id = p.brand_id WHERE p.brand_id IS NOT NULL AND b.id IS NULL
UNION ALL SELECT 'category_parent', c.id FROM categories c
LEFT JOIN categories parent ON parent.id = c.parent_category_id
WHERE c.parent_category_id IS NOT NULL AND parent.id IS NULL
UNION ALL SELECT 'variant_product', v.id FROM product_variants v
LEFT JOIN products p ON p.id = v.product_id WHERE p.id IS NULL
UNION ALL SELECT 'image_product', i.id FROM product_images i
LEFT JOIN products p ON p.id = i.product_id WHERE p.id IS NULL
UNION ALL SELECT 'image_source', i.id FROM product_images i
LEFT JOIN image_uploads u ON u.id = i.source_image_id WHERE i.source_image_id IS NOT NULL AND u.id IS NULL
UNION ALL SELECT 'source_variant', s.id FROM catalog_source_links s
LEFT JOIN product_variants v ON v.id = s.product_variant_id WHERE v.id IS NULL;

-- 7. Balance/allocation relationship. Original identities may be retained after
-- pre-existing legitimate deletes; do not require every retained balance to be live.
SELECT v.id FROM product_variants v LEFT JOIN inventory_balances b ON b.product_variant_id = v.id
WHERE b.id IS NULL;
WITH allocations AS (
    SELECT inventory_balance_id, SUM(quantity) AS units FROM inventory_reservations
    WHERE state = 'ACTIVE' GROUP BY inventory_balance_id
)
SELECT b.id, b.original_variant_id, b.stock, b.reserved, COALESCE(a.units,0) AS active_units
FROM inventory_balances b LEFT JOIN allocations a ON a.inventory_balance_id = b.id
WHERE b.stock < 0 OR b.reserved < 0 OR b.reserved > b.stock
   OR b.reserved <> COALESCE(a.units,0);
SELECT r.id FROM inventory_reservations r
LEFT JOIN inventory_balances b ON b.id = r.inventory_balance_id AND b.original_variant_id = r.original_variant_id
LEFT JOIN order_items i ON i.id = r.order_item_id AND i.order_id = r.order_id
WHERE b.id IS NULL OR i.id IS NULL;

-- 8. Stable per-row comparison fingerprints for rehearsal, not security signatures.
-- Store controlled before/after results; never emit actual receipt/address content.
SELECT id, MD5(ROW(product_name_snapshot,variant_sku_snapshot,variant_options_snapshot,
                  quantity,unit_price_snapshot,line_total)::TEXT) AS snapshot_fingerprint
FROM order_items ORDER BY id;
SELECT id, MD5(receipt_body::TEXT) AS receipt_fingerprint FROM checkout_receipts ORDER BY id;
SELECT id, MD5(ROW(order_id,order_item_id,inventory_balance_id,original_variant_id,
                  quantity,state,reserved_at,resolved_at)::TEXT) AS allocation_fingerprint
FROM inventory_reservations ORDER BY id;
SELECT 'products' AS relation_name, COUNT(*) FROM products
UNION ALL SELECT 'product_variants', COUNT(*) FROM product_variants
UNION ALL SELECT 'product_images', COUNT(*) FROM product_images
UNION ALL SELECT 'catalog_source_links', COUNT(*) FROM catalog_source_links
UNION ALL SELECT 'orders', COUNT(*) FROM orders
UNION ALL SELECT 'order_items', COUNT(*) FROM order_items
UNION ALL SELECT 'checkout_receipts', COUNT(*) FROM checkout_receipts
UNION ALL SELECT 'inventory_reservations', COUNT(*) FROM inventory_reservations;
ROLLBACK;
