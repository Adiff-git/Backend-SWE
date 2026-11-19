# Checkout Research

**Date**: 2026-11-18. Policy source: approved [spec.md](spec.md). Read-only research agent findings consolidated; local code and official documentation reviewed. No application checks executed.

## 1. Transaction and domain ownership

**Decision**: CheckoutServiceImpl is the public proxied REQUIRED/READ_COMMITTED write transaction. It owns only receipts and calls cart/catalog/user-address/order interfaces. Participants join that transaction; immutable prepared seeds cross boundaries. Conversion never queries. Controller receives success only after commit, including deferred order aggregate constraints.
**Rationale**: Existing cart -> user/catalog and order -> catalog dependencies remain acyclic with a checkout orchestrator. [Spring transaction documentation](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html) explains external proxy activation; no self-invocation assumption.
**Alternatives considered**: Controller orchestration violates layering; reverse cart/order calls risk cycles; REQUIRES_NEW reservations create enduring failed keys; distributed workflows are unnecessary.

## 2. Processing detection before any successful receipt

**Decision**: Check the exact owned committed receipt first; matching command replays201 without a fence, mismatch409. Only after an initial miss try pg_try_advisory_xact_lock on the signed64-bit big-endian first8 bytes of SHA-256 of UTF-8 `checkout:v1:<decimalCustomerId>:<exactKey>`. Reserve this lock namespace for checkout. After either successful acquisition or busy failure, freshly query the exact scoped committed receipt under READ_COMMITTED. A matching hit returns201 and different addressId returns409 CHECKOUT_KEY_CONFLICT. Only busy plus no visible committed receipt returns409 CHECKOUT_IN_PROGRESS; acquired plus no receipt may execute checkout. Persist successful receipts only, atomically with the order/cart changes. Failures release the lock and leave keys reusable.
**Rationale**: A unique receipt insertion alone can block on in-flight work rather than return the approved processing outcome. [PostgreSQL advisory functions](https://www.postgresql.org/docs/17/functions-admin.html#FUNCTIONS-ADVISORY-LOCKS) support the nonblocking transaction-scoped primitive. Exact customer/operation/key uniqueness remains authoritative; a hash collision causes conservative transient contention only, never a receipt match or disclosure. No session locks or process-local-only locks.
**Alternatives considered**: Durable IN_PROGRESS state needs independent commits and crash cleanup. Session locks risk connection-pool leakage. Waiting hides processing state. Fingerprinting mutable cart/address data breaks legitimate replay.

## 3. Lock order and coherent purchase state

**Decision**: Key fence -> user row FOR UPDATE via UserService -> existing cart row FOR UPDATE -> product rows FOR SHARE sorted by UUID text/PostgreSQL byte order -> variant rows FOR SHARE in the same stable order -> new order/items/receipt and included-row deletion. The existing getUserEntityForUpdate installs user.lock-timeout-ms before its own lock; preserve that policy. Immediately after it returns, reapply positive transaction-local checkout.lock-timeout-ms (default5000) before subsequent cart/catalog blocking locks, because the user service overwrites transaction-local lock_timeout. Replay returns before user/cart/address/catalog reads. Address is read after user lock; current address writers also take that lock. Checkout never creates a missing cart.
**Rationale**: Existing-cart writers take cart lock and do not subsequently acquire user; first-cart creation serializes user then cart creation/recheck. Therefore no new reverse order is introduced. Parent product locks do not freeze child price/options: explicit variant locks are required. This design inference is supported by current source and [PostgreSQL row-lock documentation](https://www.postgresql.org/docs/17/explicit-locking.html); it is not an executed overlap result.
**Alternatives considered**: Only child cart locks miss insertions; only product locks permit variant repricing; blanket SERIALIZABLE/retries are unapproved.

## 4. Catalog projection after locks

**Decision**: With cart locked, collect selected variant IDs and candidate parent mappings; lock products then variants, chunk100 sorted IDs without releasing locks. Reread authoritative product/variant purchase projections after all locks. If a variant moved to an unlocked parent since candidate discovery, abort409 CHECKOUT_CONFLICT instead of extending locks out of order. Missing final references are business unavailable; database/connection/unexpected failures abort, never skip. Missing rows that appear after the selected view need not be included retroactively.
**Rationale**: Existing getVariantStates is a read-only projection without price locking; getCartPresentation signs images and locks only image-bearing products. Neither is sufficient purchase synchronization. Final locked publication/name/SKU/options/price form a coherent view.
**Alternatives considered**: Cart display snapshots misprice; image signing adds irrelevant dependency failure; catch-all skips conceal outages.

## 5. Frozen receipt and order-number generation

**Decision**: Sample Clock once and truncate to microseconds before using the same timestamp in order, receipt and response, preserving PostgreSQL timestamp equality. Version1 receipt stores exact response business fields, original generated order/item IDs, initial PENDING status and sorted skipped IDs/reasons. Omit live catalog references and image URLs from receipt; current order detail retains its existing references/images. Order creation reserves next orders identity sequence ID and inserts explicitly with number `ORD-<id>` through an order-owned native creation method. Do not preset generated entity ID and call save/merge. Sequence gaps on rollback are valid. Existing fixture numbers remain unchanged; a collision with arbitrary historical ORD numbering returns safe409 and rolls back, never overwrites/retries automatically.
**Rationale**: Reconstructing current detail changes status/live references/images; immutable receipt must remain stable. Existing order number is immutable nonblank unique TEXT; sequence identity provides deterministic new numbering without allocator infrastructure.
**Alternatives considered**: Frozen detail DTO stores unnecessary live metadata. Random numbers need collision handling. Persist then update immutable number conflicts with existing mappings/constraints. Multi-order Checkout aggregate adds no requirement value.

## 6. Exact amounts and bounds

**Decision**: Keep BigInteger and exact BigDecimal conversion to unconstrained NUMERIC. Check each finite integral nonnegative unit amount, computed line and sum has no more than131072 decimal integer digits before write; otherwise422 CHECKOUT_AMOUNT_LIMIT. No long-money cap, floating point or rounding. Validate quantity1–999 separately; price0 is valid. JSON amounts retain existing numeric `{amount,currency:VND}` representation.
**Rationale**: Existing cart uses BigInteger; order SQL enforces exact finite integer values and line/aggregate equality. [PostgreSQL numeric types](https://www.postgresql.org/docs/17/datatype-numeric.html) document unconstrained numeric limits. Tests must cover bound validation without allocating huge fixtures; selected totals are authoritative only in one checkout calculator.
**Alternatives considered**: Fixed precision silently changes currency policy, long narrows valid amounts, database error alone fails pre-write validation requirement.

## 7. HTTP/security integration

**Decision**: Dedicated checkout controller/strict reader; current identity/permission filters first. One required JSON integer addressId, one exact key, no query/unknown/duplicate/trailing JSON. application/json and compatible application/*+json accepted. MANAGE_CHECKOUT alone on route; USER grant and custom-role explicit grants. Existing CartCacheFilter covers checkout success/errors; global cart media advice already maps415 for this prefix. ApiErrorFactory needs exact-checkout-path conflict mapping before its generic CART_CONFLICT prefix. Expose Location in existing CORS while retaining Authorization.
**Rationale**: Existing global advice/path matching is verified source behavior; do not replace shared advice or change cart input. Current wildcard CORS origins are pre-existing and outside this scope, recorded for separate work.
**Alternatives considered**: Broad advice rewrite, extra permission combinations, role bypass and S3 calls change approved contracts.

## 8. Migration and meaningful proof

**Decision**: Provisional V24 adds receipts/constraints and MANAGE_CHECKOUT USER grant only. Preserve existing migrations and dirty code. Update scoped latest-version/exact-grant regression expectations; isolated search-recovery successor advances V24 toV25. Normal verify discovers CheckoutConcurrencyIT, with class-local pool6 for holders/workers/observer, independently committed fixtures and actual lock overlap.
**Rationale**: Current test pool2 cannot host all overlapping roles; existing Failsafe binds integration-test/verify. Order aggregate integrity triggers are deferred, so flush-only checks are inadequate.
**Alternatives considered**: Weakening tests, global pool changes, sequential repeated calls or optional concurrency profile fail approved proof obligations.

## Resolution

All technical research questions resolved. References support mechanics; architecture/algorithm choices are explicit project design inferences. These choices require implementation validation. No unresolved policy, new dependency or approval exception.

## Accepted I1 refinement

Receipt-first lookup removes transient busy errors from overlapping completed replays. Both fence branches recheck with fresh statement visibility, covering commits during acquisition/busy recheck. Exact durable uniqueness and fence-held atomic creation remain intact. Synchronized tests cover all four races in the existing test classes. This changes lookup ordering/query-count expectations, not approved receipt/status/key/authorization/business rules.
