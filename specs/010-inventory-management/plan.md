# Implementation Plan: Inventory Management

**Branch**: `feature/cart` (unchanged) | **Date**: 2026-11-19 | **Spec**: [spec.md](spec.md)
**Input**: specs/010-inventory-management/spec.md; confirmed Q1/Q2/Q3 and FAILED scope.
**Status**: Approved design implemented. See [tasks.md](tasks.md) and [implementation-validation.md](implementation-validation.md) for actual execution and gates.

## Summary

Build one Inventory domain for variant stock, derived availability and order/item-owned ACTIVE allocations. Integrate stock-aware non-reserving carts, full-quantity checkout reservation and atomic cancellation/internal completion. Preserve existing immutable201 replay before live stock evaluation. Add FAILED to owned order read/filter vocabulary only; no failure transition/processing, expiry/payment producer, customer confirmation or admin stock-management interface.

The design uses ordered balance locks plus guarded mutations under existing REQUIRED/READ_COMMITTED transactions, deferred final integrity, explicit immutable legacy participation, zero production provisioning and retained stock/allocation identity after permitted catalog deletion. Exact contracts and validation obligations are linked below; execution claims are scoped to the linked validation record.

## Technical Context

**Language/Version**: Java25; Spring Boot4.1.2 verified in demo/pom.xml.
**Primary Dependencies**: Existing Spring MVC/Security/Data JPA/Validation, PostgreSQL driver, Flyway, Jackson3 and Lombok. No new runtime/test dependency, plugin, service, messaging or CI configuration.
**Storage**: PostgreSQL; demo/src/main/resources/db/migration/V25__inventory_management.sql, confirmed after V24 and verified on disposable PostgreSQL. New inventory_balances/inventory_reservations plus additive order participation/status/integrity changes. No prior migration edit.
**Testing**: Existing JUnit5/Mockito/AssertJ, MVC slices, PostgreSQL17 Testcontainers, normal Surefire/Failsafe, real service/API/flow and synchronized independently committed concurrency. No optional race profile or payment/shipping dependency.
**Target Platform**: Existing backend runtime and disposable Docker test infrastructure; no deployment scope.
**Project Type**: Domain-oriented modular monolith in demo/.
**Performance Goals**: Availability reads batch100 distinct variants; no per-row catalog/stock fetch, new image/network call or stock-dependent listing membership. Measure100/101/205 inputs, all client SQL, detached mapping and committed replay. No invented latency/load SLA or cart row cap; deferred integrity server cost must be reported honestly.
**Constraints**: Whole units, balance0..Long.MAX_VALUE with reserved<=stock; exact existing VND amounts; cart/reservation line1..999. Stable same-kind lock order. Positive existing checkout/order/user timeouts retained, no automatic retries or new propagation/isolation. Successful receipts remain version1/201 and lifetime immutable.
**Scale/Scope**: One authoritative balance per UUID variant, one full allocation per purchased order item/order-variant. Numeric local inventory identities do not change existing IDs. Historic stock-exempt orders remain distinct from new managed checkouts. Availability exposes no customer allocation ownership or physical/reserved counter.

## Constitution Check

**Pre-research gate: PASS. Post-design gate: PASS. Implementation structure gate: PASS.** No exception required. Applicable evidence:

**C1 reassessment (2026-11-20): resolved in implementation.** T061 moved the existing ProductListingApiIT/ProductDetailApiIT suites to catalog/controller and CatalogQueryCountIT to catalog/service, with matching packages. T066 uses the corrected service path. Assertions, discovery names and fake-storage consumers are preserved; common SQL capture is extracted once into config/SqlCapture and SqlCaptureConfiguration. Compilation and focused runtime gates passed; no duplicate suites or Constitution change. Actual full-run discovery is recorded in implementation-validation.md.

