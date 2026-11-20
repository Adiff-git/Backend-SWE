# Inventory Integration Review

**Historical baseline/design record (2026-11-19).** Statements below about absent Inventory and later implementation describe the pre-implementation review. The approved foundation and Cart/Checkout/Order deltas are now implemented; see [implementation-validation.md](implementation-validation.md) for current evidence. Admin supply, public confirmation, FAILED processing, expiry and payment producers remain deferred. No executed payment-flow test is claimed.

Reviewed2026-11-19 after the user's Q1/Q2/Q3 answers and specify-only correction. This records source findings and requirements for later implementation, not implemented or verified Inventory behavior. Plan, Tasks and Implement remain subsequent steps.

**Planning follow-up2026-11-19**: The user has now invoked `$speckit-plan`. [plan.md](plan.md), [research.md](research.md), [data-model.md](data-model.md), [API contracts](contracts/api.md), [internal contracts](contracts/internal-services.md) and [quickstart.md](quickstart.md) record the completed design phase. The source/verification statements below remain the specify-stage snapshot; implementation, task generation and application verification are still pending.

## Confirmed scope

- Admin initial-stock/replenishment functions remain deferred. No stock-write administration endpoint, offline loading feature or external feed is introduced now.
- Customer receipt confirmation remains deferred. No generic status-editing or customer completion endpoint is introduced now.
- Add FAILED as the future customer-refusal status in the later implementation, with existing-style owned read/filter support and cancellation rejection. Do not implement SHIPPING-to-FAILED or failed-order processing in this increment.
- Q3:A: validate full resulting sellable quantity for add/update/switch/true selection; select-all skips ineligible rows; preserve removal/deselection and existing switch semantics. Carts do not reserve.
- Checkout must reserve full included quantities and persist order/item/variant ownership atomically with order/cart/receipt. Only reserved increases at placement; stock/onHand remains unchanged.
- Authorized completion consumes both stock and reserved once. Cancellation releases reserved once. Future genuine expiry/terminal non-retryable failure processing must release once; those producers/transitions are deferred, including FAILED logic. Future retryable payment failure keeps the allocation. No early deduction on payment success/failure or replay.

## Baseline: Inventory foundation is future implementation work

Local feature/cart at56a1e46 contains no inventory package, balance entity/service/repository, inventory migration or reservation lifecycle. Production source and migration searches for stock/onHand/reserved/inventory found no such capability. The only Inventory files are the new010 specification documents. Historical auth rate-limit reservations and unrelated upload/mail expiry states are not order inventory.

Remote heads were inspected and current origin/main was fetched read-only. Main at070bcc2 merges feature/cart and also contains no Inventory implementation or payment source. No Inventory branch is advertised by origin. No branch switch, merge, reset, commit or push was performed during this review.

The user explicitly confirmed that the missing Inventory foundation is to be built here and integrated through the following Spec Kit steps. No dependency-location question remains. “Reuse Inventory” means one authoritative foundation shared by Cart/Checkout/Order once built, not an assertion that the current baseline already contains one. This specify step does not create it.

## Existing payment and terminal-event findings

- demo/src/main/java/com/example/demo/payment exists as an empty directory; there are no payment entities/services/controllers/callbacks or payment persistence migrations to adapt. No payment-time inventory deduction exists in the reviewed code. This is a source review finding, not an executed payment-flow regression.
- OrderStatus currently contains only PENDING, CONFIRMED, SHIPPING, COMPLETED and CANCELLED. Customer actions are list/detail/cancel. Completion, order expiry and terminal-failure event producers do not exist.
- V23 enforces those five states and terminal timestamps; V24 protects original checkout receipts. Adding EXPIRED/FAILED would be an observable status/filter/schema contract change that must be explicit, not inferred from the new event wording.
- The user answered: add FAILED for refusal, but defer SHIPPING-to-FAILED and failed-order processing. No EXPIRED status, expiry policy, refusal endpoint or arbitrary expiry/failure-to-CANCELLED mapping is approved. These future terminal producers must adopt the reservation rules when separately implemented.

