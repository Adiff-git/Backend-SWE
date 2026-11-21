# Implementation Plan: Voucher Support

**Branch**: `feature/cart` (unchanged) | **Date**: 2026-11-20 | **Spec**: [spec.md](spec.md)
**Input**: Approved owned-wallet custom Q1, calculation Q2 and explicitly deferred cancellation Q3.
**Status**: Voucher, Cart preview, atomic Checkout, immutable Order history and owned-wallet implementation completed in the working tree. Targeted checkpoints are recorded in [implementation-validation.md](implementation-validation.md); unrestricted verification passed on its preserved snapshot; subsequent localized fixes passed affected regressions and current packaging. All61 tasks fulfilled. No deployment/commit/push.

## Summary

Build one Voucher domain owning definitions, customer usage relations, explicit one-use administrator-granted entitlements and immutable order redemptions. Cart owns one selected entitlement, exact live price preview and automatic invalid-selection cleanup. Checkout independently revalidates after final skipped-item/stock classification, atomically consumes only the chosen grant and quota with order/reservations/cart/receipt. Keep item amounts unchanged. Use version2 new receipts while replaying historical1 unchanged. No admin/public-code/claiming/cancellation integration.

## Technical Context

**Language/Version**: Java25, Spring Boot4.1.2 verified in demo/pom.xml.
**Primary Dependencies**: Existing MVC/Security/Data JPA/Validation, PostgreSQL/Flyway, Jackson3/Lombok, JUnit5/Mockito/AssertJ/Testcontainers. No new dependency/plugin/tool/CI change.
**Storage**: PostgreSQL; V26__voucher_support.sql after baseline V25, rechecked during implementation. Additive new ownership/history tables, cart selection, order discount snapshots and versioned receipt validation. No old migration edit.
**Testing**: Owning layer unit/MVC/repository/service/API and cross-domain flow; normal Surefire/Failsafe synchronized independently committed races. No optional profile/skipped required overlap or duplicate suite.
**Target Platform**: Existing modular backend with disposable PostgreSQL17/LocalStack tests; no deployment.
**Project Type**: Domain-oriented modular monolith in demo/.
**Performance Goals**: Fixed Voucher work per cart observation; paginated owned wallet;100/101/205 measured batches/client/native SQL plus deferred server costs; detached0-SQL and completed replay1 receipt read/no Voucher work. No invented SLA or cart cap.
**Constraints**: Exact whole-VND BigInteger money, percentages<=2 decimals, floor once; original1..999 quantities and stock logic retained. REQUIRED/READ_COMMITTED; configured positive existing timeouts; one authoritative microsecond UTC event sampled only after all required Voucher locks, reused for eligibility/order/Inventory/grant/redemption/receipt timestamps, no auto retry/unusual propagation/network under purchase locks.
**Scale/Scope**: At most one selected entitlement and one redemption/order; multiple grants per customer/definition remain independent under shared limits. Grants require external administrator provisioning; no positive production seeds or admin endpoint. Cancellation policy remains deferred despite existing cancellation source.

## Constitution Check

**Pre-research gate: PASS (historical design assessment). Post-design reassessment against Constitution v1.2.0: PASS for the documented plan/task structure. C1 resolved by explicit risk-based scope, checkpoints, rerun criteria and environmental/evidence rules below. Implementation architecture reviewed against these same gates; targeted execution evidence is recorded separately and the unrestricted completion run passed, followed by current affected regression/packaging checks. No exception or Constitution change.**

