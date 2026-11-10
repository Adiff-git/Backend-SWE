# Implementation Plan: Product Search & Filter

> Implementation revision (owner MISSION,2026-11-09): see [mission-scope.md](mission-scope.md) for authorized approvals,local best-effort fact policy and explicit planning deltas. Four keys/five family bindings are approved;source model Rank7 and battery remain deferred. Runtime taxonomy uses actual Backend UUIDs and disables missing/ambiguous family bindings. Historical pending-approval observations below are retained as history,not current authorization. Actual execution evidence is recorded separately;this notice is not a test PASS.

**Branch**: `features/SearchAndFilter` | **Date**: 2026-11-08 | **Spec**: [spec.md](spec.md)

**Input**: `specs/007-product-search-filter/spec.md`

## Summary

Extend the Catalog listing in the existing modular monolith. Use a Catalog-owned native PostgreSQL query fragment for typed dynamic predicates, same-variant keyword/filter eligibility, deterministic ranking and ID-first pagination; retain batched projection and publication-safe image signing. Use pg_trgm for eligible name-token typos and a versioned Vietnamese normalization function for exact/prefix/partial search. No external search service or Java dependency is proposed.

**Delivery state**: Design artifacts produced for review; full local feature acceptance remains BLOCKED by approved registry/category bindings and measured verification,but independent technical tasks may start under scoped gates (analysis-remediation.md R1). Do not interpret a complete document set as approval to implement supported specification filters. No application code, runtime registry, migration, data or test changes were made.

## Technical Context

**Language/Version**: Java 25, Spring Boot 4.1.2, Maven (verified in demo/pom.xml).

**Primary Dependencies**: Existing MVC, Spring Data JPA/Hibernate, PostgreSQL JDBC, Jakarta Validation, Lombok, AWS SDK and Jackson stack. Proposed pg_trgm is a PostgreSQL extension, not a new Maven dependency; target installation capability must be verified before migration execution.

**Storage**: PostgreSQL 17 in compose.dev.yaml; existing private S3/LocalStack reads remain unchanged. Migration files currently extend through V14; enhancement is V11 in this checkout. Live applied history and installed extensions were not queried.

**Testing**: Existing JUnit/Mockito/AssertJ/MockMvc, PostgreSQL Testcontainers, Spring Boot tests. Maven Failsafe binds integration-test/verify. Verify actual Surefire/Failsafe reports after implementation; naming alone is not execution evidence. No new test/build tooling is authorized.

**Target Platform**: Existing containerized backend and PostgreSQL; UTF8 required for normalization. No compute deployment decision is introduced.

**Project Type**: Single Maven module demo/, domain-oriented web service.

**Performance Goals**: Owner-approved2026-11-10 actual1500 MVP budgets and two independent serial runs per FR-019/SC-006;see [policy and measurement protocol](evidence/performance-policy.md). Preserve unrelated synthetic/legacy timing assertions and SQL limits. Historical uniform1s target is superseded only for this measured MVP scope.

**Constraints**: Preserve legacy UUID order, defaults 0/20 and max100, distinct-product counts, same-variant eligibility, whole VND, public visibility and image publication locks. No migration rewriting, live import, tracking, inventory or AI recommendations.

**Scale/Scope**: Catalog listing only; ≤16 query tokens, ≤20 selected brands, ≤8 specification keys and ≤32 alternatives per request. These are proposed contract bounds, not measured capacity.

## Constitution Check

*Gate before research and after design.*

| Gate | Pre-research | Post-design |
| --- | --- | --- |
| I–III: Catalog owns controller/service/repository/converter/validator; DTOs only; no business orchestration in converter | PASS | PASS: query selection remains repository, validation/service workflow remains Catalog service |
| IV–VI: explicit contract, ProblemDetail, existing public GET/security | PASS | PASS: API contract defines omitted/empty/duplicates, 400, 200 empty; invalid bearer behavior preserved |
| VII–VIII: PostgreSQL/Flyway, identities, lazy batch access | PASS | PASS design: additive next migrations only, no collection fetch pagination or entity exposure |
| IX–XI: transaction, concurrency and signed-image safety | PASS | PASS: READ COMMITTED retained; existing short ordered locks retained; no automatic retry or storage writes |
| XIII–XIV: meaningful tests and truthful verification | PASS | PASS: validation plan only; no execution/performance claim; no unapproved tooling |
| II/XV–XVII: dependencies, scope, no commit/push/deploy | PASS | PASS: proposal only; no Java dependency added, PostgreSQL extension execution remains a release prerequisite |
| Feature FR-012: approved registry before specification-dependent work | OPEN for research | **BLOCKED (B1),scoped**: unapproved technical-keyword/filter activation is forbidden;unrelated technical work is independent |
| Feature SC-001: representative identifier/fuzzy acceptance corpus | OPEN for research | **BLOCKED (B2/B3),scoped**: fuzzy/core matching requires its own reviewed corpus/scope/calibration,not all future story cases |

