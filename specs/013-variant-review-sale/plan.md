# Implementation Plan: Variant Selection, Review Queries and Direct Sales

**Branch**: `draft/version-1` | **Date**: 2026-11-26 | **Spec**: [spec.md](spec.md)

**Input**: `specs/013-variant-review-sale/spec.md`, confirmed Q1=A/Q2=A/Q3=A.

## Summary

Extend the existing catalog, review and purchase flows. Store product-wide percentage/validity on Product and per-variant authoritative base prices; derive effective prices through one explicit-time database pricing function. Reuse current parent/variant locks, pass one purchase instant through checkout and voucher evaluation, and introduce receipt v3 while retaining v1/v2. Derive canonical option metadata from existing maps with database-backed normalization/integrity. Extend existing review queries and obtain purchased options through a bounded Order service projection.

## Technical Context

**Language/Version**: Java 25; Spring Boot 4.1.2, verified in `demo/pom.xml`.

**Primary Dependencies**: Existing Spring MVC/Security/Data JPA, Hibernate, Jakarta validation, Lombok, Jackson 3, PostgreSQL driver, Flyway and springdoc. No new dependencies/build plugins.

**Storage**: PostgreSQL 17 (`17-alpine` in test profile and pinned local Compose). Existing S3/LocalStack image lifecycle remains unchanged. New functions/constraints/migrations only; no materialized discounted-price scheduler.

**Testing**: Existing JUnit 5, Mockito, AssertJ, MockMvc and PostgreSQL Testcontainers. `demo/pom.xml` binds Failsafe integration-test/verify; actual Test/IT discovery must be inspected during implementation. Spotless/Checkstyle/SpotBugs/PMD are not configured in this POM; no passing static-analysis gate is claimed.

**Target Platform**: Existing containerized JVM backend and PostgreSQL; no new compute hosting choice.

**Project Type**: Domain-oriented modular monolith web service, module `demo/`.

**Performance Goals**: Preserve catalog007 approved ~1,500-product workload: p95 <500ms browse/category+brand/price+specification, <1000ms normal/selective keyword, <1500ms broad/fuzzy; each of two independent serial JVM runs, 20 warmups/100 measured size-20 requests per scenario. Preserve existing SQL-count limits and bounded fetching. Review page size remains <=100 and enrichment is batched, never per row.

**Constraints**: Whole nonnegative VND, percentage hundredths, floor unit discount, explicit UTC microsecond pricing instant, no duplicate calculations, additive migration/data preservation, no management API, no sale engine or mixed-version pricing writers during cutover.

**Scale/Scope**: Existing catalog/search workload and customer review/cart/order paths; product-wide sales only, independently priced variants, no frontend implementation.

## Constitution Check

Gate evaluated before research and after design against constitution v1.2.0. Both assessments PASS at design level; runtime acceptance is unexecuted.

| Principle/gate | Pre-research | Post-design evidence |
| --- | --- | --- |
| I–III domain ownership, acyclic services and DTO responsibilities | PASS: extend existing domains | Catalog owns pricing/options; Review calls Order read interface; Checkout orchestrates Catalog/Inventory/Voucher/Order/Cart; converters only map prepared data. No reverse service edge introduced. |
| IV–VI HTTP, ProblemDetail and security | PASS: retain public/private routes | Strict query contract; ownership/permissions unchanged; no management routes/grants; safe historical projection. |
| VII–IX persistence, lazy fetches and transaction integrity | PASS: additive-only design | New fields/functions and deferred checks; preserve references/history, locks and old receipt validators; bounded projections. |
| X–XII calculations, storage and timestamps | PASS: existing exact VND/UTC | One canonical DB price function, explicit Instant, unchanged image/storage lifecycle; no temporary URLs in snapshots. |
| XIII meaningful risk-based verification | PASS: plan must specify evidence | Targeted checks, cross-domain checkpoints, deterministic concurrency, upgrade/replay/performance tests and unrestricted final verify below. |
| XIV–XVII quality, dependencies and scope | PASS: documentation only | No new library/plugin, auto-commit/push, deployment, infrastructure edits or implementation in this invocation. Missing quality tooling disclosed. |

Post-design prerequisites are implementation gates, not unresolved business choices: verify ICU root-lowercase capability on the pinned PostgreSQL image before schema rollout; audit real legacy data; verify new SQL/receipt rules through real PostgreSQL tests. Do not waive failed gates or broaden scope silently.

## Project Structure

### Documentation (this feature)

```text
specs/013-variant-review-sale/
  spec.md
  repository-context.md
  plan.md
  research.md
  data-model.md
  contracts/api.md
  contracts/engineering.md
  quickstart.md
  checklists/requirements.md
```

