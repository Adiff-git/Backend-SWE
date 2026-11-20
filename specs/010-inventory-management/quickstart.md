# Inventory Validation Quickstart

Validation guide for the implemented feature. Actual commands, results and environment are recorded in [implementation-validation.md](implementation-validation.md). Follow [API contracts](contracts/api.md), [internal services](contracts/internal-services.md) and [data model](data-model.md) rather than duplicating their implementation here.

## Prerequisites and scope

- Java25, repository Maven wrapper, running Docker and existing PostgreSQL17 Testcontainers/Spring test dependencies. No install/build/CI/dependency change is required by this plan.
- Run commands from the repository's demo module. Tests use disposable databases/Flyway and the existing test profile; never shared production data.
- Inventory starts with zero production stock. Positive test stock must be explicit in fixture setup only; this guide does not provide a stock-edit endpoint, loader, admin command or positive migration seed.
- Persistent fixtures commit before service calls; verification of final commitment must not be hidden in test-level rollback transactions. Clean up committed fixtures, including retained balances/allocations. Existing TRUNCATE cleanup must include the new retained inventory tables explicitly when catalog CASCADE no longer removes them.
- Concurrency tests use independent connections/transactions, bounded latches/barriers, captured worker failures and class-local connection capacity where needed. Observe actual waits/real service barriers, not sleep-only overlap. Do not globally enlarge production/test pools.
- No payment/shipping service or customer confirmation is required. FAILED is read/filter only; failed-order processing, expiry scheduling and admin supply remain deferred.

## Reproducible commands

From repository root in PowerShell:

```powershell
Set-Location demo
.\mvnw.cmd '-Dtest=InventoryValidatorTest,InventoryConverterTest,InventoryServiceImplTest,OrderCompletionServiceImplTest,CheckoutServiceImplTest,CheckoutValidatorTest,CheckoutConverterTest,CartValidatorTest,CartCalculatorTest,CartConverterTest,CartTransactionServiceImplTest,OrderValidatorTest,OrderControllerTest,CheckoutControllerTest' test
.\mvnw.cmd test-compile failsafe:integration-test failsafe:verify '-Dit.test=InventoryBalanceRepositoryIT,InventoryReservationRepositoryIT,InventoryMigrationIT,InventoryServiceIT,InventoryConcurrencyIT,OrderCompletionServiceIT,CartServiceIT,CartControllerIT,CheckoutServiceIT,CheckoutControllerIT,CheckoutConcurrencyIT,CheckoutMigrationIT,OrderServiceIT,OrderControllerIT,OrderRepositoryIT,OrderCreationServiceIT,InventoryFlowIT,CheckoutFlowIT'
.\mvnw.cmd test-compile failsafe:integration-test failsafe:verify '-Dit.test=CatalogMigrationsIT,CatalogSearchMigrationIT,CatalogQueryCountIT,CatalogCartReadServiceIT,CatalogCheckoutServiceIT,ProductListingApiIT,ProductDetailApiIT,OrderMigrationIT,AddressServiceIT'
.\mvnw.cmd verify
```

Selectors name actual meaningful test classes; no empty classes were generated. Existing AddressServiceIT remains the single class in user/service; no old-package duplicate. Normal Maven verify must discover required concurrency ITs without an optional profile or skipped cases. Inspect current-run Surefire/Failsafe XML and logs for nonzero tests, failures/errors/skips and packaging; do not infer a green gate from exit text or stale reports.

## Required acceptance scenarios

### Initial/legacy rollout and database integrity — SC-001/005/007

