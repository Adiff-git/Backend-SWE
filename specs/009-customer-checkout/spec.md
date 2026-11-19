# Feature Specification: Customer Checkout

**Feature Branch**: `draft/version-1` (existing branch; no branch hook configured)

**Created**: 2026-11-18

**Status**: Implemented and verified (see implementation-validation.md)

**Input**: Specify single-vendor checkout of eligible selected cart items to an owned delivery address, with historical order snapshots, partial checkout, atomic cart changes and durable idempotent retries. Production implementation follows separately.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Place an order from my selected items (Priority: P1)

As an authenticated customer, I place one order using selected eligible cart rows and my chosen saved delivery address, so I can purchase at current prices without losing unrelated items.

**Why this priority**: Establishes the complete single-vendor purchase entry point.

**Independent Test**: Prepare an owned address and selected eligible cart rows; place an order and compare the committed order, response and remaining cart without payment, stock or shipping workflows.

**Acceptance Scenarios**:

1. **Given** all selected rows are eligible, **When** checkout succeeds, **Then** exactly one PENDING order includes every selected row at its unchanged quantity, those cart rows alone disappear, and the response identifies the order with no skipped rows.
2. **Given** selected and unselected rows, **When** checkout succeeds, **Then** unselected rows retain their identities, variants, quantities, selections and snapshots.
3. **Given** included quantities 1 and 999, **When** checked out, **Then** each quantity is preserved exactly; a quantity outside that range is never ordered or silently corrected.
4. **Given** the current price differs from the previously displayed cart price, **When** checked out, **Then** the current authoritative price becomes the immutable unit-price snapshot and determines exact totals.
5. **Given** an invalid, missing or unowned address, **When** checkout is requested, **Then** it fails without changing orders or cart; unknown and unowned addresses are indistinguishable.
6. **Given** a completed checkout, **When** its customer uses the existing order list, PENDING filter and detail views, **Then** the same order and all historical item/address/amount snapshots are available.
7. **Given** later address edits/deletion or catalog name/SKU/options/price changes, unpublication or deletion, **When** the order is viewed, **Then** historical snapshots and totals remain intact under the existing order-history rules.

### User Story 2 - Purchase available selections without losing skipped items (Priority: P1)

As a customer with mixed selections, I purchase eligible items and receive safe explanations for the selected items left behind.

**Why this priority**: Partial checkout is an explicit business requirement, not an optional fallback.

**Independent Test**: Prepare one eligible selected row, independently ineligible selected rows and an unselected row; compare the resulting order and exact retained rows.

**Acceptance Scenarios**:

1. **Given** mixed eligible/ineligible selected rows, **When** checkout succeeds, **Then** only eligible rows enter the order; every skipped selected row stays unchanged and is reported once with its cart-row ID and a safe reason code.
2. **Given** missing product/variant, non-PUBLISHED product, invalid current selling price or invalid stored quantity, **When** evaluated, **Then** the affected selected row is skipped for its business reason without exposing hidden catalog details.
3. **Given** an empty/missing cart, no selected rows or all selected rows ineligible, **When** checked out, **Then** no empty order is created and no cart row changes; the rejection distinguishes absence of eligible selections from infrastructure failure.
4. **Given** a database/dependency failure or unexpected exception while evaluating a selected row, **When** checkout runs, **Then** the operation fails atomically instead of treating that row as a business skip.
5. **Given** a persistence failure after order/item preparation or cart deletion begins, **When** checkout fails, **Then** no order, orphan item, lost cart row or successful idempotency result remains.

### User Story 3 - Retry safely and retain concurrent edits (Priority: P1)

As a customer whose response is lost or whose cart is open on multiple devices, I recover the original outcome without duplicate ordering or losing later edits.

**Why this priority**: Duplicate purchases and lost cart contents violate the checkout guarantee.

**Independent Test**: Start with prepared cart/address data; exercise successful replay, mismatched commands, lost responses and genuinely overlapping operations, comparing durable orders, receipts and cart contents.

**Acceptance Scenarios**:

