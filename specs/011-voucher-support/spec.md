# Feature Specification: Voucher Support with Cart Preview and Order Discounts

**Feature Branch**: `feature/cart` (unchanged)
**Created**: 2026-11-20
**Status**: Clarified — Q1 custom wallet policy, Q2 approved and Q3 explicitly deferred; ready for planning; specification only
**Input**: Owned voucher wallet, persistent cart selection and recalculated preview, authoritative checkout discount, redemption and historical order totals in the existing single-vendor application.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Select a voucher and understand the current total (Priority: P1)

As a customer, I select, replace or remove a voucher and see what I would currently pay without consuming a use.

**Why this priority**: Customers must understand an estimated discount before committing a purchase.
**Independent Test**: Prepare a customer cart and voucher definitions; exercise selection and every cart mutation without creating an order.

**Acceptance Scenarios**:

1. **Given** eligible selected items totaling500,000 VND and an applicable fixed50,000 voucher, **When** selected, **Then** show subtotal500,000, discount50,000 and estimated payable450,000; item prices/line totals and usage remain unchanged.
2. **Given** an existing selection, **When** replaced successfully, **Then** only the voucher selection changes; a failed replacement preserves the previous selection and all cart fields.
3. **Given** a selected voucher, **When** quantity, variant, add/remove or individual/bulk selection changes, **Then** recalculate using the current fully eligible selected rows without silently changing their quantities/preferences.
4. **Given** a selected voucher that becomes ineligible, **When** viewed or the cart changes, **Then** automatically clear the selection and return zero discount with recalculated totals; no preview consumes or reserves usage. Regaining eligibility does not reselect it automatically.
5. **Given** no selected voucher or no eligible selected items, **When** viewed, **Then** discount is zero; no eligible items also means zero subtotal/payable. Removing an absent selection succeeds without creating a cart.
6. **Given** a valid already-selected voucher, **When** reapplied, **Then** retain one selection and no duplicate usage; apply-time eligibility is still rechecked.

### User Story 2 - Commit the quoted voucher under current checkout rules (Priority: P1)

As a customer, I receive a confirmed order-level discount calculated from the items actually ordered, or a clear rejection rather than an unexpected higher charge.

**Why this priority**: Purchase correctness and limited-use fairness are essential.
**Independent Test**: Prepare selected cart/voucher/address data; perform checkout, retry and overlapping requests, verifying exact committed totals and usage independently of wallet browsing.

**Acceptance Scenarios**:

1. **Given** selected rows including unavailable items, **When** checking out, **Then** preserve existing skip precedence and full-row stock rules, calculate the voucher against only included merchandise, and create exactly one order on success.
2. **Given** a voucher whose minimum was met in preview but is not met after checkout skips, **When** placing the order, **Then** reject checkout with full rollback; never silently charge the undiscounted total.
3. **Given** any failed checkout including a failure at final commitment, **When** retried after correction, **Then** no partial order, receipt, reservation, usage or cart consumption survives the failed attempt and its unsuccessful key remains reusable.
4. **Given** one remaining global use and two eligible customers, **When** checkouts overlap, **Then** at most one consumes that use. Likewise concurrent purchases for one customer never exceed their personal limit.
5. **Given** a committed voucher order, **When** its key is replayed after voucher expiry, selection changes or later order-state changes, **Then** return the original successful receipt and discount with zero new usage/order/stock/cart mutation.
6. **Given** voucher selection changes concurrently with first checkout, **When** both complete, **Then** checkout uses one coherent selection or fails safely; a later accepted selection is never cleared by replay or lost unnoticed.

### User Story 3 - Understand historical discounts (Priority: P1)

As a customer, I can explain a committed discount after the entitlement is consumed or voucher terms change.

**Why this priority**: Historical totals and redemption attribution must remain dependable.
**Independent Test**: Prepare voucher-bearing and pre-voucher orders and read their history and original receipts without a cancellation workflow.

**Acceptance Scenarios**:

1. **Given** a discounted order, **When** its voucher is edited, disabled, expires or leaves the active wallet, **Then** original code/title/type/value/base/cap/discount/redemption-time snapshots and totals remain unchanged.
2. **Given** completion or receipt replay, **When** processed, **Then** consume no second entitlement or voucher use; preserve original redemption history.
3. **Given** a pre-voucher order/receipt, **When** read or replayed, **Then** preserve original amounts and receipt representation; fabricate no voucher/redemption.

### User Story 4 - View my administered voucher wallet (Priority: P2)

As an authenticated customer, I browse only my granted entitlements and inspect terms before selecting one.

