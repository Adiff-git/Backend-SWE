-- Expand first: existing v1/v2 writers retain unknown sale history; v3 is strict.
ALTER TABLE order_items ADD COLUMN original_unit_price_snapshot NUMERIC;
ALTER TABLE order_items ADD COLUMN direct_unit_discount_snapshot NUMERIC;
ALTER TABLE order_items ADD COLUMN direct_sale_percentage_snapshot NUMERIC;
ALTER TABLE order_items ADD COLUMN direct_sale_starts_at_snapshot TIMESTAMPTZ;
ALTER TABLE order_items ADD COLUMN direct_sale_ends_at_snapshot TIMESTAMPTZ;
ALTER TABLE order_items ADD COLUMN pricing_instant_snapshot TIMESTAMPTZ;

CREATE FUNCTION order_sale_snapshot_is_valid(original NUMERIC, paid NUMERIC, discount NUMERIC,
    percentage NUMERIC, starts_at TIMESTAMPTZ, ends_at TIMESTAMPTZ, priced_at TIMESTAMPTZ)
RETURNS BOOLEAN LANGUAGE PLPGSQL IMMUTABLE PARALLEL SAFE AS $$
BEGIN
    IF original IS NULL AND discount IS NULL AND percentage IS NULL
        AND starts_at IS NULL AND ends_at IS NULL AND priced_at IS NULL THEN RETURN TRUE; END IF;
    IF original IS NULL OR discount IS NULL OR priced_at IS NULL
        OR original::TEXT IN ('NaN', 'Infinity', '-Infinity')
        OR discount::TEXT IN ('NaN', 'Infinity', '-Infinity')
        OR original < 0 OR discount < 0 OR original <> TRUNC(original)
        OR discount <> TRUNC(discount) OR discount > original OR original - discount <> paid THEN RETURN FALSE; END IF;
    IF percentage IS NULL THEN
        RETURN starts_at IS NULL AND ends_at IS NULL AND discount = 0;
    END IF;
    IF percentage::TEXT IN ('NaN', 'Infinity', '-Infinity') OR percentage <= 0 OR percentage > 100
        OR percentage <> TRUNC(percentage, 2)
        OR (starts_at IS NOT NULL AND priced_at < starts_at)
        OR (ends_at IS NOT NULL AND priced_at >= ends_at)
        OR (starts_at IS NOT NULL AND ends_at IS NOT NULL AND starts_at >= ends_at) THEN RETURN FALSE; END IF;
    RETURN paid = catalog_effective_price_v1(original, percentage, TRUE, starts_at, ends_at, priced_at);
END $$;

ALTER TABLE order_items ADD CONSTRAINT ck_order_items_direct_sale CHECK (
    order_sale_snapshot_is_valid(original_unit_price_snapshot, unit_price_snapshot, direct_unit_discount_snapshot,
        direct_sale_percentage_snapshot, direct_sale_starts_at_snapshot, direct_sale_ends_at_snapshot, pricing_instant_snapshot)
);

CREATE FUNCTION guard_order_sale_snapshot() RETURNS TRIGGER LANGUAGE PLPGSQL AS $$
BEGIN
    IF ROW(NEW.original_unit_price_snapshot, NEW.direct_unit_discount_snapshot, NEW.direct_sale_percentage_snapshot,
        NEW.direct_sale_starts_at_snapshot, NEW.direct_sale_ends_at_snapshot, NEW.pricing_instant_snapshot)
        IS DISTINCT FROM ROW(OLD.original_unit_price_snapshot, OLD.direct_unit_discount_snapshot, OLD.direct_sale_percentage_snapshot,
        OLD.direct_sale_starts_at_snapshot, OLD.direct_sale_ends_at_snapshot, OLD.pricing_instant_snapshot) THEN
        RAISE EXCEPTION 'Order sale snapshot is immutable' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER ct_order_items_direct_sale_immutable BEFORE UPDATE ON order_items
    FOR EACH ROW EXECUTE FUNCTION guard_order_sale_snapshot();

CREATE FUNCTION checkout_receipt_v3_is_valid(body JSONB) RETURNS BOOLEAN LANGUAGE PLPGSQL IMMUTABLE AS $$
DECLARE item JSONB; sale JSONB; legacy_items JSONB := '[]'::JSONB; legacy JSONB;
    original NUMERIC; discount NUMERIC; percentage NUMERIC; start_at TIMESTAMPTZ; end_at TIMESTAMPTZ; at TIMESTAMPTZ;