| Principles | Evidence |
| --- | --- |
| I–III boundaries/DTO/rules | Voucher owns only its repositories; Cart-owned wallet facade avoids reverse dependency. Prepared immutable DTOs, Voucher calculator/validator/converter, Order coherence validation without repricing. |
| IV–VI HTTP/access | Exact owned wallet routes/auth identity, existing cart/checkout/order permissions; no admin bypass/owner override/code route. Safe ProblemDetail/private no-store, explicit null/input semantics. |
| VII–VIII integrity/JPA | Additive V26, local id BIGINT, restrictive composite ownership FKs, exact checks/commit reconciliation, no cascading history loss, scalar/LAZY mappings and safe Lombok. |
| IX–X atomicity/concurrency | Public proxied transactions; cart parent protects cleanup; catalog/stock before definition→usage→grant order; conditional quota and exact final attribution, no new Order terminal locks/hooks. |
| XI–XII storage/time | Existing image preparation outside transactions preserved; no persisted expiring URLs. One Clock instant per evaluation/purchase, Instant/TIMESTAMPTZ and original snapshots. |
| XIII meaningful tests and risk-based verification | Risk-to-test/checkpoint matrix below, targeted-first changes/fixes, T058 unrestricted full gate, conditional full reruns and explicit environmental/status reporting. Required real security/transaction/rollback/quota/expiry/replay/performance coverage and domain/layer discovery preserved; original suites reused without duplicates. |
| XIV–XVII scope/evidence | Implementation and measured targeted evidence recorded without a static-analysis claim, build/dependency/CI edits or commit/push/deployment. Existing dirty Inventory work preserved. |

Existing wildcard production CORS remains a pre-existing configuration issue outside Voucher planning; do not claim project-wide deployment compliance. Existing refresh/social flows are preserved, no new auth mechanism assumed. The approved spec explicitly permits GET Cart to reconcile stale voucher selection; write-capable service transaction implements that narrow exception without item changes. Cancellation quota is deferred, not a gate failure or authorization for either lifecycle policy.

## Risk-based verification — Constitution v1.2.0, Principle XIII

For each implementation change, inspect changed behavior, callers/dependencies, shared schema or
infrastructure and potential regression impact. Record the risk and choose the smallest sufficient
verification scope: compilation plus the affected unit/MVC/repository/service/API tests and connected
contracts. A checkpoint widens to the relevant cross-domain paths below, not automatically to every
suite. Risk-based selection never removes required cases or weakens assertions/real transaction proof.

| Risk / affected change | Targeted checks | Cross-domain checkpoint / tasks |
| --- | --- | --- |
| Definition/calculation/rounding/large money | VoucherCalculatorTest, VoucherValidatorTest, VoucherMigrationIT definition constraints | T016 foundation; T027 Cart subtotal/preview and T039 Checkout/order exact totals; unchanged item amounts |
| Identity, entitlement ownership, strict API/auth/cache | Voucher/CartVoucher controller slices and real APIs; caller/foreign parity, current identity/grants, checkout-only permission | T027 Cart selection; T052 own wallet; T053 bearer wallet→Cart→Checkout→Order flow |
| Cart selection/recalculation/cleanup/deletion | CartVoucherServiceImplTest/IT, existing changed Cart tests; failed replacement, missing-cart, no-image delete | T027 Cart→Catalog→Inventory→Voucher; T038/T039 cleanup/replace/checkout overlap protects later selections |
| Order/schema/redemption/usage mutations | Voucher repository/migration tests, CheckoutServiceIT: actual commit, foreign/missing attribution, counters, late write/encoding failure | T039 Checkout→Order→Inventory→Voucher→Cart→receipt atomicity/rollback; T056 clean upgrade/recovery and legacy integrity |
| Quota/entitlement/lock/time changes | VoucherConcurrencyIT and affected CheckoutConcurrencyIT cases: real independent waits, last global/personal/same grant, timeout/deadlock | T039 connected checkout races; T054 confirms complete required matrix, including post-lock expiry rejection at validUntil and before-end timestamp equality |
| Receipt codec/idempotency/fingerprint changes | CheckoutConverterTest, CheckoutValidatorTest, CheckoutServiceImplTest and receipt service/API cases | T039/T044 original v1/new v2 recovery and acquired/busy fresh checks; zero replay usage/stock/cart/selection mutation |
| Historical read/projection/sort/snapshot changes | Order converter/read/repository/controller tests, legacy0/null and discounted payable sort | T044 Order/history→original Checkout receipt; no live Voucher lookup or item repricing |
| Voucher query/serialization/paging performance | Affected Voucher/Cart/Checkout query tests, shared SqlCapture, 100/101/205 inputs, overflow, detached0 SQL | T055 measured fixed additions/replay1 lookup and deferred server cost; Catalog query ceilings retained when affected |
| Shared framework/configuration or broad dependency impact | Affected tests first; identify all dependent domains and uncertainty | T058 full regression near completion; repeat full verification when impact is broad/shared-infrastructure or regression uncertainty remains |

