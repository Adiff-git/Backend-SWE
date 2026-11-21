# Feature Specification: Inventory Management

**Feature Branch**: `feature/cart` (unchanged; feature directory is independent of branch)
**Created**: 2026-11-19
**Status**: Implemented; verification and remaining operational limits are recorded in [implementation-validation.md](implementation-validation.md).
**Input**: Specify variant physical stock and order-owned reservations integrated with existing checkout and order lifecycle in Backend-SWE. Implementation follows the approved Plan/Tasks/Implement workflow; see the execution record.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Purchase only fully reservable quantities (Priority: P1)

As a customer, I purchase selected quantities that can actually be allocated to my order, without overselling or losing rows I could not purchase.

**Why this priority**: Reliable stock commitment is necessary at the existing purchase entry point.
**Independent Test**: Prepare known balances, published variants, an owned address and selected/unselected rows; checkout and replay, comparing orders, reservations, stock and retained cart rows.

**Acceptance Scenarios**:

1. **Given** onHand=10/reserved=0/available=10, **When** purchasing3, **Then** one PENDING order owns an ACTIVE reservation of3, onHand=10/reserved=3/available=7.
2. **Given** available=3 and requested3, **When** checkout succeeds, **Then** reserve all3 and leave available=0; never lower physical stock at placement.
3. **Given** a selected row requiring4 with only3 available and another fully reservable selected row, **When** checkout runs, **Then** order only the latter and preserve/report the former as INSUFFICIENT_STOCK without reducing its quantity.
4. **Given** missing inventory, zero stock or an existing catalog/price/quantity eligibility failure, **When** evaluated, **Then** skip that selected row with one safe first-applicable reason; never assume unlimited inventory.
5. **Given** no fully reservable selected rows, **When** checking out, **Then** reject without an order, reservation, successful receipt or cart/balance change.
6. **Given** successful checkout and later stock/address/cart/order changes, **When** its accepted key/command is replayed, **Then** return the original immutable201 receipt/Location and change no stock, reservation, order or cart row.
7. **Given** a failure after any reservation, order write, cart removal or receipt preparation, including final commit failure, **When** checkout fails, **Then** roll back all changes and leave its unsuccessful key reusable.
8. **Given** two customers requesting the final available unit concurrently, **When** both finish, **Then** at most one reserves it; the other receives the applicable shortage/no-eligible outcome or recognized concurrency conflict, without negative stock.

### User Story 2 - Cancel without leaking or duplicating reservations (Priority: P1)

As a customer, I cancel an eligible order and release only its allocated units without changing physical stock.

**Why this priority**: Cancellation must restore availability and preserve other orders' commitments.
**Independent Test**: Prepare two orders sharing a variant, cancel one and repeat; compare both orders and all allocations/balances.

**Acceptance Scenarios**:

1. **Given** onHand=10/reserved=7, with this order owning3 and another owning4, **When** this PENDING/CONFIRMED order is cancelled, **Then** onHand=10/reserved=4/available=6 and only its allocation becomes RELEASED.
2. **Given** CANCELLED, **When** valid cancellation repeats, **Then** return204 without another release or changing its first timestamp/reason; authentication, ownership and input validation still apply.
3. **Given** SHIPPING or COMPLETED, **When** customer cancellation is attempted, **Then** return409 and change no order or stock.
4. **Given** a managed order with missing, wrong-owner/item/variant/quantity or non-active reservations, **When** an initial cancellation is attempted, **Then** return a consistency conflict with no partial release or terminal transition.
5. **Given** a legacy pre-inventory PENDING/CONFIRMED order, **When** cancellation succeeds, **Then** retain its existing204 contract and change no inventory; missing legacy reservations do not imply releasable units.
6. **Given** an eligible first cancellation before placedAt according to the current time, **When** attempted, **Then** preserve409/no changes; an already-CANCELLED valid repeat retains204 and its original event time.

### User Story 3 - Deduct only the stock owned by a completed order (Priority: P1)