**Why this priority**: Ownership determines which vouchers the customer can apply.
**Independent Test**: Prepare administrator-granted entitlements for two customers; list/details/application disclose and use only the caller's grants.

**Acceptance Scenarios**:

1. **Given** my available grants and another customer's grants, **When** browsing, **Then** return only my active unconsumed entitlements with terms/current eligibility, never another customer's ownership or usage.
2. **Given** a known voucher without an available grant to me, **When** applying its identifier or another customer's entitlement, **Then** use the same safe not-found response as unknown ownership and change nothing.
3. **Given** three granted entitlements for one voucher and a personal limit of3, **When** two separate purchases succeed, **Then** remove only the two used entitlements from the active wallet, preserve their history, and retain the third for a later eligible purchase.
4. **Given** available grants but an exhausted personal/global limit, **When** applying/checking out, **Then** reject; grants do not bypass limits, including across multiple grants or sessions.

### Edge Cases

- Exact minimum spend; start instant inclusive and end instant exclusive under approved Q2; absent bounds and limits have explicit approved meanings.
- Fractional percentage result, maximum cap, fixed discount larger than subtotal, zero-price merchandise, whole-VND precision and amounts larger than ordinary machine integers.
- Empty cart, no selected items, eligible subtotal zero, missing Inventory, partial stock, unpublished variants and current price changes.
- Voucher expiry/disable/exhaustion between preview and checkout, between simultaneous application and checkout, and while checkout waits for required Voucher locks. Validity is evaluated at the single post-acquisition event; exactly validUntil is expired.
- Global and personal limits reached together; limits of1; repeated apply/remove, real overlapping final-use claims and failure after all provisional writes.
- Lost successful response; completed replay concurrently with original commitment or a busy request; address-command mismatch and current authorization still apply.
- Selected entitlement consumed/revoked externally, historical voucher deletion, multiple grants of one voucher, unlimited personal limit with finite grants and finite personal limit below granted count.
- Existing cancellation remains outside Voucher integration. No voucher quota/entitlement restoration or retention policy is introduced; future cancellation support needs a separate approved policy. FAILED refusal processing and payment/expiry producers remain deferred.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Support FIXED_AMOUNT and PERCENTAGE discounts on one order's total; preserve every item unit price, quantity and line total. One successful checkout still creates exactly one order.
- **FR-002**: Use exact existing VND money conventions; no client prices, owner IDs, discounts, calculated totals or usage counts are authoritative.
- **FR-003**: Preserve authenticated current identity/grants and safe ownership. Cart voucher mutations use existing cart permission; checkout/replay retain checkout permission without requiring a separate wallet/cart-read grant. Wallet list/detail require authenticated eligible identity and are scoped to that identity. Order access remains unchanged; no admin bypass or client owner override. Exact wallet read permission mapping belongs to planning.
- **FR-004**: Maintain voucher identity, unique customer-facing code, title/description, type/value, minimum eligible subtotal, optional percentage cap, validity bounds, enabled flag and optional global/personal usage limits. Invalid definitions cannot become redeemable.
- **FR-005**: Allow at most one selected entitlement per cart/checkout and one voucher per order. Apply by owned wallet-entitlement identifier only, never code or voucher-definition identifier. Maintain an explicit user–voucher ownership/usage relationship. Only administrator-granted entitlements may be applied; no public discovery or user-facing claim/code-redemption workflow. Administrator provisioning is an external business precondition; its API is out of scope. Tests use explicit granted-entitlement fixtures, not fabricated unlimited ownership.
- **FR-006**: Use approved exact whole-VND floor rounding, percentage >0 through100 with at most2 decimals and optional cap, fixed discount capped at eligible subtotal, pre-discount eligible-merchandise base, optional validity bounds (start inclusive/end exclusive), null limits unlimited and configured limits positive integers. Trimmed case-insensitive uppercase codes are definition data only; no customer code input is introduced. Full definition rules are recorded below.
- **FR-007**: Model each administrator-granted wallet entitlement as one spendable voucher use linked to its owning customer and voucher definition, with a distinct retained identity and available/consumed outcome. Multiple permitted uses require multiple explicit grants. Checkout consumes exactly the chosen entitlement, removes it from the active inventory and retains its redemption attribution; other available entitlements remain. Global/personal limits also constrain committed redemptions across all grants of that definition. Null personal limit does not create grants or grant unlimited ownership; new grants do not reset historical usage. No grant-creation API is introduced.
- **FR-008**: Validate existence, caller ownership, available entitlement, enabled status, current validity, global/personal usage, positive eligible subtotal, minimum spend and all approved eligibility rules on apply/reapply/replace. Reject any failure without mutation. One selection persists; no application/preview reserves or consumes quota. A failed replacement preserves the previous selection even if it has become ineligible; a later successful cart evaluation performs the specified cleanup. Applying to no eligible subtotal rejects without creating a cart. Removal is idempotent and never deletes wallet ownership.
- **FR-009**: Recalculate on every view and after every existing cart mutation and voucher mutation using current eligible rows, voucher terms/time, owned entitlement and global/caller usage. A successful cart-content mutation automatically clears an ineligible selection and recalculates undiscounted totals, without rejecting an otherwise valid mutation or rewriting item quantity/preferences. Once cleared, later eligibility does not reselect it. For a cart read detecting external invalidation, return no applied selection and reconcile that stale selection atomically before successful response; this is an explicit voucher-selection-only exception to existing GET business nonmutation. Reads never consume quota/grants or change items. Checkout must independently revalidate its captured selection and reject invalidity, never run preview auto-clear to silently proceed undiscounted.
- **FR-010**: Expose eligible selected subtotal, nullable applied voucher/entitlement summary, discount and estimated payable total, retaining existing items/counts. finalTotal becomes estimated payable; without an applied voucher it equals subtotal and discount is zero. No ineligible selection remains represented as selected with a zero-discount preview. Provide a safe selection-cleared reason for automatic removal so the customer understands the change. Wallet entries may report current applicability without being selected. No other customer usage is exposed.
- **FR-011**: Calculate base B as the exact sum of selected currently checkout-eligible whole line amounts for preview and actually included whole line amounts for checkout. Minimum spend compares B before this discount. Fixed raw discount is its value; percentage raw discount is B × percentage /100, then optional percentage cap. Floor-round once to whole VND after any cap, then cap final discount at B. Payable=B-discount; all amounts remain nonnegative. No shipping/other charge or per-item allocation.
- **FR-012**: Preserve prior catalog/quantity/price/Inventory skip precedence and no-eligible-items rejection. Revalidate selected voucher after the final included subtotal is known and all required Voucher locks have been acquired. Only then sample the authoritative event and evaluate validity; do not reuse a pre-lock time. Reuse that event unchanged for order placement, grant consumption, Inventory reservation, redemption and receipt creation. Do not weaken stock limits, reclassify dependency errors as skips or use preview values as purchase truth.
- **FR-013**: Commit exactly one order, unchanged item snapshots, exact Inventory reservations, voucher redemption/usage, represented cart-item removal, consumption/removal of only the chosen wallet entitlement and selection cleanup and successful idempotency outcome together. Any failure, including final commitment, rolls all of them back; skipped/unselected rows remain unchanged.
- **FR-014**: Consume usage only for committed voucher-bearing orders, enforce optional global and per-customer limits without oversubscription under overlap, and prevent duplicate redemption for one order. No-voucher orders consume none. Per-customer consumption is scoped to authenticated stable identity; a new session/key does not reset it.
- **FR-015**: Preserve receipt-first success lookup and fresh checks after acquired/busy concurrency fencing. Recover original201/Location/body/discount without evaluating the current cart/voucher or mutating current selections, stock or quotas. Preserve accepted address-command equality; current mutable selection is captured only for first execution, not added to replay identity. Changing selection after success does not turn that success into a new execution or mismatch. A new purchase needs a new key. Authentication/input validation still precedes replay.
- **FR-016**: Preserve successful receipt lifetime, legacy decoding and immutable snapshots without rewriting old bodies or adding fields to old replay results. New discounted receipts must faithfully explain the committed discount; representation/version evolution belongs to planning and must preserve historical compatibility.
- **FR-017**: Persist retained voucher identity/reference and original code/title/type/value/base/applied discount/relevant cap/redemption instant linked to owned order/customer. Existing merchandise total remains sum of unchanged lines; payable is merchandise minus stored voucher discount. No-voucher/legacy discount is zero. Later edits/deletion must not change historical totals; restrict physical definition removal while any retained ownership/selection/redemption references it. Disabled definitions remain explainable through owned wallet/history but cannot be applied.
- **FR-018**: Expose appropriate stored discount explanation through existing owned order summaries/details and new checkout outcomes. Preserve existing pagination/sorts and privacy; payable sorting uses the committed discounted payable amount. No retroactive repricing, fabricated legacy redemption or changed old item snapshots.
- **FR-019**: Cancellation quota behavior is a deferred business decision: assume neither restoration nor permanent retention as an approved lifecycle policy. Add no cancellation-specific Voucher endpoint, service, hook or workflow, and do not change existing Order cancellation/Inventory behavior. Retain redemption/discount data for future integration. Completion and replay never consume again. No refusal/FAILED transition, expiry/payment producer or generic terminal handler is introduced.
- **FR-020**: Concurrent selection/checkout/read cleanup must use a coherent owned entitlement and preserve accepted later selections. Concurrent first redemptions cannot consume the same entitlement twice or exceed definition-level global/personal limits across grants. Read cleanup must not erase a newer accepted selection. Retries/replays never consume again; conflicts fail safely with full rollback rather than automatic retry.
- **FR-021**: Require meaningful deterministic calculation, strict-input/security, real persistence/commit/rollback, historical/replay and synchronized independent concurrent coverage under current test conventions. Include every existing cart mutation, minimum/validity/rounding boundaries, cap/zero/large amounts, global/customer last use, same-entitlement overlap, expiry during an observed Voucher-lock wait (including the exclusive-end boundary and before-end success control), multiple-grant exhaustion, ownership denial, read cleanup versus selection/checkout, selection versus checkout and old/new receipt recovery. No application tests are claimed executed at specification time.