1. Clean-apply migrations and upgrade a V24 database containing variant/cart/order/receipt data. Verify old rows/snapshots/receipts unchanged, old orders explicitly inventory_managed=false, every existing variant0/0 and new order insertion default true. A newly inserted variant provisions0/0. No positive production seed/backfill.
2. Delete an unused zero balance in an isolated fixture; catalog/cart show missing0/INVENTORY_UNAVAILABLE and no unlimited-stock behavior. Contrast with known0/OUT_OF_STOCK.
3. At actual commit reject reserved<0, stock<reserved, counter-only edits, wrong balance/original variant, foreign order/item, wrong quantity, missing/extra managed allocation, participation downgrade, immutable attribution/terminal rewrite, active deletion and multi-variant inconsistency. Also reject allocation insertion for legacy parents and insertion directly as CONSUMED/RELEASED. No rejected transaction leaves any stock/order/cart/receipt change.
4. Legacy order cancellation remains204 with no stock mutation; new managed missing allocations conflict instead of legacy fallback. Test old receipt replay without allocations.
5. Adding FAILED must not break legacy terminal-time checks, order filters or historical snapshots. Do not fabricate failedAt/refusal processing. Advance only current latest migration assertions to25 and isolated recovery successor to26 if V25 remains next free; keep historic upgrade targets.

### Cart/catalog — SC-002/006/008

1. With available3, add3 succeeds and reserves0; add1 to an existing3 rejects409 unchanged. Test quantity1/999 independently from stock; direct invalid quantity400 and widened cart-limit422 retain precedence.
2. Replacement quantity3 succeeds at available3;4 rejects unchanged. Test missing inventory, hidden/missing product, invalid price and cross-product switch with their existing precedence.
3. Switch to absent variant uses1; switch to existing target checks unchanged target quantity; same-variant checks unchanged quantity then no-ops. No failed switch deletes source or changes target/preferences/time/snapshots.
4. Select true checks full quantity, including already-selected shortage; select-all true skips ineligible rows and preserves their preferences, false clears all, and unavailable removal/deselection stays usable.
5. Let another customer's purchase make a selected row short. GET keeps stored quantity/selected and valid unitPrice, reports false/INSUFFICIENT_STOCK, null subtotal and excludes it from eligible-selected counts/totals. Every cart action changes zero counters/allocations.
6. Published out-of-stock choices keep approved public fields/images. Hidden cart rows/no representative return availability=null; missing stock is explicit0/INVENTORY_UNAVAILABLE. Listing stock is that exact representative; search/filter/rank/page membership and no-representative compatibility remain unchanged.

### Checkout and immutable recovery — SC-001/002/004/005

1. With stock10/reserved0, buy3: one managed PENDING order, owned ACTIVE3, stock10/reserved3/available7, exact current VND/address/item snapshots, only included rows removed.
2. Exact available quantity succeeds to0. Mixed rows include only fully reservable quantities; insufficient/missing stock is reported once with the new safe reasons, after catalog causes. Skipped/unselected fields remain exact. All unavailable gives422 with no order/receipt/allocation/deletion.
3. Force failures after allocation, order/item write, cart removal, encoding and successful-receipt preparation, including deferred commit failure. Assert full rollback, reusable unsuccessful key and zero leaked units.
4. Replay old and new successful receipts after balance/cart/address/catalog/current-status changes: original201/Location/body/skips and zero mutations. Preserve all existing completed-replay/acquisition/busy fresh-check races; success hits make no Inventory call or stock query. Different accepted command still conflicts.

### Cancellation and guarded internal completion — SC-001/003/005/006/010

1. Two orders own3 and4 units from stock10/reserved7. Cancel the3-unit eligible order: stock10/reserved4, only its allocation RELEASED; valid repeat204 preserves first time/reason with no new stock or Clock action.
2. In a separate fixture, complete the3-unit SHIPPING order through trusted internal OrderCompletionService: stock7/reserved4, only its allocation CONSUMED, completedAt/resolvedAt atomic. COMPLETED repeat does nothing. No customer confirmation endpoint is added.
3. stock10/reserved10/available0 with the order owning10 completes correctly. Aggregate reserved units belonging to another order cannot substitute for missing/wrong owned allocations.
4. Wrong order/item/variant/quantity, mixed/missing/non-active allocations, invalid event time and late writes/commit failures roll back every variant and terminal state. Foreign/missing stays404, current incompatible states409; include FAILED cancellation/completion rejection.
5. FAILED owned reads/filtering work, but customer refusal/SHIPPING-to-FAILED, failed processing, expiry and payment endpoints remain absent. Source-audit the empty payment boundary; do not label that an executed payment-flow test. Future retryable failure must retain stock; future true terminal expiry/failure must release once when separately implemented.