As a customer, I need an authorized completion of my order to consume only its allocated stock once, even though the customer confirmation function will be delivered later.

**Why this priority**: Fulfilled units must be deducted at completion, never twice or from another order's allocation.
**Independent Test**: Prepare a SHIPPING order with known owned reservations; verify the guarded stock-consumption lifecycle and competing/repeated resolution without adding a customer confirmation interface or requiring shipping infrastructure. Concrete invocation/coordination belongs to planning.

**Acceptance Scenarios**:

1. **Given** onHand=10/reserved=10/available=0 and this order owns10, **When** eligible completion succeeds, **Then** onHand=0/reserved=0/available=0, its reservation becomes CONSUMED and COMPLETED/completedAt commit together.
2. **Given** onHand=10/reserved=7 with this order owning3 and another owning4, **When** this order completes, **Then** onHand=7/reserved=4/available=3 and the other's allocation remains intact.
3. **Given** enough aggregate reserved units but a missing/wrong reservation for this order/item, **When** completion is attempted, **Then** return409 without borrowing units or changing stock/status.
4. **Given** an already completed order, **When** the same authorized completion is processed again, **Then** preserve the existing terminal result/time and never deduct again; no new customer-facing response contract is introduced.
5. **Given** an ACTIVE reservation and an independently authorized PENDING/CONFIRMED/SHIPPING progression, **When** that progression succeeds, **Then** hold the same allocation without another reservation or physical deduction. No such progression workflow is added here.
6. **Given** multiple variants and one invalid reservation or later write/commit failure, **When** completion fails, **Then** no balance, allocation, status or terminal timestamp changes.
7. **Given** a legacy order, **When** a separately authorized completion workflow applies, **Then** apply explicit legacy exemption without touching unrelated aggregate reservations.

### User Story 4 - See availability without reserving a cart (Priority: P2)

As a shopper, I see current variant availability and cart shortages while understanding that cart contents do not guarantee stock.

**Why this priority**: Availability supports shopping decisions; only checkout commits units.
**Independent Test**: Read published variants and an owned cart before/after another order's reservation, consumption and release; exercise Q3 without checking out this cart.

**Acceptance Scenarios**:

1. **Given** published variants with positive, zero or missing balances, **When** shown, **Then** report availableQuantity and a safe stock classification, without hidden catalog metadata or other customers' allocations.
2. **Given** cart quantity5 and available3, **When** stock changes or the cart is read, **Then** stored quantity/selection remain unchanged, shortage is visible and this row is excluded from selected purchasable counts/totals under Q3:A.
3. **Given** an unavailable row, **When** removed or deselected by its owner, **Then** allow the existing action and change no inventory.
4. **Given** any cart add/update/variant/select/select-all action, **When** accepted or rejected, **Then** create no reservation and change no stock counter. Rejection changes no cart business fields.
5. **Given** a permitted variant switch, **When** applied, **Then** preserve same-product rules: absent target resets to1; an existing target remains entirely unchanged and only source disappears; valid same-variant is a no-op. Check the actual resulting quantity against availability, never cap it; a rejection preserves both rows.
6. **Given** select-all under approved Q3:A, **When** selecting, **Then** select only fully eligible rows while preserving other stored preferences; deselect-all still clears all preferences.
7. **Given** an accumulated add or quantity replacement exceeds stock-reserved, **When** requested, **Then** reject the whole cart mutation with a safe409 stock-conflict outcome, preserve the prior quantity/selection and leave both stock counters unchanged.

### Edge Cases

