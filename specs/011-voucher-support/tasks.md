# Tasks: Voucher Support with Cart Preview and Order Discounts

**Input**: specs/011-voucher-support/spec.md, plan.md, research.md, data-model.md, contracts/ and quickstart.md. Constitution applies.

**Tests**: Explicitly requested by FR-021/SC-009. Write tests before corresponding implementation and record actual red/green. All tasks start unchecked; no application execution occurs during generation.

**Paths**: Main/test paths are repository-relative. Sibling filenames within one task share the stated package directory. No dependency/plugin/build/CI installation, admin/code/cancellation workflow, branch switch or commit/push.

**Scheduling**: Nonparallel tasks depend on preceding work. Each [P] writing wave depends on the last preceding non-wave task and joins at the next task. Shared helpers/source/migrations and runtime verification are sequential; never parallel writes to shared classes.

## Verification execution policy — inherited from Constitution v1.2.0

Use the risk→test→checkpoint matrix in [plan.md](plan.md) and exact command/status guidance in
[quickstart.md](quickstart.md). For each change, select tests from affected behavior, dependencies and
regression impact; record scope/rationale. Targeted compilation/tests are the default, followed by
relevant cross-domain checkpoints T016/T027/T039/T044/T052. All mandatory discount/security,
transaction/rollback, quota/concurrency, expiry-during-lock-wait, immutable receipt replay and affected
performance tests remain required. Test-writing tasks and their assertions are unchanged.

After an isolated fix, rerun affected tests first, not automatically every suite. Repeat full verification
for broad impact, shared infrastructure changes or unresolved regression uncertainty, documenting
why. Avoid repeated unrelated expensive tests without a justified dependency/regression risk.
T058 remains the required unrestricted full run near completion. T054–T057 reconcile evidence and
run missing/new/affected checks; already valid unchanged-scope evidence can satisfy overlapping
obligations with an explicit command/revision mapping. Do not silently omit required scenarios or
claim a targeted rerun is a successful full run; preserve failed full-run history.

Unavailable Docker/Testcontainers/PostgreSQL/LocalStack blocks affected checks. Record exact
commands/cause/affected scope/uncertainty, continue independent feasible checks, retry when available
and leave affected gates unchecked until fulfilled. Do not mock away a required real boundary,
change build/dependencies, skip tests to claim unrestricted success or waive assertion failures.
Report PASS, FAIL, BLOCKED, SKIPPED and NOT RUN as defined in quickstart, with command/selectors,
working-state/revision, environment, timestamps/current-report counts, failures/fixes and limitations.
No unexecuted or skipped test is passed; separate targeted/checkpoint/full-suite evidence.

## Phase 1: Setup

**Goal**: Preserve baseline and verify existing tools.

**Independent test / checkpoint**: Actual environment/baseline recorded without installing tooling.

- [X] T001 Record branch/HEAD/dirty baseline, active feature, read-only16/16 checklist, hook status and approved exclusions in specs/011-voucher-support/implementation-validation.md; preserve uncommitted Inventory work, no branch switch/commit/push.
- [X] T002 Verify existing JDK25/Maven/Docker/PostgreSQL17/LocalStack readiness, ignore files, normal Surefire/Failsafe and actual static-analysis configuration in demo/pom.xml and demo/src/test/resources/application-test.properties; record results in specs/011-voucher-support/implementation-validation.md. Recheck next migration afterV25; no dependency/plugin/build/CI/tool installation. Document blocked prerequisites and affected scope without installing new tooling; continue independent checks under the verification execution policy.

## Phase 2: Foundation

**Goal**: Shared owned-grant schema, exact rules and integrity before all stories.

**Independent test / checkpoint**: Pure rules and real committed schema constraints pass.

