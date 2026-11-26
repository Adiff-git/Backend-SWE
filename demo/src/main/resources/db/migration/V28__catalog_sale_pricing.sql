-- Fail atomically on data that cannot be safely initialized as a future base price.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM product_variants WHERE selling_price < 0
        OR selling_price <> TRUNC(selling_price)
        OR selling_price::TEXT IN ('NaN', 'Infinity', '-Infinity')) THEN
        RAISE EXCEPTION 'Invalid legacy price; audit and approved remediation required' USING ERRCODE = '23514';
    END IF;
    IF CURRENT_SETTING('server_encoding') <> 'UTF8' THEN
        RAISE EXCEPTION 'Catalog option identity requires UTF8';
    END IF;
END $$;

CREATE COLLATION catalog_option_root_v1 (PROVIDER = ICU, LOCALE = 'und', DETERMINISTIC = TRUE);

CREATE FUNCTION catalog_option_identity_v1(label TEXT) RETURNS TEXT
LANGUAGE SQL IMMUTABLE STRICT PARALLEL SAFE AS $$
    SELECT LOWER(BTRIM(NORMALIZE(label, NFC),
        U&'\0009\000A\000B\000C\000D\0020\0085\00A0\1680\2000\2001\2002\2003\2004\2005\2006\2007\2008\2009\200A\2028\2029\202F\205F\3000') COLLATE catalog_option_root_v1)
$$;

ALTER TABLE products ADD COLUMN sale_percentage NUMERIC;
ALTER TABLE products ADD COLUMN sale_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE products ADD COLUMN sale_starts_at TIMESTAMPTZ;
ALTER TABLE products ADD COLUMN sale_ends_at TIMESTAMPTZ;
ALTER TABLE products ADD CONSTRAINT ck_products_sale_configuration CHECK (
    (sale_percentage IS NULL AND NOT sale_enabled AND sale_starts_at IS NULL AND sale_ends_at IS NULL)
    OR (sale_percentage IS NOT NULL
        AND sale_percentage::TEXT NOT IN ('NaN', 'Infinity', '-Infinity')
        AND sale_percentage > 0 AND sale_percentage <= 100
        AND sale_percentage = TRUNC(sale_percentage, 2)
        AND (sale_starts_at IS NULL OR sale_ends_at IS NULL OR sale_starts_at < sale_ends_at))
);

ALTER TABLE product_variants ADD COLUMN base_price NUMERIC;
UPDATE product_variants SET base_price = selling_price;
-- Flush existing deferred catalog checks before changing the populated table definition.
SET CONSTRAINTS ALL IMMEDIATE;
ALTER TABLE product_variants ALTER COLUMN base_price SET NOT NULL;
ALTER TABLE product_variants ADD CONSTRAINT ck_product_variants_base_price CHECK (
    base_price::TEXT NOT IN ('NaN', 'Infinity', '-Infinity')
    AND base_price >= 0 AND base_price = TRUNC(base_price)
);
SET CONSTRAINTS ALL DEFERRED;

CREATE FUNCTION maintain_variant_base_mirror() RETURNS TRIGGER LANGUAGE PLPGSQL AS $$
BEGIN
    IF TG_OP = 'UPDATE' AND NEW.selling_price IS DISTINCT FROM OLD.selling_price
        AND NEW.selling_price IS DISTINCT FROM NEW.base_price THEN
        RAISE EXCEPTION 'selling_price is a read-only base mirror; update base_price' USING ERRCODE = '23514';
    END IF;
    IF NEW.base_price IS NULL THEN
        RAISE EXCEPTION 'base_price is required' USING ERRCODE = '23502', COLUMN = 'base_price', TABLE = 'product_variants';
    END IF;
    NEW.selling_price := NEW.base_price;
    RETURN NEW;
END $$;
CREATE TRIGGER ct_product_variants_base_mirror BEFORE INSERT OR UPDATE ON product_variants
    FOR EACH ROW EXECUTE FUNCTION maintain_variant_base_mirror();

CREATE FUNCTION catalog_active_sale_percentage_v1(percentage NUMERIC, enabled BOOLEAN,
    starts_at TIMESTAMPTZ, ends_at TIMESTAMPTZ, pricing_at TIMESTAMPTZ) RETURNS NUMERIC
LANGUAGE SQL IMMUTABLE PARALLEL SAFE AS $$
    SELECT CASE WHEN enabled AND percentage IS NOT NULL
        AND (starts_at IS NULL OR pricing_at >= starts_at)
        AND (ends_at IS NULL OR pricing_at < ends_at) THEN percentage ELSE NULL END
$$;

CREATE FUNCTION catalog_effective_price_v1(base NUMERIC, percentage NUMERIC, enabled BOOLEAN,
    starts_at TIMESTAMPTZ, ends_at TIMESTAMPTZ, pricing_at TIMESTAMPTZ) RETURNS NUMERIC
LANGUAGE PLPGSQL IMMUTABLE PARALLEL SAFE AS $$
BEGIN
    IF pricing_at IS NULL THEN
        RAISE EXCEPTION 'A pricing instant is required' USING ERRCODE = '22004';
    END IF;
    IF base IS NULL OR base::TEXT IN ('NaN', 'Infinity', '-Infinity')
        OR base < 0 OR base <> TRUNC(base) THEN RETURN NULL; END IF;
    IF percentage IS NOT NULL AND (percentage::TEXT IN ('NaN', 'Infinity', '-Infinity')
        OR percentage <= 0 OR percentage > 100 OR percentage <> TRUNC(percentage, 2)) THEN
        RAISE EXCEPTION 'Invalid sale percentage' USING ERRCODE = '22023';
    END IF;
    IF catalog_active_sale_percentage_v1(percentage, enabled, starts_at, ends_at, pricing_at) IS NOT NULL THEN
        RETURN base - FLOOR(base * percentage / 100);
    END IF;
    RETURN base;
END $$;
