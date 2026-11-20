# Inventory Internal Service Contracts

Initial Inventory lockAvailability/reserveReservations locks live-linked balances only; missing/detached links are unavailable. Owned terminal release/consume locks retained balances by original UUID, followed by owned allocations. V25 final validation rejects ACTIVE allocations lacking the matching live balance link even when a direct SQL writer raced with detachment.

Design for [spec.md](../spec.md); storage in [data-model.md](../data-model.md), HTTP deltas in [api.md](api.md). Implemented behavior and tests are recorded in [implementation-validation.md](../implementation-validation.md).

## 1. Ownership graph and prepared data

Allowed dependency direction: Checkout -> User/Address/Cart/Catalog/Order/Inventory; Cart -> Catalog/User; Catalog -> Inventory/Storage; Order -> Inventory/Catalog read boundaries; Inventory -> its own repositories only. Inventory never calls Order/Cart/Catalog back. A service accesses only its domain's repositories. No controller/converter queries or repository workflow orchestration.

InventoryService/Impl owns availability observations, ordered balance locks, full-quantity reserve and exact allocation consumption/release. InventoryValidator owns quantity/identity/state/time/correspondence rules. InventoryConverter maps already-computed data only. Inventory repositories own projections, explicit locks, bound conditional DML and persistence integrity. Services prepare all required rows; no lazy/entity fetch can occur during response/receipt conversion.

Commands are Lombok immutable classes in inventory/dto/request, internal response seeds in dto/response. They are not HTTP payloads or new endpoints:

| DTO | Required fields / rules |
| --- | --- |
| ReservationCommand | Positive orderId, placement Instant and nonempty immutable ReservationLine list; order has just been created managed in the outer checkout transaction |
| ReservationLine | Positive orderItemId, original variant UUID, quantity1..999; no duplicate item or original variant; prepared from created item identity and immutable purchase command |
| ResolutionCommand | Positive orderId, exact expected item IDs/original variant IDs/quantities and one terminal Instant; owning order is already locked and first-transition eligible |
| InventoryAllocationSeed | Immutable owned reservation identity/balance/original variant/quantity/state/times projection; never a public allocation response |
| InventoryAvailability | availableQuantity long and status IN_STOCK/OUT_OF_STOCK/INVENTORY_UNAVAILABLE; no public physical/reserved/known/owner field |
| OrderInventorySeed | Owning Order's immutable prepared managed flag/id/owner/status/placedAt and exact item identities/quantities/live variant references; no catalog repricing |

Reject null commands/lists/items, invalid/duplicate identifiers, quantity outside1..999 or invalid event time with InventoryConflictException409; do not execute partial commands. Commands cannot establish authorization: existing HTTP orchestration derives current owner and validates access; future internal callers are trusted business orchestrators, not client-supplied identity overrides.

## 2. InventoryService operations

### getAvailability(variantIds)

Read-only REQUIRED transaction (joins a suitable outer transaction). Deduplicate and globally sort UUID text; query batches<=100. For every requested live UUID return a prepared observation: known physical-reserved difference and state, or missing0/INVENTORY_UNAVAILABLE. Never auto-create/repair a missing balance, reserve, lock every read, fetch images or query order owners. Calling Catalog filters hidden/missing catalog data before returning a response.

One stock observation is informational. No across-request guarantee or cross-batch snapshot is promised. Empty collection uses no SQL. No row cap is introduced. Prepared scalar data is mapped without additional SQL, including after the transaction ends.

Project stock and reserved as scalar values and validate0<=reserved<=stock before deriving the difference with checked arithmetic. A corrupt balance aborts the whole operation as a consistency fault; never clamp it to zero, manufacture missing availability or classify it as normal shortage. Converters receive the prepared nonnegative difference/state and do not calculate/repair counters.

### lockAvailability(variantIds)

Read-write REQUIRED; only a genuinely new checkout calls it after protected catalog state. Globally sort before chunk100, select known balance rows FOR UPDATE in original UUID order, then return fresh current observations. Missing rows remain unavailable without inserting or treating absence as unlimited. Locks last until outer completion; no cart/catalog/order lock is acquired afterward by Inventory.

