# Implementation Plan: Product Reviews and Ratings with Optional Images

**Branch**: `feature/voucher` (actual Git branch; setup returned feature-context fallback `012-product-reviews`) | **Date**: 2026-11-20 | **Spec**: [spec.md](spec.md)

**Input**: `specs/012-product-reviews/spec.md`

## Summary

Add a domain-oriented review feature for verified completed purchases, public product review pages and rating summaries, owned indefinite editing, and optional ordered references to up to ten completed image uploads. Comments are normalized plain text up to 2,000 Unicode code points. Anonymous reads apply only to PUBLISHED products; inactive authors display as “Former customer.” Deletion is excluded.

Use a new review aggregate, immutable purchase/product identity, database uniqueness per order item, short serialized writes, aggregate statistics and bulk owning-domain read interfaces. Reuse current JWT, ProblemDetail, storage and test facilities. The user explicitly approved all proposals on 2026-11-21, including P-01–P-05, MANAGE_ORDER and the HTTP/update/error/media/author contracts. This approved baseline governs implementation.

## Technical Context

**Language/Version**: Java 25 LTS, verified in demo/pom.xml.

**Primary Dependencies**: Spring Boot 4.1.2, Spring MVC/Security, Spring Data JPA, Jakarta Validation, Jackson, Lombok, Flyway, existing AWS SDK S3 and springdoc. No new dependencies/plugins.

**Storage**: PostgreSQL with new Flyway migration; AWS S3 deployed and existing LocalStack/S3 gateway locally. Review records store UUID image references, never signed URLs.

**Testing**: JUnit 5, Mockito, AssertJ, WebMvcTest/MockMvc, PostgreSQL Testcontainers, SpringBootTest, existing isolated storage doubles and LocalStack flow support. Existing Maven Failsafe binds integration-test/verify; implementation must inspect actual Surefire/Failsafe reports for discovery.

**Target Platform**: Existing Dockerized Java web service, no compute platform or deployment change.

**Project Type**: Domain-oriented modular-monolith web service in demo/.

**Performance Goals**: Bounded pages (1–100), constant query count as review count per page grows; no converter queries or per-review lazy fetching. One batch each for references, users and media; one prepared URL signer. No unsupported throughput/latency SLA.

**Constraints**: Single vendor, owned COMPLETED item only, one persistent review/item, rating integer 1–5, <=10 distinct images, 2,000 Unicode code points after stripping, no deletion/social/moderation scope. Preserve approved Q1–Q3 and existing IDs/lifecycle.

**Scale/Scope**: Four external operations, new review and image-reference tables, focused additions to order/user/media services and security/error handling. No frontend or catalog-detail statistics integration.

## Constitution Check

Pre-research assessment: PASS for producing a design within approved feature scope. Post-design assessment: PASS; the user approved all remaining policy/contract proposals on 2026-11-21, including the existing MANAGE_ORDER mutation mapping. There are no unresolved technical clarifications or required architecture exceptions.

| Principles | Pre / post-design evidence |
| --- | --- |
| I–III: domain ownership, DTOs, validators, pure conversion | review depends on owning service interfaces; no upstream review dependency or cross-domain repository access; bulk prepared DTOs and dedicated validators/converters |
| IV–V: HTTP semantics/errors | Four documented operations; presence-aware PATCH; 201/200 and scoped ProblemDetail mappings; page DTO instead of framework serialization |
| VI: authorization | Current JWT actor, ACTIVE recheck, ownership; public product routing explicitly integrated in captured catalog chain. Existing MANAGE_ORDER mapping is explicitly approved; no new permission grant |
| VII–VIII: persistence | New migration, preserved ID contracts/history, restricted cross-aggregate deletion, owned references only, lazy/scalar mapping, uniqueness/check/FK constraints |
| IX–X: transactions/concurrency | READ COMMITTED short writes on separate bean; explicit user→order→item→review→sorted media lock order; no network under write locks; no retries/global isolation change; justified REPEATABLE READ page assembly |
| XI–XII: storage/time | Existing upload lifecycle, no object deletion, revalidation at commit; safe unavailable media reads; injected Clock, UTC Instant, stable creation timestamp |
| XIII: meaningful verification | Risk-specific security, concurrency, rollback, fetch and storage checks; checkpoints and final unrestricted verify; documented rerun triggers |
| XIV–XVII: tooling/scope | No new tooling/dependencies, commits/push/deploy, global security changes, unrelated refactoring or destructive data changes |

**Approval record**: All proposals were explicitly approved on 2026-11-21. No policy confirmation remains pending. Preserve excluded scope and record the approval in implementation-decisions.md without seeking it again.

## Project Structure

### Documentation (this feature)

```text
specs/012-product-reviews/
  spec.md
  plan.md
  research.md
  data-model.md
  quickstart.md
  contracts/product-reviews.md
  checklists/requirements.md
```

`tasks.md` was generated and is the implementation progress record.

### Source Code (repository root)

