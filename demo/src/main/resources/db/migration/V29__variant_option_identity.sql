CREATE FUNCTION catalog_option_map_v1(options JSONB) RETURNS JSONB
LANGUAGE PLPGSQL IMMUTABLE STRICT PARALLEL SAFE AS $$
DECLARE entry RECORD; name TEXT; value TEXT; result JSONB := '{}'::JSONB;
BEGIN
    IF JSONB_TYPEOF(options) IS DISTINCT FROM 'object' THEN
        RAISE EXCEPTION 'Invalid canonical option map' USING ERRCODE = '23514';
    END IF;
    FOR entry IN SELECT * FROM JSONB_EACH(options) LOOP
        name := catalog_option_identity_v1(entry.key);
        IF JSONB_TYPEOF(entry.value) IS DISTINCT FROM 'string' THEN
            RAISE EXCEPTION 'Invalid canonical option value' USING ERRCODE = '23514';
        END IF;
        value := catalog_option_identity_v1(entry.value #>> '{}');
        IF name = '' OR value = '' OR result ? name THEN
            RAISE EXCEPTION 'Blank or duplicate canonical option dimension' USING ERRCODE = '23514';
        END IF;
        result := result || JSONB_BUILD_OBJECT(name, value);
    END LOOP;
    RETURN result;
END $$;

CREATE FUNCTION catalog_option_keys_v1(options JSONB) RETURNS TEXT[]
LANGUAGE SQL IMMUTABLE STRICT PARALLEL SAFE AS $$
    SELECT ARRAY(SELECT key FROM JSONB_OBJECT_KEYS(options) AS key ORDER BY key COLLATE "C")
$$;

CREATE FUNCTION validate_catalog_option_identity_v1(product_key UUID) RETURNS VOID
LANGUAGE PLPGSQL AS $$
DECLARE variants BIGINT; combinations BIGINT; dimensions BIGINT;
BEGIN
    SELECT COUNT(*), COUNT(DISTINCT identity), COUNT(DISTINCT catalog_option_keys_v1(identity))
        INTO variants, combinations, dimensions
    FROM (SELECT catalog_option_map_v1(option_combination) AS identity
          FROM product_variants WHERE product_id = product_key) AS normalized;
    IF variants <> combinations THEN
        RAISE EXCEPTION 'Duplicate canonical variant combination for product %', product_key
            USING ERRCODE = '23514', CONSTRAINT = 'ct_product_variants_option_identity';
    END IF;
    IF dimensions > 1 THEN
        RAISE EXCEPTION 'Inconsistent variant dimension sets for product %', product_key
            USING ERRCODE = '23514', CONSTRAINT = 'ct_product_variants_option_identity';
    END IF;
END $$;

-- Audit before enabling stronger constraints. Never merge identities or rewrite labels.
DO $$
DECLARE product_key UUID;
BEGIN
    FOR product_key IN SELECT id FROM products ORDER BY id LOOP
        PERFORM validate_catalog_option_identity_v1(product_key);
    END LOOP;
END $$;

CREATE FUNCTION check_catalog_option_identity_v1() RETURNS TRIGGER LANGUAGE PLPGSQL AS $$
DECLARE affected UUID[]; product_key UUID;
BEGIN
    IF TG_TABLE_NAME = 'products' THEN
        affected := ARRAY[NEW.id];
    ELSIF TG_OP = 'INSERT' THEN
        affected := ARRAY[NEW.product_id];
    ELSIF TG_OP = 'DELETE' THEN
        affected := ARRAY[OLD.product_id];
    ELSE
        affected := ARRAY[OLD.product_id, NEW.product_id];
    END IF;
    FOR product_key IN SELECT DISTINCT id FROM UNNEST(affected) AS id WHERE id IS NOT NULL ORDER BY id LOOP
        PERFORM 1 FROM products WHERE id = product_key FOR NO KEY UPDATE;
        IF FOUND THEN
            -- Fresh READ COMMITTED statement after parent coordination sees committed competitors.
            PERFORM validate_catalog_option_identity_v1(product_key);
        END IF;
    END LOOP;
    RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER ct_product_variants_option_identity AFTER INSERT OR UPDATE OR DELETE
    ON product_variants DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION check_catalog_option_identity_v1();
CREATE CONSTRAINT TRIGGER ct_products_option_identity AFTER INSERT OR UPDATE
    ON products DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION check_catalog_option_identity_v1();