- [X] T003 Prepare demo/src/test/java/com/example/demo/testdata/voucher/VoucherTestData.java and fixture/voucher/VoucherFixtures.java: create fresh pure factories and explicit persisted definitions/customer usages/one-use grants with owned cleanup; separate committed fixture/service boundaries and explicit Inventory test stock. No production seed/admin grant API or preview ownership upsert.
- [X] T004 [P] Write demo/src/test/java/com/example/demo/voucher/repository/VoucherMigrationIT.java for clean/V25 upgrade, preserved legacy orders/carts/v1 receipts/Inventory flags, all definition/state/composite ownership/history/count constraints and actual final-commit rollback; keep historical migration targets/checksums.
- [X] T005 [P] Write demo/src/test/java/com/example/demo/voucher/calculator/VoucherCalculatorTest.java and voucher/validator/VoucherValidatorTest.java for exact examples, fixed cap/floor/0.01..100 percentage boundaries, invalid precision/nonfinite amounts, optional zero cap, huge exact VND, positive base rounded0, zero base rejection, validity/limits/ownership/reason precedence.
- [X] T006 [P] Write demo/src/test/java/com/example/demo/voucher/repository/VoucherRepositoryIT.java, VoucherEntitlementRepositoryIT.java and VoucherRedemptionRepositoryIT.java for scoped scalar queries, ordered locks, exact guarded counts, composite attribution and immutable retained/duplicate history. Use PostgreSQL repository conventions, not mocks.
- [X] T007 Run foundational expected-red compile/tests with demo/mvnw.cmd and record actual missing APIs/schema/failures in specs/011-voucher-support/implementation-validation.md; do not artificially break passing tests or label unavailable infrastructure a business red.
- [X] T008 Create confirmed next migration, provisionally demo/src/main/resources/db/migration/V26__voucher_support.sql, with vouchers: "code VARCHAR(64) normalized trimmed uppercase ASCII [A-Z0-9_-]", uk_vouchers_code, "title nonblank, description nullable", "discount_type FIXED_AMOUNT/PERCENTAGE", "discount_value NUMERIC positive (fixed integral, percentage<=100 and scale<=2)", "minimum_subtotal NUMERIC integral>=0", "maximum_discount NUMERIC nullable integral>=0 (percentage only)", nullable bounds with start<end, enabled BOOLEAN, "global_usage_limit BIGINT nullable>0", "per_customer_usage_limit BIGINT nullable>0", "redeemed_count BIGINT>=0"; finite exact money and count<=configured limits. No older migration edit/seed.
- [X] T009 Extend demo/src/main/resources/db/migration/V26__voucher_support.sql with customer usages: "unique(voucher_id,customer_id) and unique(id,voucher_id,customer_id)", "redeemed_count BIGINT>=0"; entitlements with "state AVAILABLE/CONSUMED", "AVAILABLE iff consumed_at null", "CONSUMED has immutable consumed_at>=granted_at and exactly one matching redemption". All PKs id BIGINT identity, restrictive composite ownership FKs, specified uniques/indexes, immutable owner/identity/granted_at/consumed history. One row=one use; null limits never create grants.
- [X] T010 Extend demo/src/main/resources/db/migration/V26__voucher_support.sql with immutable redemptions: "Unique order_id and voucher_entitlement_id", exact order/customer, grant/voucher/customer and usage triples; "redeemed_at equals placement and consumed_at", "B>0; discount may be0". Persist all code/title/type/value/minimum/cap/base/discount/time snapshots with valid type/money rules. Add carts nullable owned grant and "voucher_selection_revision BIGINT>=0" with overflow-safe revisions; orders "voucher_discount NUMERIC NOT NULL DEFAULT0; voucher_snapshot JSONB nullable", immutable exact snapshots and payable=merchandise-discount. Preserve V23 item sums and old amounts.
- [X] T011 Implement named deferred validation in demo/src/main/resources/db/migration/V26__voucher_support.sql for final exact counters across all redemptions, consumed grant/redemption/order snapshot correspondence, no AVAILABLE redemption or retained Cart reference to its consumed grant. Acquire definition→usage→entitlement ascending locks, never reverse to existing order/cart/user/Inventory. Names<=63 bytes, uppercase SQL/types, restrictive deletion, no repair/backfill/status-based quota/cancellation policy.
- [X] T012 Implement demo/src/main/java/com/example/demo/voucher/entity/Voucher.java, VoucherCustomerUsage.java, VoucherEntitlement.java, VoucherRedemption.java, VoucherDiscountType.java and VoucherEntitlementState.java matching every field/constraint in specs/011-voucher-support/data-model.md; scalar/LAZY safe mappings, protected constructors/getters/string enums/Instant, no unsafe Lombok/cascade history loss/entity HTTP output.
- [X] T013 Implement demo/src/main/java/com/example/demo/voucher/calculator/VoucherCalculator.java and validator/VoucherValidator.java: integer hundredths percentage1..10000 without precision truncation, exact floor after optional cap then<=B, no per-item allocation; valid positive B rounded0 permitted, zero B ineligible; inclusive start/exclusive end/absent bounds and safe reason precedence. Preserve131072-digit supported amounts and checked counter arithmetic.
- [X] T014 Implement narrow demo/src/main/java/com/example/demo/voucher/repository/VoucherRepository.java, VoucherCustomerUsageRepository.java, VoucherEntitlementRepository.java, VoucherRedemptionRepository.java and VoucherMutationRepository.java/VoucherMutationRepositoryImpl.java: scalar projections, definition→usage→grant locks, bound conditional increments/AVAILABLE consumption/insertion and exact counts; no generic quota edit service, foreign repo, ownership upsert or automatic retry.
- [X] T015 Implement demo/src/main/java/com/example/demo/voucher/exception/ safe missing/inapplicable/conflict types and common/exception/ApiErrorFactory.java exact named allowlist;404 foreign/unknown,422 owned apply,409 authoritative checkout/quota/lock conflict,500 unknown. Preserve checkout CHECKOUT_CONFLICT, Cart errors and55P03/40P01 adaptation; no blanket23514/message parsing.
- [X] T016 Run foundational pure/repository/migration gates via demo/mvnw.cmd, inspect current XML and actual commits, fix scoped failures and record counts/errors/skips in specs/011-voucher-support/implementation-validation.md; do not claim later integrations complete.