BEGIN
    IF JSONB_TYPEOF(body->'order'->'items') IS DISTINCT FROM 'array' THEN RETURN FALSE; END IF;
    FOR item IN SELECT value FROM JSONB_ARRAY_ELEMENTS(body->'order'->'items') LOOP
        IF NOT item ?& ARRAY['originalUnitPriceSnapshot','directUnitDiscountSnapshot','directSaleSnapshot','pricingInstantSnapshot']
            OR NOT checkout_money_is_valid(item->'originalUnitPriceSnapshot')
            OR NOT checkout_money_is_valid(item->'directUnitDiscountSnapshot')
            OR JSONB_TYPEOF(item->'pricingInstantSnapshot') IS DISTINCT FROM 'string' THEN RETURN FALSE; END IF;
        original := (item->'originalUnitPriceSnapshot'->>'amount')::NUMERIC;
        discount := (item->'directUnitDiscountSnapshot'->>'amount')::NUMERIC;
        at := (item->>'pricingInstantSnapshot')::TIMESTAMPTZ;
        IF at IS DISTINCT FROM (body->'order'->>'placedAt')::TIMESTAMPTZ THEN RETURN FALSE; END IF;
        sale := item->'directSaleSnapshot';
        percentage := NULL; start_at := NULL; end_at := NULL;
        IF sale <> 'null'::JSONB THEN
            IF JSONB_TYPEOF(sale) IS DISTINCT FROM 'object'
                OR NOT sale ?& ARRAY['percentage','startsAt','endsAt']
                OR sale - ARRAY['percentage','startsAt','endsAt'] <> '{}'::JSONB
                OR JSONB_TYPEOF(sale->'percentage') IS DISTINCT FROM 'number'
                OR JSONB_TYPEOF(sale->'startsAt') NOT IN ('null','string')
                OR JSONB_TYPEOF(sale->'endsAt') NOT IN ('null','string') THEN RETURN FALSE; END IF;
            percentage := (sale->>'percentage')::NUMERIC;
            start_at := (sale->>'startsAt')::TIMESTAMPTZ;
            end_at := (sale->>'endsAt')::TIMESTAMPTZ;
        END IF;
        IF NOT order_sale_snapshot_is_valid(original, (item->'unitPriceSnapshot'->>'amount')::NUMERIC,
            discount, percentage, start_at, end_at, at) THEN RETURN FALSE; END IF;
        legacy_items := legacy_items || JSONB_BUILD_ARRAY(item - ARRAY[
            'originalUnitPriceSnapshot','directUnitDiscountSnapshot','directSaleSnapshot','pricingInstantSnapshot']);
    END LOOP;
    legacy := JSONB_SET(body, '{order,items}', legacy_items);
    RETURN checkout_receipt_v2_is_valid(legacy);
EXCEPTION WHEN OTHERS THEN RETURN FALSE;
END $$;

ALTER TABLE checkout_receipts DROP CONSTRAINT ck_checkout_receipts_version;
ALTER TABLE checkout_receipts ADD CONSTRAINT ck_checkout_receipts_version CHECK (receipt_version IN (1,2,3));
ALTER TABLE checkout_receipts DROP CONSTRAINT ck_checkout_receipts_body;
ALTER TABLE checkout_receipts ADD CONSTRAINT ck_checkout_receipts_body CHECK (
    ((receipt_version = 1 AND checkout_receipt_is_valid(receipt_body))
        OR (receipt_version = 2 AND checkout_receipt_v2_is_valid(receipt_body))
        OR (receipt_version = 3 AND checkout_receipt_v3_is_valid(receipt_body)))
    AND (receipt_body->'order'->>'id')::BIGINT = order_id
    AND (receipt_body->'order'->>'placedAt')::TIMESTAMPTZ = created_at
);

