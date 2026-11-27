-- Read-only prefeature audit. Run only against an explicitly selected environment.
-- This file performs no cleanup, backfill, schema change or price reconstruction.
SELECT VERSION(), CURRENT_SETTING('server_encoding') AS encoding;
SELECT collname, collprovider, collversion,
       pg_collation_actual_version(oid) AS actual_version
FROM pg_collation WHERE collname = 'und-x-icu';

SELECT id AS variant_id, product_id, selling_price,
       'invalid current price; original-price provenance cannot be inferred' AS issue
FROM product_variants
WHERE selling_price::TEXT IN ('NaN', 'Infinity', '-Infinity')
   OR selling_price < 0 OR selling_price <> TRUNC(selling_price);

-- Requires the root ICU collation reported above; missing capability is a gate failure.
WITH entries AS (
    SELECT v.id, v.product_id, e.key, e.value,
        LOWER(BTRIM(NORMALIZE(e.key, NFC),
            U&'\0009\000A\000B\000C\000D\0020\0085\00A0\1680\2000\2001\2002\2003\2004\2005\2006\2007\2008\2009\200A\2028\2029\202F\205F\3000') COLLATE "und-x-icu") AS canonical_key,
        CASE WHEN JSONB_TYPEOF(e.value) = 'string' THEN
            LOWER(BTRIM(NORMALIZE(e.value #>> '{}', NFC),
                U&'\0009\000A\000B\000C\000D\0020\0085\00A0\1680\2000\2001\2002\2003\2004\2005\2006\2007\2008\2009\200A\2028\2029\202F\205F\3000') COLLATE "und-x-icu") END AS canonical_value
    FROM product_variants v CROSS JOIN LATERAL JSONB_EACH(v.option_combination) e
), invalid AS (
    SELECT id, 'blank/invalid option or duplicate canonical dimension' AS issue
    FROM entries GROUP BY id
    HAVING BOOL_OR(canonical_key = '' OR canonical_value IS NULL OR canonical_value = '')
        OR COUNT(*) <> COUNT(DISTINCT canonical_key)
), maps AS (
    SELECT v.id, v.product_id,
        COALESCE(JSONB_OBJECT_AGG(e.canonical_key, e.canonical_value)
            FILTER (WHERE e.canonical_key IS NOT NULL), '{}'::JSONB) AS identity
    FROM product_variants v LEFT JOIN entries e ON e.id = v.id
    WHERE NOT EXISTS (SELECT 1 FROM invalid i WHERE i.id = v.id)
    GROUP BY v.id, v.product_id
), duplicates AS (
    SELECT product_id, identity FROM maps GROUP BY product_id, identity HAVING COUNT(*) > 1
), dimensions AS (
    SELECT m.id, m.product_id,
        ARRAY(SELECT JSONB_OBJECT_KEYS(m.identity) ORDER BY 1) AS keys FROM maps m
), inconsistent AS (
    SELECT product_id FROM dimensions GROUP BY product_id HAVING COUNT(DISTINCT keys) > 1
)
SELECT id AS variant_id, issue FROM invalid
UNION ALL
SELECT m.id, 'duplicate canonical combination' FROM maps m JOIN duplicates d
    ON d.product_id = m.product_id AND d.identity = m.identity
UNION ALL
SELECT d.id, 'inconsistent sibling dimensions' FROM dimensions d
    JOIN inconsistent i ON i.product_id = d.product_id;