## Phase 3: US1 - Cart selection and preview (P1, MVP)

**Goal**: Persist owned selection and accurate auto-clearing totals without spending grants.

**Independent test / checkpoint**: Explicit grants/Cart fixtures; all mutations, reads, failed replacement and missing-cart independently verifiable.

- [X] T017 [P] [US1] Write demo/src/test/java/com/example/demo/voucher/service/VoucherServiceImplTest.java and converter/VoucherConverterTest.java for immutable prepared preview, owned/missing/exhausted grant and no writes/upserts/hidden fetch; cart/service/CartVoucherServiceImplTest.java proves owner preflight and image preparation outside locks.
- [X] T018 [P] [US1] Write demo/src/test/java/com/example/demo/cart/controller/CartVoucherControllerTest.java for exact walletEntitlementId Long1..9223372036854775807; null/blank/string/fraction/exponent/overflow/duplicate/unknown/code/voucherId/owner overrides400, auth-first401/403, foreign/unknown404, owned ineligible422,415 media, private/no-store and absent remove204.
- [X] T019 [P] [US1] Write demo/src/test/java/com/example/demo/cart/service/CartVoucherServiceIT.java and cart/controller/CartVoucherControllerIT.java for apply/reapply/replace/remove, failed replacement preserving invalid prior selection, all item mutations, delete204 no-image cleanup, missing-cart no insertion, external GET invalidation/reason once, no reselection and zero quota/Inventory mutation.
- [X] T020 [US1] Execute US1 expected-red compile/tests through demo/mvnw.cmd; record concrete missing preview/selection APIs in specs/011-voucher-support/implementation-validation.md before implementation. Finish fixture changes before parallel writing.
- [X] T021 [US1] Implement immutable validated commands/results under demo/src/main/java/com/example/demo/voucher/dto/request/ and dto/response/, cart/dto/request/ApplyCartVoucherRequest.java, voucher/service/VoucherService.java and impl/VoucherServiceImpl.java getPreview plus pure converter/VoucherConverter.java. Prepared owner/grant/B/event, own repos only; no grant reservation/consumption/Cart callback.
- [X] T022 [US1] Extend demo/src/main/java/com/example/demo/cart/entity/Cart.java, repository/CartRepository.java and dto/response/PreparedCart.java/CartResponse.java with nullable owned selection, checked revision, prepared voucher/discount/cleared reason. Retain items/counts/selectedSubtotal, finalTotal=B-discount, no per-item price/line changes or persisted preview totals.
- [X] T023 [US1] Implement demo/src/main/java/com/example/demo/cart/service/CartVoucherService.java, impl/CartVoucherServiceImpl.java, CartVoucherTransactionService.java and impl/CartVoucherTransactionServiceImpl.java: image capability outside transaction, parent ownership lock/current subtotal, validate before setting, final apply recheck invalidation rolls replacement back, reapply stays one, removal204 and no absent-cart creation. Public proxied REQUIRED/READ_COMMITTED boundaries, no self-invocation assumption.
- [X] T024 [US1] Integrate final preview in demo/src/main/java/com/example/demo/cart/service/impl/CartTransactionServiceImpl.java, calculator/CartCalculator.java and converter/CartConverter.java: prepare authoritative eligible base once, microsecond Clock event, Voucher preview then clear locked current invalid selection/revision with safe reason. GET existing parent locks with positive timeout and write-capable transaction; no stale preflight cleanup, converter query or quantity/preference mutation.
- [X] T025 [US1] Cover all final add/quantity/variant/individual/bulk paths and void removeItem in demo/src/main/java/com/example/demo/cart/service/impl/CartTransactionServiceImpl.java: deletion computes remaining base via no-image Catalog reads and clears invalid selection before204. Preserve original stock-enforcing/nonblocking categories and target-switch rules; signing/flush/commit rejection rolls cleanup back, no network preparation under locks.
- [X] T026 [US1] Implement demo/src/main/java/com/example/demo/cart/controller/CartVoucherController.java and strict reader, exact PUT/DELETE /api/me/cart/voucher MANAGE_CART matchers in auth/config/AuthSecurityConfiguration.java, safe privacy/error adaptation; no public/code/admin route or new auth mechanism.
- [X] T027 [US1] Extend existing demo/src/test/java/com/example/demo/cart/service/CartServiceIT.java, CartTransactionServiceImplTest.java, cart/converter/CartConverterTest.java and controller/CartControllerTest.java in place for additive fields with original assertions preserved; execute US1 unit/MVC/service/API gates via demo/mvnw.cmd and record SC-001/002/003/008 in specs/011-voucher-support/implementation-validation.md. This is the risk-scoped Cart/Catalog/Inventory/Voucher checkpoint in plan.md, not a full-suite command.

