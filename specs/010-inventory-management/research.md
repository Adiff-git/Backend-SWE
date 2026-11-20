# Inventory Research Decisions

**Historical baseline/design record (2026-11-19).** Statements below about absent Inventory and later implementation describe the pre-implementation review. The approved foundation and Cart/Checkout/Order deltas are now implemented; see [implementation-validation.md](implementation-validation.md) for current evidence. Admin supply, public confirmation, FAILED processing, expiry and payment producers remain deferred. No executed payment-flow test is claimed.

**Date**: 2026-11-19. Read-only research against feature/cart56a1e46, the current Constitution and [spec.md](spec.md). Two research agents inspected persistence/concurrency and contracts/tests; neither wrote files or ran application checks. All technical choices below are resolved designs, not implemented behavior.

## R1 — One owning Inventory domain

**Decision**: Add a small Inventory domain with its own balance/reservation repositories, service interface/implementation, validator and pure availability converter. Checkout, Order, Cart and Catalog use its service interface; Inventory never invokes their services/repositories. Keep order state/ownership in Order and authoritative price calculation in Checkout.

**Rationale**: Satisfies Constitution I/III without copying counters or creating a parallel ordering workflow. The baseline contains no Inventory or payment implementation; establish one foundation in later implementation. Customer completion/admin supply/FAILED processing remain deferred.

**Alternatives considered**: Counters in Cart or catalog converters duplicate ownership; Inventory orchestrating Order creates a cycle; an external inventory/payment platform is unapproved scope.

## R2 — Quantities and stable attribution

**Decision**: PostgreSQL BIGINT/Java Long stock and reserved, range0..9223372036854775807, derived available=stock-reserved. New tables have local BIGINT identity primary keys named id. Retain existing UUID catalog identifiers. Each reservation references the balance, exact order/item and immutable original variant UUID, quantity1..999, state and event timestamps. Sum ACTIVE quantities with NUMERIC in integrity validation to avoid aggregate overflow.

**Rationale**: The guarded stock difference makes nonnegative addition/subtraction safe; the cart999 rule is not an inventory cap. Aggregate units never establish order ownership. Use named local constraints plus deferred cross-table integrity, not a cross-table CHECK function falsely declared immutable.

**Alternatives considered**: Floating-point/fractional units, silently narrowed integers, editing available directly and making variant_id the new primary-key column conflict with requirements or Constitution VII.

## R3 — Explicit legacy rollout, zero provisioning and retention

**Decision**: Provisional next migration V25 initializes existing balances at0/0, provisions new variant balances at0/0 and adds immutable orders.inventory_managed. Existing orders are false; change the insertion default to true in the same migration, and explicitly write true in production order creation. Historical fixture inserts must opt into false deliberately. No backfill or positive production seed/loading tool.

Select a migration-owned product_variants AFTER INSERT trigger for zero provisioning because no owning variant-creation/admin API currently exists. Provision atomically with variant insertion, never as a repair/upsert during ordinary reads or checkout. The trigger adds no positive stock or stock-management interface.

Balance identity contains immutable original_variant_id and nullable live product_variant_id. Null only the live reference after permitted catalog deletion; retain physical quantities, original identity and all reservation history. Block deletion of a live variant while ACTIVE reservations exist. No balance/reservation cascade or stock loss. No runtime delete/update API is introduced.

**Rationale**: Default false forever lets new orders bypass inventory. Absence of reservation rows cannot identify legacy orders. Cascade deletion destroys stock; restricting every future catalog deletion breaks terminal history compatibility. Preserve stable original identity with a nullable live FK instead.

**Alternatives considered**: Reserve historical active orders automatically, invent initial positive quantities, infer legacy from time/missing rows, cascade-delete balances, or forbid all terminal catalog deletion.

## R4 — Allocation-time enforcement and consistent resource order

**Decision**: Remain REQUIRED/READ_COMMITTED. Checkout retains receipt-first/fresh fence checks, then user -> cart -> products -> variants -> inventory balances -> new allocations. Existing-order terminal work takes the owned order first, then attributed balances and reservations. Sort resources globally by UUID text/native UUID order before batch100; Java UUID.compareTo has signed ordering and is unsuitable for matching PostgreSQL UUID lock order. Inventory never obtains an existing order/cart/catalog lock after balance locks.

