# Feature Specification — Admin Inventory Management

**Feature Branch** `features/AdminInventoryManagement`
**Created** 2026-11-24
**Status** OWNER-APPROVED — business specification
**Input** UC-037 — Manage Product Stock

An authorized Administrator can inspect variant-level stock, replenish it, and understand administrative stock changes. The business objective is to keep stock records accurate while honoring units already committed to customer orders. The primary actor is the Administrator.

## Clarifications

### Session 2026-11-24

The project owner explicitly approved BR-I01 through BR-I13, including the BR-I08 amendment for Disabled Variants. This records business approval. Implementation and runtime verification remain pending.

## User Scenarios & Testing

### US1 — Inventory listing and details (P1)

An Administrator finds a variant by Product or SKU and reads its inventory position. Test independently using existing balances, including zero-stock and reserved variants.

- **AC-01** Given two variants of one Product, when an Administrator searches by Product or SKU, then results identify the matching variants and their parent, within the approved search and paging contract. (FR-02, FR-03)
- **AC-02** Given a balance of 10 on hand and 4 reserved, when the Administrator reads it, then quantities are 10 on hand, 4 reserved and 6 available, with catalog eligibility shown separately. (FR-02, FR-04)
- **AC-03** Given a known zero balance and a separate variant whose balance cannot be found, when each is inspected, then zero and unavailable inventory are distinguishable. (FR-04)

### US2 — Replenish stock (P1)

An Administrator records a delivery against one eligible variant. Test independently with an existing reservation.

- **AC-04** Given 10 on hand and 4 reserved, when a valid increase of 5 commits, then the result is 15 on hand, 4 reserved, 11 available and one corresponding stock-change record. (FR-05, FR-08, FR-09)
- **AC-05** Given missing identity, a malformed or non-positive increase, or a result outside representable bounds, when submitted, then the service rejects it with no stock or history effect. (FR-06)
- **AC-06** Given a failure before the operation commits, when the Administrator checks stock and history, then neither reflects the failed operation. For an ambiguous response, the Administrator can reread authoritative stock without inferring success. (FR-08, FR-09, NFR-02)

### US3 — Correct stock (P2)

An Administrator corrects stock with a signed delta under the approved policy. Test both signs and rejection at the reservation floor.

- **AC-07** Given 10 on hand and 4 reserved, a valid correction of −6 commits with zero available, while a correction of −7 from the original state returns HTTP 409 with current on-hand, reserved and available quantities for the authorized target and leaves balance, reservations and history unchanged. (FR-06, FR-07, FR-08)
- **AC-08** Given a zero delta, an absolute set request, or a correction with a blank reason, the request is rejected without effects. A valid positive correction with a reason is recorded as a correction. (FR-07)
- **AC-09** Given two distinct valid concurrent increases by 2 and 3 on a starting stock of 10, when both are accepted, then stock is 15 and both changes are traceable. A conflicting request that is rejected has no partial effects. (FR-05, FR-08, FR-09, NFR-01)
- **AC-10** Given an administrative write overlapping Checkout, cancellation or completion, when either commits first, then the final state matches a valid ordering of accepted operations and preserves exact reservation ownership. (FR-08, FR-12, NFR-01)
- **AC-11** Given a lifecycle transition overlapping an inventory write, when the operation takes effect, then eligibility follows the approved policy for that state, and neither operation changes the other's domain state implicitly. (FR-06, FR-12)
- **AC-12** Given a repeated submission after an uncertain response, an authenticated retry with the same actor, identifier and equivalent payload returns the original committed result without another change, including after an intervening stock or lifecycle change. A changed payload under the same identity returns a conflict. (FR-13)

### US4 — Inspect stock-change history (P2)

An Administrator reviews what changed and when. Test independently with a committed replenishment and a rejected write.

- **AC-13** Given a committed replenishment and a later rejected write, when the Administrator inspects history, then the committed change is attributable to the variant and time and explains the quantity change. The rejected write does not appear as a committed stock change. (FR-09, FR-10)
- **AC-14** Given an older variant or order without administrative stock events, when inspected after adoption, then existing data remains readable and the service does not fabricate past changes or actors. (FR-10, FR-12)

### US5 — Identify out-of-stock variants (P3)

An Administrator can distinguish known zero available units from an unavailable balance. Numerical low-stock monitoring is outside this release.

- **AC-15** Given 4 on hand and 4 reserved, 4 on hand and 3 reserved, and a missing balance, the results distinguish out of stock, positive available stock, and unavailable inventory. Catalog eligibility is reported independently. (FR-04, FR-11)

