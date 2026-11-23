-- OFFLINE PREPARATION ONLY: execution requires G1/G2/G3 and coordinated reader/writer stop.
-- Never repair, normalize or rewrite existing business records in this migration.
ALTER TABLE products ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE products ADD CONSTRAINT ck_products_version CHECK (version >= 0);
ALTER TABLE products ADD COLUMN last_restored_at TIMESTAMPTZ;
ALTER TABLE products DROP CONSTRAINT ck_products_status;
ALTER TABLE products ADD CONSTRAINT ck_products_status
    CHECK (status IN ('DRAFT', 'PUBLISHED', 'UNPUBLISHED', 'ARCHIVED'));
ALTER TABLE product_variants ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE product_images ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE product_images ADD CONSTRAINT ck_product_images_active_primary CHECK (active OR NOT is_primary);
ALTER TABLE categories ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE categories ADD CONSTRAINT ck_categories_version CHECK (version >= 0);
ALTER TABLE brands ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE brands ADD CONSTRAINT ck_brands_version CHECK (version >= 0);

CREATE TABLE catalog_taxonomy_state (
    id SMALLINT CONSTRAINT pk_catalog_taxonomy_state PRIMARY KEY CHECK (id = 1),
    version BIGINT NOT NULL DEFAULT 0 CONSTRAINT ck_catalog_taxonomy_state_version CHECK (version >= 0)
);
INSERT INTO catalog_taxonomy_state (id, version) VALUES (1, 0);

-- PostgreSQL collation/LOWER is authoritative. No accent/internal-space normalization.
CREATE FUNCTION catalog_name_trim(value TEXT) RETURNS TEXT
LANGUAGE SQL IMMUTABLE STRICT PARALLEL SAFE AS $$
    SELECT BTRIM(value, U&'\0009\000A\000B\000C\000D\0020\0085\00A0\1680\2000\2001\2002\2003\2004\2005\2006\2007\2008\2009\200A\2028\2029\202F\205F\3000')
$$;
CREATE FUNCTION catalog_name_key(value TEXT) RETURNS TEXT
LANGUAGE SQL IMMUTABLE STRICT PARALLEL SAFE AS $$
    SELECT LOWER(public.catalog_name_trim(value))
$$;
ALTER TABLE categories ADD CONSTRAINT ck_categories_name_key CHECK (catalog_name_key(name) <> '');
ALTER TABLE brands ADD CONSTRAINT ck_brands_name_key CHECK (catalog_name_key(name) <> '');
CREATE UNIQUE INDEX uk_categories_parent_name_key ON categories (parent_category_id, catalog_name_key(name))
    WHERE parent_category_id IS NOT NULL;
CREATE UNIQUE INDEX uk_categories_root_name_key ON categories (catalog_name_key(name))
    WHERE parent_category_id IS NULL;
CREATE UNIQUE INDEX uk_brands_name_key ON brands (catalog_name_key(name));

-- Retained inactive associations still own their object/source identity and upload FKs.
ALTER TABLE product_images DROP CONSTRAINT uk_product_images_product_id_display_order;
CREATE UNIQUE INDEX uk_product_images_product_id_display_order ON product_images (product_id, display_order) WHERE active;
DROP INDEX uk_product_images_product_id_primary;
CREATE UNIQUE INDEX uk_product_images_product_id_primary ON product_images (product_id) WHERE active AND is_primary;

-- Final structural backstop, shared by Product/status, variant and gallery events.
-- Detailed category-applicable typed facts remain in the shared application validator.
CREATE FUNCTION validate_admin_catalog_product(affected_id UUID) RETURNS VOID LANGUAGE PLPGSQL AS $$
DECLARE
    lifecycle VARCHAR;
    variant_count BIGINT;
    enabled_count BIGINT;
    distinct_options BIGINT;
    image_count BIGINT;
    primary_count BIGINT;