Checkout initially passes only catalog/quantity/price-eligible selected candidates, preserving safe reason precedence and avoiding stock-based hidden-catalog discovery. All candidate balances are protected before classification. Sort order is PostgreSQL UUID/native text order, not Java UUID.compareTo.

### reserveReservations(command)

Join the same checkout transaction after order/items obtain real IDs. Revalidate command and exact correspondence; lock/revalidate involved balances in the same global order (already held when checkout called lockAvailability). For each full line atomically increase reserved only if stock-reserved>=quantity, and insert its unique owned ACTIVE/unresolved allocation at the purchase time. Legacy parents and terminal-state bootstrap inserts are rejected by integrity guards. Every affected count must match; no capped quantity, upsert that increments twice, durable processing reservation or automatic retry.

Direct repeated creation is a consistency conflict/unique rejection, not another allocation. HTTP idempotent replay never reaches this operation. An unexpected conditional miss after protected classification aborts409; it is not converted into a partial row skip after order creation. Database completeness prevents this operation or OrderCreationService being committed independently with a partial managed aggregate.

### consumeReservations(command) / releaseReservations(command)

Distinct methods clarify business intent; no generic external state setter. The caller has locked/validated the owned order and established that this is an eligible first terminal transition. Read that order's attributed allocations, lock all corresponding retained balances in global original UUID order, then lock/revalidate reservation rows in the same order. No lookup of live catalog eligibility, current price or available stock is needed for resolution.

Match the complete allocation set against exact expected order/item/original-variant/quantity identities. Missing/extra/wrong/partially resolved records or event<reservation time give409 with no changes. Require every allocation ACTIVE for a first transition, even if all already equal the proposed terminal state: do not conceal an inconsistent first transition as a repeat. Use stable retained balance/original identity; never consume aggregate reserved stock without ownership.

- Consumption subtracts q from both stock and reserved with underflow predicates, sets CONSUMED/resolvedAt.
- Release subtracts q only from reserved, leaves stock unchanged, sets RELEASED/resolvedAt.
- Any count mismatch/fault rolls back all variants and the owning order transition.

The existing Order state is the idempotency fence for terminal events: already-CANCELLED/COMPLETED repeats are classified by Order before a second resolution command or Clock sample. Inventory is not an independent transaction/event log. No REQUIRES_NEW/savepoint partial success or separate transaction manager is added.

## 3. Existing cart orchestration

CatalogCartReadService extends its prepared variant state with InventoryAvailability, keeping publication/price/metadata independent from stock. CartTransactionService owns cart locks and applies CartValidator to actual resulting quantities. Compute widened accumulated add before stock; for switches determine source/target outcome before validating the result and never mutate either row on failure.

Initial catalog validation preserves hidden/missing404 and price/cross-product422 before stock409. On add, quantity replacement, variant change and individual selected=true response preparation, use fresh final catalog presentation/availability and recheck that target's full resulting quantity before commit. Do not apply this whole-mutation stock409 recheck to bulk true, false selections, removal or GET; bulk true retains partial-success skip semantics. Rendering/true selection/eligible totals use quantity-sensitive prepared reasons, not a scalar isPurchasable meaning merely published/price or available>0. Prepare ineligible-reason maps/availability for all rows before passing them to CartCalculator/Converter; the calculator computes exact values/counts and the converter only maps prepared outcomes.

Stock observations do not acquire Inventory write locks in cart operations. Current catalog image/publication locks may be acquired during presentation; inventory-first locks would reverse checkout's product/variant/balance ordering. Stock can change after an observation/recheck; this may make an accepted cart unavailable later without invalidating non-reserving cart semantics. Checkout always revalidates/allocates under locks.

Removal/deselection never depend on stock. Bulk true selects fully eligible rows but preserves every ineligible stored preference; bulk false clears all. Reads never fix stored selection/quantity/snapshots. A retained selected shortage row is excluded from purchasable selected totals, not silently reduced to available units.