## Phase 4: US2 - Atomic authoritative checkout (P1)

**Goal**: One exact grant redemption/discounted order with Inventory, Cart and immutable receipt.

**Independent test / checkpoint**: Independent committed purchase/rollback/replay/overlap proofs from prepared owned cart/grants/address.

- [X] T028 [P] [US2] Write demo/src/test/java/com/example/demo/checkout/service/CheckoutServiceImplTest.java, validator/CheckoutValidatorTest.java, converter/CheckoutConverterTest.java and order/validator/OrderValidatorTest.java for final included base/discount, distinct totals, unchanged lines, one event sampled only after all required Voucher locks and reused unchanged for placement/Inventory/grant/redemption/receipt timestamps, v1 exact keys/v2 round-trip and receipt hits zero mutable-domain work.
- [X] T029 [P] [US2] Write demo/src/test/java/com/example/demo/checkout/service/CheckoutServiceIT.java and controller/CheckoutControllerIT.java cases for fixed/percentage/rounded0, remaining grants/rows, invalid/expired/exhausted/minimum-after-skips409 without cleanup, v1/v2 recovery, late encode/write/count/actual-commit rollback and failed-key reuse.
- [X] T030 [P] [US2] Write normally discovered demo/src/test/java/com/example/demo/voucher/service/VoucherConcurrencyIT.java for different-customer final global use, multiple-grant same-customer personal exhaustion, same grant twice, failure releasing availability and expiry during an observed required Voucher-lock wait: separate lock holder, bounded worker barrier/real wait observation, controlled Clock crossing exactly validUntil or later before release,409 VOUCHER_EXPIRED and full rollback. Include before-end success with identical post-lock persisted purchase timestamps; bounded real barriers/waits, independent transactions/commits and captured worker errors, no sleeps-only/rollback-only overlap.
- [X] T031 [US2] Run US2 expected-red compile/tests via demo/mvnw.cmd and record actual missing discount/claim/version APIs in specs/011-voucher-support/implementation-validation.md; preserve existing receipt-first acquired/busy fresh-hit tests.
- [X] T032 [US2] Implement lockCheckoutVoucher/redeemVoucher in demo/src/main/java/com/example/demo/voucher/service/VoucherService.java and impl/VoucherServiceImpl.java with immutable request/result DTOs: definition→usage→grant lock acquisition and protected reread first, then sample injected Clock once at microsecond precision and validate exact identity/AVAILABLE/time/B/quota; remove pre-lock event input and return the authoritative event with the quote, conditional count increments/consume+immutable actual-order redemption; no-selection0 SQL, same REQUIRED transaction, no current Order fetch or independent commit.
- [X] T033 [US2] Extend demo/src/main/java/com/example/demo/cart/dto/response/CheckoutCartSeed.java, service/CartCheckoutService.java and impl/CartCheckoutServiceImpl.java to capture selection/revision under cart lock and clear only captured current selection with exact affected counts on new voucher-bearing success. Preserve included-row predicates/skips; never clear on receipt hit/failure.
- [X] T034 [US2] Extend demo/src/main/java/com/example/demo/order/dto/request/CreateOrderCommand.java, dto/response/CreatedOrderSeed.java, validator/OrderValidator.java and repository/OrderCreationRepositoryImpl.java with explicit merchandiseTotal/voucherDiscount/payableTotal/full prepared snapshot and separate bindings. Validate item sum/coherence without authoritative voucher recalculation; unchanged item snapshots and inventory_managed=true.
- [X] T035 [US2] Integrate demo/src/main/java/com/example/demo/checkout/service/impl/CheckoutServiceImpl.java: unchanged receipt-first/fresh checks; user/cart/catalog/sorted Inventory locks, included base, Voucher locks/quote followed by its single authoritative post-lock event (no earlier Checkout sample), reuse event for order placement/Inventory reservation/grant consumption/redemption/receipt, order/items, exact Inventory reserve, redemption, included-row removal/captured clear, receipt before proxy commit. Invalid captured voucher409, no preview cleanup/undiscounted fallback/new balance after definition lock.
- [X] T036 [US2] Evolve demo/src/main/java/com/example/demo/checkout/converter/CheckoutConverter.java, dto/response/, validator/CheckoutValidator.java and repository/CheckoutReceiptRepository.java with explicit version-preserving1/2 codecs/models and new insert2. New no-voucher0/null, old1 adds no new keys; unchanged address fingerprint/fence namespace/201/Location/131072-digit scoped mapper. Unknown/corrupt version500, never re-execute.
- [X] T037 [US2] Extend demo/src/main/resources/db/migration/V26__voucher_support.sql with version-aware version/body and ct_checkout_receipts_integrity checks: unchanged strict1 including V25 stock reasons; strict2 item sum=merchandise, merchandise-discount=payable and exact original snapshot/redemption correspondence. Insert-only comparison, immutable old rows, no replay/live-status comparison or broad whitelist weakening.
- [X] T038 [US2] Extend existing demo/src/test/java/com/example/demo/checkout/service/CheckoutConcurrencyIT.java in place for completed replays and receipt commits during acquired/busy fresh checks, selection/replace/remove versus checkout, GET-cleanup versus accepted replacement/checkout and exact grant/quota/order/Inventory/cart outcomes. Preserve all original races/timeouts/deadlocks/explicit retry; no lost later selection.
- [X] T039 [US2] Extend demo/src/test/java/com/example/demo/voucher/repository/VoucherMigrationIT.java and VoucherRedemptionRepositoryIT.java for actual-final wrong/missing/extra attribution/count/snapshot/receipt failures, multivariant rollback and V25 old receipt recovery; execute US2 unit/API/service/normal races via demo/mvnw.cmd and record SC-003/004/005/007/009 in specs/011-voucher-support/implementation-validation.md. This is the risk-scoped atomic purchase/quota/replay/rollback integration checkpoint; preserve the post-lock expiry regression.