### Cross-cutting acceptance

- **AC-16** Given a guest, Customer, invalid or expired token, revoked or expired session, or disabled account, when inventory reads or writes are attempted, then access is denied and no stock changes occur. A current authorized Administrator can perform supported actions. (FR-01, NFR-03)
- **AC-17** Given an active allocation and existing order and checkout history, when the variant is disabled or its Product archived, restored or republished, then the balance and historical references persist, existing orders can resolve their allocations, and new sales still require current Catalog and Inventory eligibility. (FR-12, NFR-04)

- **AC-18** Given on-hand at 9,223,372,036,854,775,806, a one-unit increase reaches the maximum exactly. A further increase or input outside signed integer representation fails without stock or journal effects. (FR-05, FR-06)
- **AC-19** Given concurrent identical requests with one actor and operation identifier, at most one mutation and one event commit. A retry after a precommit rollback can succeed once. A retry racing an uncommitted operation may wait or receive a retryable conflict. (FR-08, FR-09, FR-13, NFR-01, NFR-02)
- **AC-20** Given a live variant missing its balance, a detached balance, and an unknown Variant UUID, reads distinguish the live unavailable case and unknown target. Writes create neither balances nor events for any of these targets. (FR-02, FR-04, FR-06)
- **AC-21** Given an Enabled or Disabled variant on Draft, Published or Unpublished and otherwise valid input, a write can commit. The same fresh request against an Archived parent returns conflict and no change, while authorized reads remain available. Disabled variants under the three editable parent states accept stock writes and remain Disabled. (FR-01, FR-06, FR-12)
- **AC-22** Given matching Product names, one matching SKU among siblings, tied sort values and multiple pages, the approved search and lifecycle filter select the right variant rows and paginate deterministically on an unchanged dataset. History pages similarly preserve order across timestamp ties. (FR-03, FR-10)
- **AC-23** Given a reduction racing Checkout, cancellation or completion, the final result for each race is equivalent to one valid ordering. Checkout may raise the floor, cancellation may lower it, and completion lowers both counters. A reduction below the resulting floor fails without any journal or reservation effect. (FR-07, FR-08, FR-12, NFR-01, NFR-02)

- **AC-24** Given a correction reason of 500 characters after trimming, it is accepted with normalized text. A blank correction reason or a normalized reason of 501 characters is rejected. An omitted and a whitespace-only replenishment reason replay the same result. (FR-07, FR-09, FR-13)
- **AC-25** Given one Admin has committed an operation, reuse of its UUID by that Admin for another Variant or action returns 409. A different authorized Admin can independently use that UUID. A revoked session cannot replay a committed operation. (FR-01, FR-13, NFR-03)
- **AC-26** Given an existing journal event for a Variant that is no longer in the live catalog, authorized history remains readable by original UUID, and a fresh mutation cannot recreate or change its balance. (FR-06, FR-10, FR-12, NFR-04)

### Edge Cases

A live variant with a missing balance, a retained balance with no live variant, zero available units with stock fully reserved, a result beyond the supported integer range, stale counts, duplicate submissions, and history spanning a SKU or lifecycle change.

## Requirements

### Functional Requirements

- **FR-01** — Require a valid current authenticated Administrator session and approved authority for all administrative inventory reads and writes, including history and replay. Derive acting identity from the authenticated session. See BR-I11.
- **FR-02** — Show a variant-level record with stable Variant and parent Product references, SKU, lifecycle/eligibility context and authoritative quantities.
- **FR-03** — Support Product name and SKU search, lifecycle filtering, and stable pagination according to BR-I13.
- **FR-04** — Report on-hand and reserved quantities and derive available as on-hand minus reserved for a known balance. Distinguish unavailable inventory from a known zero and numeric availability from purchase eligibility.
- **FR-05** — Under BR-I02, replenish one eligible variant with a positive whole-unit delta and return exact committed quantities.
- **FR-06** — Validate target identity, live balance, eligibility and representable arithmetic under BR-I02, BR-I08 and BR-I13. Return a structured rejection with zero partial effects.
- **FR-07** — Under BR-I03 and BR-I05, accept a nonzero signed correction with a nonblank bounded reason. Enforce the reservation floor and reject the entire operation below it (BR-I04).
- **FR-08** — Validate against current authoritative state at the point of mutation. Commit accepted stock and its associated stock-change evidence together while preserving all outstanding reservations.
- **FR-09** — Under BR-I06, persist one durable Admin journal event per committed change with authenticated actor attribution and sufficient evidence for replay.
- **FR-10** — Under BR-I06, BR-I12 and BR-I13, expose deterministic paginated history to authorized Administrators and distinguish an empty journal from unavailable historical evidence.
- **FR-11** — Under BR-I07, distinguish known zero available stock from positive available and unavailable balances. Numerical low-stock monitoring is outside this release.
- **FR-12** — Preserve existing catalog lifecycle and publication rules, cart and Checkout behavior, order snapshots, historical receipts, and order-owned allocation identity and resolution. An inventory write cannot change Catalog state, authorize a sale, or resolve an allocation.
- **FR-13** — Under BR-I10, require a client operation identifier, replay an equivalent committed operation for its original actor, and reject changed payload reuse. Allow a separate fresh read of authoritative stock.

