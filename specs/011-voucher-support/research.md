# Voucher design research

2026-11-20; read-only source research, not executed application evidence. Inputs: [spec.md](spec.md), [repository-context.md](repository-context.md), Constitution and current Cart/Checkout/Order/Inventory/auth source. Dedicated research agents inspected Cart/auth boundaries and Checkout/receipt/persistence integration without writing code.

## 1. Owned one-use grants

**Decision**: Voucher domain owns definitions, per-user usage rows, one-use wallet entitlements and immutable redemptions. BIGINT local identity columns named id preserve existing IDs. Entitlement identity is the sole application input. AVAILABLE becomes CONSUMED once; retain the row/history, exclude consumed grants from wallet list. Definition/customer usage counts span all grants and sessions. Null limits never imply infinite ownership.
**Rationale**: Removes the used grant without discarding unused permitted uses. No definition-ID/code apply or collection/provisioning endpoint.
**Alternatives**: Deleting a user–voucher association loses remaining uses/history; a reusable selected definition alone cannot identify which grant was spent. Reusable counter entitlement is possible but unnecessary for the approved explicit-grant contract.

## 2. Preview cleanup and dependency direction

**Decision**: Cart owns selection columns and all selection mutation; VoucherService accepts prepared owner/entitlement/subtotal/time and returns prepared terms/eligibility/discount only. Voucher never calls Cart/Checkout/Order. Cart final presentation calculates merchandise once, asks Voucher for discount and clears invalid selection while holding the cart parent lock. GET gains a narrowly scoped cart write/lock for existing carts to reconcile selection; absent carts remain absent. Image preparation remains outside the transaction. Both successful deletion204 and bulk paths still perform selection recalculation even when no full response is returned.
**Rationale**: Acyclic Cart→Voucher, Checkout→Cart/Voucher/Order/Inventory; a cart-owned wallet facade prepares subtotal applicability so Voucher never calls Cart; invalidation cannot erase a newer selection. No public mutation in a converter or hidden self-invoked transaction. Failure to sign/flush/commit rolls back cleanup too.
**Alternatives**: Nonlocking stale GET cleanup loses newer accepted selections; keeping stale zero-discount selection violates Q1; Voucher→Cart callback creates dependency cycles. Checkout must not call this cleanup path before validating its captured selection.

## 3. Exact calculation

**Decision**: VoucherCalculator owns BigInteger/BigDecimal exact discount computation. Represent percentage as integer hundredths of a percent internally (1..10000), multiply B then divide by10000 with floor, after optional integral cap (equivalent exact rational handling). No floating-point/double and no item allocation. Apply once, discount<=B. Existing subtotal calculator remains authoritative for lines/base; OrderValidator validates coherence without repricing or duplicating voucher calculation.
**Rationale**: Whole VND and up-to2-decimal percentages match approved examples. Preserve existing131072-digit money boundary; reject out-of-range computed/storage representations safely before persistence, no narrowing to long.
**Alternatives**: HALF_UP changes approved rounding; per-line rounding/allocation changes line totals and aggregate outcome.

## 4. Locking and quota authority

**Decision**: REQUIRED/READ_COMMITTED, no automatic retry. New checkout order: receipt lookup/fence/fresh lookup → user → cart → catalog products/variants UUID order → Inventory balances UUID order → voucher definition → customer usage row → entitlement → new order/items/redemption/receipt. Lock the selected definition before its usage/entitlement and reread eligibility under those locks. Conditional usage increments and AVAILABLE→CONSUMED updates require exact affected counts; complete database transaction supplies final protection. Missing usage relation is not auto-created by preview; administrator fixture creates it with grants. All Voucher mutating paths follow definition→usage→entitlement; no runtime grant/revoke writer exists here. Future admin tools must follow this graph.
**Rationale**: Definition lock serializes global last-use claims across customers; existing user/cart locks protect selection. Holding Inventory first avoids later acquisition of a new balance after a voucher lock. Existing order terminal paths take order→Inventory and do no Voucher work, so no new reverse edge. Reserve may relock only balances already held.
**Alternatives**: Per-entitlement locks alone oversubscribe global/personal quota; per-process mutex cannot protect multiple instances; SERIALIZABLE/independent redemption commits or retries are unnecessary/unapproved. Counts computed without a shared definition lock are unsafe.