## 4. Existing checkout orchestration

One public proxied CheckoutService transaction, REQUIRED/READ_COMMITTED:

1. Validate current authenticated access/shape and command/key policy as before. Read successful scoped receipt before the mutation fence. Every matching hit returns immutable201 before address/cart/catalog/stock work.
2. On miss, try the existing per-key transaction fence, freshly recheck committed receipt on both acquired/busy paths, and honor every hit/mismatch. Busy plus no visible receipt returns CHECKOUT_IN_PROGRESS409. No new stock call on any successful receipt path.
3. Acquired+continued miss: user lock under existing user policy; immediately reapply positive checkout.lock-timeout-ms for later locks; coherent owned address -> cart lock/selected seed -> product/variant protected final catalog state as before.
4. Apply existing missing-reference/publication/quantity/price reasons. Lock eligible candidate balances through Inventory; classify full-row missing/insufficient stock after earlier reasons. Duplicate variant demand is rejected safely, never independently allocated against the same available unit.
5. No included row ->422, no order/allocation/cart/receipt mutation. Otherwise compute exact current VND purchase lines, sample one purchase Clock Instant at persisted microsecond precision and call owning OrderCreationService. It explicitly creates inventory_managed=true and returns actual item IDs with prepared lines.
6. Build ReservationCommand from those exact IDs/original variants/quantities; reserve full quantities. Consume only the included captured cart rows; encode/validate the immutable receipt with original safe skips; insert successful receipt as before.
7. Outer commit validates every order/item/allocation/counter/receipt constraint before HTTP201. Any application/dependency/conditional-write/flush/final-commit failure rolls back all changes. Sequence gaps are acceptable; successful keys are never reused and unsuccessful attempts retain neither stock nor key.

There is no physical stock deduction at order placement or payment time. No network/image signing occurs under stock locks. Partial checkout means business-ineligible rows are retained; it never means partially committing an order or surviving multi-variant failure.

## 5. Order cancellation and internal completion

### Existing OrderService.cancelOrder

Current identity/permission/shape validated as before; set positive order lock timeout and find owned order FOR UPDATE, preserving foreign/missing404. Valid CANCELLED repeat returns204 before sampling Clock or calling Inventory. Reject SHIPPING/COMPLETED/FAILED409 unchanged. For first PENDING/CONFIRMED action, sample one valid terminal Instant; managed order prepares its exact item/allocation seed through Order repositories and calls releaseReservations, then sets CANCELLED/cancelledAt/reason in the same transaction. Legacy false skips Inventory entirely. Terminal order/resolved times are the same persisted microsecond event; winning reason/time remain immutable.

### New OrderCompletionService.completeOrder(ownerId,orderId)

Trusted internal mutation, not a controller/API/permission-grant extension. Validate identifiers, set the same existing positive order timeout and lock the owned order before stock. Unknown/foreign404. COMPLETED repeat returns without another Clock/sample/Inventory mutation. First completion only SHIPPING; PENDING/CONFIRMED/CANCELLED/FAILED conflict409. No generic status argument, client-provided quantity, confirmation endpoint or SHIPPING progression.

Sample one terminal Instant>=placedAt, prepare exact immutable item seed, consume owned allocations if managed, and set COMPLETED/completedAt atomically. Legacy completion changes no inventory and never invents a reservation. Adapt Order.completedAt's historical-only updatable=false mapping for the first allowed internal mutation; retain snapshots/first timestamp. Future customer confirmation must independently specify caller access/request/status contract before exposing this internal capability.

Repeated completion and cancellation compete on the same owned order lock. Only status-eligible work resolves allocations; both cannot resolve them. Current rules do not make both actions eligible from one state: tests must include real rejected opposing actions, not invent a generic setter or broaden eligibility solely to manufacture a race.

## 6. Lock graph, trigger interactions and timeouts