No architectural violation requires a waiver. The blocked feature gates are missing evidence, not waived requirements. Do not collapse these into a global phase barrier. G0 covers baseline/protocol scope,G-N SQL proof,G1 core foundation,G-ID/G-F model/core fuzzy evidence,G-S approved specification work,G-L local full acceptance and G-D deployment only. Independent ready tasks may proceed;blocked work must not be skipped or marked approved. analysis-remediation.md R1/R4 is authoritative.

## Project Structure

### Documentation (this feature)

```text
specs/007-product-search-filter/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── registry-inventory.md
├── design-resolutions.md
├── analysis-remediation.md
├── quickstart.md
├── contracts/
│   ├── products-search.md
│   └── openapi.yaml
└── checklists/requirements.md
```

No tasks.md is generated in this phase.

### Source Code (repository root)

Existing owning paths, with proposed files explicitly labelled:

```text
demo/src/main/java/com/example/demo/catalog/
  controller/ProductCatalogController.java
  service/ProductCatalogService.java, ProductCatalogServiceImpl.java
  repository/ProductRepository.java
  repository/ProductSearchRepository.java, ProductSearchRepositoryImpl.java [proposed fragment]
  dto/request/ProductSearchRequest.java [proposed Lombok DTO]
  dto/response/ProductListingItem.java [proposed listing-only DTO]
  dto/response/ProductPage.java, ProductSummary.java, SellingPrice.java
  converter/ProductCatalogConverter.java
  validator/ProductSearchValidator.java [proposed]
  search/ [proposed cohesive query normalization/registry value types; not a new business domain]
demo/src/main/resources/catalog/specification-filter-registry.json [proposed, approval-gated]
demo/src/main/resources/db/migration/ [new migrations only]
demo/src/test/java/com/example/demo/catalog/{controller,service,repository,validator,converter}/
demo/src/test/resources/catalog/search/ [proposed frozen corpus and approved test registry]
```

**Structure Decision**: Extend ProductCatalogService with validated search request overload while retaining listProducts(page,size) for existing callers. Both paths delegate to one eligibility builder; legacy mode preserves membership/order and uses a bounded cheapest-variant lookup with D7 orphan sentinel/null-pair handling. This corrects the prior unsupported listing409 while preserving existing detail409. Introduce ProductListingItem instead of changing the shared ProductSummary used by wishlist and detail helpers. Reuse SellingPrice and existing converter responsibilities; no price recalculation in converters.

## Query and loading design

1. Controller allowlists query names, handles repeated brandId and JSON specFilters, checks syntax/limits, then delegates. Domain validator resolves registry applicability, types and allowed operations before SQL construction. Apply D2/D4 deterministic tokenization, pre-dedup limits, normalized-token deduplication and strict duplicate JSON-member rejection. D1 supplies approved-only typed keyword views; D5 makes category-unsupported keys/combinations400 independently of product data.
2. Service creates immutable normalized criteria plus Pageable. Resolve image signer credentials before any publication locks. No new cross-domain repository access.
3. Repository fragment builds SQL from trusted fragments only; bind query text, UUID arrays, amounts and specification operands. Neither keys, operators nor ORDER BY are interpolated from client text. Registry-resolved expression paths are controlled constants.
4. Recursive category CTE includes self and descendants with UNION deduplication, terminating even for corrupt cycles. Add parent index. Unknown identity yields no category candidates. No hierarchy rewrite or category service endpoint.
5. Eligible-variant relation combines one Product row, optional Brand and each owned Variant. Every query token must match a shared field OR this variant's SKU/approved specification values. Preserve D7 legacy orphan products through a separate guarded branch (NOT EXISTS any owned variant, no price/spec conditions, all tokens shared-field matches), with a null representative pair; real-but-nonmatching variants never qualify for that branch. Price and all specification-key groups apply to this same row. OR alternatives are nested within their own key group; ranges internally AND their bounds. Inapplicable/malformed/missing facts fail that predicate.
6. Compute per-variant relevance tuple, then aggregate the best tuple per product independently of representative price. Representative is minimum (selling_price,id) among eligible variants, never among all variants after filtering. A higher-priced more relevant variant may supply the rank while a cheaper eligible variant supplies displayPrice: both must qualify. No N+1 variant selection. Null orphan prices sort last in either explicit price direction; no listing409 is introduced.
7. Produce a one-row-per-product eligibility relation. Derive count and ordered ID/projection page from this SAME relation and statement snapshot. Return total separately even when OFFSET is beyond the page using a total CTE LEFT JOIN page relation; do not rely only on COUNT OVER rows, which loses totals on empty pages. No pre-pagination top-K fuzzy truncation.
8. Batch load products/categories and primary image metadata for page IDs; retain ordered publication rechecks and local signing. Reassemble in query rank order. Use ID+representative projection already returned by eligibility query, not a later unrestricted MIN(price).
9. Under concurrent publication changes existing image safety may remove items after the candidate snapshot. Preserve fail-closed exclusion, never refill from an unvalidated row. Totals describe the eligibility statement snapshot; repeated-page guarantees apply to unchanged data. No claim of transaction-wide frozen state under READ COMMITTED. Add a race test proving no private row or signed URL leaks. If strict final-time counts are required, that is a separate isolation/contract decision, not a silent global lock increase.

