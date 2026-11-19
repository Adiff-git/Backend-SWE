# Feature Specification: Customer Order Management

**Feature Branch**: Existing `draft/version-1`; no branch created.
**Created**: 2026-11-17
**Status**: Implemented and validated; completion evidence and full-run/targeted-rerun details are in [implementation-validation.md](implementation-validation.md). Completed 2026-11-18.
**Input**: Manage existing customer orders in Backend-SWE; no application implementation.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Browse my orders (Priority: P1)

As a customer, I identify my purchases and find orders in a particular state.

**Why this priority**: Private order visibility enables all other order operations.
**Independent Test**: Prepare orders for two customers in all five states, including timestamp ties; browse without an order-creation API.

**Acceptance Scenarios**:

1. **Given** no owned orders, **When** listing, **Then** return empty items and zero totals without creating data.
2. **Given** multiple customers' orders, **When** listing without a filter, **Then** return only the current customer's orders across all statuses.
3. **Given** each of the five valid statuses, **When** filtered, **Then** ownership/status filtering precedes pagination/counting; unmatched filters return an empty page.
4. **Given** unchanged orders with timestamp/amount ties, **When** traversing supported sorts at sizes 1, 20 and 100, **Then** each matching order appears exactly once with accurate filtered totals.
5. **Given** a beyond-last page, **When** listed, **Then** return empty items with matching totals preserved.
6. **Given** invalid pagination, status or sort input, **When** listed, **Then** return validation failure without changes.
7. **Given** more rows than the approved preview limit, **When** listed, **Then** return the summary with accurate counts/truncation and without full address or item detail.

### User Story 2 - Inspect historical details (Priority: P1)

As a customer, I see what I purchased and where it was addressed despite subsequent profile/catalog changes.

**Why this priority**: Purchase history must remain trustworthy and private.
**Independent Test**: Prepare snapshots, change profile/catalog values and visibility, then view the historical order.

**Acceptance Scenarios**:

1. **Given** an owned order, **When** viewed, **Then** return all detail fields and every historical item in deterministic item-ID order.
2. **Given** changed prices, product names, SKU/options or customer address, **When** viewed, **Then** stored recipient/item/amount snapshots remain unchanged.
3. **Given** unpublished/unavailable or removed catalog records/images, **When** viewed, **Then** the order and snapshots remain visible, with missing optional references/images null.
4. **Given** unknown or foreign orders, **When** read or mutated, **Then** return the same safe not-found outcome without disclosure or changes.

### User Story 3 - Cancel an eligible order (Priority: P2)

As a customer, I cancel an order when the approved policy permits it.

**Why this priority**: Customers need a bounded way to stop eligible purchases.
**Independent Test**: Prepare each status and exercise the approved cancellation matrix without checkout.

**Acceptance Scenarios**:

1. **Given** an owned PENDING or CONFIRMED order, **When** cancelled, **Then** status becomes CANCELLED and cancelledAt is recorded atomically, retaining the order and snapshots.
2. **Given** an incompatible state, **When** cancellation is requested, **Then** conflict leaves every field unchanged.
3. **Given** CANCELLED, **When** cancellation repeats, **Then** return 204 success without changing the first timestamp or reason.
4. **Given** action input, **When** validated, **Then** apply the optional-reason input contract below and reject invalid input without changes.
5. **Given** SHIPPING or COMPLETED, **When** cancellation is requested, **Then** return 409 with every field unchanged.
6. **Given** synchronized cancellations of one owned PENDING or CONFIRMED order in independent transactions, **When** both finish, **Then** exactly one initial transition is committed; the other returns idempotent 204 after the winning cancellation is visible (a separate recognized concurrency failure may return 409), with one unchanged winning timestamp/reason and intact historical snapshots.
7. **Given** an order with any status, **When** a customer attempts to confirm receipt or set an arbitrary status, **Then** no supported customer operation performs that transition and no order data changes.

### Edge Cases