| Principles | Design evidence |
| --- | --- |
| I–III domain/layers/DTOs | Inventory owns only its repositories; Checkout/Order/Cart/Catalog use its service. No reverse Inventory -> Order/Catalog dependency. Rules in validators, authoritative quantity decisions in services/calculators, converters receive prepared immutable DTOs and never query. |
| IV–VI contracts/errors/access | Existing routes/auth/current permissions/ownership preserved; additive public availability and safe stock409; no new inventory/admin/customer completion endpoint or owner/status override. FAILED reads/filter only. |
| VII–VIII schema/JPA | Additive Flyway; all new PK columns id, named constraints/FKs/indexes, TIMESTAMPTZ, scalar mappings and use-case projections/native guarded mutations; no unsafe Lombok or entity HTTP bodies/cascade loss. |
| IX–X transaction/locking | Public proxied orchestration, REQUIRED/READ_COMMITTED, stable catalog/balance and existing-order/balance order, no self-invoked transaction assumption, no network under stock locks, actual commit checks. |
| XI–XII storage/time | Inventory has no S3 dependency; no expiring URLs in receipts. One UTC Clock event per first terminal action/reservation timestamp, persisted microsecond precision. Repeats do not resample or rewrite. |
| XIII meaningful verification | Owning domain/layer tests; explicit stock/legacy fixtures, required different-customer shared-stock races, actual commit/rollback and normal discovery; no duplicate/placeholder suites or skipped required scenarios. |
| XIV–XVII quality/scope | No static-analysis/dependency/build/CI installation, unrelated refactor, commit/push/deploy or scope expansion. Missing runtime gates are not claimed passing. |

Existing wildcard CORS origin configuration remains a recorded pre-existing issue, not Inventory deployment-policy work. The current feature-local idempotency/concurrency rules remain approved; no project-wide policy change.

## Project Structure

### Documentation (this feature)

```text
specs/010-inventory-management/
  spec.md
  repository-context.md
  integration-review.md
  checklists/requirements.md
  plan.md
  research.md
  data-model.md
  contracts/api.md
  contracts/internal-services.md
  quickstart.md
```

[tasks.md](tasks.md) records the approved execution sequence. [implementation-validation.md](implementation-validation.md) records actual Inventory gates separately from older Checkout results.

### Source Code (implemented, repository root)

All main Java paths below are under demo/src/main/java/com/example/demo/:

```text
inventory/
  entity/InventoryBalance.java, InventoryReservation.java, ReservationState.java
  repository/InventoryBalanceRepository.java, InventoryReservationRepository.java
  repository/InventoryMutationRepository.java, InventoryMutationRepositoryImpl.java
  service/InventoryService.java, impl/InventoryServiceImpl.java
  validator/InventoryValidator.java
  converter/InventoryConverter.java
  dto/request/ReservationCommand.java, ReservationLine.java, ResolutionCommand.java
  dto/response/InventoryAvailability.java, InventoryAllocationSeed.java
  exception/InventoryConflictException.java
order/
  service/OrderCompletionService.java, impl/OrderCompletionServiceImpl.java
  dto/response/OrderInventorySeed.java
cart/
  exception/CartStockConflictException.java
```

Extend existing owning files rather than duplicate workflows:

- cart/service/impl/CartTransactionServiceImpl.java, validator/CartValidator.java, calculator/CartCalculator.java, converter/CartConverter.java and dto/response/PreparedCart.java/CartItemResponse.java: actual resulting-quantity validation, prepared row-specific stock eligibility, totals/reasons, non-reserving preferences.
- catalog/service/ProductCatalogServiceImpl.java, CatalogCartReadServiceImpl.java; catalog/converter/ProductCatalogConverter.java; dto/response/ProductListingItem.java, VariantSummary.java and CartVariantState.java: batched prepared availability, no shared ProductSummary/wishlist expansion or stock-based search filtering.
- checkout/service/impl/CheckoutServiceImpl.java, validator/CheckoutValidator.java and dto/response/SkippedCheckoutItem.java: protected availability/full-row stock classification, attributed allocation and preserved receipt-first/fresh-check sequencing. Existing calculator/converter remain prepared/pure.
- order/service/impl/OrderServiceImpl.java, service/impl/OrderCreationServiceImpl.java, repository/OrderCreationRepositoryImpl.java, repository/OrderItemRepository.java, entity/Order.java/OrderStatus.java and validator/OrderValidator.java: explicit managed insertion, prepared owned-item seed, cancellation release, internal completion and six-status compatibility. No OrderController completion action.
- common/exception/ApiErrorFactory.java: preserve checkout409 adaptation and safely recognize only named new integrity conflicts. No broad conversion of unrelated SQL/persistence faults into stock skips/conflicts.
- resources/db/migration/V25__inventory_management.sql (implemented): described in data-model.md; never copy the removed premature implementation or edit applied V23/V24.

Implemented tests under demo/src/test/java/com/example/demo/:

```text
inventory/validator/InventoryValidatorTest.java
inventory/converter/InventoryConverterTest.java
inventory/service/InventoryServiceImplTest.java, InventoryServiceIT.java, InventoryConcurrencyIT.java
inventory/repository/InventoryBalanceRepositoryIT.java, InventoryReservationRepositoryIT.java, InventoryMigrationIT.java
order/service/OrderCompletionServiceImplTest.java, OrderCompletionServiceIT.java
e2e/InventoryFlowIT.java
fixture/inventory/InventoryFixtures.java
testdata/inventory/InventoryTestData.java
```