1. **Given** successful checkout, **When** the same customer repeats the same command/key while retained, **Then** the original successful outcome is recovered before reading the changed/empty cart; no repricing, new order or further deletion occurs.
2. **Given** the same retained key but a different addressId, **When** retried, **Then** a conflict occurs without changes; changing address contents or cart contents alone does not change the command fingerprint.
3. **Given** two customers use an identical key, **When** each checks out, **Then** outcomes are independently scoped and neither can read the other's result.
4. **Given** overlapping same-customer requests with the same key, **When** they complete, **Then** at most one order commits; an unfinished original returns 409 CHECKOUT_IN_PROGRESS with client-directed retry.
5. **Given** a committed checkout whose HTTP response was lost, **When** retried with its retained key, **Then** its original order reference and skipped-row outcome remain recoverable.
6. **Given** simultaneous checkout requests with different keys against the same rows, **When** they complete, **Then** a removed row is ordered at most once; later eligible additions may form a separate checkout only when represented in its consistent view.
7. **Given** checkout overlaps quantity replacement, variant change, selection, add or removal, **When** operations complete, **Then** no accepted cart change disappears unless its exact row/variant/quantity was represented in a committed order; losing operations may safely conflict without partial mutation.
8. **Given** authorization is revoked or the account/session becomes ineligible after success, **When** a replay is requested, **Then** current authentication/permission checks reject access before returning the stored owned result.

9. **Given** a failed validation or rolled-back checkout with no successful receipt, **When** the corrected/current command reuses that key, **Then** it is evaluated as a new attempt and may succeed without recovering a nonexistent order.
10. **Given** a successful retained receipt and later order cancellation, **When** replayed, **Then** HTTP 201, the original Location, original PENDING receipt and original skipped outcome are returned; current owned order detail shows CANCELLED.
11. **Given** a valid case-sensitive key at lengths 16 and 128, **When** submitted, **Then** it passes key validation; lengths 15/129, whitespace, non-ASCII, invalid characters or multiple header values fail with 400. Keys differing only in letter case are distinct.
12. **Given** an old successful key during its order lifetime, **When** replayed, **Then** age alone never expires or repurposes it; a different command still conflicts.
13. **Given** a committed successful receipt, **When** identical replays overlap, **Then** each returns the original201/Location/body without acquiring a mutation fence or creating an order, consuming cart rows, repricing or invoking stock/voucher operations.
14. **Given** an initial receipt miss, **When** the original commits before the fence is acquired or before a busy-fence receipt recheck, **Then** the fresh committed receipt is returned as201 instead of CHECKOUT_IN_PROGRESS; mismatched commands retain their409 contract.
15. **Given** the original still holds the fence without a committed receipt, **When** a competing request misses both receipt checks, **Then** it returns409 CHECKOUT_IN_PROGRESS without executing checkout.
### Edge Cases