`tasks.md` is the next command's output and is not generated here.

### Source Code (repository root)

```text
demo/src/main/java/com/example/demo/
  catalog/{entity,repository,service,validator,converter,dto,search}/
  review/{repository,service,validator,converter,dto,controller}/
  cart/{service,calculator,converter,dto}/
  checkout/{service,calculator,converter,repository,dto}/
  order/{entity,repository,service,converter,dto}/
  voucher/{service,dto}/
demo/src/main/resources/db/migration/
demo/src/test/java/com/example/demo/
  catalog/{repository,service,controller,converter}/
  review/{repository,service,controller,validator,converter}/
  cart/{service,calculator,converter}/
  checkout/{repository,service,controller,calculator,converter}/
  order/{service,repository,converter}/
  voucher/service/
  fixture/{catalog,review,checkout,voucher}/
  e2e/
demo/src/test/resources/
```

**Structure Decision**: Modify real owning domains and existing test responsibilities. New meaningful classes are identified in engineering contracts; do not mechanically create empty classes/directories. All source paths above are planned implementation scope, not claims that files were changed.

## Phase 0 — Research Decisions

Completed in [research.md](research.md): price authority and SQL reuse, normalization ownership, consistent time/locks, safe review filters/history, receipt compatibility, additive rollout and verification. Repository investigations were delegated to two read-only research agents as required by the planning skill. No external library guidance was needed to establish current source contracts; unexecuted infrastructure capabilities are explicitly rollout gates.

## Phase 1 — Design and Contracts

Completed [data-model.md](data-model.md), [public contracts](contracts/api.md), [engineering contracts](contracts/engineering.md) and [quickstart validation](quickstart.md). No production code, migrations, build configuration or tests are implemented by planning.

### Implementation sequence for task generation

1. Install all V28 -> V29 -> V30 schema in Foundation, including capability/data preflight, canonical identity and version-aware receipt-v3 expansion. Keep v1/v2 intermediate writes valid, adapt base mappings/fixture writers and pass current no-sale behavior after each migration stage. Never enable out-of-order or active sales before full US2 integration.
2. Integrate catalog projections, dimension metadata, representative/range prices and search SQL using one instant.
3. Integrate immutable operation-scoped PreparedOperationPricing into cart mutations/preview/wallet and reuse its coherent one-statement inputs/evaluation through validation/totals/response. Independently capture checkout protected view/time, pass voucher time, update native OrderCreationRepositoryImpl INSERT columns/bindings and verify committed order/receipt snapshots by fresh PostgreSQL reload.
4. Extend review filters/counts/distribution and bounded historical option projection.
5. Complete cross-domain, concurrency, upgrade, security, performance and final verification gates; retain evidence by tested revision.

### Risk-based verification and integration checkpoints

| Risk/increment | Targeted verification | Integration checkpoint |
| --- | --- | --- |
| SQL normalization/pricing/migration | Function boundary/Unicode tests, direct SQL integrity, deferred commit checks, legacy upgrade fixtures | CP1: catalog detail/search plus old data/receipt compatibility |
| Catalog effective pricing/search | Projection/MVC/repository tests for matching variant, pairs/ranges, null orphan and sort ties | CP1 plus inherited catalog performance/query budgets |
| Cart/voucher/checkout/orders | Affected service/calculator/converter/MVC tests; receipt v1/v2/v3 shape and snapshot tests | CP2: real CartFlowIT, CheckoutFlowIT, VoucherFlowIT; inventory reservation/cancellation, rollback and synchronized pricing-edit concurrency |
| Review filters/history | Parser/MVC, identical page/count predicate and image multiplicity repository tests; safe Order batch projection | CP3: ReviewFlowIT/ReviewHistoryIT plus catalog visibility and owned-history regression |
| Completion | Current-run reports, selectors/counts and environment recorded | One unrestricted `./mvnw.cmd verify` from `demo/`, with normal discovery and packaging; run feature-specific performance/concurrency checks even if normal discovery omits them |

Use existing test classes where responsibilities fit; new classes only for meaningful new boundaries. `@WebMvcTest`/MockMvc for controller contracts; PostgreSQL `@DataJpaTest` for query/integrity; `@SpringBootTest` independent transactions for workflow/overlap. Do not conceal commit constraints with test-level rollback-only transactions. Tests remain in domain/layer packages with Test/IT suffixes and constitution method conventions.

After a localized fix rerun affected tests first; rerun the full suite only for broad/shared changes or unresolved regression uncertainty, recording why. No repeated expensive unrelated checks without risk justification. Preserve mandatory security/concurrency/rollback/idempotency/performance checks. Record exact commands/selectors, working-state revision, environment, discovered counts, failures/fixes and omissions. Inspect current reports; never relabel earlier full runs as later revisions. Environmental blocks stay explicit; targeted success cannot substitute for unrestricted verification.