## Phase 5: US3 - Historical discounts (P1)

**Goal**: Explain stored original discounts without live voucher lookup or cancellation integration.

**Independent test / checkpoint**: Old/new owned order paging/detail/sorting and original receipt recovery from valid fixtures.

- [X] T040 [P] [US3] Write demo/src/test/java/com/example/demo/order/converter/OrderConverterTest.java, service/OrderReadServiceImplTest.java, repository/OrderRepositoryIT.java and existing checkout/controller/CheckoutControllerIT.java / checkout/service/CheckoutServiceIT.java for legacy0/null, exact new snapshots/discounted payable sort/ownership; preserve OrderControllerIT regressions, prepared no-Voucher reads and preserved counts/previews/overflow.
- [X] T041 [P] [US3] Extend demo/src/test/java/com/example/demo/checkout/service/CheckoutServiceIT.java and voucher/repository/VoucherMigrationIT.java for later definition edit/disable/expiry, active-wallet removal, immutable history update/delete rejection and unchanged old1/new2 receipts; no cancellation/restoration test workflow.
- [X] T042 [US3] Run expected-red history tests via demo/mvnw.cmd; record missing fields/projection mismatches in specs/011-voucher-support/implementation-validation.md; never derive snapshots from edited definitions.
- [X] T043 [US3] Extend demo/src/main/java/com/example/demo/order/entity/Order.java, dto/response/OrderReadSeed.java, OrderSummaryResponse.java, OrderDetailResponse.java, repository header/detail/native long-offset projections and converter/OrderConverter.java: summary discount+compact nullable voucher; detail full immutable stored snapshot, no Voucher service/lazy query. Preserve old pageable fields/item history/payable sort.
- [X] T044 [US3] Update demo/src/test/java/com/example/demo/fixture/order/OrderFixtures.java and testdata/order/OrderTestData.java only as needed for0/null/discounted fixtures, preserving explicit Inventory managed/legacy behavior; run history gates via demo/mvnw.cmd and record SC-006/008 in specs/011-voucher-support/implementation-validation.md. This is the risk-scoped historical-order/receipt regression checkpoint; unrelated expensive suites are not repeated here.
- [X] T045 [US3] Audit demo/src/main/java/com/example/demo/order/service/impl/OrderServiceImpl.java and OrderCompletionServiceImpl.java without adding Voucher calls/hooks/status-driven quota triggers. Preserve baseline cancellation/Inventory regressions in full gate; no restoration/retention decision, reissue or new cancellation test workflow. Record scope audit in specs/011-voucher-support/implementation-validation.md.