- Omitted/null/empty/malformed addressId or key, duplicate/unknown JSON fields, prohibited owner/prices/status fields, wrong types, extra query parameters and unsupported media types cannot trigger partial mutation.
- Zero is a valid whole-VND price; negative, fractional, missing or nonfinite prices are ineligible. Positive quantity never implies stock availability.
- Multiple ineligibility causes use deterministic safe reason precedence; no hidden product name/options/image is disclosed.
- A changed/deleted address after success cannot invalidate an otherwise authorized replay; a new attempt must validate an owned current address. Initial address capture must represent one coherent saved address, never mixed fields from concurrent updates.
- Missing optional images do not invalidate a purchase or alter durable snapshots; durable receipts must not contain expiring URLs as identifiers.
- Monetary arithmetic or representation bounds must be validated before commit; failure creates no order and deletes no rows.
- Cancellation after checkout does not re-add cart rows, release stock or refund payment. Replay does not reset order status or undo cancellation.
- Rejected or rolled-back initial attempts leave their keys reusable against current state. Successful receipts have no time-based expiry or key reuse during the order lifetime; processing requests return 409 CHECKOUT_IN_PROGRESS.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Resolve customer solely from existing authenticated identity, checking current active account/session and current permissions on every request including replay. Never accept an owner override or disclose another customer's cart, address or outcome.
- **FR-002**: Follow established feature permission naming/default USER assignment and explicit custom-role grants. Require MANAGE_CHECKOUT, assigned to existing/future USER role assignments; custom roles require an explicit grant. Checkout requires no additional MANAGE_CART, MANAGE_ADDRESS or MANAGE_ORDER grant. ROLE_ADMIN alone never bypasses permission or ownership; existing order routes retain their own MANAGE_ORDER gate.
- **FR-003**: Require one addressId of the existing positive numeric Long identifier type and a checkout idempotency key. Resolve an owned saved address; no implicit default-address fallback for omitted/null input. Unknown and foreign addresses use identical safe not-found behavior.
- **FR-004**: Consider stored selected cart rows only. An included row requires an existing variant and product, PUBLISHED product, integer quantity 1–999 and valid current nonnegative whole-VND selling price. Do not evaluate inventory.
- **FR-005**: Include every eligible selected row in one order, preserving quantity exactly. Skip and preserve business-ineligible selected rows; preserve every unselected row unchanged. Never use stale cart display snapshots as authoritative current purchase data.
- **FR-006**: Classify skipped rows with safe stable codes: VARIANT_UNAVAILABLE, PRODUCT_UNAVAILABLE, PRODUCT_NOT_FOR_SALE, INVALID_QUANTITY or INVALID_PRICE. Evaluate missing references before publication, then quantity, then price; return one first-applicable reason per row. When a missing variant prevents parent resolution, report VARIANT_UNAVAILABLE; report PRODUCT_UNAVAILABLE only when a known parent reference cannot resolve. Do not reveal unpublished metadata.
- **FR-007**: No eligible selected rows means rejection without any empty order, deletion or successful receipt. Infrastructure/unknown failures abort checkout; they are never converted into skips or apparent success.
- **FR-008**: Use current authoritative prices within a consistent checkout view. Line total equals unit-price snapshot times quantity; merchandise total equals the exact sum; payable total equals merchandise total. Preserve existing explicit VND money representation and exact zero-fraction arithmetic without rounding. Validate all arithmetic/storage/response bounds before commit.
- **FR-009**: Create exactly one owned order with unique immutable order number, absolute placed timestamp, PENDING status, recipient-name/phone/full-address snapshots, all included item rows and stored totals. cancelledAt/completedAt/cancellationReason initially remain null.
- **FR-010**: Each item stores historical product name, SKU, options, purchased quantity, unit price and line total, with appropriate existing product/variant references. Preserve existing order-history behavior when catalog/address records change or disappear. Do not persist temporary image URLs.
- **FR-011**: Commit order creation, every item, included cart-row deletion and successful idempotency outcome as one all-or-nothing operation. Delete only rows/variants/quantities actually represented by that committed order. Retain the persistent cart, including when empty.
- **FR-012**: Coordinate checkout with existing cart mutations and competing checkouts. Cart selection/content and catalog eligibility/prices must form a documented consistent purchase view; address snapshots must be coherent. No lost accepted quantity/variant/selection/add/remove change or duplicate consumption is permitted. Planning selects coordination/fetch details and documents conflict behavior, without increasing isolation or adding automatic retries by default.
- **FR-013**: Scope keys to authenticated customer plus checkout operation. Fingerprint the validated client command (addressId), never mutable cart state, address contents, prices or generated values. Same retained key/different command conflicts; customer scopes remain independent.
- **FR-014**: Durably recover original order reference and skipped-row outcome after success/lost response. Resolve successful replay before evaluating current cart or current address existence, while retaining current authentication/permission/owned-result checks. Replay never reprices, consumes new rows or changes the order. Check for a committed successful receipt before acquiring the per-key fence. On an initial miss, try the fence and recheck the committed receipt after acquisition; if the fence is busy, recheck the committed receipt before returning CHECKOUT_IN_PROGRESS. Every matching receipt hit returns the original immutable201 outcome; a different successful command still returns CHECKOUT_KEY_CONFLICT409. Processing409 requires a failed fence attempt and no committed receipt visible on the subsequent check. Only a fence holder with no successful receipt may execute checkout. Concurrent identical keys commit at most one order; different keys still coordinate cart consumption.
- **FR-015**: Require exactly one case-sensitive ASCII key matching `[A-Za-z0-9_-]{16,128}`, without trimming. Retain successful receipts for the order lifetime with no time-based expiry or successful-key reuse. An unfinished original returns 409 CHECKOUT_IN_PROGRESS; clients decide whether to retry. Rejected/rolled-back initial attempts do not reserve the key and may be retried against current state.
- **FR-016**: Return an immutable stored purchase receipt containing the original order ID/number, placedAt, initial PENDING status, historical recipient/items/totals and original skipped-row IDs/reasons. Return 201 Created and the same owned-order Location on first success and successful replay, regardless of later order status. Omit image URLs from the durable receipt. Apply the exact error catalogue and MANAGE_CHECKOUT policy below.
- **FR-017**: Use existing private response/error and historical order-summary/detail responsibilities. First success and successful replay return 201 with Location referencing owned order detail; return created-order information and original skipped-row outcomes. Malformed/unauthorized/rejected/failed operations never return apparent success.
- **FR-018**: Errors use safe existing ProblemDetail handling, revealing no SQL, stack traces, secrets or hidden catalog/address details. Responses containing private order/address/skipped data must be private and non-cacheable under established order/cart conventions.
- **FR-019**: Existing list, PENDING filter and owned detail must show created orders. Keep cancellation eligibility/repeat behavior unchanged; introduce no customer receipt confirmation or automatic CONFIRMED/SHIPPING/COMPLETED progression.
- **FR-020**: Require deterministic synchronized independent-transaction tests for same-key requests, different-key cart consumption, checkout versus every cart mutation category (add, quantity replacement, variant change, individual/bulk selection and removal), and coherent address/catalog publication/price changes. Include concurrent completed replays, commits between initial lookup and fence acquisition, commits before busy-fence rechecks and genuinely in-progress originals; assert zero duplicate orders/repeated cart consumption and no stock/voucher calls or mutations, without adding excluded implementations. Execute these tests in the normal integration verification gate before feature completion; sequential retries do not establish overlap safety. This is feature-local, not a new global testing policy.
- **FR-021**: Respect current domain/service/validator/converter responsibilities, repository ownership, lazy use-case fetches, custom errors and additive migrations. No separate Checkout aggregate merely to copy a multi-order design; durable idempotency data is justified by FR-013–015. No generic workflow engine, messaging or distributed transaction infrastructure.