| Path | Lock sequence |
| --- | --- |
| New checkout | Key transaction fence -> user -> cart -> sorted products -> sorted variants -> sorted inventory balances -> new allocation writes |
| Existing-order first terminal action | Owned order -> sorted retained balances -> sorted owned allocations |
| Cart change/read | Existing cart/user serialization and current catalog presentation locks; only nonlocking stock observations |
| Inventory operation | Balance/allocation locks only; never existing Order/Cart/Catalog locks afterward |

New order identity/rows belong to their creating transaction and are not a second existing-order lock. Same-kind global ordering precedes batch100. Keep native numeric order for Long IDs and text/native UUID order for UUIDs. Do not acquire balance locks in a converter, catalog signing helper or cart preflight.

V3 obtains product FOR NO KEY UPDATE at deferred commit and V23 obtains order FOR NO KEY UPDATE for item integrity. Future/cooperative catalog deletion affecting historical item references must pre-coordinate affected existing order IDs before catalog/balance resources; preserve existing cart FK restrictions. No new catalog deletion endpoint is introduced. Noncooperative variant-first or reverse-order SQL may deadlock and abort: tests assert safe409/rollback and client-directed retry, not impossible absence of all conflicts.

User wait keeps user.lock-timeout-ms. Checkout reapplication after user locking remains mandatory. Inventory waits join the installed checkout/order timeout rather than resetting it. SQL55P03/40P01 remain recognized conflicts; no global isolation/pool change, automatic retry or long external call under locks. Class-local pool overrides for independent tests do not alter application defaults.

## 7. Integrity, errors and future workflows

Use database checks described in data-model.md as final protection, not business repair. Expected allocation/counter/ownership mismatch is InventoryConflictException409/INVENTORY_CONFLICT. Checkout adapts that to CHECKOUT_CONFLICT using existing ApiErrorFactory policy. Cart shortages use CartStockConflictException409/CART_STOCK_CONFLICT. Safely recognize only named new inventory integrity constraints where database rejection must produce an equivalent409; unrelated constraints/infrastructure faults retain established safe500/dependency behavior. No framework exception is wrapped solely for naming and no SQL text is exposed.

FAILED is enum/schema/read/filter/cancellation-rejection compatibility only. No transition, timestamp/reason, failure resolver or test fixture that pretends FAILED processing exists. No expiry producer or payment flow currently exists. Preserve source audit findings and future obligations: eventual true expiry/non-retryable failure release once; retryable payment failure retains; payment success alone is not completion. Do not add unused callback/scheduler/service placeholders.

## 8. Query and verification contract

Availability projections add at most one query per distinct returned/requested variant batch100, not per row. Map stock after published catalog/representative selection without changing search SQL/ranking. Empty sets/no-representative results do no stock SQL. Prepared mapping emits no lazy query. Committed replay remains one scoped receipt read before authentication-related queries are counted separately; acquired/busy misses add the existing fence/fresh-read operations, not stock work on hits.

Use existing calibrated all-JDBC SQL capture plus Hibernate statistics so native/JDBC mutations cannot disappear from measurements. Inventory mutation work is proportional to included lines plus bounded read batches, while database deferred-trigger cost must be measured/reported separately. Retain existing fixed query ceilings if still met; otherwise document only a measured baseline+ceil(distinctIds/100) change with exact stock-query assertions, no blanket weakened gate.

Required proof includes all existing replay fence races, different-customer final-unit oversell, shared-variant independent orders, duplicate/opposing terminal actions, exact attribution, multi-variant failure, locks/timeouts and final commit rollback. [quickstart.md](../quickstart.md) defines scenarios/commands. No execution or passing test is claimed by this plan.


## Voucher integration follow-up — feature011

Feature011 composes existing Inventory reservation with atomic Voucher purchase; it does not deduct stock early, change reservation lifecycle or add payment/admin/confirmation/FAILED transition producers. Cart estimated payable now subtracts the order-level Voucher discount from the existing eligible selected subtotal, with item and stock rules unchanged. Verified targeted deltas and the separately tracked unrestricted completion gate are recorded in [Voucher execution evidence](../../011-voucher-support/implementation-validation.md). Earlier completed tasks and their historical execution records are preserved; this follow-up adds no admin/code/public-discovery/cancellation-quota contract.
