# Implementation Plan: Admin Catalog Management

**Branch**: `features/AdminCatalogManagement` (retained) | **Date**: 2026-11-21 | **Spec**: [spec.md](spec.md)

**Application baseline**: `86512d7ab1e0bb262f783c05c0051d6be46b1983`. **Stage**: Plan, Phase 0 research and Phase 1 design only. Approved requirements are unchanged: 7 stories, 36 FRs, 6 NFRs, 43 scenarios, BR-01–BR-20; requirements checklist 16/16. No implementation or deployment is claimed.

## Summary

Extend the existing Catalog domain with authenticated `ROLE_ADMIN` operations for UC-031–UC-036. Reuse the five catalog entities and existing images, private staged uploads, typed JSON facts, search, pricing, cart, checkout and inventory service boundaries. Add `UNPUBLISHED` to the current string lifecycle; introduce Enabled variants and aggregate revision checks; allow variantless non-Published preparation without weakening mandatory variant fields. One publication validator governs Publish and every change to a Published aggregate. No new catalog system, RBAC, inventory adjustment, frontend or price-history service.

The design uses conditional aggregate revision claims plus short ordered row locks. Category hierarchy has a small database-owned revision/gate row to prevent cycle and reference phantoms. Image associations are logically detached to preserve legacy upload FKs; physical object deletion is excluded. Read-only data preflight is supplied, but actual target records and applied migrations remain **UNVERIFIED**. Schema rollout is gated by clean preflight and any separately authorized data remediation.

## Technical Context

- **Language/Version**: Java 25, Spring Boot 4.1.2; Maven module `demo/` (`demo/pom.xml:5-8`, `demo/pom.xml:29-30`).
- **Primary Dependencies**: Existing MVC, Security Resource Server, JPA, JDBC, Flyway, Jakarta Validation, Lombok, Jackson, AWS SDK and image decoding. No new dependency, plugin, framework or infrastructure proposed.
- **Storage**: PostgreSQL 17 in Compose, private S3/LocalStack images. Source migrations V1–V27; next available at this baseline is V28, to recheck at implementation. No live schema/history verified.
- **Testing**: Existing JUnit 5, Mockito, AssertJ, MockMvc, PostgreSQL/LocalStack Testcontainers; Surefire and bound Failsafe (`demo/pom.xml:120-131`). Preserve real Backend-to-AI RecommendationHttpIT.
- **Target Platform / Project Type**: Existing Linux/container backend, domain-oriented modular monolith. Docker is required for disposable integration environments; no compute deployment change.
- **Performance Goals**: No invented SLA. Retain current same-variant search semantics and existing measured/tested query budgets; listing ≤6 statements, search ≤8, detail ≤5 in `CatalogQueryCountIT`. Preserve batched inventory/catalog reads and search workload budgets; measure new admin queries without inventing acceptance thresholds.
- **Constraints**: REQUIRED / READ COMMITTED, domain-owned repositories, existing UUID catalog and Long customer/commerce IDs, exact whole VND, no physical product/variant deletion or allocation modification, no network under new catalog locks, safe ProblemDetail, no automatic stale retry.
- **Scale/Scope**: Six use cases, existing gallery maximum ten, existing list bounds 0/20 and size1–100. No invented catalog-size, cart-size or concurrency quota.

## Constitution Check

Pre-research and post-design gates reviewed against constitution **1.2.0**. PASS means design compliance; it is not a runtime certification.

| Principles | Pre-research | Post-design evidence |
| --- | --- | --- |
| I–III domain/layers/DTOs | PASS | Catalog owns catalog repositories; MediaService owns upload access; service interface/Impl, Lombok DTOs, presence-aware binding, validators and pure converters. Narrow legacy attachment boundary correction only. |
| IV–VI contracts/security/errors | PASS | [API](contracts/admin-api.md), current JWT/session/ROLE_ADMIN, explicit null/version/errors; admin advice preserves structured details without changing old public error shapes. |
| VII–VIII data/integrity/JPA | PASS | [Model](data-model.md), new Flyway only, non-null SKU/price, stable IDs/FKs, normalized uniqueness, no entity exposure or cascade delete. |
| IX–X transactions/concurrency | PASS | [Consistency](contracts/consistency.md): sorted parent-before-child locks, conditional versions, taxonomy gate, real commit tests, no general retry or increased isolation. |
| XI–XII storage/time | PASS | Reuse preparation/copy/checkpoint; no S3 rollback assumption; no new media architecture; Instant/TIMESTAMPTZ if timestamps are needed. |
| XIII testing | PASS | [Testing](testing-strategy.md): targeted checks, cross-domain checkpoints, required real races, one unrestricted full verify near implementation completion and honest blocked results. |
| II/XIV tooling | PASS | No dependencies/plugins added; static-analysis suite is not configured and is not reported passing. |
| XV–XVII safety/scope | PASS | Only feature planning artifacts written; no code/config/test/migration/commit/push; all excluded use cases deferred. |