- Missing/invalid/expired bearer token, revoked/expired session, missing/inactive account: established 401. Valid identity lacking permission: 403. Current grants apply on the next request.
- Unknown/foreign order outcomes match on every route. Authentication/authorization precede business access.
- Reject blank/repeated/unknown query fields, invalid identifiers, numeric overflow, signs, decimals, exponent forms, whitespace and leading zeroes except zero.
- Stable offset ordering applies to unchanged data; concurrent changes can shift pages. No cross-request snapshot is promised.
- Missing/deleted/unavailable images or image-access preparation failures yield null image access and never prevent historical viewing.
- Reads do not change business data. Rejected actions preserve all status/timestamps/snapshots/reason fields. A lost response does not prove rollback.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Provide only current-customer list, detail and cancellation; derive owner from authenticated identity and reject client owner overrides.
- **FR-002**: Use feature-local `MANAGE_ORDER`, adapting MANAGE_CART/MANAGE_WISHLIST naming and USER assignment policy. Existing/future USER assignments inherit it; custom roles need an explicit grant. Unrelated permissions or ROLE_ADMIN alone do not grant access or bypass ownership. No admin endpoints.
- **FR-003**: Enforce ownership on reads and writes; unknown/foreign identifiers return identical safe 404 outcomes.
- **FR-004**: Support exactly PENDING, CONFIRMED, SHIPPING, COMPLETED, CANCELLED. No generic status setter or progression into CONFIRMED/SHIPPING is provided.
- **FR-005**: Omitted status returns all owned orders; one exact uppercase status filters before pagination/counting. Blank, null-like text, lowercase, unknown and repeated values are invalid.
- **FR-006**: Default page 0, size 20; canonical integer page 0..2147483647 and size 1..100. Return `items`, `page`, `size`, `totalItems`, `totalPages` in an application-owned page representation. Count matching owned/status-filtered orders first as totalElements (public totalItems) and calculate offset using long arithmetic before multiplication. If offset >= totalElements, return an empty page with accurate requested page/size and filtered totals, without executing the paginated content or preview query. If offset > Integer.MAX_VALUE and offset < totalElements, use an explicit long-offset query path preserving the same filtering/sorting/metadata; never narrow, wrap, clamp, reject an otherwise valid page solely for its offset, or pass an invalid offset to JPA. Zero results mean zero totalPages. Preserve the existing totalPages representation/overflow safeguard described in contracts/api.md.
- **FR-007**: Allow one `sort` value: `placedAt,asc`, `placedAt,desc`, `payableTotal,asc` or `payableTotal,desc`. Default placedAt descending; append order ID ascending as unique tie-breaker. Reject unsupported fields/directions, repeated sorts and unknown parameters; never expose arbitrary persistence-property sorting.
- **FR-008**: Return separate summary/detail representations defined below, using the confirmed distinct-row count and first-three-row preview.
- **FR-009**: Historical recipient/product/SKU/options/price snapshots and totals never follow later profile/catalog changes. Order viewing never requires current purchasability/publication.
- **FR-010**: Preserve orders/items when catalog records are removed; live references may become null while snapshots remain. No catalog/account deletion cascade may erase history. Broader account retention/anonymization is excluded; restrict deletion where necessary to retain historical ownership.
- **FR-011**: Use exact nonnegative whole-VND monetary values with established `{amount, currency: "VND"}` representation. Positive integer quantities; stored lineTotal equals unitPriceSnapshot times quantity; merchandiseTotal equals sum of line totals. Stored payableTotal remains authoritative, with no current-price recalculation or invented fees/discounts.
- **FR-012**: Allow cancellation only from PENDING or CONFIRMED, with an optional reason; successful cancellation sets CANCELLED/cancelledAt atomically, retains history and performs no inventory release, refund or external notification. For an otherwise eligible first cancellation, if the current Clock sample precedes placedAt, return HTTP 409 without changes. Classify already-CANCELLED first: valid repeats remain 204 without sampling the clock or altering the original timestamp/reason.
- **FR-013**: Defer customer receipt confirmation and all SHIPPING-to-COMPLETED/received transitions to a later feature. Provide no confirm-receipt endpoint, generic status mutation or placeholder. SHIPPING and COMPLETED remain readable/filterable fixture states; completedAt is historical data only.
- **FR-014**: Validate owner/current state before mutation and protect the complete state/timestamp change against competing requests. Rejections change nothing; recognized competing-state conflicts return 409. Planning selects transaction/concurrency mechanisms.
- **FR-015**: Repeated cancellation of an owned CANCELLED order returns 204 success without refreshing cancelledAt, replacing cancellationReason or rewriting snapshots. Validate authentication, permission, ownership and request shape on repeats; invalid repeated requests still return the applicable error.
- **FR-016**: Use established safe ProblemDetail errors and private no-store responses; never expose infrastructure internals or foreign data.
- **FR-017**: Prepare existing orders through valid fixtures/test data, never demo migration inserts. No excluded-domain placeholders or unused creation services.

### Status Meanings and Allowed Customer Transitions

| Status | Meaning | Customer action |
| --- | --- | --- |
| PENDING | Exists and awaits confirmation | Cancel to CANCELLED |
| CONFIRMED | Accepted | Cancel to CANCELLED |
| SHIPPING | Being delivered; tracking is not implemented | Read/filter only; receipt confirmation deferred |
| COMPLETED | Receipt confirmed | Read/filter only |
| CANCELLED | Cancelled | Repeated cancellation returns 204 without changes |