CREATE OR REPLACE FUNCTION check_checkout_receipt_integrity() RETURNS TRIGGER LANGUAGE PLPGSQL AS $$
DECLARE original JSONB; discount NUMERIC; snapshot JSONB;
BEGIN
    SELECT JSONB_BUILD_OBJECT('id',o.id,'orderNumber',o.order_number,'status',o.status,
        'recipientNameSnapshot',o.recipient_name_snapshot,'recipientPhoneSnapshot',o.recipient_phone_snapshot,
        'deliveryAddressSnapshot',o.delivery_address_snapshot,
        'merchandiseTotal',JSONB_BUILD_OBJECT('amount',o.merchandise_total,'currency','VND'),
        'payableTotal',JSONB_BUILD_OBJECT('amount',o.payable_total,'currency','VND'),
        'items',(SELECT JSONB_AGG(JSONB_BUILD_OBJECT('id',i.id,'productNameSnapshot',i.product_name_snapshot,
            'variantSkuSnapshot',i.variant_sku_snapshot,'variantOptionsSnapshot',i.variant_options_snapshot,
            'quantity',i.quantity,'unitPriceSnapshot',JSONB_BUILD_OBJECT('amount',i.unit_price_snapshot,'currency','VND'),
            'lineTotal',JSONB_BUILD_OBJECT('amount',i.line_total,'currency','VND')) ||
            CASE WHEN NEW.receipt_version = 3 THEN JSONB_BUILD_OBJECT(
                'originalUnitPriceSnapshot',JSONB_BUILD_OBJECT('amount',i.original_unit_price_snapshot,'currency','VND'),
                'directUnitDiscountSnapshot',JSONB_BUILD_OBJECT('amount',i.direct_unit_discount_snapshot,'currency','VND'),
                'pricingInstantSnapshot',i.pricing_instant_snapshot,
                'directSaleSnapshot',CASE WHEN i.direct_sale_percentage_snapshot IS NULL THEN NULL ELSE
                    JSONB_BUILD_OBJECT('percentage',i.direct_sale_percentage_snapshot,
                        'startsAt',i.direct_sale_starts_at_snapshot,'endsAt',i.direct_sale_ends_at_snapshot) END)
            ELSE '{}'::JSONB END ORDER BY i.id) FROM order_items i WHERE i.order_id = o.id)),
        o.voucher_discount,o.voucher_snapshot INTO original,discount,snapshot
    FROM orders o WHERE o.id = NEW.order_id AND o.customer_id = NEW.customer_id AND o.placed_at = NEW.created_at;
    IF NEW.receipt_version IN (2,3) THEN
        original := original || JSONB_BUILD_OBJECT('voucherDiscount',JSONB_BUILD_OBJECT('amount',discount,'currency','VND'),'voucher',snapshot);
    END IF;
    -- Compare timestamp values semantically; Java's Z and PostgreSQL's +00:00 are equivalent.
    IF NEW.receipt_version = 3 THEN
        IF JSONB_ARRAY_LENGTH(NEW.receipt_body->'order'->'items') <>
            (SELECT COUNT(*) FROM order_items WHERE order_id = NEW.order_id)
            OR EXISTS (SELECT 1 FROM JSONB_ARRAY_ELEMENTS(NEW.receipt_body->'order'->'items') j
                WHERE NOT EXISTS (SELECT 1 FROM order_items i
                    WHERE i.id = (j->>'id')::BIGINT AND i.order_id = NEW.order_id)) THEN
            RAISE EXCEPTION 'V3 receipt item identities do not match order' USING ERRCODE = '23514';
        END IF;
        IF EXISTS (SELECT 1 FROM order_items i WHERE i.order_id = NEW.order_id
            AND (i.pricing_instant_snapshot IS DISTINCT FROM NEW.created_at OR i.original_unit_price_snapshot IS NULL)) THEN
            RAISE EXCEPTION 'Missing v3 order sale evidence' USING ERRCODE = '23514';
        END IF;
        IF EXISTS (SELECT 1 FROM JSONB_ARRAY_ELEMENTS(NEW.receipt_body->'order'->'items') j
            JOIN order_items i ON i.id = (j->>'id')::BIGINT AND i.order_id = NEW.order_id
            WHERE (j->>'pricingInstantSnapshot')::TIMESTAMPTZ IS DISTINCT FROM i.pricing_instant_snapshot
                OR (j->'directSaleSnapshot'->>'startsAt')::TIMESTAMPTZ IS DISTINCT FROM i.direct_sale_starts_at_snapshot
                OR (j->'directSaleSnapshot'->>'endsAt')::TIMESTAMPTZ IS DISTINCT FROM i.direct_sale_ends_at_snapshot) THEN
            RAISE EXCEPTION 'V3 sale timestamps do not match order' USING ERRCODE = '23514';
        END IF;
        -- Normalize timestamps to PostgreSQL JSON rendering before exact snapshot comparison.
        NEW.receipt_body := JSONB_SET(NEW.receipt_body,'{order,items}',
            (SELECT JSONB_AGG(j || JSONB_BUILD_OBJECT('pricingInstantSnapshot',i.pricing_instant_snapshot,
                'directSaleSnapshot',CASE WHEN i.direct_sale_percentage_snapshot IS NULL THEN NULL ELSE
                    (j->'directSaleSnapshot') || JSONB_BUILD_OBJECT('startsAt',i.direct_sale_starts_at_snapshot,
                        'endsAt',i.direct_sale_ends_at_snapshot) END) ORDER BY i.id)
             FROM JSONB_ARRAY_ELEMENTS(NEW.receipt_body->'order'->'items') j
             JOIN order_items i ON i.id = (j->>'id')::BIGINT AND i.order_id = NEW.order_id));
    END IF;
    IF original IS NULL OR original <> (NEW.receipt_body->'order') - 'placedAt' THEN
        RAISE EXCEPTION 'Checkout receipt does not match original order'
            USING ERRCODE = '23514', CONSTRAINT = 'ct_checkout_receipts_integrity';
    END IF;
    RETURN NULL;
END $$;
