-- At READ COMMITTED, acquire the affected Product rows in UUID order before taking
-- fresh child-count snapshots. Concurrent conflicting writers either serialize or
-- one transaction aborts on a deadlock; neither can commit an invalid final state.
CREATE FUNCTION check_catalog_variant_invariants() RETURNS TRIGGER AS $$
DECLARE
    affected_ids UUID[];
    affected_id UUID;
    variant_count BIGINT;
    distinct_option_count BIGINT;
    invalid_options BOOLEAN;
BEGIN
    IF TG_TABLE_NAME = 'products' THEN
        IF TG_OP = 'DELETE' THEN
            RETURN NULL;
        END IF;
        affected_ids := ARRAY[NEW.id];
    ELSIF TG_OP = 'INSERT' THEN
        affected_ids := ARRAY[NEW.product_id];
    ELSIF TG_OP = 'DELETE' THEN
        affected_ids := ARRAY[OLD.product_id];
    ELSE
        affected_ids := ARRAY[OLD.product_id, NEW.product_id];
    END IF;

    FOR affected_id IN
        SELECT DISTINCT a.id FROM unnest(affected_ids) AS a(id) WHERE a.id IS NOT NULL ORDER BY a.id
    LOOP
        PERFORM 1 FROM products WHERE id = affected_id FOR NO KEY UPDATE;
        IF NOT FOUND THEN
            CONTINUE;
        END IF;

        SELECT COUNT(*), COUNT(DISTINCT option_combination)
          INTO variant_count, distinct_option_count
          FROM product_variants
         WHERE product_id = affected_id;

        IF variant_count = 0 THEN
            RAISE EXCEPTION 'Product % requires at least one variant', affected_id
                USING ERRCODE = '23514', CONSTRAINT = 'ct_products_variant_integrity';
        END IF;

        SELECT EXISTS (
            SELECT 1
              FROM product_variants AS v
              CROSS JOIN LATERAL jsonb_each(v.option_combination) AS option_entry(option_name, option_value)
             WHERE v.product_id = affected_id
               AND (option_entry.option_name !~ '[^[:space:]]'
                    OR jsonb_typeof(option_entry.option_value) <> 'string'
                    OR COALESCE(option_entry.option_value #>> '{}', '') !~ '[^[:space:]]')
        ) INTO invalid_options;

        IF invalid_options OR distinct_option_count <> variant_count OR
           (variant_count > 1 AND EXISTS (
               SELECT 1 FROM product_variants
                WHERE product_id = affected_id AND option_combination = '{}'::JSONB
           )) THEN
            RAISE EXCEPTION 'Product % has invalid variant options', affected_id
                USING ERRCODE = '23514', CONSTRAINT = 'ct_product_variants_integrity';
        END IF;
    END LOOP;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER ct_products_variant_integrity
    AFTER INSERT OR UPDATE ON products
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION check_catalog_variant_invariants();

CREATE CONSTRAINT TRIGGER ct_product_variants_integrity
    AFTER INSERT OR UPDATE OR DELETE ON product_variants
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION check_catalog_variant_invariants();

CREATE FUNCTION check_product_gallery_invariants() RETURNS TRIGGER AS $$
DECLARE
    affected_ids UUID[];
    affected_id UUID;
    image_count BIGINT;
    primary_count BIGINT;
BEGIN
    IF TG_OP = 'INSERT' THEN
        affected_ids := ARRAY[NEW.product_id];
    ELSIF TG_OP = 'DELETE' THEN
        affected_ids := ARRAY[OLD.product_id];
    ELSE
        affected_ids := ARRAY[OLD.product_id, NEW.product_id];
    END IF;

    FOR affected_id IN
        SELECT DISTINCT a.id FROM unnest(affected_ids) AS a(id) WHERE a.id IS NOT NULL ORDER BY a.id
    LOOP
        PERFORM 1 FROM products WHERE id = affected_id FOR NO KEY UPDATE;
        IF NOT FOUND THEN
            CONTINUE;
        END IF;

        SELECT COUNT(*), COUNT(*) FILTER (WHERE is_primary)
          INTO image_count, primary_count
          FROM product_images
         WHERE product_id = affected_id;

        IF image_count > 10 OR (image_count > 0 AND primary_count <> 1) THEN
            RAISE EXCEPTION 'Product % has an invalid image gallery', affected_id
                USING ERRCODE = '23514', CONSTRAINT = 'ct_product_images_gallery_integrity';
        END IF;
    END LOOP;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER ct_product_images_gallery_integrity
    AFTER INSERT OR UPDATE OR DELETE ON product_images
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION check_product_gallery_invariants();