```text
demo/src/main/java/com/example/demo/
  review/                       # new domain
    controller/                 # ProductReviewController, OrderItemReviewController, ReviewController
    service/                    # ReviewService, ReviewWriteService, ReviewStatisticsService
    service/impl/
    repository/                 # ProductReviewRepository, ProductReviewImageRepository
    entity/                     # ProductReview, ProductReviewImage
    dto/request/                # CreateReviewRequest, PatchReviewRequest, review-list criteria
    dto/response/               # ReviewResponse, ReviewPage, ProductRatingSummary
    converter/                  # ReviewConverter
    validator/                  # ReviewValidator, code-point content validation
    exception/
  order/service/                # OrderReviewReadService + impl, eligibility DTO/repository support
  user/service/                 # UserReviewReadService + impl, safe bulk projection
  media/service/                # extend MediaService + impl, bulk preparation/read/recheck DTOs
  common/security/CatalogSecurityConfiguration.java
  auth/config/AuthSecurityConfiguration.java
  common/exception/             # narrowly scoped new-constraint error mapping
  catalog/service/              # reuse lightweight published-product service

demo/src/main/resources/db/migration/  # new next-version product reviews migration
demo/src/test/java/com/example/demo/
  review/                       # unit, MVC, repository/service/API/flow/concurrency IT
  fixture/review/                # new fixtures built on existing order/media fixtures
  order/                        # focused eligibility service checks
  user/                         # bulk privacy and active-state race checks
  media/                        # bulk readiness/fallback checks
  config/                       # reuse existing integration support; add review support only as needed
```

**Structure Decision**: Extend the existing module and domains. Method retrieval names distinguish DTOs from entities. Existing upstream services never import review DTOs; their service-owned seeds remain in their own domains.

## Phase 0 — Research Decisions

Completed in [research.md](research.md): service ownership, identity preservation, serialized writes, safe media preparation, aggregate statistics, security chain ordering, request coercion and exact error handling. The installed setup PowerShell twin was used after Git Bash runtime failure. Actual branch was verified independently because setup's BRANCH is a feature-context fallback.

## Phase 1 — Data and Interface Design

See [data-model.md](data-model.md) and [contracts/product-reviews.md](contracts/product-reviews.md).

Write flow: validate request → prepare explicit uploaded images outside write transaction → join short writer transaction → lock/recheck active actor and owned purchase → create or lock owned review → recheck prepared media metadata without I/O → persist/flush review plus ordered references → commit → assemble safe response using prepared/bulk reads. Separate beans prevent proxy self-invocation from bypassing transactions. Duplicate constraint errors are translated after rollback, never swallowed inside a rollback-only transaction. A response assembly media failure produces null URLs, so a committed review is not reported as failed solely because signing failed.

Page flow: read-only REPEATABLE READ snapshot → published-product service check → page parent rows and count → one ordered-reference query → bulk safe author references and one combined avatar/review media batch → pure converter. No collection fetch join with Pageable. Summary checks visibility and calculates count/average in the same read transaction. External object existence is not part of the database snapshot.

Migration uses immutable product UUID snapshot with no product FK and restricted author/item/upload FKs. Product identity is captured while the exact item is locked. Existing review edits use retained product identity even after order-item links become null. No SQL migration body or application code is generated during planning.

## Verification Strategy and Checkpoints

| Risk/change | Required verification |
| --- | --- |
| Binding/validation | Unit and MVC boundaries for integer coercion, 1–5, 1,999/2,000/2,001 code points including supplementary characters, strip/blank/null, field presence, duplicate/unknown inputs |
| Access/privacy | MVC + API anonymous PUBLISHED reads, 404 unpublished, invalid/inactive JWT, missing permission, cross-user resources, all inactive statuses anonymized, no private fields |
| Persistent integrity | PostgreSQL repository tests for unique item, rating/length/position checks, FK restrictions, image order, history after product deletion |
| Transaction integrity | Real service commits/rollbacks; invalid last image and simulated persistence failure leave original content/references/statistics; no test-level transaction masking boundaries |
| Concurrency | Barrier-coordinated independent transactions: simultaneous create (one row), same/disjoint-field patches and reorder (complete sequential results), active-state locking race, product-link nulling during creation; lock conflict surfaced safely |
| Statistics | Known averages, HALF_UP ties, zero/null, repeated purchases, quantity>1, inactive author retention, edits without count change, uncommitted writes excluded |
| Storage/fetch | Existing upload flow with isolated media; foreign/pending/expired/missing/unavailable cases; no per-review SQL or object HEAD; query count bounded from 1 to 20/100 rows |

Checkpoint A: after schema/owning-domain service additions, run relevant repository/order/user/media integration checks and migration upgrade checks. Checkpoint B: after API integration and creation of ReviewFlowIT in T050, execute T051 with review API/flow plus existing order completion, user image/privacy, catalog security and media flow regressions. Use current fixtures and isolated test storage.

After each implementation change, run affected compilation/tests first. Near completion run one unrestricted `demo/mvnw.cmd verify` from demo/ (PowerShell `./mvnw.cmd verify`) without skips/exclusions; inspect discovery and packaging results. After isolated fixes rerun affected tests, repeating full verify only for shared infrastructure/broad changes or unresolved regression uncertainty; record revision and rationale. Missing Docker/JDK/dependencies or test discovery are blocked/unverified, never passing evidence. Planning-only work needs artifact consistency checks, not Maven or infrastructure provisioning.

## Complexity Tracking

No constitution exception is requested. Added bulk service methods and a separate writer bean are required to preserve domain ownership, bounded fetching and short transaction boundaries; mutable aggregate counters, new libraries and new endpoints are unnecessary.

## Planning Validation Record

All five generated artifacts exist, local Markdown links resolve, and no template or clarification placeholders remain. A separate read-only research review found no substantive security-routing, locking, historical-identity or contract inconsistency; ambiguous Bearer-header wording was corrected. Pre/post-design constitution checks are recorded above. No before_plan/after_plan hooks exist. No application tests or infrastructure were run for this planning-only change; runtime assertions remain implementation validation targets.
