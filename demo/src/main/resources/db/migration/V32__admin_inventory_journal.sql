CREATE TABLE admin_inventory_operations (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    admin_id BIGINT NOT NULL CHECK (admin_id > 0),
    operation_id UUID NOT NULL,
    inventory_balance_id BIGINT NOT NULL,
    original_variant_id UUID NOT NULL,
    operation_type TEXT NOT NULL,
    delta BIGINT NOT NULL,
    on_hand_before BIGINT NOT NULL,
    on_hand_after BIGINT NOT NULL,
    reserved_observed BIGINT NOT NULL,
    reason VARCHAR(500),
    occurred_at TIMESTAMPTZ NOT NULL,
    response_version INTEGER NOT NULL DEFAULT 1 CHECK (response_version > 0),
    CONSTRAINT uk_admin_inventory_operations_actor_operation UNIQUE (admin_id, operation_id),
    CONSTRAINT fk_admin_inventory_operations_balance FOREIGN KEY (inventory_balance_id, original_variant_id)
        REFERENCES inventory_balances (id, original_variant_id) ON DELETE RESTRICT,
    CONSTRAINT ck_admin_inventory_operations_reason CHECK (reason IS NULL OR reason ~ U&'[^\0009-\000D\0020\0085\00A0\1680\2000-\200A\2028\2029\202F\205F\3000]'),
    CONSTRAINT ck_admin_inventory_operations_type CHECK (
        (operation_type = 'REPLENISHMENT' AND delta > 0) OR
        (operation_type = 'CORRECTION' AND delta <> 0 AND reason IS NOT NULL)),
    CONSTRAINT ck_admin_inventory_operations_amounts CHECK (
        reserved_observed >= 0 AND on_hand_before >= reserved_observed
        AND on_hand_after >= reserved_observed AND on_hand_before::NUMERIC + delta = on_hand_after)
);

CREATE INDEX idx_admin_inventory_operations_variant_history
    ON admin_inventory_operations (original_variant_id, occurred_at DESC, id DESC);

CREATE FUNCTION reject_admin_inventory_history_mutation() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    -- Existing disposable fixture resets cascade through an empty journal.
    -- Production runtime roles must not have TRUNCATE or table ownership.
    IF TG_OP = 'TRUNCATE'
        AND current_user = (SELECT pg_get_userbyid(relowner) FROM pg_class WHERE oid = TG_RELID)
        AND NOT EXISTS (SELECT 1 FROM admin_inventory_operations) THEN
        RETURN NULL;
    END IF;
    RAISE EXCEPTION 'Administrative inventory history is immutable'
        USING ERRCODE = '23514', CONSTRAINT = 'ct_admin_inventory_operations_immutable';
END;
$$;

CREATE TRIGGER ct_admin_inventory_operations_immutable
    BEFORE UPDATE OR DELETE ON admin_inventory_operations
    FOR EACH ROW EXECUTE FUNCTION reject_admin_inventory_history_mutation();

CREATE TRIGGER ct_admin_inventory_operations_no_truncate
    BEFORE TRUNCATE ON admin_inventory_operations
    FOR EACH STATEMENT EXECUTE FUNCTION reject_admin_inventory_history_mutation();