## 5. Receipt compatibility

**Decision**: New checkouts (with or without voucher) store receipt version2, preserving operation/key scope, address-only fingerprint,201/Location and receipt-first replay. New order JSON adds voucherDiscount and voucher (nullable original snapshot). Old version1 decodes/reserializes with its original exact field set; no null/zero fields added to old replay. Explicit version-aware DTO/codec mapping must prevent current manual decode from dropping v2 fields or default serialization adding fields to v1. No live cart/wallet reads on any receipt hit.
**Rationale**: CheckoutValidator currently accepts only1, repository hardcodes1, converter manually reconstructs original fields; V24/V25 validators require merchandise=payable and deferred comparison reconstructs old shape. All must evolve together in new migration and code while retaining old validation.
**Alternatives**: Reusing version1 with changed semantics makes historical schemas ambiguous. Fingerprinting current selection after checkout clears it rejects valid replay. Rewriting old receipts violates immutability.

## 6. Integrity and rollout

**Decision**: Provisional V26__voucher_support.sql after current V25, recheck available number during implementation. Add tables/counters, nullable cart selection, zero/default order discount and original snapshot fields. New version-aware receipt constraints/functions preserve v1 validator unchanged and add v2. Keep V23 item-sum check unchanged; add payable=merchandise-discount and final attributed redemption checks. No old migration edit, positive voucher/grant production seed or dependency/build/CI change.
**Rationale**: Additive zero-discount legacy migration preserves history. Snapshot/redemption linkage must be validated at actual commit, not merely Java flush. Out-of-scope cancellation remains unchanged; no voucher terminal-state trigger/restoration/retention policy is introduced.
**Alternatives**: Blanket relaxing receipt/body checks or deriving historical discounts from current definitions loses integrity/history.

## 7. Access and validation

**Decision**: Wallet GET /api/me/vouchers and /api/me/vouchers/{walletEntitlementId} require current authenticated eligible identity; use existing authenticated-route security machinery, no new permission/grant. Selection PUT/DELETE requires MANAGE_CART; checkout-only permission remains sufficient for checkout/replay. Strict readers reject owner/definition/code/discount overrides. Positive Long entitlement IDs, identical404 for foreign/unknown, safe owned consumed detail for retained history (list excludes consumed). Personalized responses/errors private,no-store.
**Rationale**: Matches approved authenticated own-wallet scope without inventing a permission product. Public /api/vouchers remains denied.
**Alternatives**: Requiring MANAGE_CART for receipt recovery breaks checkout-only role contract. Role-only ADMIN bypass violates existing authorization.

## 8. Evidence boundary

**Decision**: Require owning domain/layer unit/MVC/repository/service/API tests, synchronized independently committed races, v25 upgrade/old receipt recovery and normal full verify. Reuse shared config/SqlCapture and existing query suites. No mandatory new quality tool/dependency. Prior Inventory572/585 gates are baseline evidence only, never Voucher verification.
**Rationale**: Constitution test organization and honest claims; no duplicate Address/Catalog suites. Measure100/101/205 rows, native statements, deferred server work and detached mapping rather than invent SLA.
**Alternatives**: Sequential quota tests or stale XML cannot demonstrate concurrency/discovery.

## 9. Post-lock authoritative event (U1 resolution)

**Decision**: lockCheckoutVoucher takes owner/entitlement/base without caller time. Acquire all definition→usage→entitlement locks, reread protected data, then sample one injected Clock event at microsecond UTC precision and validate validity/eligibility. Return that event in the quote; Checkout and downstream persistence reuse it unchanged for placement, Inventory reservation, grant consumption, redemption and receipt. No-voucher execution requires no Voucher locks/SQL and returns a single event after its other prerequisite locks.
**Rationale**: A request may wait across validUntil; its earlier timestamp cannot establish current locked eligibility. Exactly validUntil rejects under the approved exclusive end.
**Alternatives**: Sampling before the wait accepts expired vouchers; separate timestamps introduce inconsistent purchase attribution. Receipt hits remain independent of Clock/current voucher validity.
**Verification**: Extend existing planned VoucherConcurrencyIT and Checkout unit/service tests; hold a real required row lock, observe worker waiting, advance a controlled clock across expiry, release and require409/full rollback. Before-end control proves identical post-lock persisted timestamps. No sleeps-only proof or duplicate test class.