## Phase 6: US4 - Owned administered wallet (P2)

**Goal**: Expose only owned grants with observed current applicability and no writes.

**Independent test / checkpoint**: Two-customer fixture browsing/detail, boundary paging and safe ownership independent of purchase execution.

- [X] T046 [P] [US4] Write demo/src/test/java/com/example/demo/voucher/controller/VoucherControllerTest.java for exact authenticated routes, page/size/positive Long/body/query grammar, private/no-store, foreign/unknown404/consumed own detail and no public/code/admin route; no new permission or role bypass.
- [X] T047 [P] [US4] Write demo/src/test/java/com/example/demo/voucher/controller/VoucherControllerIT.java and service/VoucherServiceIT.java for own AVAILABLE grants/distinct entries, consumed own detail, expired/disabled/exhausted reasons, empty-cart base0/no insertion and no selection cleanup/quota/stock/S3 writes. Extend cart/service/CartVoucherServiceIT.java only after this parallel wave if shared.
- [X] T048 [US4] Run expected-red wallet compile/tests via demo/mvnw.cmd; record missing owned-page/facade/routes in specs/011-voucher-support/implementation-validation.md.
- [X] T049 [US4] Implement wallet immutable DTOs/seeds/read-only getWalletPage/getWalletEntitlement in demo/src/main/java/com/example/demo/voucher/service/VoucherService.java, impl/VoucherServiceImpl.java and repositories/converter. Own AVAILABLE count; normalized code ASC+grant id ASC, page0/size20 defaults and size1..100; widened count-first empty shortcut and bound long native offset for nonempty>Integer.MAX_VALUE. No JPA narrowing/per-grant fetch/upsert.
- [X] T050 [US4] Extend demo/src/main/java/com/example/demo/cart/service/CartVoucherService.java and impl/CartVoucherServiceImpl.java to prepare no-image observational current eligible subtotal through Cart-owned read helper then VoucherService. No reverse Voucher→Cart dependency/controller orchestration/cart insertion/stale-selection cleanup while browsing.
- [X] T051 [US4] Implement demo/src/main/java/com/example/demo/voucher/controller/VoucherController.java and VoucherRequestReader.java plus auth/config/AuthSecurityConfiguration.java exact GET /api/me/vouchers and /api/me/vouchers/{walletEntitlementId} authenticated matchers/privacy. Code is definition data only, no public discovery/claim/admin route; checkout-only MANAGE_CHECKOUT remains sufficient.
- [X] T052 [US4] Add empty/count=offset/>offset and large synthetic count/boundary Long-offset tests in demo/src/test/java/com/example/demo/voucher/repository/VoucherRepositoryIT.java and service/VoucherServiceImplTest.java. Run US4 MVC/own-page/service/API gates via demo/mvnw.cmd and record SC-002/007/008 in specs/011-voucher-support/implementation-validation.md. This is the risk-scoped wallet/Cart-context/auth checkpoint, not another full run.

## Phase 7: Polish and cross-cutting gates

**Goal**: Prove all stories, normal discovery and honest completion without scope expansion.

**Independent test / checkpoint**: Full verify, evidence, source/Constitution review and hooks complete.