- Stock quantities are integral/nonnegative and may exceed the cart-row limit1..999; never wrap, round or cap inventory arithmetic. Concrete representation bounds belong to planning.
- Missing inventory is distinct from a known zero balance. Corrupt counters, ownership/quantity mismatches and infrastructure failures are not business shortages and abort the whole mutation.
- Preserve catalog reason precedence: missing variant/product, publication, quantity, price, then inventory presence/full-quantity sufficiency. Never expose hidden metadata through stock reasons.
- Combined included demand for one variant cannot reuse the same units, even if inconsistent/future inputs contain duplicate variant rows. Existing cart uniqueness remains.
- Previously stored checkout receipts remain recoverable without reservations; new stock reasons cannot cause old receipt bodies to be rewritten or rejected.
- Lost responses, logout, API failures, pending age and product unpublication do not release committed reservations. Failure before commitment rolls back that attempt rather than cancelling a prior order.
- Active allocations cannot disappear when catalog records are deleted. Any allowed later deletion preserves historical order snapshots and durable reservation attribution; planning reconciles this with nullable historical catalog references.
- Completion/cancellation cannot both mutate one allocation. A terminal repeat performs no new inventory mutation and preserves the first terminal event time.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Associate one authoritative balance with each managed variant while preserving existing identifiers. Draft default: every existing/new variant is managed; a missing record means unavailable, never unlimited stock.
- **FR-002**: Define onHandQuantity as physical holdings including allocations, reservedQuantity as ACTIVE order allocations, and availableQuantity=onHandQuantity-reservedQuantity. All are integers; onHand>=0, reserved>=0, reserved<=onHand and available>=0 always hold. availableQuantity is derived, never independently editable. reservedQuantity reconciles with summed ACTIVE attributed quantities.
- **FR-003**: Introduce stock without inventing positive quantities or retroactively reserving existing orders. Initial balances are0/0. Initial positive stock and replenishment are administrative functions deferred to the future admin feature; do not add a stock-write endpoint, offline operational loading feature or external stock feed here. Controlled test fixtures may supply explicit balances without being a production stock-management workflow. Stock-managed purchases remain unavailable until stock is supplied through a separately authorized future admin capability.
- **FR-004**: Keep cart operations non-reserving. Approved Q3:A requires full resulting quantity<=availableQuantity for add (including accumulated quantity), quantity replacement, variant change and explicit true selection. Bulk true selects fully eligible rows only while preserving unavailable preferences; bulk false clears selections. Selected purchasable counts/totals exclude stock-ineligible rows without rewriting stored quantities/selections. Preserve existing1..999 limits, ownership, same-product switching and removal/deselection. An absent switch target is checked at quantity1; an existing target and same-variant request are checked at their unchanged quantities. A valid same-variant request is a no-op; insufficient stock rejects unchanged, never resets/caps quantity. A distinct existing target is never silently merged or capped.
- **FR-005**: Expose current availableQuantity and stock classification in existing variant-bearing catalog and cart responses. Known positive/zero stock is distinguished from INVENTORY_UNAVAILABLE for missing records; display missing availability conservatively as0 with that explicit state, not a claim of known physical zero. Do not expose other orders/customers or require exposing physical/reserved counters publicly. Listing availability describes its existing representative variant, not a sum of unlike choices; detail describes each variant. Preserve publication visibility, search/filter membership, representative-variant selection, ordering and pagination: out-of-stock products remain visible and no stock filter is added. Existing no-representative compatibility cases report availability as unknown/not applicable, without inventing a variant or known stock. Additive response-field placement belongs to later contracts.
- **FR-006**: At the existing checkout entry point, validate current catalog/quantity/price eligibility and reserve every included row's full requested quantity. Enforce sufficiency when allocation changes, not only on an earlier read. Do not decrement physical stock at placement, split a row, cap quantity, substitute a variant or backorder.
- **FR-007**: Adopt the brief's recommended full-row skip policy as an explicit draft default: insufficient availability gives INSUFFICIENT_STOCK; missing inventory gives INVENTORY_UNAVAILABLE. Preserve skipped/unselected rows unchanged. Existing catalog reasons precede stock reasons; return one safe reason per row. No includable rows means existing422/no-eligible behavior, not an empty order. Integrity/infrastructure faults abort instead of becoming skips.
- **FR-008**: Commit order/items, full item-owned reservations, exact included-cart-row removal and the successful checkout receipt atomically. Every included item owns its ACTIVE quantity at commitment. Any failure, including final commitment failure, leaves no partial order, reservation, stock change, receipt or cart removal.
- **FR-009**: Preserve accepted mutations across customers/keys/orders sharing variants. Concurrent reservations cannot oversell or lose releases/consumption/replenishment. Total included demand for a variant must respect its authoritative available units.
- **FR-010**: Durably attribute each reservation to its order, item and original variant identity, with full purchased quantity, ACTIVE/CONSUMED/RELEASED state and reservation/resolution timestamps. Validate correspondence and prevent duplicate allocation or resolution. Ownership cannot be inferred from aggregate reserved stock. Concrete persistence design belongs to planning.
- **FR-011**: Hold reservations during PENDING, CONFIRMED and SHIPPING. Nonterminal progression neither allocates again nor deducts physical stock. Do not introduce generic customer status editing or progression into CONFIRMED/SHIPPING.
- **FR-012**: Completing a managed order verifies ACTIVE reservations for every item at the exact purchased quantity, deducts those quantities from both physical and reserved stock, marks CONSUMED and sets COMPLETED/completedAt atomically. availableQuantity is not a completion precondition. Terminal time cannot precede placement and the first successful event time remains immutable. Customer receipt confirmation remains deferred under approved Q2: add no confirm-receipt endpoint, customer status setter or shipping progression. The consumption boundary must be reusable by an authorized future completion workflow and must be verifiable without exposing that future customer function.
- **FR-013**: Integrate reservation release into existing cancellation: first cancellation only from PENDING/CONFIRMED, with the existing optional reason. Verify/release only the order's correct ACTIVE quantities, mark RELEASED and set CANCELLED/cancelledAt atomically; physical stock is unchanged. Preserve204 success/repeats, winning reason/time, unknown/foreign404, and409 for SHIPPING/COMPLETED or a first cancellation before placedAt. Completed allocations cannot be released.
- **FR-014**: A missing, misattributed, wrong-quantity or already-resolved reservation on a first managed terminal transition is a safe409 consistency conflict with no changes. Never invent/repair allocations, borrow another order's units or silently invoke legacy exemption. Classify an already-terminal repeat before a second inventory mutation and apply its approved response policy.
- **FR-015**: Add FAILED to the order status vocabulary for future customer refusal to receive goods; support its existing-style owned read/filter representation and reject customer cancellation from FAILED with409. Do not implement SHIPPING-to-FAILED, refusal processing, a failed-order timestamp/reason contract, or FAILED-driven reservation processing in this increment. Retain the lifecycle rule that a future genuinely committed expiry/terminal non-retryable failure must release its owned ACTIVE reservations exactly once, decrementing reserved only; its producer/transition and integration are deferred. No EXPIRED status, expiry policy/scheduler, payment provider or payment callback is introduced. A retryable payment failure must retain ACTIVE reservations and change neither counter; payment success alone is not completion and must not deduct stock. API failure, logout, order age or lost response never imply a terminal event. No payment flow currently exists to adapt or claim verified.
- **FR-016**: Preserve checkout key scope, strict inputs/address command fingerprint, receipt-first lookup and fresh receipt checks after acquired/busy fence outcomes. Every matching successful receipt returns original201/Location/body before current inventory evaluation, with zero stock/order/cart mutations. Failed initial attempts retain neither stock nor durable key. Current MANAGE_CHECKOUT remains sufficient; no extra inventory grant is required. Existing order actions keep MANAGE_ORDER/ownership checks.
- **FR-017**: Preserve historical recipient/item/price/amount snapshots, receipt lifetime/content and current order list/filter/detail/preview behavior. Stock reasons extend only newly created outcomes. Current order state and original PENDING receipt remain separate after completion/cancellation.
- **FR-018**: Explicitly distinguish pre-inventory legacy stock-exempt orders from all new managed orders using a durable unambiguous rollout policy, never absence of reservations alone. Do not allocate/deduct/release legacy stock during migration or ordinary actions. Preserve legacy reads/cancellation; if a separately authorized workflow completes a legacy order, apply approved status/ownership rules without stock mutation. No automatic backfill. Missing reservations on new managed orders remain409.
- **FR-019**: Use additive data-preserving rollout, preserving catalog/cart/orders/receipts and earlier migration history. Do not invent positive demo stock or retrospective inventory transactions. New variants receive a known0/0 balance through the approved provisioning boundary; missing-record fixtures remain possible. Update managed-order fixtures with explicit stock/reservations while retaining legacy fixtures. No stock-exempt checkout may bypass enforcement after activation.
- **FR-020**: Apply current identity/account/session/permission and ownership validation before protected actions. Unknown/foreign orders remain indistinguishable and rejected actions change nothing. Existing customer grants do not authorize replenishment or reading allocation ownership; stock display preserves hidden catalog privacy.
- **FR-021**: Preserve safe established errors:400 malformed inputs,401 invalid current identity,403 missing permission,404 protected missing/foreign resources,415 unsupported media,422 no eligible checkout rows/checked amount bounds,409 cart stock validation/recognized concurrency/reservation-integrity conflicts, and safe500 unexpected failure with any existing specific dependency-unavailable status. Preserve existing catalog/price reason precedence before reporting stock failure; do not expose hidden information. Never expose internals or disguise unknown failures as shortages.
- **FR-022**: Coordinate complete read/check/mutate operations with consistent resource ordering and all-or-nothing multi-variant changes. Completion, cancellation, checkout and approved stock supply preserve unrelated accepted updates. Planning selects mechanisms under existing transaction conventions; no default automatic retry or unusual transaction policy is introduced.
- **FR-023**: Execute synchronized concurrent verification in the normal integration gate: last-unit competing customers, same-key active/completed replay races, different-key checkout, different orders sharing variants, duplicate completion/cancellation, completion versus cancellation and multi-variant rollback. Use genuinely overlapping independent operations and actual commitment boundaries; sequential repetitions or skipped/deferred cases do not establish safety. Carry forward existing feature-local concurrency practice rather than inventing a project-wide policy.
- **FR-024**: Require meaningful unit, request/security and real persistence/integration coverage under existing conventions, including final-commit rollback and valid/invalid reservation attribution. Require no payment/shipping infrastructure. The reviewed baseline had no Inventory foundation; establish one authoritative implementation and reuse it across Cart/Checkout/Order rather than parallel stock logic. Execution evidence belongs in [implementation-validation.md](implementation-validation.md), with no dependency/build/CI change, commit or push.