### Required synchronized matrix — SC-001/003/004/005/009

- Different customers/carts compete for the last unit and exact multi-unit stock: at most one allocation of each available unit; no leaked receipt/cart deletion for rejected work.
- Same-key original/uncommitted and already-completed replays; receipt commits before acquired or busy fence recheck; different-key cart consumption. Every race proves original allocation/receipt count and no second stock mutation.
- Different orders share variants while one reserves, cancels or completes: all accepted independent changes survive and reserved equals ACTIVE owned totals.
- Duplicate cancellation and duplicate internal completion synchronize on the same order; one initial resolution, repeat no-op. Opposing completion/cancellation observe current eligibility rather than making both legal from one state merely for a test.
- Reverse input order across multiple variants still locks globally; held-balance55P03 and recognized40P01 abort safely with no partial counters/status. Explicit client retry can succeed after the blocker releases; no automatic app retry.
- Retain existing checkout-vs-cart/address/catalog matrix. Active catalog deletion conflicts without allocation loss; after legitimate resolution, allowed catalog deletion nulls live refs while retaining stock and terminal history. Include both wait orders using real connections/observed locks.

## Query/resource measurements

Use the shared config/SqlCapture and SqlCaptureConfiguration calibration for all JDBC statements and Hibernate statistics for entity fetch/lazy checks. T061 relocated that suite to demo/src/test/java/com/example/demo/catalog/service/CatalogQueryCountIT.java (package com.example.demo.catalog.service), and the existing ProductListingApiIT/ProductDetailApiIT suites to demo/src/test/java/com/example/demo/catalog/controller/ (package com.example.demo.catalog.controller). Preserve assertions, annotations, profiles, class names and the existing simple-name Failsafe selectors above. Update fixture imports and all affected references/accessibility, including ProductSearchPerformanceIT and ProductSourceWorkloadPerformanceIT, while retaining one shared SQL-capture/fake-storage implementation; do not duplicate suites or expand their scope. The moves, imports and performance consumers are implemented and verified without duplicate suites. Cover100/101/205 distinct variants and cart rows, new and replayed checkout, no-representative/empty sets and detached mapping. Added availability work is at most ceil(distinct requested/returned variants /100) stock reads per observation, never one per item. Capture separate initial/final cart observations rather than hiding them in a per-item helper.

Retain existing catalog/checkout query ceilings when actual extra work still fits. If a bound genuinely must change, record the measured existing baseline and exact batch increment with stock-SQL assertions; do not broadly relax performance tests. Count native mutation SQL too and distinguish server-side deferred-trigger cost. No arbitrary row cap, giant fabricated database fixture or new latency SLA follows from these examples.

## Evidence and completion register

Use implementation-validation.md for actual commands/environment, current-run discovery/counts, independent overlap, commit/rollback, ownership, old/new receipt and query results. Update affected older Cart/Checkout/Order deferred sections to describe only implemented/verified behavior and retain explicit admin/confirmation/FAILED/payment/expiry exclusions. Do not change the reviewer-owned requirements checklist as an implementation progress tracker. No commit/push/deployment is implied by this guide.

**Execution status**: focused unit/MVC, real persistence/API/flow/concurrency, migration and measurement gates have run. The full gate and exact discovery totals are recorded in [implementation-validation.md](implementation-validation.md). Earlier removed specify-stage work is not used as passing evidence.
