# Inventory Data Model

Initial availability locking/reservation requires the balance live product_variant_id to match the requested original UUID. Terminal resolution locks the retained original UUID instead. ACTIVE allocation validation also requires the matching live balance link at final commit, preventing stale direct-SQL allocation after catalog detachment; terminal history remains usable after permitted deletion.

Implemented by V25 for [spec.md](spec.md), verified on disposable PostgreSQL; no production rollout was performed. See [execution evidence](implementation-validation.md). Quantity terms stock/onHandQuantity and reserved/reservedQuantity identify the same counters. availableQuantity is always derived. All new local PKs are BIGINT identity columns named id; existing variant UUID and order/item/customer Long identities are unchanged.

## 1. inventory_balances

| Column | Type | Invariant |
| --- | --- | --- |
| id | BIGINT identity PK | Internal stable balance identity |
| original_variant_id | UUID NOT NULL | Immutable original catalog variant identity; globally unique within balances; never reused/reassigned |
| product_variant_id | UUID NULL | Unique live FK to product_variants.id, ON DELETE SET NULL; when nonnull equals original_variant_id |
| stock | BIGINT NOT NULL DEFAULT0 | Physical units,0..9223372036854775807 |
| reserved | BIGINT NOT NULL DEFAULT0 |0..stock, reconciles to ACTIVE owned allocations |

No stored available counter or customer-editable quantity. Numeric subtraction is safe under the nonnegative checks. A guarded increase reserves q only when stock-reserved>=q, so reserved+q cannot exceed stock/Long.MAX_VALUE. Consume/release predicates prevent underflow. Reconciliation sums use NUMERIC; never narrow aggregate quantities or cap at999. The1..999 bound applies to purchased lines, not balances.

Named objects: uk_inventory_balances_original_variant_id; uk_inventory_balances_product_variant_id; uk_inventory_balances_id_original_variant_id for composite allocation references; fk_inventory_balances_product_variant_id; ck_inventory_balances_quantities and ck_inventory_balances_variant_identity. Unique constraints provide their lookup indexes; avoid duplicate indexes on the same keys.

No public balance identifier/read/write endpoint is added. No ordinary balance deletion API. Retain physical stock/original identity when live catalog reference disappears. Prohibit deletion of a balance with nonzero counters or any reservation history; missing-inventory fixtures may deliberately remove an unused zero balance in isolated tests. Never cascade-delete balances from catalog/order/customer deletion.

Provisioning is a migration-owned AFTER INSERT trigger on product_variants, named ct_product_variants_inventory_initialize. It inserts exactly one matching0/0 balance in that same variant-creation transaction; rollback removes both. Existing variants receive0/0 during the additive migration. No missing-record repair/upsert occurs on reads, cart actions or checkout, and this trigger supplies no positive stock or admin capability.

## 2. inventory_reservations

| Column | Type | Invariant |
| --- | --- | --- |
| id | BIGINT identity PK | Local allocation identity, not exposed in purchase receipt |
| order_id | BIGINT NOT NULL | Restrictive orders.id FK |
| order_item_id | BIGINT NOT NULL | Exactly one allocation per purchased item |
| inventory_balance_id | BIGINT NOT NULL | Restrictive reference to retained balance |
| original_variant_id | UUID NOT NULL | Immutable variant identity, paired with inventory_balance_id |
| quantity | INTEGER NOT NULL | Exact purchased quantity1..999 |
| state | TEXT NOT NULL DEFAULT ACTIVE | New rows must start ACTIVE; later guarded resolution permits CONSUMED or RELEASED |
| reserved_at | TIMESTAMPTZ NOT NULL | Original purchase event time, matches managed order placedAt |
| resolved_at | TIMESTAMPTZ NULL | Null iff ACTIVE; otherwise first terminal event,>=reserved_at |

Add uk_order_items_id_order_id on existing order_items, then composite fk_inventory_reservations_order_item_id_order_id to prevent foreign item attribution. Composite fk_inventory_reservations_balance_variant references (inventory_balance_id,original_variant_id) to the retained balance pair to prevent swapping balance/variant identity. The shorter FK name avoids PostgreSQL's63-byte identifier truncation; all planned names stay within that limit. Restrictive order FK and no deletion cascade retain history. Do not FK original_variant_id directly to the live catalog row: terminal catalog deletion must remain possible without deleting attribution.