### Customer-visible operations and errors

Provide owned wallet list/detail, apply/replace/remove cart selection, and existing cart/checkout/order response extensions. Use current-user resource semantics; the earlier public /api/vouchers suggestions are superseded. Exact wallet paths and entitlement identifier representation belong to planning, preserving existing IDs. Apply input contains exactly walletEntitlementId, never code, voucherId, owner, totals or discount. Missing/null/blank/wrong-type/malformed identifier, duplicate/unknown fields or multiple selectors are400. No body/query is accepted for removal; existing strict cart parsing conventions remain.

Wallet listing defaults page0/size20, sizes1–100, stable normalized-code order then entitlement identity; only the caller's available unconsumed grants are listed. Multiple grants of the same definition remain distinguishable. Expired/disabled/quota-exhausted grants can remain in the wallet with safe applicability reasons, but cannot be selected. Consumed grants leave active inventory while their history remains. Own grant detail is visible even when currently ineligible; foreign/unknown entitlements share safe404. Availability never reserves usage.

Apply/replace succeeds200 with full recalculated CartResponse; removal succeeds204 even when absent, without creating a cart. Authentication401/permission403 precede validation; foreign/unknown ownership404; ineligible owned application422; independently invalidated checkout409 VOUCHER_NOT_APPLICABLE; recognized concurrency/quota conflicts409; unsupported supplied request media415; unexpected failures safe500. Use safe ProblemDetail and private,no-store. Rejected apply/replace/checkout changes no selection or other state; successful cart evaluation may perform the explicitly approved auto-clear, with a safe reason. A failed checkout must not clear its invalid selection or remove grants/items.