Only PENDING-to-CANCELLED and CONFIRMED-to-CANCELLED are included. Customer receipt confirmation is deferred; no SHIPPING-to-COMPLETED transition is exposed. Terminal states cannot be reversed. No DELIVERED/payment states.

### Summary and Detail Definitions

| Representation | Fields and meaning |
| --- | --- |
| Summary | id; immutable unique orderNumber; placedAt; status; stored payableTotal; itemCount; itemPreview; remainingItemCount |
| Detail | id; orderNumber; placedAt; status; recipientNameSnapshot; recipientPhoneSnapshot; deliveryAddressSnapshot (complete stored address text); all items; stored merchandiseTotal/payableTotal; cancelledAt/completedAt; nullable cancellationReason |
| Complete item | id; nullable productId/productVariantId; productNameSnapshot; variantSkuSnapshot; variantOptionsSnapshot (option-name/value map); quantity; unitPriceSnapshot; lineTotal; nullable primaryImageUrl |

Confirmed: itemCount counts distinct order rows regardless of quantity; first 3 rows by item ID as preview; preview fields item ID, historical product name, SKU/options, quantity and nullable primaryImageUrl; remainingItemCount counts omitted rows, zero if untruncated. remainingItemCount = max(itemCount - 3, 0). These preview/count decisions are approved.

Product/variant references retain Backend-SWE UUID types; customer IDs remain numeric. Order/item ID choice is local planning work, never a new global identifier policy or a copy of ProjectCV's catalog IDs. Valid fixtures have at least one item and complete snapshots. placedAt/orderNumber are immutable; number generation and creation workflows are excluded. cancelledAt exists exactly for CANCELLED, completedAt exactly for COMPLETED, and neither precedes placedAt; other states have null terminal timestamps.

Images are best-effort current authorized catalog/media access, not guaranteed historical image snapshots. Never issue current hidden-product URLs, expose storage keys or persist temporary signed URLs. Return null on absence/removal/unpublication/access-preparation failure without hiding history.

### HTTP and Input Outcomes

| Surface | Success |
| --- | --- |
| GET /api/me/orders | 200 paginated summaries |
| GET /api/me/orders/{orderId} | 200 detail |
| POST /api/me/orders/{orderId}/cancel | 204, no body, including valid repeats on owned CANCELLED orders |

Synchronous 204 follows the brief's suggested empty success contract, a documented standards-compliant exception to the constitution's normal 200/202 business-action preference.

400 invalid identifiers/query/body under an accepted media type; 401/403 existing authentication/permission contract; 404 identical unknown/unowned; 409 incompatible owned state, recognized concurrency conflict, or current clock time preceding the order's placedAt during an otherwise eligible first cancellation; 415 unsupported request media type, including a nonempty cancellation body with missing/incompatible JSON Content-Type. Nonempty cancellation bodies accept application/json and compatible application/*+json; an absent/zero-byte body requires no Content-Type. All errors use safe established ProblemDetail behavior; rejected operations leave data unchanged. Unexpected failures use safe established 500 behavior. A valid already-CANCELLED repeat remains 204 regardless of the current clock, after normal access/input validation.

Detail/cancellation accept no query parameters. List/detail accept no body. Empty query values never trigger omitted defaults.

Optional reason contract (documented specification defaults): cancellation accepts an absent/zero-byte body, `{}`, or an object containing only `reason`. Omitted reason or explicit null means no reason. A string is trimmed; blank becomes no reason; nonblank text is stored with a maximum of 500 Unicode code points after trimming. Reject longer strings, non-string/non-null values, duplicate or unknown fields, owner/status overrides, top-level null/arrays/scalars, whitespace-only bodies and malformed JSON with 400 and no changes. Detail exposes the stored value as nullable cancellationReason. Only the successful initial cancellation records the reason; repeats never replace it.

### Confirmed Clarifications (2026-11-17)

- **Q1**: Both PENDING and CONFIRMED orders can be cancelled; reason is optional. Reason parsing/length rules above are explicit specification defaults rather than individually approved choices.
- **Q2**: Customer receipt confirmation is deferred. This feature must not let a customer change SHIPPING to COMPLETED or received, and must not add a placeholder for that operation. Existing COMPLETED orders remain viewable/filterable.
- **Q3**: Count distinct rows; preview first three rows with historical name, SKU/options, quantity and optional image; report remaining rows. Require synchronized competing-cancellation tests using independent transactions.