Existing shared production wildcard CORS is an inherited constitution discrepancy, not an approved new design. This plan requires no CORS header change and does not authorize deployment with an unresolved production policy. Existing catalog attachment's direct media-repository access is corrected only at the touched boundary. No new MUST violation or waiver is proposed.

## Project Structure

### Documentation (this feature)

```text
specs/013-admin-catalog-management/
  spec.md, compatibility.md, clarification-validation.md       # unchanged approvals
  checklists/requirements.md                                   # unchanged 16/16
  plan.md, research.md, data-model.md, quickstart.md
  testing-strategy.md, planning-validation.md
  contracts/admin-api.md, contracts/consistency.md
  contracts/data-preflight.sql                                 # SELECT-only guide
  checklists/plan-quality.md
```

No tasks.md is generated. Markdown is the contract format, as used by related feature009/010 plans; endpoint/DTO/error semantics below are normative for implementation.

### Source Code (repository root; proposed changes, not generated files)

All Java paths below are under `demo/src/main/java/com/example/demo/`.

```text
catalog/
  controller/admin/ AdminProductController, AdminCategoryController,
                    AdminBrandController, AdminVariantController, AdminGalleryController
  service/ AdminProductService + Impl, AdminCategoryService + Impl,
           AdminBrandService + Impl, AdminVariantService + Impl,
           AdminGalleryService + Impl
  validator/ ProductPublicationValidator, AdminCatalogValidator
  converter/ AdminCatalogConverter
  dto/request/ presence-aware admin requests, lifecycle/version/gallery commands
  dto/response/ admin product/page, category/brand/variant/gallery and revision DTOs
  repository/ existing Product/Variant/Category/Brand/Image repositories extended;
              CatalogTaxonomyStateRepository for the single hierarchy gate
  entity/ existing Product/Variant/Category/Brand/ProductImage extended;
          CatalogTaxonomyState
  service/ existing ProductImageService attachment orchestration adapted;
           public CatalogCartRead/CatalogCheckout/ProductCatalog services extended
media/service/ MediaService + impl extended with prepared product-image contract
recommendation/ existing candidate and refresh selection extended for enabled variants
common/security/ existing explicit matchers extended
common/exception/ admin-scoped advice and named catalog-constraint mappings
```

Use ordinary existing catalog packages for shared helpers; do not create a second admin domain/database. Proposed business services require interfaces/Impl; no mechanical one-class-per-endpoint rule. ProductImageService becomes an interface/Impl at the touched responsibility if retained as its own business service; the AdminGallery orchestration must not duplicate its copy/validation algorithm. Catalog repositories may have a small custom fragment for conditional revisions and complex paged projections; avoid a generic CRUD framework.

## Existing Architecture and Reuse

| Existing responsibility | Reuse / necessary extension | Requirement / evidence |
| --- | --- | --- |
| Product, Category, Brand, ProductVariant | Keep identities, references and JSONB; add lifecycle/eligibility/revisions | FR-003/013/031/036; `catalog/entity/Product.java:28-51`, `catalog/entity/ProductVariant.java:30-53` |
| CatalogDataValidator / TechnicalSpecificationValidator | Separate basic aggregate validation from publication; keep option/SKU/whole-VND/typed shape rules | FR-005/014/022/024; `catalog/validator/CatalogDataValidator.java:19-70`, `catalog/validator/TechnicalSpecificationValidator.java:8-51` |
| ProductCatalogService and repositories | Keep public DTO shapes; add enabled eligibility and coherent post-lock projection; separate admin reads | FR-002/027; `catalog/service/ProductCatalogServiceImpl.java:121-226` |
| Existing search registry / SQL | Keep approved four keys, named-family binding, quality quarantine, same-variant predicates; filter disabled choices consistently | FR-012/023; `catalog/repository/CatalogSearchSql.java:26-100`; approved `../007-product-search-filter/mission-scope.md` |
| MediaService, storage and ProductImageService | Existing presign/completion, owner/content/copy/hash/checkpoint and signing; add version-aware gallery commands and safe detachment | FR-018–021; `catalog/service/ProductImageService.java:37-104` |
| CatalogCartRead / CatalogCheckout | Extend internal state with enabled, keep unavailable response and checkout skipping/replay | FR-026–028; `catalog/service/CatalogCheckoutServiceImpl.java:28-45` |
| Inventory / Order service interfaces | No new mutation; retain variant-insert zero initialization and historical snapshot/reservation resolution | FR-017/026/028; `order/repository/OrderCreationRepositoryImpl.java:28-33`; V25 |
| CurrentStateJwtConverter / security chains / shared error factory | Reuse current authority; exact admin route matchers and scoped code-preserving advice needed | FR-001/030; `auth/security/CurrentStateJwtConverter.java:22-35`; `common/exception/ApiExceptionHandler.java:14-34` |

Paths in this table are relative to the Java root above unless a feature path is shown. [Research](research.md) records decisions, alternatives and complete evidence qualifications.

## Proposed Implementation Approach