Named uniqueness: uk_inventory_reservations_order_item_id and uk_inventory_reservations_order_id_original_variant_id. Current cart uniqueness allows one purchased row per variant/order. Reject duplicate variant/item command entries before mutation; do not independently test duplicate demand against the same units. Index idx_inventory_reservations_inventory_balance_id_state supports reconciliation; idx_inventory_reservations_order_id_original_variant_id is unnecessary if equivalent unique indexing already exists. Use the order/variant unique index for ordered owned-resolution lookup and add a separate index only if actual query plans justify it.

Attribution, quantity and reserved_at never change. ACTIVE can resolve once to CONSUMED or RELEASED; terminal state/resolved_at cannot be rewritten, reversed, transferred or deleted. A first mutation with already-resolved/missing/wrong allocation is a consistency conflict. An already-terminal order repeat is handled before this first-resolution operation is invoked; do not silently accept a corrupt first transition just because its allocations happen to have the requested terminal state.

An insertion guard requires state=ACTIVE and resolved_at=NULL; a caller cannot bootstrap CONSUMED/RELEASED rows even if they would match a final order state. Allocation creation is forbidden for inventory_managed=false orders. Legacy exemption means no allocations at all, not merely skipping completeness validation; otherwise stock could become stranded when legacy cancellation intentionally bypasses Inventory.

## 3. Order participation and status compatibility

Add orders.inventory_managed BOOLEAN NOT NULL. Within one migration: existing rows become false, then new insertion default becomes true. Native production checkout creation explicitly sets true. Freeze participation after insertion. There is no backfill/repair endpoint or false-to-true/true-to-false mutation; absent allocations never imply legacy.

- Legacy false: existing history/cancellation remains valid, with no allocation creation, release or consumption. A separately authorized internal completion likewise changes no stock. Historical fixtures explicitly insert false after the new default.
- Managed true: every purchased item must have exactly its corresponding allocation at commit. Missing allocation/known zero stock is not a legacy exception. Existing direct order-creation tests must compose creation and reservation in one transaction rather than exempt production creation.

Extend ck_orders_status and OrderStatus with FAILED. FAILED means future customer receipt refusal; no failed timestamp/reason, SHIPPING-to-FAILED transition or reservation processing is designed. Keep existing terminal-time constraint semantics for CANCELLED/COMPLETED, and reject FAILED cancellation/internal completion. Legacy FAILED fixtures prove read/filter compatibility. A managed FAILED value cannot authorize releasing/consuming anything; since no supported transition creates it, integrity requires its existing ACTIVE allocations remain unchanged until a separately approved future transition/processing policy is implemented. Do not build a failed-resolution handler.

Order.completedAt currently maps updatable=false: adjust only for first guarded internal completion, while preserving placedAt/number/recipient/item/money snapshot immutability and the winning terminal timestamp. No generic status setter.

## 4. Database integrity boundaries

Local constraints cover numeric bounds, state/time shape, identities and uniqueness. Named deferred constraint triggers validate final transaction state after composed order/reservation/counter writes. Cross-table functions are trigger validators, not IMMUTABLE CHECK functions. Do not weaken existing V23 order-total or V24 receipt integrity.

Use explicit short names for errors/review: ct_inventory_balances_allocation_integrity and ct_inventory_reservations_allocation_integrity for affected-balance reconciliation; ct_orders_inventory_integrity, ct_order_items_inventory_integrity and ct_inventory_reservations_order_integrity for owned completeness; ct_inventory_reservations_immutable, ct_orders_inventory_participation and ct_inventory_balance_retention for mutation/retention guards. Checks include ck_inventory_reservations_quantity/state/times. ApiErrorFactory uses an exact named allowlist rather than converting all23514/23503 failures into409. These exact named constraints are implemented in V25 and exercised by migration/service tests.

| Boundary | Required final invariant |
| --- | --- |
| Balance INSERT/UPDATE/DELETE | reserved equals SUM(ACTIVE quantity) for that balance; no missing balance with active units; no nonzero/history deletion or identity reassignment |
| Allocation INSERT/UPDATE/DELETE | Managed parent required; insertion starts ACTIVE/unresolved; exact balance/original variant/order/item/quantity correspondence; reservation time equals placement; immutable attribution and one-way resolution; affected balance reconciliation |
| Managed order INSERT/UPDATE | Nonempty items; exact allocation set and state for every item; participation unchanged; original purchase/terminal times consistent |
| Item INSERT/UPDATE/DELETE | No missing/extra managed allocation or quantity/order reassignment; affected old/new order completeness; legacy V23 historical totals remain intact |
| Live variant deletion/nulling | Cannot sever active allocations; terminal-only deletion retains balance/original UUID/allocation history and physical stock; existing order snapshots survive |