### Request, result and error decisions

Confirmed interface: POST `/api/me/cart/checkout`, existing Bearer authentication, required `Idempotency-Key`, JSON body containing only `addressId`. This is the only new customer operation; no preview endpoint. Required address omission/null/empty are invalid rather than defaulting; prohibited fields are rejected. Apply the exact key policy in FR-015; missing, repeated or malformed key headers return 400.

Confirmed error catalogue: 400 malformed/missing/invalid address/key/body/query; 401 existing invalid authentication/account/session; 403 missing checkout grant; 404 identical unknown/unowned address; 409 key-command mismatch, processing/recognized concurrent conflict; 422 no eligible selected items or checked amount bounds exceeded; 415 missing/incompatible JSON media type for the required body; safe established 500 unexpected persistence/infrastructure failure, preserving any existing more-specific dependency-unavailable status.

The result is the immutable purchase receipt specified in FR-016, with Location pointing to current owned order detail. Successful replay returns the original receipt and 201, including the original PENDING status even after cancellation; the current detail route reflects later status. The receipt remains recoverable after the original address changes or disappears. No expiring image URL is stored or required.

### Key Entities *(include if feature involves data)*

- **Customer/cart/selected row**: Existing owned persistent cart and row identities, stored selection, variant and quantity; only successfully ordered rows are consumed.
- **Delivery address**: Existing owned numeric ID and recipient name/phone/address text, captured coherently into history.
- **Catalog purchase state**: Current publication, variant identity/name/SKU/options and valid authoritative price, independent of stock.
- **Order and order item**: Existing historical aggregate with numeric Long IDs, optional UUID catalog references, exact VND totals and immutable purchase/address snapshots.
- **Idempotency outcome**: Customer/operation/key, command fingerprint and durable original order reference/skipped outcome, retained for the order lifetime, with processing conflicts and reusable unsuccessful keys as specified in FR-015. This is a required recovery concept, not a separate multi-order checkout model.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Every successful all-eligible or mixed checkout creates exactly one PENDING order; 100% of eligible selected rows appear at their original quantities and every skipped/unselected row remains unchanged.
- **SC-002**: In every boundary/current-price scenario, quantities 1 and 999 are preserved and each total exactly matches current whole-VND prices times included quantities, with zero fees/discounts and zero rounding discrepancies.
- **SC-003**: Every authorized successful-key replay within the approved retention policy returns the original order reference and identical skipped outcomes, with zero additional orders, repricing or cart consumption, including after lost responses and subsequent cart/address changes.
- **SC-004**: Every ownership/permission test denies unauthorized access without disclosing foreign address/cart/order/receipt data or mutating state.
- **SC-005**: Every injected pre-commit failure leaves zero partial orders/orphan items/successful receipts and preserves all original cart rows; competing operations commit no duplicate consumption or lost accepted edits.
- **SC-006**: Every created order is discoverable through the existing list/PENDING filter/detail; all purchase/address snapshots remain unchanged after later catalog/address modifications or deletion.
- **SC-007**: Customers can place and recover a purchase using only an owned address, selected cart rows and a key, without supplying prices/owner/status or invoking inventory, payment, vouchers, shipping or preview workflows.