After an isolated fix, rerun affected tests first and document why that evidence is sufficient.
Do not automatically repeat the full suite or unrelated expensive media/search/benchmark work.
Repeat full verification when the fix has broad impact, changes shared infrastructure, or leaves
unresolved regression uncertainty; record the concrete trigger and dependency rationale. Preserve
all affected security, discount accuracy, transaction/rollback, quota/concurrency, post-lock expiry,
immutable replay and performance scenarios. Failure of a required assertion is not an environment
waiver. A narrow passing rerun does not turn an earlier failed full run into a passing full run.

T058 requires at least one unrestricted full `mvn verify` from demo/ via the existing wrapper near
feature completion, including normal discovery/packaging, without exclusions or skip flags. T016,
T027, T039, T044 and T052 are scoped integration checkpoints, not additional full-suite runs.
T054–T057 reconcile complete required evidence; reuse valid results for unchanged code/dependency
scope and run missing/new/affected cases rather than blindly repeating every earlier selector.
Record which run satisfies each obligation; no required scenario may be silently omitted. The full
run may supply normal-discovery confirmation, but targeted evidence must still identify the risks.

When Docker/Testcontainers, PostgreSQL, LocalStack or another prerequisite is unavailable, record
the exact failed/blocked command, cause, affected cases and residual uncertainty. Continue feasible
independent compilation/pure/MVC work; do not replace required real persistence/concurrency tests
with mocks or selective execution labeled as a full run. Restore only authorized existing resources
and retry when available. Keep affected tasks/gates incomplete while blocked, disclose limitations,
and do not claim fully verified completion. No new tool/dependency/infrastructure scope is implied.

Use the PASS/FAIL/BLOCKED/SKIPPED/NOT RUN definitions and evidence fields in
[quickstart.md](quickstart.md). Records include exact commands/selectors, revision or identified dirty
working state, scope/rationale, environment, timestamps/current reports, counts/results, failures/fixes,
skips/limitations and remaining uncertainty. Separate targeted/checkpoint/full-suite outcomes and
retain old failures with their original revision attribution. Planning-only synchronization ran no application tests; subsequent implementation commands/results are recorded in implementation-validation.md.

## Project Structure

### Documentation (this feature)

```text
specs/011-voucher-support/
  spec.md
  repository-context.md
  checklists/requirements.md
  plan.md
  research.md
  data-model.md
  contracts/api.md
  contracts/internal-services.md
  quickstart.md
```

[tasks.md](tasks.md) defines61 implementation tasks with their original IDs; the earlier policy-only synchronization preserved unchecked state, and implementation now marks only fulfilled work. Research decisions are in [research.md](research.md); persisted invariants in [data-model.md](data-model.md), route/prepared-service contracts under contracts/, and validation guide in [quickstart.md](quickstart.md).

### Source Code (planned, repository root)

All main paths under demo/src/main/java/com/example/demo/:

```text
voucher/
  controller/VoucherController.java, VoucherRequestReader.java, VoucherCacheFilter.java
  service/VoucherService.java, impl/VoucherServiceImpl.java
  repository/VoucherRepository.java, VoucherCustomerUsageRepository.java
  repository/VoucherEntitlementRepository.java, VoucherRedemptionRepository.java
  repository/VoucherMutationRepository.java, VoucherMutationRepositoryImpl.java
  entity/Voucher.java, VoucherCustomerUsage.java, VoucherEntitlement.java, VoucherRedemption.java
  entity/VoucherDiscountType.java, VoucherEntitlementState.java
  calculator/VoucherCalculator.java
  validator/VoucherValidator.java
  converter/VoucherConverter.java
  dto/request/   # applicable immutable commands/validated inputs
  dto/response/  # wallet page, prepared quote, preview and immutable snapshots
  exception/     # safe missing ownership/inapplicability/conflict types
cart/
  controller/CartVoucherController.java
  service/CartVoucherService.java, impl/CartVoucherServiceImpl.java
  service/CartVoucherTransactionService.java, impl/CartVoucherTransactionServiceImpl.java
  service/CartPreviewService.java, impl/CartPreviewServiceImpl.java
  service/CartWalletReadService.java, impl/CartWalletReadServiceImpl.java
resources/db/migration/V26__voucher_support.sql
```

Names are concrete responsibilities, not instructions to create placeholder packages/accessor tests. Reuse a single Cart-owned preparation helper if it removes actual duplication. Changes to existing CartService/CartTransactionService/CartCheckoutService, Cart entity/repository/seeds/DTO/calculator/converter must preserve existing item behavior and add selection/subtotal context. Wallet controller delegates Cart-owned facade to prepare current subtotal without Voucher→Cart callback.

CheckoutServiceImpl/Validator/Converter/ReceiptRepository/receipt DTOs evolve in place with explicit version-aware codec/response representation; retain exact v1 shape. Order creation command/repository/validator/read projections/summary/detail/converter add prepared discount snapshots and distinct bound totals. AuthSecurityConfiguration exact wallet authenticated matchers and cart voucher permission matchers; no new grant. ApiErrorFactory recognizes only specific new constraints/safe domain exceptions. No OrderService cancellation/completion Voucher integration.

Planned meaningful tests under demo/src/test/java/com/example/demo/:

```text
voucher/calculator/VoucherCalculatorTest.java
voucher/validator/VoucherValidatorTest.java
voucher/converter/VoucherConverterTest.java
voucher/controller/VoucherControllerTest.java, VoucherControllerIT.java
voucher/service/VoucherServiceImplTest.java, VoucherServiceIT.java, VoucherConcurrencyIT.java
voucher/repository/VoucherRepositoryIT.java, VoucherEntitlementRepositoryIT.java
voucher/repository/VoucherRedemptionRepositoryIT.java, VoucherMigrationIT.java
cart/controller/CartVoucherControllerTest.java, CartVoucherControllerIT.java
cart/service/CartVoucherServiceImplTest.java, CartVoucherServiceIT.java
fixture/voucher/VoucherFixtures.java
testdata/voucher/VoucherTestData.java
e2e/VoucherFlowIT.java
```

Extend existing Cart/Checkout/Order/Inventory domain-layer suites and CheckoutConcurrencyIT, catalog/controller/ProductListingApiIT/ProductDetailApiIT and catalog/service/CatalogQueryCountIT only where changed behavior warrants assertions. Shared capture remains config/SqlCapture and SqlCaptureConfiguration. Existing user/service/AddressServiceIT stays single. No tests at voucher root or top-level integration/api packages; no duplicate suites mechanically generated for each DTO/entity.

**Structure Decision**: One authoritative Voucher domain plus Cart-owned orchestration, integrated into current checkout, not a parallel payment/ordering system. New identifiers are local BIGINT, not a global policy change.

## Phase0 — Resolved research

No technical clarification remains. Two read-only agents inspected Cart/auth and Checkout/receipt/trigger boundaries. Decisions resolve one-use grant retention, exact percentages, cart read/delete cleanup, acyclic wallet applicability, quota locks, v1/v2 integrity and schema rollout. [research.md](research.md) records decisions, rationale and alternatives. No application commands ran.

## Phase1 — Design and validation boundaries

