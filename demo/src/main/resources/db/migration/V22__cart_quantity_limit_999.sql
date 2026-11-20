-- Reject incompatible legacy data rather than silently cap or discard quantities.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM cart_items WHERE quantity > 999 OR quantity < 1) THEN
        RAISE EXCEPTION 'Cart quantity migration requires existing quantities in 1..999; review incompatible rows before retrying';
    END IF;
END;
$$;

ALTER TABLE cart_items DROP CONSTRAINT ck_cart_items_quantity;
ALTER TABLE cart_items ADD CONSTRAINT ck_cart_items_quantity CHECK (quantity BETWEEN 1 AND 999);