- [X] T053 Implement demo/src/test/java/com/example/demo/e2e/VoucherFlowIT.java real bearer wallet→Cart apply/recalculate→discounted Checkout→history→old/new replay: unchanged lines,1 chosen grant consumed/others retained, exact Inventory/skips. No admin/code/payment/cancellation integration.
- [X] T054 Execute complete demo/src/test/java/com/example/demo/voucher/service/VoucherConcurrencyIT.java and checkout/service/CheckoutConcurrencyIT.java matrix: last global/personal/same grant, expiry across an observed Voucher-lock wait (exclusive-end409/full rollback and before-end timestamp-equality control), multiple grants, cleanup/replace/remove/checkout, acquired/busy receipt freshness, held-row timeout/deadlock rollback and explicit retry. Real independent waits/commits, no optional profile/skipped original cases; record in specs/011-voucher-support/implementation-validation.md. Reconcile prior unchanged-scope evidence and execute new/missing/affected cases; retain full required matrix and actual overlap proof, no automatic duplicate execution.
- [X] T055 Measure100/101/205 Cart/wallet inputs in demo/src/test/java/com/example/demo/voucher/service/VoucherServiceIT.java, cart/service/CartVoucherServiceIT.java, checkout/service/CheckoutServiceIT.java and existing catalog/service/CatalogQueryCountIT.java. Reuse config/SqlCapture/SqlCaptureConfiguration: no-selection0 Voucher SQL, fixed preview/page reads, all native mutations, detached0 SQL, replay1 lookup/no Voucher. Preserve unchanged ceilings; document only measured additions and deferred server cost, no SLA/cart cap. Reuse valid unchanged measurements with revision/scope attribution; rerun measurements whose query/serialization/dependency risk changed.
- [X] T056 Recheck migration number and update only current/recovery assertions in demo/src/test/java/com/example/demo/catalog/CatalogMigrationsIT.java, catalog/repository/CatalogSearchMigrationIT.java, order/repository/OrderMigrationIT.java and checkout/repository/CheckoutMigrationIT.java as needed. Run clean/V25-upgrade/isolated recovery/legacy1/new2 integrity gates; preserve older targets/checksums/Inventory distinctions and no production grants/new permission seed. Reuse valid current-migration evidence where unchanged; rerun for migration/fixture/schema/recovery impacts and missing coverage.
- [X] T057 Execute focused specs/011-voucher-support/quickstart.md selectors through demo/mvnw.cmd, reconcile actual meaningful classes and inspect current-run XML timestamps/nonzero discovery/errors/skips. Fix authorized failures and record exact commands/results in implementation-validation.md; no empty/duplicate tests/stale reports/prior Inventory success substitution. Select missing/new/affected checks from the plan risk matrix rather than blindly repeating every earlier selector; record scope/rerun rationale and PASS/FAIL/BLOCKED/SKIPPED/NOT RUN evidence.
- [X] T058 Run unrestricted demo/mvnw.cmd verify including all auth/user/cart/catalog/search/performance/media/order/Inventory/wishlist/flow and Boot packaging. Inspect current Surefire/Failsafe totals plus VoucherConcurrencyIT/CheckoutConcurrencyIT normal discovery with0 required skips; record elapsed/environment/failures/fixes in specs/011-voucher-support/implementation-validation.md. No exclusions/build changes or false completion. Run at least once near feature completion. After isolated fixes rerun affected tests first; repeat this full run only for broad impact, shared-infrastructure changes or unresolved regression uncertainty, with documented rationale. Environmental inability is BLOCKED, not PASS; record exact cause/unrun cases, continue feasible independent work and leave this gate unchecked while its required execution is blocked. Resolve any executed failures with risk-appropriate verification before closure; a localized fix does not itself require a second full run. A failed full run plus a targeted fix remains separately labeled evidence, never relabeled full-suite PASS.
- [X] T059 Reconcile specs/011-voucher-support/plan.md, data-model.md, contracts/, quickstart.md, repository-context.md and implementation-validation.md with actual schema/codecs/classes/locks/errors/fixtures/evidence; update affected older Cart/Checkout/Order/Inventory deferred voucher references only for verified deltas. Preserve old immutable receipts and explicit admin/code/public-discovery/cancellation-policy exclusions.
- [X] T060 Review scoped source/test/docs against specs/011-voucher-support/spec.md and .specify/memory/constitution.md: acyclic repositories/prepared pure mapping, exact grant/counter/discount/ownership integrity, cleanup lock graph,1/2 compatibility and no excluded hooks/retry. Preserve baseline dirty work, old migrations/build/CI/checklist and single user/service/AddressServiceIT/relocated Catalog suites. Record/fix demonstrated findings with appropriate verification in implementation-validation.md. Reassess Constitution v1.2.0 Principle XIII risk/scope/checkpoint/rerun and environmental/evidence compliance; any fix follows the same targeted-first conditional rerun policy.
- [X] T061 Verify actual completed markers and SC-001–SC-009 evidence in specs/011-voucher-support/tasks.md and implementation-validation.md; check local links/diff whitespace and .specify/extensions.yml after_implement hooks. Report actual remaining operational limits; no admin provisioning/code claim/cancellation quota policy, automatic commit/push/deploy or unexecuted static-analysis claim. Distinguish actual targeted/checkpoint/full outcomes, retain failures/skips/blocks/NOT RUN cases and residual uncertainty; never complete an unfulfilled gate.