### Quantity and order-to-reservation lifecycle

| Quantity | Business meaning | Customer editable? |
| --- | --- | --- |
| onHandQuantity | Physical units including allocated units | No |
| reservedQuantity | Sum of ACTIVE order-owned quantities | No |
| availableQuantity | onHandQuantity - reservedQuantity | No; derived |

The follow-up's `stock` means onHandQuantity and `reserved` means reservedQuantity; `stock - reserved` is the same availableQuantity, not a second set of counters.

| Event | Reservation transition | Physical change | Reserved change |
| --- | --- | --- | --- |
| Successful checkout of q | None -> ACTIVE | 0 | +q |
| Approved active-status progression | ACTIVE -> ACTIVE | 0 | 0 |
| Approved completion | ACTIVE -> CONSUMED | -q | -q |
| Eligible cancellation | ACTIVE -> RELEASED | 0 | -q |
| Future approved expiry/terminal failure — transition and processing deferred | ACTIVE -> RELEASED when that future workflow is implemented | 0 | -q |
| Future retryable payment failure — payment integration deferred | ACTIVE -> ACTIVE | 0 | 0 |
| Valid repeated terminal action | None | 0 | 0 |
| Failed uncommitted attempt | No committed transition | 0 | 0 |

Example:10/0/10 becomes10/3/7 after purchase3; completion yields7/0/7, while alternative cancellation yields10/0/10. Completion/cancellation are alternative terminal outcomes, never two sequential inventory mutations for one allocation.