After ownership/existence resolution, deterministic eligibility reasons are entitlement-unavailable, disabled, not-started, expired, global-exhausted, personal-exhausted, no-eligible-selected-items, below-minimum. Capture one authoritative time per evaluation. Missing/unowned details never expose another customer's state. Invalidity arising only during checkout is rejected even if a preview previously showed a discount.

### Key Entities

- **Voucher**: Stable definition identity, normalized code/terms and global/personal limits; code is not an application or claiming input.
- **Cart voucher selection**: At most one persistent reference to an available owned wallet entitlement, automatically cleared on invalidation during successful preview/cart evaluation, never a quota reservation.
- **User–voucher entitlement**: Retained ownership/usage relationship; one administrator grant permits one use, multiple grants support repeated eligible use, consumption removes only that grant from active inventory.
- **Price preview**: Observed eligible subtotal, selection/applicability/reason, calculated discount and estimated payable; no durable purchase authority.
- **Voucher redemption**: Durable order/customer/voucher/entitlement association, original terms, exact calculation/time and consumed-use attribution; retained independently of active-wallet removal. Cancellation disposition is deferred.
- **Order discount snapshot**: Historical merchandise base, applied voucher discount, resulting payable and explanation; item arithmetic remains unchanged.
- **Successful checkout receipt**: Immutable original outcome, preserving old representation and first-execution voucher without consulting later mutable selections.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Every approved fixed/percentage/cap/rounding example produces the exact expected VND result, with0 negative payable amounts and0 modified item unit prices/line totals.
- **SC-002**: All eight cart mutation categories and every view display totals consistent with their observed eligible rows and voucher, without consuming a use or silently changing quantities/preferences.
- **SC-003**: Every rejected apply/replace/checkout preserves prior cart/selection and consumes0 uses; injected commitment failures leave0 partial order/redemption/reservation/receipt.
- **SC-004**: Every successful voucher-bearing checkout creates exactly1 order and1 redemption and consumes exactly1 eligible use; no concurrent tested outcome exceeds global/personal limits.
- **SC-005**: Every retained-key successful replay returns the original outcome with0 further usage, stock/order/cart/selection mutation, including after voucher expiry and new selection.
- **SC-006**: Every historical discount remains exact after later voucher changes or completion; all pre-voucher receipts retain their original representation and totals.
- **SC-007**: Every successful voucher purchase removes exactly1 chosen entitlement from active inventory and preserves all other permitted grants; duplicate/concurrent redemption consumes it at most once. Every exhausted personal/global limit rejects despite remaining grants.
- **SC-008**: Every protected foreign/unknown ownership case has the same safe rejection and0 unauthorized changes/disclosures; no wallet result exposes another customer's usage.
- **SC-009**: All required synchronized quota, replay and selection/checkout cases execute in the normal verification gate without skipped required scenarios before later implementation is declared complete.