## Dependencies and execution order

```text
Setup → Foundation → US1(P1) → US2(P1) → US3(P1) → US4(P2) → final gates
```

Business criteria are independently testable from valid prepared fixtures, but shared-file integration is sequential. US1 needs grants/Cart only; US2 integrates US1 selection without waiting for wallet browsing; US3 uses persisted US2 snapshots; US4 reuses prepared Cart subtotal context without Voucher→Cart cycles. All stories and final gates required for completion.

## Parallel examples

| Story/wave | Disjoint test-writing tasks |
| --- | --- |
| Foundation | T004, T005, T006 |
| US1 | T017, T018, T019 |
| US2 | T028, T029, T030 |
| US3 | T040, T041 |
| US4 | T046, T047 |

Examples are scheduling opportunities, not execution/agent claims. Complete shared fixtures first; serialize later edits to any shared Checkout/Cart suite. Run gates sequentially.

## Implementation strategy

MVP: Setup+Foundation+US1 demonstrates owned selection/accurate auto-clearing preview with explicit grants. It is a technical checkpoint, not a discounted purchase release. Complete US2 atomic checkout and US3 history before purchase demonstration; US4 and all final gates before feature completion. No deployment/commit/push at checkpoints.

Reuse one authoritative Voucher domain; preserve old nonvoucher and v1 replay behavior before adding v2, then historical reads/wallet browsing. Failed required gates stop dependent work until scoped fixes are verified. Never weaken security/integrity/query assertions to advance.

## Traceability

| Requirements / outcomes | Tasks |
| --- | --- |
| Definition/grants/quotas FR-004–007/014; SC-001/004/007 | Foundation; US2 claim/races; US4 owned grants |
| Ownership/access FR-003/005/008; SC-003/008 | US1 strict selection; US4 wallet; final bearer flow |
| Recalculation FR-009–011; SC-001/002/003 | US1 all Cart paths/read/delete; final measurements |
| Checkout atomicity FR-012/013/020; SC-003/004/009 | US2 included base/Inventory/redemption/selection; final races |
| Recovery FR-015/016; SC-005/006 | US2 codec/schema/fresh-hit branches; final replay bounds |
| History FR-017/018; SC-006 | Foundation immutable attribution; US2 snapshots; US3 read/sorts |
| Deferred policy FR-019 | US3 audit; final source/docs scope gate |
| Meaningful tests FR-021; SC-009 | Story test-before-code gates and final normal full verify |

## Generation validation (historical)

61 sequential numbered unchecked tasks; 13 [P] tasks in five disjoint-file waves. Story tasks carry their [USn] label; setup/foundation/final tasks do not. Each task names concrete repository paths and constrained business fields. No duplicate/placeholder task or application execution. Checklist markers untouched. Next workflow: speckit-analyze before implementation. Before/after generation extension hooks checked; none registered when extensions.yml is absent.

## Implementation completion — 2026-11-20

All61 tasks fulfilled with original IDs. Evidence: [implementation-validation.md](implementation-validation.md). Unrestricted full run627 unit/MVC +654 IT PASS; subsequent localized media/Cart deferred-lock fixes33 affected unit/MVC +174 IT PASS, including all normal concurrency methods; current Boot packaging PASS. These are distinct source scopes, not a second full-run claim. No remaining required checks blocked/skipped, no source/build/Constitution exception, no automatic commit/push/deployment.