### Confirmed clarifications and scope boundaries

- **Q1 stock supply — confirmed2026-11-19**: Initial stock loading and replenishment belong to future admin work. Do not introduce a production stock-management endpoint, loading tool or feed in this feature. Initialize known balances at zero; fixtures explicitly supply stock for tests.
- **Missing inventory**: All variants are managed; missing record is unavailable, never unlimited. Known initial zero and missing configuration remain distinguishable.
- **Insufficient stock**: Adopt full-row skips and retain rows; no eligible rows gives422. This is the user's recommended integration, openly selected for review rather than silently inferred.
- **Q2 completion — confirmed2026-11-19**: Customer confirmation remains future work. Inventory consumption belongs to the guarded terminal lifecycle; no customer completion endpoint or arbitrary status transition is introduced now.
- **Q3 cart — confirmed A,2026-11-19**: Enforce current full resulting quantity on add/update/variant change/true selection, including accumulated adds and unchanged existing switch targets. Select-all skips stock-ineligible rows; selected purchasable totals exclude them. Keep stored quantities/preferences, removability, deselection and non-reserving carts.
- **Historical orders**: Pre-rollout orders are explicitly stock-exempt, without automatic backfill or mutation of unrelated balances. Their existing cancellation remains valid. A future approved controlled backfill would be separate work; new managed missing allocations are not historical exemptions.
- **Terminal failure — confirmed2026-11-19**: FAILED means the customer refused receipt. Add the status only, including owned reading/filtering; SHIPPING-to-FAILED and all failed-order processing logic are future work. No automatic release merely from receiving a FAILED value is authorized here. Future terminal-failure/expiry processing must atomically release once, while retryable payment failure retains the reservation.
- **Implementation timing — confirmed2026-11-19**: This request runs specify only. The missing Inventory foundation, Cart/Checkout/Order integrations and regression tests are requirements for subsequent Plan/Tasks/Implement steps. “Now that Inventory is available” is a target-state dependency statement, not evidence that stock code or a payment flow exists today.
- **Concurrent tests**: Required synchronized tests run in the normal gate, following already-approved checkout/order practice; no test deferral is assumed.
- **Deferred-work documentation**: Later implementation must reconcile affected Cart/Checkout/Order specifications/contracts and mark only behavior and regression checks actually implemented and verified. This specify-only deliverable records those obligations without changing historical receipt data or claiming completed integrations.