### Repeated-Cancellation Decision (2026-11-17)

- **Confirmed**: Repeated cancellation of an owned CANCELLED order returns 204 success. Preserve the original cancelledAt and cancellationReason, including when a repeat supplies a different valid reason. Authentication, permission, ownership and input validation still apply; no mutation or excluded side effect is performed.

### Analysis Remediation Decisions (2026-11-17)

- **U1**: Count first; compute long offset; return accurate empty pages without paginated/preview queries when offset >= totalElements. Explicitly support nonempty offsets beyond Integer.MAX_VALUE without invalid JPA offsets. Preserve page range, response shape and sorting; verify boundaries/overflow without billion-row fixtures.
- **I1**: Explicit 415 media-type failures and 409 pre-placement clock failures are part of the error catalogue. Clock checks apply to eligible first cancellations; already-CANCELLED repeats stay idempotent.

### Key Entities *(include if feature involves data)*

- **Order**: One customer's historical purchase with immutable number/placement/recipient/amount snapshots, current state and terminal timestamps; nullable optional cancellation reason.
- **Order item**: Historical row with stable identity, optional live catalog references and independent product/SKU/options/quantity/price information.
- **Customer identity**: Existing authenticated account/current grants; never proven by client-supplied ownership fields.

### Dependencies, Exclusions and Verification

Depend on existing current-state identity/authorization, catalog/media service boundaries and PostgreSQL fixture infrastructure. Future creation must supply valid snapshots but is not needed for fixture-driven acceptance.

Exclude admin order management, order-creation API, checkout/cart conversion, inventory validation/deduction/reservation/release, payment/status/refunds, shipping entities/carriers/tracking, shops, vouchers/discount workflows, returns/exchanges/deletion, notifications, customer receipt confirmation and automatic progression. No placeholder code or excluded infrastructure.

Verify all three stories, each status, empty/no-order pages, supported sort directions/ties, invalid inputs, summary/detail separation, historical changes/unavailability/deletion, missing images, every ownership boundary, current permission grant/revocation, approved/rejected/repeated actions, unchanged rejected mutations and atomic terminal timestamps. Synchronized competing-cancellation verification with independent transactions is required; sequential repetitions do not prove competing-request safety. Verify no receipt-confirmation route or customer completion capability is introduced.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: With 125 owned and 25 foreign orders, all five filters and supported sorts traverse exactly the owned matching set once at sizes 1, 20 and 100 on unchanged data, with exact totals. Boundary tests prove count-first empty-page short-circuit at offset=totalElements and offset>totalElements, exact widened arithmetic through page=2147483647/size=100, and correct nonempty dispatch below/at/above Integer.MAX_VALUE without narrowing or invalid JPA offsets; synthetic counts are allowed for impractically large datasets.
- **SC-002**: Every foreign/unknown detail/mutation pair has the same safe not-found outcome, zero protected disclosure and zero changes.
- **SC-003**: Every historical-data scenario preserves 100% of recipient/item/amount snapshots after profile/catalog changes or catalog/image removal.
- **SC-004**: Every approved transition records exactly its matching terminal timestamp; rejected actions change zero fields; valid repeats on owned CANCELLED orders return 204 and preserve the first timestamp and reason.
- **SC-005**: Every summary excludes full address/item detail and respects approved preview/count bounds; every detail includes all historical rows without requiring images.
- **SC-006**: All three workflows complete with existing orders without checkout/inventory/payment/shipping services or a client owner override.

## Assumptions

- Confirmed scope/ownership/status/history/atomicity decisions come from the brief. Q1–Q3 and repeated-cancellation answers above are confirmed; no business clarifications remain.
- Pagination/sort rules are feature-local specification defaults using bounded private pagination conventions; they do not alter existing endpoints.
- MANAGE_ORDER/USER rollout adapts the existing permission policy; the permission is not claimed to exist today.
- Constitution establishes no mandatory global concurrency-test policy; the user explicitly requires synchronized competing-cancellation coverage for this feature.
- Repository evidence and requested engineering constraints are in [repository-context.md](repository-context.md). No application checks were run for this document-only draft.



## Subsequent order-creation scope

The creation/checkout/number-generation exclusions above apply to this historical read/cancel increment. [Customer Checkout](../009-customer-checkout/spec.md) separately authorizes specifying one PENDING order from eligible selected cart rows with owned address and historical snapshots, atomic cart consumption and idempotent recovery. It will define order-number generation during planning. Existing cancellation, historical reads and deferred receipt confirmation remain unchanged. Checkout policies are pending clarification; this specification step adds no production creation service.