## Assumptions

- This specification adds checkout creation; earlier cart/order exclusions describe the boundaries of those previous feature increments, not a permanent prohibition. Their source specs link to this feature.
- Existing code, rather than stale implementation-status prose in earlier specs, establishes current integration availability. Cart and order behavior exists in the working tree; this step does not implement checkout.
- Existing user/order/cart identities are Long; product/variant identities are UUID; addresses contain recipientName, recipientPhone and addressText. Product PUBLISHED and valid nonnegative integral price establish current sale eligibility; free items are valid.
- Dependencies: current-state bearer identity/session, owned address access, selected cart state, catalog prices/publication and historical order storage/read services. Planning must preserve acyclic service boundaries and the constitution; no new dependencies are preapproved.
- Exclude stock checks/reservation/deduction/release, vouchers/promotions/discounts, payment methods/records/processing/refunds, shipping fees/shipments/tracking, multi-vendor grouping, guest checkout, separate preview, notifications and receipt confirmation. No excluded placeholders.
- Key lifecycle, immutable receipt/201 replay, exact errors, MANAGE_CHECKOUT and normal-gate synchronized concurrency tests were explicitly approved together; no remaining clarification blocks planning.

## Clarifications

### Session 2026-11-18

- **Q1: A — Idempotency lifecycle**: Case-sensitive ASCII `[A-Za-z0-9_-]`, 16–128 characters, no trimming; retain successful receipts for the order lifetime without expiry/reuse; unfinished original returns 409 CHECKOUT_IN_PROGRESS; rejected/rolled-back initial attempts leave the key reusable against current state.
- **Q2: A — Result, errors and permission**: Immutable stored receipt; 201 and owned-order Location on first success and replay; confirmed error catalogue; MANAGE_CHECKOUT assigned to USER, explicit grants for custom roles, no additional cart/address/order permission gate on checkout. Current order routes keep their existing authorization.
- **Q3: A — Concurrency verification**: Deterministic synchronized independent-transaction tests cover same-key, different-key, every cart mutation category and coherent address/catalog eligibility/price changes; execute in normal integration verification.
### Accepted analysis remediation — C1 and I1

Move the existing AddressServiceIT responsibility to user/service during implementation, preserving every assertion and coverage, with no duplicate class. Receipt-first lookup, post-acquisition recheck and busy-fence recheck are mandatory; current authorization and successful-command mismatch checks apply at every receipt hit. Synchronized race proofs remain in the normal integration gate. These refinements preserve all approved business rules; stock/voucher operations remain excluded, not newly introduced for tests.