## Assumptions

- Q1 custom wallet/ownership, automatic invalid-selection removal, independent invalid-voucher checkout rejection and atomic successful entitlement cleanup are approved. Q2 definitions/calculation are approved unchanged. Q3 cancellation quota policy is explicitly deferred, not an unresolved prerequisite for this scope.
- Definition rules: trimmed ASCII uppercase canonical codes1–64 characters [A-Z0-9_-], case-insensitive uniqueness; nonblank title, optional description; fixed positive whole VND, nonnegative whole-VND minimum/cap; percentage >0 and <=100 with at most2 decimals, optional nonnegative cap. Validity bounds may be absent; when both exist start<end. Start inclusive/end exclusive. Null global/personal limits unlimited; nonnull limits positive whole counts. Codes support administrative definitions/future claiming only, never current customer input.
- Approved examples:500,000 minus50,000=450,000;10% of500,000=50,000;20% of500,000 capped60,000=60,000; fixed50,000 on30,000 caps30,000;10% of101 yields10 VND. Zero eligible subtotal never redeems even if minimum is0.
- One-use entitlement representation resolves the requested remaining-use safeguard: three administrator grants can support three purchases if both limits permit; spending one removes only one available grant. A personal limit of2 still forbids the third purchase even with3 grants. An unlimited personal limit still requires an available grant for every purchase. Administrative granting/revocation tooling remains external and out of scope.
- Read-time cleanup is an explicit interpretation of the requirement that an ineligible voucher must not remain selected, including external expiry/quota changes. It changes only stale selection, returns an explanatory reason and preserves accepted concurrent changes; no usage reservation or item changes. Failed replacements/checkout roll back and do not run cleanup as a hidden side effect.
- The user's statement that cancellation is absent differs from the reviewed repository, which already has customer cancellation and managed Inventory release. This feature neither removes nor extends it. Voucher quota restoration/retention, entitlement return and cancellation-specific integration/testing remain deferred pending a separate business decision; baseline history stays durable. No future cancellation behavior is promised by unchanged current counters.
- Include an owned administrator-provisioned wallet. Exclude code claiming/redemption, public voucher discovery/application, customer collection workflows, stacking, shop/platform separation, item allocation, category/product promotions, loyalty, best-voucher automation, admin APIs, fees/payments/refunds/shipping and new terminal producers. Existing Inventory admin-supply/public-confirmation deferrals remain.
- Depends on current Cart/Checkout/Order/Inventory/Catalog/authentication described in [repository-context.md](repository-context.md). No production implementation is changed by this specification update. Planning must preserve owning repositories, acyclic service boundaries and atomic checkout with immutable receipt recovery.
- [plan.md](plan.md), [data-model.md](data-model.md), [contracts](contracts/internal-services.md) and [quickstart.md](quickstart.md) now carry these approved decisions into design, including per-use entitlements, remaining grants, shared usage and read-cleanup concurrency. tasks.md remains a later workflow output. No production/test Java, migration, dependency/build/CI, branch switch, commit or push is included.

## Implementation tracking

The scope exclusions above describe the approved specification stage. The subsequent plan/tasks/implement workflow now supplies the application/schema/tests for these requirements; [implementation-validation.md](implementation-validation.md) distinguishes targeted checkpoint evidence, the unrestricted completion run and resolved final-review cases. No approved business rule, admin/payment/cancellation-quota deferral or immutable legacy receipt contract is changed by this status note.