### Dependencies and exclusions

Depend on current published catalog/variant/price rules, current identity and feature permissions, owned addresses, persistent cart coordination, durable checkout receipts and historical order lifecycle. This feature extends inventory-specific exclusions of earlier increments only as stated; other approved business contracts remain unchanged. Concrete persistence, numeric bounds, response contracts, lock ordering and responsibility placement follow during planning under the Constitution.

Exclude multiple warehouses, suppliers/purchase orders, backorders/negative stock, partial fulfilment/cancellation, returns/refunds/restocking returns, payment-provider workflows, shipping services/tracking/progression, automatic expiry scheduling, administrative stock management, customer receipt confirmation, generic status setters and SHIPPING-to-FAILED/failed-order processing. Future terminal-event inventory rules remain requirements for later workflows; no producers are invented here. The initially missing Inventory foundation and current Cart/Checkout/Order integrations are implemented through this feature. No payment flow exists to adapt or claim tested. No dependency/build/CI change, commit or push is included.

### Key Entities *(include if feature involves data)*

- **Variant balance**: Authoritative physical/reserved integer units and derived availability for an existing variant.
- **Order-owned reservation**: Durable order/item/original-variant attribution, full quantity, ACTIVE/CONSUMED/RELEASED state and absolute reservation/resolution times.
- **Managed/legacy order**: Existing historical purchase with an explicit inventory participation distinction, independent of whether reservation rows happen to exist.
- **Availability observation**: Current variant/cart stock classification and available units, not a commitment.
- **Approved stock-supply event**: Trusted initial loading/replenishment source and attributable quantity change, subject to Q1; cannot directly edit availability or overwrite allocations.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**:100% of tested initial/reserve/consume/release/failure/concurrent outcomes preserve all quantity invariants and reconciliation of reserved units to ACTIVE allocations, with zero oversold units or leaked reservations.
- **SC-002**: Every successful managed checkout owns its exact included quantities, leaves physical stock unchanged and removes only included rows. All skipped/unselected rows remain unchanged; all-unavailable attempts create zero orders/receipts/reservations.
- **SC-003**: Every valid completion deducts its exact owned quantity once from both counters, including available=0; eligible cancellation releases once without changing physical stock. No action consumes/releases another order's units.
- **SC-004**: Every accepted retained-key replay returns the original receipt/reference/skips and changes zero inventory, reservation, order or cart records, including after terminal events and lost responses.
- **SC-005**: Every injected pre-commit failure leaves zero partial reservation/deduction/release/order/receipt/cart removal; overlapping operations preserve accepted unrelated changes.
- **SC-006**: Every protected foreign/unknown-action pair produces the same safe rejection with zero disclosure/mutation; availability reveals no hidden catalog data or other customers' allocations.
- **SC-007**: Every legacy migration/read/action preserves history and receipts with zero automatic stock mutation; every post-activation checkout is managed and missing new allocations never trigger exemption.
- **SC-008**: Every cart action follows approved Q3:A, changes zero stock and never silently rewrites quantity/preferences. Availability examples agree with their observed balances, without promising future availability.
- **SC-009**: Every required synchronized case executes without skipped/deferred scenarios before implementation completion, demonstrating zero duplicate terminal mutations/lost accepted stock changes without payment/shipping workflows.
- **SC-010**: FAILED can be read/filtered as an owned status while customer cancellation rejects it without changes. Zero new customer confirmation, stock-administration, SHIPPING-to-FAILED, expiry-scheduler or payment workflows are introduced by this increment; deferred processing is never claimed implemented or verified.