State correspondence: managed PENDING/CONFIRMED/SHIPPING items have ACTIVE allocations; COMPLETED has CONSUMED; CANCELLED has RELEASED. FAILED does not trigger resolution as described above. Resolved times equal the corresponding managed order terminal time. Later legitimate catalog nulling must not revalidate a historical snapshot against mutable live catalog data; use retained identity for matching once resolved.

Aggregate validators serialize on affected balance rows before fresh sums, using stable order. Normal services already hold the needed balance locks. Existing-order orchestration must acquire its order lock first; inventory validation must not take an existing order lock after balances. V23's existing deferred order lock and V3's product lock remain; noncooperative SQL can safely conflict/deadlock and roll back, never commit invalid inventory. Do not mistake an unlocked SELECT SUM for concurrency protection.

## 5. Lifecycle and event time

| Event | Preconditions | Allocation | Counter effects |
| --- | --- | --- | --- |
| New checkout q | Current sale eligibility; full protected availability>=q; managed order/items | Create ACTIVE | stock unchanged; reserved+q |
| Active status change | Separately authorized workflow | ACTIVE retained | None |
| First internal completion | Owned locked SHIPPING order; exact ACTIVE allocations; valid event time | CONSUMED | stock-q; reserved-q |
| First eligible cancellation | Owned locked PENDING/CONFIRMED; exact ACTIVE allocations; valid event time | RELEASED | stock unchanged; reserved-q |
| COMPLETED/CANCELLED valid repeat | Current auth/ownership/input where applicable, correct terminal state | No new mutation | None; keep first time/reason |
| Failed uncommitted work | Any intermediate failure, including commit validation | No committed change | Full rollback |

Use one Clock sample per first business event and Instant/TIMESTAMPTZ UTC semantics. Persist the same event at microsecond precision in order and allocations. Validate event>=placedAt/reservedAt. Already-terminal repeats skip clock/inventory. Quantity receipts remain original PENDING regardless of current order state.

Future expiry/non-retryable terminal failure must reuse release semantics only when their own transitions/producers are approved; retryable payment failure retains ACTIVE units. This model includes no expiry schedule, payment table/callback or FAILED processing.

## 6. Rollout and backward-compatible receipt validation

Implemented new migration V25__inventory_management.sql, confirmed as the next version after V24. It adds the above tables/invariants, zero balances/provisioning, immutable participation and FAILED status acceptance. All changes are additive to data; new constraints/CREATE OR REPLACE functions may refine current behavior without editing old migration files. Clean and V24-upgrade tests apply this migration on disposable databases; older migrations remain unchanged.

For receipts, narrowly extend checkout_receipt_is_valid(JSONB)'s five-code whitelist to include INVENTORY_UNAVAILABLE and INSUFFICIENT_STOCK, preserving its other structure/number/quantity/time/identity checks and original-order deferred trigger. Keep version1, status201, original body/Location and immutable update prohibition. Existing receipts are neither rewritten nor checked against later current order status. Unknown/corrupt codes remain invalid.

Coordinate rollout so old binaries cannot continue committing reservation-free managed orders after enforcement. Default true/completeness fails such writes safely; zero initial stock means no live purchase can succeed until separately approved future admin supply exists. No positive bootstrap or hidden loader is introduced to disguise that operational limitation.

Fixtures explicitly supply positive balances only for purchase/cart scenarios that require them; legacy fixtures insert false, new managed fixtures compose actual allocations. Test clean apply and V24 upgrade with preserved cart/order/receipt data, default true, missing rows, zero stock, no fabricated historical allocation, immutable flags, exact foreign attribution, overflow/underflow and final commit rollback. Update only latest-version assertions25 and disposable recovery successor26; retain historical migration targets.

## 7. Mapping and ownership

InventoryBalance/InventoryReservation use safe scalar identity mappings, protected constructors/getters and string enums; no unsafe Lombok Data/equality or eager aggregate graph. Native guarded mutations and scalar projections may share a repository fragment with existing Spring Data conventions. Do not retain stale managed balance entities or clear the whole persistence context and detach the owning Order; final reads must reflect actual SQL state. InventoryAvailability is a Lombok immutable response DTO; commands are validated immutable request DTOs. Controllers/converters never access these repositories.