### Non-functional Requirements

- **NFR-01 — Concurrency** — Use authoritative balance and eligibility state under compatible serialization. Admin/Admin, Checkout, terminal Order resolution and Catalog lifecycle overlap must yield a valid ordering without lost accepted writes (BR-I09).
- **NFR-02 — Atomicity** — Commit balance and journal effects together. A failed transaction has zero partial effects. Use safe structured errors distinguishing malformed requests, denial, unknown target and state conflict. See BR-I04 and BR-I13.
- **NFR-03 — Security** — Reuse current-session JWT and current account authorization. Preserve existing conventions for unauthenticated and forbidden responses.
- **NFR-04 — Compatibility** — Preserve balances, allocations, and historical references from V25 through V28, including pre-V25 orders without inventory participation. Any later schema extension must preserve applied migrations and existing records.

### Business Rules

- **BR-I01 — Variant identity and quantities.** Existing model uses ProductVariant UUID, with on-hand and reserved stored per variant and available equal to their difference. **OWNER-APPROVED:** expose all three and separate sale eligibility. Product name and SKU provide lookup context.
- **BR-I02 — Replenishment. OWNER-APPROVED:** one Variant, positive whole-unit delta to on-hand, no batch. Existing BIGINT bounds are 0 through 9,223,372,036,854,775,807 for counters. For stock S and reserved R, a representable nonzero valid delta lies between R − S and MAX − S. Reject out-of-range input or result without wraparound. Checkout's 999-unit reservation-line cap is separate.
- **BR-I03 — Corrections. OWNER-APPROVED:** signed nonzero whole-unit deltas, with positive and negative corrections and no absolute SET. Positive corrections and replenishment share arithmetic but retain distinct business intent and reason rules. Deltas apply to current stock and avoid overwriting intervening changes with an earlier observed total.
- **BR-I04 — Reservation floor.** Existing V25 enforces 0 ≤ reserved ≤ on-hand and reconciles reserved to ACTIVE allocations. Admin changes must preserve reservation ownership, quantity and state. Equality leaves zero available. **OWNER-APPROVED:** use a structured 409 conflict below the floor. Include current on-hand, reserved and available for the authorized target in the reservation-floor conflict.
- **BR-I05 — Reason. OWNER-APPROVED:** Trim leading and trailing whitespace. Store at most 500 normalized characters. Corrections require nonblank free text. Replenishment treats omitted and blank reasons as absent.
- **BR-I06 — Journal. OWNER-APPROVED:** persist a durable Admin event with operation identifier, Variant UUID, authenticated actor identity, operation type, signed delta, before/after on-hand, reserved observed at mutation, reason and timestamp. Preserve the canonical request identity and original success outcome or sufficient immutable information to reproduce it exactly. Admin history is paginated and excludes reservation lifecycle events.
- **BR-I07 — Stock indicator. OWNER-APPROVED:** derive out-of-stock when a known balance has zero available units. Defer numerical low-stock monitoring until metric, value, comparison, lifecycle population and global/per-variant scope are approved. Missing balance is unavailable.
- **BR-I08 — Lifecycle. OWNER-APPROVED:** new writes require an Enabled or Disabled Variant under Draft, Published or Unpublished, with a live balance. Archived parents reject new writes and remain readable. A replay of a previously committed operation does not perform a new write. Catalog sale rules remain independent.
- **BR-I09 — Concurrency.** Existing Checkout locks Product then Variant then balance. Terminal Order flows lock Order then balance then allocations. Admin synchronization must protect Product and Variant eligibility through the balance mutation without a reverse dependency. Validate fresh counters after acquiring mutation protection. **OWNER-APPROVED:** revalidate deltas on current state without requiring a stale-view token. Commit journal and change together.
- **BR-I10 — Replay. OWNER-APPROVED:** require a UUID operation identifier on every write, unique in the Admin Inventory namespace per authenticated actor across both operation types and all variants. Equivalence includes Variant UUID, operation type, exact delta and canonical reason. Authenticate before any replay, then return the original committed result before mutable eligibility/stock checks. A conflicting request receives 409. Concurrent duplicates converge to one committed mutation and one event. A failed transaction reserves no successful outcome. Retain durable replay evidence with the journal with no automatic short expiry. Future archival must preserve uniqueness and replay.
- **BR-I11 — Authorization.** Existing JWT validation checks current session and active account. **OWNER-APPROVED:** use current ROLE_ADMIN for all Inventory resources, with actor attribution from authentication and 401/403 behavior aligned with Admin Catalog. Guest and Customer cannot access private Inventory.
- **BR-I12 — Historical compatibility.** Preserve current balances, allocations, receipts, Order snapshots and Variant identities. Existing inactive and terminal history remains intact. Any new migration must be additive after the latest actual migration and preserve V1–V28. **OWNER-APPROVED:** the journal begins at deployment with no synthesized old events.
- **BR-I13 — Inventory and history reads. OWNER-APPROVED:** show live Catalog variants across all states by default, including Disabled and Archived. Exclude detached balances from current listing and retain their history by original identity. Apply case-insensitive literal substring matching to Product name or the row's SKU and allow lifecycle filtering. Use stable Product name and Variant UUID ordering, newest-first history with a unique tie-breaker, and Catalog's zero-based page convention (default 20, maximum 100) for both. Require bounded queries without per-row fetches, with a query count independent of page size. A known live variant with a missing balance reads as unavailable and rejects mutation with conflict without provisioning a balance. Use existing Admin-style safe errors: 400 for invalid request shape, 404 for unknown target, and 409 for eligibility, missing-balance, floor or replay conflicts. Use 422 for well-formed requests with invalid quantity or reason.