## Complexity Tracking

No constitution exception requested. Canonical SQL functions are justified by existing native catalog search and database integrity; repositories compute projections/invariants while services orchestrate workflows. Retained deprecated price storage is a write-protected base mirror for additive schema compatibility, not a second price authority (see data model).

## Planning Validation

Setup used the supplied PowerShell equivalent after `bash` was unavailable. Setup JSON reports BRANCH=`013-variant-review-sale` from feature resolution; `git branch --show-current` verifies actual branch `draft/version-1`, which remains unchanged. Feature directory is independent. No extension configuration exists, so before/after hooks are skipped. Documentation consistency checks are the gate for this invocation; application tests are deferred to implementation.

## Requirement Coverage for Task Generation

| Requirements | Design artifact | Acceptance/verification focus |
| --- | --- | --- |
| FR-001–005, FR-027 options | data-model.md; API/engineering contracts | Default/sparse combinations, normalized duplicates, uniform dimensions, cart UUID semantics, Unicode and concurrent integrity |
| FR-006–009, FR-027 reviews | API/engineering contracts; research R6 | Exact/boolean filter matrix, all sorts/ties, filtered counts, image multiplicity, safe immutable options and independent histogram |
| FR-010–013, FR-022–025 pricing/search | research R1–R5; data model | One formula, active/disabled/time/zero/rounding, paired ranges, same-variant search and inherited budgets |
| FR-014–017, FR-025–026 purchase | research R3/R7; engineering contract | Shared time, voucher stacking, stock/quota/current-price coherence, rollback, immutable snapshots and replay |
| FR-018–021 compatibility/security | data-model.md; quickstart.md | Audit/collision failure, additive upgrade, old receipt shapes, privacy/visibility, bounded queries and exact evidence |

No tasks or implementation are generated by this planning command.

## Approved Analysis Remediation and Compatibility

G1: OrderCreationRepositoryImpl uses a native INSERT, so entity/command changes are insufficient. Explicit persistence writer changes and committed-row PostgreSQL reload tests are mandatory before US2 completion.

I1: All schema is installed in Foundation in ascending order before independently testable US1/US2. Unconditionally requiring new fields at V30 would break current v2 checkout; use nullable expansion and version-specific strict v3 checks while existing v1/v2 all-null evidence writes/replays remain valid. No-sale intermediate behavior is a Foundation gate, and active sales wait for complete US2. Base/mirror mappings and fixture writers are adapted in Foundation; normalized collision/data/ICU failures block rollout. Do not enable Flyway out-of-order, infer original history or allow mixed pricing writers.

U1: One timestamp is insufficient across separate READ COMMITTED queries. Prepare a deep-immutable variant pricing map from one set-based statement (bound ID array) and reuse it across voucher eligibility, discount/subtotal and response. Capture once per operation, never cache across requests; retain publication/inventory rules without repricing. Default isolation and existing locks remain; checkout independently obtains current protected pricing/vouchers. Regression tests edit both base and sale inputs at former duplicate-read points and assert consistent in-flight values plus fresh next-request/checkout values.

The revised tasks own exact IDs/dependencies and preserve CP1/CP2/CP3 and full verification. A Foundation intermediate-compatibility gate precedes every story. No production migration, application code or test implementation is authorized by this documentation remediation.

## Implemented component mapping

The working tree uses `CatalogPricingRepository`/`CatalogPricingRepositoryImpl` with one bound UUID-array query and `CatalogPricingRow` scalar inputs, plus `VariantPricingConverter`, `ProductPriceRange` and `PreparedOperationPricing`. Cart preparation evaluates prices once; availability/publication observations reuse those prices. Cart preview and wallet receive the captured instant explicitly, including empty/hidden rows. `CartWalletContext` carries the operation event to Voucher. Historical `DirectSaleSnapshot` belongs to Order and is reused by checkout receipt v3, avoiding an Order-to-Checkout dependency. Review filters use `ProductReviewQueryRepositoryImpl` with shared scoped predicates; Order owns the bounded immutable option snapshot batch. Actual verification and remaining rollout gates are in implementation-validation.md.

Recommendation card hydration also uses the bounded Catalog `getPriceRangesByIds` service interface. Its single product-ID-array projection evaluates canonical SQL once with a fresh shared instant; converter output and representative inventory use the prepared minimum endpoint, with paired maximum metadata. Ranking inputs retain their existing behavior. No additional database locks, isolation changes or cross-request pricing cache are introduced.