## Integration map and regression obligations for later Plan/Tasks/Implement

| Existing boundary | Current behavior | Required later integration | Required later regression evidence |
| --- | --- | --- | --- |
| CartTransactionServiceImpl.addItem | Catalog eligibility and widened1..999 final sum; no stock lookup | Validate accumulated resulting quantity against current stock-reserved without reservation | Exact limit, shortage/missing stock, repeated adds, concurrent cart edits and no counter mutation |
| updateQuantity/changeVariant | Full replacement; absent target resets1; existing target unchanged; same-variant no-op | Check resulting quantity, preserve no-op/target semantics and rollback | Shortage rejection unchanged, correct target handling, removability/deselection |
| selectItem/selectAll and totals | Published/valid-price eligibility | Full quantity-based stock eligibility under Q3:A, no preference rewrite on reads | Individual/bulk true/false, stock changes after selection, totals, no reservation |
| Catalog detail/listing/cart presentation | No stock fields; publication-safe current catalog state | Add availability without changing ranking, filters, representative variant or hidden-data policy | Positive/zero/missing balances, representative/no-representative cases, hidden metadata protection |
| CheckoutServiceImpl | Receipt-first success recovery; protected selected/catalog state; one atomic order/cart/receipt | Add authoritative allocation check and per-order/item/variant ACTIVE reservations in the same transaction | Mixed/full shortages, failed write/commit rollback, exact cart preservation, last-unit race |
| Checkout receipt repository/codec | Lifetime original201 receipt, fixed stock-free skip enum | Preserve old receipt decoding; new outcomes may carry safe stock reasons; replay bypasses inventory mutation | Old/new receipts, acquired/busy fresh-hit races, post-terminal replay, no duplicate reservation |
| OrderServiceImpl.cancelOrder | Owned locked PENDING/CONFIRMED transition; already-CANCELLED204 | Release owned ACTIVE allocations in the same terminal transaction; legacy exemption explicit | Duplicate cancellation, ownership/missing allocation, physical unchanged, rollback at commit |
| Completion lifecycle | Not implemented; customer action explicitly deferred | Guarded owned-allocation consumption atomically with terminal order state, according to approved internal boundary | available=0 but fully reserved, wrong attribution, duplicate terminal events, multi-variant rollback |
| FAILED vocabulary and future terminal processing | No FAILED status, refusal transition or expiry producer | Add FAILED read/filter support only; defer refusal transition and failed-order processing. Future expiry/failure must release once when their actual workflows are authorized | FAILED owned/foreign visibility and cancellation409; no transition/processing endpoint. Future event integration needs its own repeat/race/rollback checks |
| Payment lifecycle | No implementation | Retain its absence; future payment workflows must keep allocations on retryable failure and never deduct early or twice | Audit stock mutation responsibilities and test inventory ownership/idempotency; no claim of executed payment-flow regression or invented provider/callback |
| Migration/fixtures | No balances or managed/legacy distinction | Additive rollout, zero initial stock, explicit legacy cohort; managed fixtures own actual allocations | Existing data/receipts unchanged, no retroactive stock mutation, new missing allocation cannot masquerade as legacy |

## Specify-stage verification status (historical)

- Completed: source/specification review, current-main dependency search, recording confirmed scope and required integration/regression boundaries. All Q1/Q2/Q3 and the FAILED follow-up are resolved.
- Not implemented: Inventory foundation, integrations, terminal-event/payment handlers or regression tests. No plan/tasks artifacts are retained.
- A premature test-compilation attempt failed on missing Inventory/completion symbols before the specify-only correction. Its newly added source/test/migration files were removed; no migration was applied and no runtime Inventory test was executed. This attempt is not a validation result for this specification. The previous checkout gate is evidence for checkout before inventory, not these new requirements.
- Final changes are feature requirements/review/checklist documents and active feature selection only. Existing application source, tests, migrations, dependencies and CI remain unchanged. Prior deferred-work documents are reviewed references; later implementation must update their affected sections with actual verified results, not claim that work occurred during specify.