## Delivery sequence and verification

1. Establish independent baseline,parser/DTO,normalization and PG proofs. Freeze US1.core/model/fuzzy evidence independently from registry approval. Resolve G-S before approved-key work;never expose unapproved facts. Follow per-task readiness,not a blanket phase barrier.
2. Add normalization/pg_trgm and measured index migrations after confirming next free version. D3 is an explicit deployment capability gate even in operator diagnostic exact-only mode; no silent fuzzy fallback. D6 describes failure detection, partial committed migration state and safe recovery without changing immutable migrations. Verify fresh+upgrade schema checksums and unchanged business values on disposable PostgreSQL.
3. Add criteria parsing, registry validation and repository predicates/ranking; prove same-variant membership/counts against oracle fixtures before response integration.
4. Add listing-only DTO and batch projection; preserve old listProducts overload and other endpoint contracts. Update executable listing contract tests for exactly two additive fields.
5. Validate API, existing Catalog/wishlist/image regressions and bounded-query behavior; run benchmark/EXPLAIN matrix. Do not weaken existing tests to accept changed behavior.

Tests by responsibility: normalizer/registry/validator unit tests; MockMvc ProductListingControllerTest for parsing/status/security; ProductSearchRepositoryIT with PostgreSQL for predicates/recursion/ranking/counts; service unit and real integration for selection/signing orchestration; converter tests for projection without fetching; ProductSearchControllerIT for HTTP-to-database cases; catalog flow/performance tests for paging and query bounds. New tests follow mirrored layer packages; do not relocate unrelated legacy tests. Target ≤6 data statements for plain listing and ≤8 for search/filter including registry/category resolution (configuration SET operations counted separately), independent of size20 vs100 and number of variants. Instrument native JDBC SQL as well as Hibernate; Hibernate statistics alone cannot prove a JDBC query budget.

## Complexity Tracking

No constitution exception. Native SQL is justified by recursive categories, correlated typed JSON predicates and ranked per-variant selection; a dedicated small repository fragment avoids scattered string queries. No search microservice, runtime attribute editor, shadow catalog or materialized search refresh lifecycle.

## Design correction and pending verification gate

[design-resolutions.md](design-resolutions.md) closes all seven documented design FAILs and preserves the eight NEEDS VERIFICATION criteria. Tasks generation is now ready as a gated backlog, not authorization to execute it. Only specification-dependent tasks require registry/category approval. Core matching requires its scoped corpus/model/calibration;primitive DTO/parser/normalizer/basic filtering have their actual independent prerequisites. No approval receipt, prototype result or target state is invented. No tasks.md is created in this revision.

## Analyze remediation architecture and execution contract

[analysis-remediation.md](analysis-remediation.md) R1–R7 is normative:core query has a small approved-key contributor interface;the later registry-backed contributor can register independently without a shared query writer race. No dummy registry or unapproved key becomes supported. Full US1 waits its approved-specification branch;US2 basics can start after the core gate while approval remains blocked.

Relevance uses the seven Exact-refining tiers and exact deterministic tie tuple in R3/RK01–RK10,not the obsolete identifier-count-first tuple. Corpus manifests partition US1.core,US1.specification,US2,US3 and US4. US1.core golden cases are exactly RK01–RK06 and RK08–RK10 (RK10 uses keyword-only eligibility);RK07 belongs to US3.configuration and is verified in T043/T046/T048 after freeze T039. Final T056/T062/T065 require all RK01–RK10;the normative per-case partition is in analysis-remediation.md R3. T065 is local acceptance and may record deployment NOT_VERIFIED/BLOCKED;only T063 plus separately authorized rollout prerequisites can satisfy G-D.

Integration tests execute serially by default;parallel authoring does not authorize shared-DB execution. Prove unique PostgreSQL instances for extension/orphan/migration failure workers and distinct storage namespaces before opting into concurrent ITs. Benchmarks get exclusive resources. T061/T062 are independent readiness branches after functional/migration gates;final acceptance needs both reports and complete discovery coverage.

The future Compose healthcheck uses the existing keyword listing probe in R6 instead of a no-keyword URL;no new endpoint/Actuator/infrastructure. T026/T028 cover capability evaluation and real HTTP200/503 health outcomes,not merely ApplicationAvailability events. Coordinate the probe with the new backend version;no Compose file changes occur in this document revision.

## Final owner-authorized implementation evidence

See [mission-scope.md](mission-scope.md),[local verification](evidence/verification-summary.md),[coverage](evidence/implementation-coverage.md) and [handoff](evidence/handoff.md). New V16–V19 and generated description cache/conservative typo guard are reported design deltas;the original20FR/6SC and all RK expectations remain. Actual1500 performance is required;10k is exploratory under resumed owner policy. T059 optional index work is deferred with no-index dependency resolution;T063 deployment remains blocked. Historical proposal/pending notes above are retained as history,not current approval or execution state.