Lock candidate balance rows FOR UPDATE, classify full-row shortages from that protected view, and enforce sufficient stock again in conditional reservation mutations. Order/items/reservations/cart deletion/receipt commit together. First terminal mutations require exact ACTIVE owned allocations; counter-only changes, inconsistent allocation state or a failed conditional write roll back the operation. Terminal repeats are classified by the locked order before Inventory is called.

**Rationale**: READ_COMMITTED provides fresh statements; an earlier availability display is insufficient. Ordered locks and mutation predicates address shared-variant conflicts without raising isolation. [PostgreSQL isolation](https://www.postgresql.org/docs/17/transaction-iso.html) documents fresh statement snapshots and predicate re-evaluation; [explicit locking](https://www.postgresql.org/docs/17/explicit-locking.html) documents row-lock lifetime and conflict behavior.

**Alternatives considered**: Read-only checks, per-user locks alone, optimistic stock displays, automatic application retries, SERIALIZABLE and independent reservation transactions.

## R5 — Existing deferred locks and timeouts

**Decision**: Preserve existing checkout timeout reapplication after UserService's own timeout. Balance waits inherit checkout.lock-timeout-ms; existing-order terminal waits inherit order.lock-timeout-ms. No new global timeout/pool profile. Normal cart availability reads do not acquire inventory write locks: cart presentation may later lock products for images, so inventory-first locks would reverse ordering.

V3 catalog integrity obtains product FOR NO KEY UPDATE at commit; V23 item integrity obtains existing order FOR NO KEY UPDATE. Cooperative test/future catalog writers take affected existing orders before catalog resources where order-item nulling is involved, then products/variants/balances in stable order. Uncooperative variant-first/deletion SQL may conflict/deadlock; recognize55P03/40P01 as409, roll back, and let clients retry. Do not modify the old migrations or claim parent locks protect children/stock.

**Rationale**: The real lock graph includes deferred triggers/FKs, not just service annotations. Existing service transactions already rely on a single database boundary. [Spring transaction annotations](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html) support service boundaries through proxies; no self-invocation assumption or extra propagation is needed.

**Alternatives considered**: Inventory read locks in all cart requests, independent timeout policies that overwrite user/order semantics, or deferred checks acquiring an existing order after stock.

## R6 — Database integrity and immutable records

**Decision**: Preserve all V23/V24 integrity. Add named CHECK/UNIQUE/FK constraints and deferred validation of reserved-to-ACTIVE totals, managed-item completeness, exact balance/order/item/original-variant correspondence, state/time matching and active deletion safety. Validate every mutation boundary, including item/reservation deletion and both old/new owners where relevant. Serialize aggregate checks on affected balances before fresh sums; normal services already hold them in order. Validators handle expected conflicts; database checks remain the final guard.

Reservation attribution/quantity/reservedAt and order participation are immutable. ACTIVE may resolve once to CONSUMED or RELEASED; terminal rewrites/deletion/reassignment are rejected. No reserve-again operation or silent repair. Pure JSON validation may remain immutable; cross-table stock validation may not.

Inserted allocations must start ACTIVE/unresolved and must belong to managed orders. Reject legacy allocation insertion rather than treating legacy as an unchecked parent, and reject terminal-state bootstrap inserts rather than trusting only final state correspondence.

**Rationale**: Temporary inconsistency inside one transaction is necessary; final commit must reconcile all resources. [PostgreSQL constraint triggers](https://www.postgresql.org/docs/17/sql-createtrigger.html) allow end-of-transaction validation. Tests must observe actual commit failures, not only flush.

**Alternatives considered**: Application-only reconciliation, unlocked concurrent aggregate sums, counter-only reservations, and immediate completeness checks that reject valid composed creation.

## R7 — Cart semantics and additive availability

**Decision**: Separate catalog eligibility, stock observation and full-row eligibility. Preserve initial product/privacy -> quantity/price -> stock reason precedence. Validate resulting accumulated add/replacement quantity; switch checks1 for absent target or the unchanged existing/same target quantity; true selection validates the full stored quantity. Bulk true skips ineligible rows without rewriting their preference; removal/deselection remain available.

Add nested availability only to ProductListingItem, VariantSummary and CartItemResponse. Hidden/missing catalog cart rows and no-representative listing cases use null; a published variant missing its balance uses quantity0/status INVENTORY_UNAVAILABLE. Stock shortages retain current valid unitPrice, return null subtotal and exclude the row from selected counts/quantity/totals while preserving stored selected=true. Converters only map prepared row-specific outcomes.

**Rationale**: stock>0 cannot determine whether quantity5 is sellable against3. Adding fields to shared ProductSummary unintentionally changes wishlist/other consumers. Published out-of-stock metadata/images remain public; stock never changes search membership/ranking/representative choice.

**Alternatives considered**: Scalar stock-only purchasable predicate, silent quantity capping, deselecting rows on reads, summing unlike variants or choosing a different representative because of stock.

## R8 — Receipt compatibility and safe errors

**Decision**: Extend version1 skipped reasons with INVENTORY_UNAVAILABLE and INSUFFICIENT_STOCK. The additive migration narrowly CREATE OR REPLACEs V24's JSON validator whitelist while retaining every other check, FK/trigger and old receipt body. No receipt rewrite, version reset, live availability payload or extra replay stock lookup.

Use distinct CartStockConflictException409/CART_STOCK_CONFLICT, preserving existing malformed quantity400, final cart-limit422, hidden/missing target404 and catalog/price422. Internal InventoryConflictException409/INVENTORY_CONFLICT covers allocation mismatch. Existing checkout error adaptation still exposes CHECKOUT_CONFLICT for inventory/recognized concurrency409. Recognized named inventory integrity failures map safely; unrelated persistence faults remain established500. No SQL/private allocation details in errors.

**Rationale**: Java enum extension alone fails V24's strict receipt check. Existing CartItemUnavailableException is422 and cannot represent the approved stock409. Successful receipts are immutable even after terminal order changes.

**Alternatives considered**: Bypass JSON checks, rewrite old receipts, reuse a misleading price/unavailability422, or introduce a second idempotency policy.

## R9 — Guarded internal completion and FAILED-only compatibility

**Decision**: Add OrderCompletionService/Impl as a trusted internal owning-domain boundary: owner/order lookup and lock, SHIPPING first completion only, exact prepared item quantities, Inventory consumption and COMPLETED/completedAt in one transaction. COMPLETED repeat is a no-op before clock/Inventory; other states conflict. Legacy orders change no stock. Deliberately adjust historical-only completedAt mapping for first mutation; never rewrite first completion time or snapshots. No customer endpoint/security matcher or new confirmation permission is designed.

FAILED is added to enum/schema, exact status filtering and owned reads; cancellation/internal completion reject it. No failedAt/reason, SHIPPING-to-FAILED mutator, failed release handler, expiry scheduler/status or payment flow. Future true terminal failure/expiry must adopt release-once semantics; retryable payment failure must retain reservations. The current payment directory is empty, so source review is the only current payment finding.

**Alternatives considered**: Customer confirmation now, generic status setters, treating every payment error as FAILED, or implementing unused event handlers for excluded workflows.

## R10 — Verification and measured work

**Decision**: Keep applicable existing suites and add focused inventory, internal completion and real shared-variant concurrency tests in owning packages. Different-customer last-unit races are mandatory: existing same-customer races can be serialized by user/cart locks and cannot prove inventory safety. Preserve old receipt/race/ownership/history tests, adapt fixtures with explicit test-only stock and resolve active purchases before allowed terminal catalog deletion.

Use all-JDBC SQL capture alongside Hibernate statistics; one extra availability query per distinct batch100 is bounded, not per item. Measure100/101/205 rows/variants, detached mapping, replay lookup counts, final commit and rollback. Keep existing query ceilings when they still pass; justify only measured formula-based additions rather than silently weakening gates. Record server trigger cost separately from client SQL counts; no invented latency/capacity SLA. Normal Failsafe concurrency and one full Maven verify are later implementation obligations, not plan execution.

**Alternatives considered**: Mocked concurrency, sequential repeats, hidden JDBC statements, optional/disabled races, duplicating existing test classes or calling a stale successful checkout build Inventory evidence.

## Resolution

All planning unknowns resolved. No new business question, dependency installation, application build/test, source/migration implementation or task generation is needed for this plan. The design gate is assessed in [plan.md](plan.md); details are in [data-model.md](data-model.md) and [contracts/internal-services.md](contracts/internal-services.md).