BEGIN
    -- FK inserts hold KEY SHARE; final validation must not promote that to conflicting UPDATE.
    -- NO KEY UPDATE still serializes validators/status writes; Admin claims UPDATE before children.
    SELECT status INTO lifecycle FROM products WHERE id = affected_id FOR NO KEY UPDATE;
    IF NOT FOUND THEN RETURN; END IF;

    SELECT COUNT(*), COUNT(*) FILTER (WHERE enabled), COUNT(DISTINCT option_combination)
      INTO variant_count, enabled_count, distinct_options FROM product_variants WHERE product_id = affected_id;
    IF distinct_options <> variant_count OR EXISTS (
        SELECT 1 FROM product_variants v
        CROSS JOIN LATERAL JSONB_EACH(CASE WHEN JSONB_TYPEOF(v.option_combination) = 'object'
                                         THEN v.option_combination ELSE '{}'::JSONB END) e
        WHERE v.product_id = affected_id AND (e.key !~ '[^[:space:]]'
            OR JSONB_TYPEOF(e.value) <> 'string' OR COALESCE(e.value #>> '{}', '') !~ '[^[:space:]]')
    ) OR (variant_count > 1 AND EXISTS (
        SELECT 1 FROM product_variants WHERE product_id = affected_id AND option_combination = '{}'::JSONB
    )) THEN
        RAISE EXCEPTION 'Invalid catalog variant options' USING ERRCODE = '23514', CONSTRAINT = 'ct_product_variants_integrity';
    END IF;
    IF lifecycle = 'PUBLISHED' AND (enabled_count = 0 OR EXISTS (
        SELECT 1 FROM product_variants WHERE product_id = affected_id AND enabled
            AND (selling_price::TEXT IN ('NaN', 'Infinity', '-Infinity') OR selling_price <= 0
                 OR TRUNC(selling_price) <> selling_price)
    )) THEN
        RAISE EXCEPTION 'Invalid Published variants' USING ERRCODE = '23514', CONSTRAINT = 'ct_products_variant_integrity';
    END IF;

    SELECT COUNT(*), COUNT(*) FILTER (WHERE is_primary) INTO image_count, primary_count
      FROM product_images WHERE product_id = affected_id AND active;
    IF image_count > 10 OR (image_count > 0 AND primary_count <> 1)
       OR (lifecycle = 'PUBLISHED' AND image_count = 0) THEN
        RAISE EXCEPTION 'Invalid catalog gallery' USING ERRCODE = '23514', CONSTRAINT = 'ct_product_images_gallery_integrity';
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION check_catalog_variant_invariants() RETURNS TRIGGER LANGUAGE PLPGSQL AS $$
DECLARE affected_ids UUID[]; affected_id UUID;
BEGIN
    IF TG_TABLE_NAME = 'products' THEN
        IF TG_OP = 'DELETE' THEN RETURN NULL; END IF;
        affected_ids := ARRAY[NEW.id];
    ELSIF TG_OP = 'INSERT' THEN affected_ids := ARRAY[NEW.product_id];
    ELSIF TG_OP = 'DELETE' THEN affected_ids := ARRAY[OLD.product_id];
    ELSE affected_ids := ARRAY[OLD.product_id, NEW.product_id]; END IF;
    FOR affected_id IN SELECT DISTINCT id FROM UNNEST(affected_ids) AS a(id) WHERE id IS NOT NULL ORDER BY id LOOP
        PERFORM validate_admin_catalog_product(affected_id);
    END LOOP;
    RETURN NULL;
END;
$$;

CREATE OR REPLACE FUNCTION check_product_gallery_invariants() RETURNS TRIGGER LANGUAGE PLPGSQL AS $$
DECLARE affected_ids UUID[]; affected_id UUID;
BEGIN
    IF TG_OP = 'INSERT' THEN affected_ids := ARRAY[NEW.product_id];
    ELSIF TG_OP = 'DELETE' THEN affected_ids := ARRAY[OLD.product_id];
    ELSE affected_ids := ARRAY[OLD.product_id, NEW.product_id]; END IF;
    FOR affected_id IN SELECT DISTINCT id FROM UNNEST(affected_ids) AS a(id) WHERE id IS NOT NULL ORDER BY id LOOP
        PERFORM validate_admin_catalog_product(affected_id);
    END LOOP;
    RETURN NULL;
END;
$$;

CREATE FUNCTION validate_admin_catalog_hierarchy() RETURNS VOID LANGUAGE PLPGSQL AS $$
BEGIN
    -- Services lock this gate BEFORE category rows. Unsupported direct DML may abort
    -- on lock inversion, but must never commit a cyclic graph or manufacture a gate.
    PERFORM 1 FROM catalog_taxonomy_state WHERE id = 1 FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Missing catalog taxonomy gate' USING ERRCODE = '23514', CONSTRAINT = 'ct_catalog_taxonomy_state_integrity';
    END IF;
    IF EXISTS (
        WITH RECURSIVE ancestry AS (
            SELECT id AS origin, id, parent_category_id, ARRAY[id] AS path, FALSE AS cycle FROM categories
            UNION ALL
            SELECT a.origin, c.id, c.parent_category_id, a.path || c.id, c.id = ANY(a.path)
            FROM ancestry a JOIN categories c ON c.id = a.parent_category_id WHERE NOT a.cycle
        ) SELECT 1 FROM ancestry WHERE cycle
    ) THEN
        RAISE EXCEPTION 'Invalid catalog hierarchy' USING ERRCODE = '23514', CONSTRAINT = 'ct_categories_hierarchy_integrity';
    END IF;
END;
$$;
CREATE FUNCTION check_admin_catalog_hierarchy() RETURNS TRIGGER LANGUAGE PLPGSQL AS $$
BEGIN
    PERFORM validate_admin_catalog_hierarchy();
    RETURN NULL;
END;
$$;
CREATE CONSTRAINT TRIGGER ct_categories_hierarchy_integrity AFTER INSERT OR UPDATE OR DELETE ON categories
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_admin_catalog_hierarchy();

-- Replacement trigger functions do not retroactively fire. Reject incompatible
-- existing rows before this transactional migration can commit; never repair them.
DO $$
DECLARE product_id UUID;
BEGIN
    PERFORM validate_admin_catalog_hierarchy();
    FOR product_id IN SELECT id FROM products ORDER BY id LOOP
        PERFORM validate_admin_catalog_product(product_id);
    END LOOP;
END;
$$;