## Assumptions

- Quantity meaning and reserve/consume/release lifecycle come directly from the brief. Q1/Q2/Q3 are answered above. Missing-inventory, skip, legacy and normal-gate policies remain explicit defaults carried from the brief and existing approved conventions.
- Earlier inventory exclusions bounded earlier increments, not a permanent prohibition. Their affected deferred-work sections now link to these implemented deltas; immutable historical receipts remain unchanged.
- Customer receipt confirmation is explicitly deferred. Guarded inventory consumption on authorized completion is still required, without a new customer confirmation interface. No existing completion eligibility/repeat implementation is assumed. FAILED is the only newly approved status; its refusal transition and processing, expiry and payment integration remain future work under FR-015.
- Without a trusted positive stock supply, zero-initialized managed variants cannot be purchased. No stock-edit endpoint or external inventory system is assumed to exist.
- Existing exact money, identity/ownership, publication privacy, strict inputs, snapshots and immutable receipt policies remain applicable. No global identifier, permission or retry policy is inferred.
- [repository-context.md](repository-context.md) and [integration-review.md](integration-review.md) retain the historical baseline findings. Current implementation, regression and integration evidence is recorded separately in [implementation-validation.md](implementation-validation.md); the removed specify-stage attempt is not used as passing evidence.


## Voucher integration follow-up — feature011

Feature011 composes existing Inventory reservation with atomic Voucher purchase; it does not deduct stock early, change reservation lifecycle or add payment/admin/confirmation/FAILED transition producers. Cart estimated payable now subtracts the order-level Voucher discount from the existing eligible selected subtotal, with item and stock rules unchanged. Verified targeted deltas and the separately tracked unrestricted completion gate are recorded in [Voucher execution evidence](../011-voucher-support/implementation-validation.md). Earlier completed tasks and their historical execution records are preserved; this follow-up adds no admin/code/public-discovery/cancellation-quota contract.
