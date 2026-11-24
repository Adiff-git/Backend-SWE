# Data Model — UC-037

## Existing authoritative records

- ProductVariant UUID selects its live V25 balance. Preserve the stable original UUID when the optional live link is detached. Preserve `stock`, `reserved`, all ACTIVE and terminal allocations, and all provisioning/reconciliation behavior.
- The approved write affects `stock` only. For current stock S and reserved R, compare nonzero delta D to `R − S` and `MAX − S` before addition. Both bounds are safe since V25 guarantees `0 ≤ R ≤ S ≤ MAX`. Literal input outside signed BIGINT is rejected before mutation.

## Planned journal

One append-only `admin_inventory_operations` relation, introduced by V29.

| Fields | Type and meaning |
| --- | --- |
| id | BIGINT generated primary key, deterministic history tie-breaker |
| admin_id, operation_id | Positive BIGINT actor from current JWT and UUID supplied by caller, both required |
| inventory_balance_id, original_variant_id | Required BIGINT and UUID, restrictive composite FK to the V25 retained pair |
| operation_type | Required REPLENISHMENT or CORRECTION |
| delta, on_hand_before, on_hand_after, reserved_observed | Required BIGINT, committed event evidence |
| reason | Nullable normalized text, at most 500 Unicode code points |
| occurred_at | Required TIMESTAMPTZ, one clock sample at persisted microsecond precision |
| response_version | Required version discriminator for reconstructing immutable original success |

Named uniqueness on `(admin_id, operation_id)` spans both operation types and all variants. An index on `(original_variant_id, occurred_at DESC, id DESC)` supports history. Require nonnegative observed counters, reserved bounded by both stock snapshots, positive replenishment delta or nonzero correction delta, and nonblank correction reason. Validate normalized text at the service boundary and enforce null/nonempty/length rules in the database. Check before-plus-delta equality with widened exact arithmetic so a malformed row cannot overflow a CHECK expression. Type, delta, variant and normalized reason are the canonical payload, with a null-safe comparison. All success response fields are immutable event fields or exact derivations.

The journal survives live Catalog detachment and retains scalar actor attribution independent of User lifecycle. The composite FK blocks deletion of an event-bearing retained balance even if its current counters are zero. It does not change live Variant detachment policy. No delete/update operation is exposed through the journal repository. Preserve keys and replay evidence across future archival. No prior data is synthesized or copied into the journal.

## Migration

At this checkout V29 is available after V28. Recheck before implementation and choose the next actual version. Add only the journal and its constraints/index. Preserve checksums for V1–V28 and preserve order, reservation, receipt, product, variant and balance rows. Any optional search index requires evidence and an additive design. The [plan](plan.md) governs deployment prerequisites.

## Database enforcement

The additive migration installs a named PostgreSQL BEFORE UPDATE OR DELETE row trigger that always rejects mutation with a stable constraint diagnostic, following the V25 reservation guard pattern. Add a BEFORE TRUNCATE statement guard. The table owner can reset an empty journal so existing disposable fixture resets can cascade without erasing events. The guard rejects all truncation of a populated journal and all truncation by non-owners. The runtime role must have neither DDL/trigger-disable privileges nor a bypass path. Use an owner/migration identity distinct from the runtime identity where deployed grants require it. No cascade from actor, live Variant or retained balance may erase events. Test ordinary SQL UPDATE, DELETE, TRUNCATE and retained balance deletion as rejected, while live Variant detachment and actor deactivation preserve journal attribution. Use an explicit non-null branch for correction reasons because a PostgreSQL CHECK can otherwise accept an unknown result.