1. Prepare migration compatibility and focused regression fixtures first. Introduce explicit `UNPUBLISHED`, `enabled`, revisions, hierarchy gate and active image membership. Keep existing NOT NULL SKU/price, global case-insensitive SKU and per-product option uniqueness.
2. Implement one reusable publication validator over an explicitly loaded proposed aggregate; call it at Publish and after any Published mutation, before commit. Deferred SQL guards provide final cross-row integrity; no controller business validation or stock prerequisite.
3. Implement admin product/variant/taxonomy/gallery services using [consistency](contracts/consistency.md). Existing public catalog service remains customer-read-only and does not accidentally inherit admin visibility.
4. Extend all current consumers for enabled variants and active gallery membership, preserving existing wire shapes and historical behavior. Recheck current selection after locks; late disable/withdrawal cannot leak stale purchase eligibility.
5. Extend existing security matchers and error handling narrowly. Keep legacy admin attachment route as an adapter to the same version-aware command, not a bypass.
6. Prove acceptance, migrations, races and cross-module regression before rollout. The Plan phase creates none of these implementations.

## Dependencies and Sequencing

| Boundary | Deliverable before dependent work | Gate |
| --- | --- | --- |
| Foundation | Confirm baseline/migration numbering, freeze current schema/data preflight output, specify authorized remediation if needed | Current actual data unknown; schema execution blocked until clean/approved |
| Data + integrity | Additive migration design implemented/tested on empty and V27-upgrade DB; lifecycle/enable/version/active mapping | Basic validator and final deferred guards agree; old migrations unchanged |
| Service rules | Revision claim, taxonomy gate, publication validator, shared normalization | Unit + real repository/transaction rollback/race checks |
| Administrative API | DTO/presence/pagination/security/errors and six UC services | HTTP and authorized/denied matrix; no legacy attachment bypass |
| Cross-module | Cart/checkout/search/recommendation/wishlist/order/media eligibility and coherent reads | Integration checkpoint suites; unchanged snapshots/allocations and existing query budgets |
| Completion | All 43 acceptance oracles, migration tests and unrestricted full verify | Report failures/skips/blocked prerequisites; do not substitute selective success |
| Release (separate authorization) | Read-only target preflight, backup/restore drill, writer quiescence, compatible binary and Flyway rollout | No automatic data repair, deployment or rollback mapping |

No task IDs or executable backlog are produced in Plan. Ready for `$speckit-tasks` as a dependency-gated design; actual data readiness is a release/migration gate, not falsely satisfied.

## Risks, Blockers and Deferred Work

- **HIGH, unverified data**: normalized collisions, invalid legacy Published rows or unexpected live migration history block migration execution. Exact queries and application/media proof obligations are supplied. No data was changed or connected successfully.
- **HIGH, cross-module races**: child-only writes, stale signing projections, legacy image replay and taxonomy cycles require real committed transaction tests, not just @Version on Product.
- **HIGH, legacy image FK**: physical image-row deletion can violate retained upload references; active membership avoids destructive rewrite. Every image consumer and unique/count/primary guard must use the correct active scope.
- **HIGH, coordinated rollout**: old binaries cannot safely read UNPUBLISHED or ignore disabled/detached rows. Stop old writers/readers; no mixed-version rollout assumed.
- **MEDIUM, named taxonomy bindings**: permitted repeated names under distinct parents may disable a family under the existing approved missing/ambiguous lookup rule. Keep this behavior explicit/tested; stable identity-based registry redesign is deferred, not silently introduced.
- **MEDIUM, error advice**: current catalog advice drops codes/properties. Test actual precedence so admin conflicts/readiness feedback are actionable.
- **Operational**: Docker daemon/socket unavailable and configured DB targets unreachable in this session. Tests and actual data preflight NOT RUN/BLOCKED, never passed. No new infrastructure provisioned.

Excluded: UC-030/037/038/044/047/048, optional admin functions, frontend, payments, price-history ledger, new RBAC, source-registry management, physical object cleanup redesign, unrelated refactoring. Trusted import/bootstrap code outside this repository needs a coordinated compatibility handoff, not implementation here.

## Complexity Tracking

No constitutional MUST waiver. Two narrowly necessary structures are justified: a single taxonomy revision/gate row (row versions alone cannot prevent hierarchy cycles/phantom assignments) and active gallery membership (legacy published-upload FK/check prevents safe physical association deletion). Reject distributed locks, generic audit/event infrastructure and a second media/catalog system. Existing search name-binding behavior is preserved rather than adding a new registry-binding table.

## Workflow Execution and Verification

Existing `speckit-plan` skill read; `setup-plan.sh --json` resolved this existing feature. Its BRANCH `013-admin-catalog-management` is a workflow alias; actual Git branch remains `features/AdminCatalogManagement`. Before/after extension configuration is absent. Phase 0 read-only research delegated per the skill, consolidated in research.md; Phase 1 artifacts generated. Approved specification/checklist/clarification/compatibility remain byte-identical. See [planning-validation.md](planning-validation.md) and [plan checklist](checklists/plan-quality.md). Stop after Plan; no Tasks/Implement, commit or push.