### Key Entities

- **Inventory balance** — Stable original Variant identity, optional live link, on-hand and reserved counters.
- **Order reservation** — Order-item-owned allocation with immutable identity, quantity and terminal history.
- **Administrative stock-change evidence** — A durable explanation of a committed administrative change linked to the affected variant.
- **Catalog eligibility** — Parent lifecycle and Variant enabled state, independent of inventory quantities.

## Success Criteria

### Measurable Outcomes

- **SC-01** — All listing/detail examples show the correct variant and consistent quantities, including a distinct zero and unavailable case.
- **SC-02** — Every success in the replenishment scenarios changes the intended variant by the accepted quantity and has exactly one corresponding committed change record.
- **SC-03** — Zero unauthorized mutations, oversells, lost accepted writes, orphaned allocations or partial writes in the acceptance matrix.
- **SC-04** — Existing orders and receipts in the compatibility scenarios retain their references and allocation outcomes across catalog lifecycle changes.

## Assumptions

- One existing variant balance is the accounting unit. Whole-unit administrative inputs must fit the inventory model. Checkout's order-line ceiling is a separate rule.
- Priorities describe the delivery order. API formatting, lock mechanics and query budgets are design decisions documented in Plan.

## Dependencies and Compatibility

- Stacked on Feature 1 commit `1e79c3adafc8a2740168bbde1f2442d3d5f83e1d`. At Specify, Feature 1 was absent from the verified `origin/draft/version-1` and `origin/main` histories. Its enabled flag, Product lifecycle transitions and Admin authorization pattern are compatibility dependencies.
- [Existing customer Inventory specification](../010-inventory-management/spec.md) supplies the reservation model. [Feature 1 specification](../013-admin-catalog-management/spec.md) assigns UC-037 stock operations to this feature. [Repository audit](repository-context.md) records the baseline evidence and concurrency risks.

## Scope Exclusions

Admin Orders, Admin Customers, Admin Sales Overview, Product Import (UC-038), payments, shipping, suppliers, warehouses and multi-warehouse inventory, purchase orders, automatic procurement, email/SMS low-stock notifications and customer stock subscriptions. Backend-SWE is the delivery boundary for Feature 2.

## Owner Decision Record

- Write actions, reservation conflicts, normalization, lifecycle and authorization are approved under BR-I02–BR-I05, BR-I08, BR-I09 and BR-I11.
- Journal, replay scope, canonical payload, durable retention and rollout are approved under BR-I06, BR-I10 and BR-I12.
- Variant presentation, out-of-stock-only scope, and listing and history reads are approved under BR-I01, BR-I07 and BR-I13.

See the [decision register](decisions.md) for the approval record.