[API contract](contracts/api.md) fixes owned routes/strict entitlement-only input, errors, additive Cart/Order output and version-preserving receipts. [Internal contract](contracts/internal-services.md) fixes prepared interfaces, atomic sequence, full lock graph and query limits. Model defines exact ownership/usage/attribution/time/snapshot constraints and legacy rollout. Quickstart names later meaningful selectors and required real-commit/overlap/bounds checks without producing test code.

Post-design review confirms no new cancellation quota policy, code claiming/public discovery/admin API, receipt rewrite, per-item discount, lost remaining grants or infinite-ownership shortcut. A valid voucher may round to0 discount; it still spends the explicitly selected entitlement once. Failed checkout preserves selection/grants and all items; preview auto-clear cannot run on its validation path. U1 resolved in design: Voucher lock acquisition precedes Clock sampling and validity validation; the quote returns the sole purchase event, with no pre-lock timestamp argument. Existing concurrency/unit tasks include observed-wait expiry rejection and before-end success timestamp equality.

## Workflow execution

PowerShell setup-plan equivalent used because this Windows repository provides it; active template resolved by setup. FEATURE_DIR resolves011-voucher-support; returned BRANCH is the feature alias, actual Git branch stays feature/cart. Before/after extensions.yml checks are required; absent at setup. Specification checklist remains read-only16/16; old source/migration/test/build/CI and dirty Inventory work untouched. The planning step did not create tasks; later task generation created61 pending tasks. This documentation synchronization executes no application verification, commit/push or deployment.

## Implementation reconciliation

V26 preserves zero/null legacy additions without inventing discounts or changing old payable values. The arithmetic CHECK exempts only retained nonmanaged zero/null history; all managed new orders, voucher-bearing orders, creation commands and version2 receipts require exact merchandise-discount=payable arithmetic. Immutable guards protect original voucher-bearing financial/placement/owner values. Deferred Voucher order reconciliation runs on insert/delete, not status-only updates; no cancellation/completion quota hook exists.

Order entities retain raw JSON text; OrderReadServiceImpl materializes immutable typed snapshots under the scoped131072-digit parser before pure conversion. Normal and native long-offset projections preserve this boundary. New receipt inserts persist version2; explicit1/2 decoding retains original1 bodies unchanged. CartPreviewService centralizes prepared subtotal/reason mapping; CartWalletReadService prepares observational context without cleanup/images. Wallet uses one owned AVAILABLE count plus one joined projection with a bound long OFFSET.

Meaningful assertion placement follows the existing domain/layer suites: final immutable attribution/history tests are in VoucherMigrationIT plus CheckoutServiceIT; real Order API discount assertions extend CheckoutControllerIT alongside existing OrderControllerIT regression. Existing classes remain single and normally discovered; no duplicate tests were created merely to match illustrative file lists. See execution evidence for exact selected scopes and separate full-snapshot/current-affected status.

Final lock review reproduced PostgreSQL40P01 between a Cart selection FK key-share and the purchase's deferred grant locks. Cart-only final validation now checks consumed state without quota locks; quota mutation reconciliation keeps the approved ordered locks and full retained-reference checks. A synchronized real-commit regression plus consumed-selection negative guards protect both liveness and integrity. Local route media mapping also preserves compatible JSON/empty400/unsupported415 semantics. The execution register distinguishes the unrestricted pre-fix source snapshot from current affected regressions and packaging; unrelated expensive suites are not repeated without regression risk.

## Final Constitution reassessment — PASS

Constitution v1.2.0, including Principle XIII, is satisfied by recorded risk-based targeted gates, connected integration checkpoints, one unrestricted full verify (627+654 cases), affected-first correction of demonstrated local defects and current33-unit/174-IT regression success with repackaging. No required security/transaction/rollback/quota/expiry/replay/performance scenario was removed or skipped. Full pre-fix snapshot and final affected execution are explicitly distinguished; no unnecessary second search/media benchmark run, unexecuted static-analysis claim, Constitution amendment or exception. All61 tasks complete; checklist16/16 unchanged. Admin/code/payment/confirmation/FAILED producers and cancellation-quota policy remain deferred.