These are responsibility-based targets, not orders to create empty classes or accessor tests. Reuse existing cart/catalog/checkout/order/controller/service/repository/converter/validator suites for changed behavior, including existing CheckoutConcurrencyIT. Shared test infrastructure stays in config only when real duplication is removed. Existing AddressServiceIT stays in user/service with no duplicate. See quickstart.md for focused selectors/regression responsibilities.

T061 relocated the existing catalog suites under demo/src/test/java/com/example/demo/ to catalog/controller/ProductListingApiIT.java, catalog/controller/ProductDetailApiIT.java and catalog/service/CatalogQueryCountIT.java. Packages, imports, accessibility and performance consumers were updated while retaining class names, assertions, scope and normal Failsafe selectors. Shared capture lives once in config/SqlCapture.java and SqlCaptureConfiguration.java; no duplicate suites.

**Structure Decision**: One inventory foundation reused by existing workflows, with a private order-owned completion boundary; no new web API or parallel checkout/lifecycle engine.

## Phase0 — Research Results

[research.md](research.md) resolves numeric representation, owned allocation integrity, explicit legacy rollout, retention, lock ordering including V3/V23 triggers, existing timeout policies, DTO/receipt compatibility, errors and meaningful concurrency/query verification. Read-only agents and official PostgreSQL/Spring sources informed decisions; no application checks were run.

## Phase1 — Design and Contracts

- [data-model.md](data-model.md): fields, ownership, constraints, mutation/deletion lifecycle, final-commit integrity, rollout and migration compatibility.
- [contracts/api.md](contracts/api.md): existing route deltas, nested availability/null semantics, cart409 and subtotal rules, two new skip reasons, FAILED and explicit exclusions.
- [contracts/internal-services.md](contracts/internal-services.md): precise service graph/commands, transaction/lock order, reserve/consume/release and trusted internal completion, absence of payment/expiry handlers.
- [quickstart.md](quickstart.md): later runnable validation scenarios, focused/full commands, measurable query/commit/race evidence and honest completion register.

Post-design assessment finds no unresolved business or technical clarification. Admin supply and public confirmation remain future features; FAILED transition/processing and payment/expiry producers remain absent. Their future invariant obligations do not authorize present placeholders.

Final read-only review resolved concrete design clarifications: select an atomic zero-provisioning variant trigger, reject allocations for legacy parents and terminal-state bootstrap inserts, and limit final cart stock409 rechecks to the four stock-enforcing individual mutations rather than bulk/deselect/read paths. Cross-document links, placeholder/whitespace checks and document-only scope pass; these are documentation checks, not application verification.

## Implementation sequencing boundaries

Task generation should order foundation/zero provisioning/integrity and tests before integrating availability, carts, checkout and order terminals. Preserve existing receipt recovery before adding stock work. Default-managed rollout requires application+schema coordination: old binaries cannot continue committing unreserved new orders. Implement stock/reservation fixtures explicitly and retain legacy upgrade coverage. Add FAILED compatibility without weakening existing cancellation/history tests; then run focused and full gates and update older deferred sections from actual results.

The approved sequence is recorded in tasks.md; actual execution and rollout limits are recorded in implementation-validation.md.

## Workflow execution

setup-plan PowerShell equivalent was used because Bash is unavailable on this Windows host. It resolved the active template and absolute FEATURE_SPEC/IMPL_PLAN/FEATURE_DIR paths. Its BRANCH value010-inventory-management is the feature alias; actual Git branch remains feature/cart. No branch switch. Before/after .specify/extensions.yml absent, no hooks. Source, tests, migrations, dependencies, build/CI, approved specification/checklist markers and existing branch history remain unchanged by planning.


## Voucher integration follow-up — feature011

Feature011 composes existing Inventory reservation with atomic Voucher purchase; it does not deduct stock early, change reservation lifecycle or add payment/admin/confirmation/FAILED transition producers. Cart estimated payable now subtracts the order-level Voucher discount from the existing eligible selected subtotal, with item and stock rules unchanged. Verified targeted deltas and the separately tracked unrestricted completion gate are recorded in [Voucher execution evidence](../011-voucher-support/implementation-validation.md). Earlier completed tasks and their historical execution records are preserved; this follow-up adds no admin/code/public-discovery/cancellation-quota contract.
