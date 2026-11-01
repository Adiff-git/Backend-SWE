# Implementation Plan: Personal Product Wishlist

**Branch**: `feature/wishlist` | **Date**: 2026-10-30 | **Spec**: [spec.md](spec.md)
**Input**: Confirmed Q1 B, Q2 A, Q3 A in `specs/004-product-wishlist/spec.md`.

## Summary

Introduce a dedicated wishlist domain with account-owned membership, MANAGE_WISHLIST authorization, published-only paginated summaries, atomic idempotent additions and hidden-product removal. Duplicate insertion uses targeted INSERT ... ON CONFLICT DO NOTHING as successful no-op handling without transaction failure or new pessimistic locking for this operation; retain pair uniqueness. Actual persistence exceptions use separate rollback/error handling. Re-add samples a fresh timestamp without strict monotonicity, with deterministic UUID ordering for ties. Reuse current auth, catalog converters and image signing through new internal catalog service operations. Deliver design only; no code, migrations, tests or dependency changes.

## Technical Context

**Language/Version**: Java 25, verified in demo/pom.xml.
**Primary Dependencies**: Existing Spring Boot 4.1.2, MVC, Security/resource server, Data JPA, Flyway, Lombok, PostgreSQL driver and AWS SDK 2.57.2. No additions required.
**Storage**: Existing PostgreSQL 17; existing private S3/LocalStack image access. No new object workflow.
**Testing**: Constitution XIII is authoritative: package by domain/layer, class by responsibility; isolated service/validator/converter unit tests, @WebMvcTest controller slices, @DataJpaTest PostgreSQL repository ITs, @SpringBootTest real-service ITs and @SpringBootTest/MockMvc controller ITs. In-memory factories use testdata/wishlist, persistent fixtures fixture/wishlist; create helpers only for actual reuse. Use JUnit 5, Mockito, AssertJ, MockMvc, PostgreSQL Testcontainers and Boot integration facilities. Failsafe has integration-test/verify goals; future actual discovery must be checked.
**Target Platform**: Existing JVM/container backend, Windows developer workflow.
**Project Type**: Single-module Maven web service, modular monolith.
**Performance Goals**: Page sizes 1–100/default 20; feature listing SQL budget <=6 statements independent of item count, excluding authentication/fixtures. No invented latency/load SLA.
**Constraints**: Trusted immutable identity, approved permission, no catalog HTTP changes, pure converters, no cross-domain repository calls, OSIV already false. REQUIRED/READ COMMITTED transactions; write-capable listing solely for existing signing safeguards.
**Scale/Scope**: Three endpoints, one association table, one permission/default-role grant and two internal catalog methods. No wishlist capacity limit.

## Constitution Check

Pre-research gate: PASS. Product decisions are confirmed. The former generic spec-checklist technical-detail exceptions were resolved on 2026-10-31 by relocating binding engineering requirements to contracts/engineering.md.

| Principles | Pre-design gate | Post-design gate |
| --- | --- | --- |
| I–III: domain, naming, mapping/validation | Dedicated domain/interface/impl required | PASS: own repositories; catalog owns references/summaries; pure converters, applicable validator |
| IV–V: HTTP/errors | 201/200 duplicate/204 fixed | PASS: explicit contracts; current ApiError/security handling |
| VI: security | MANAGE_WISHLIST approved | PASS: existing bearer chain, USER grant without custom-role reassignment |
| VII–VIII: database/JPA | New migration, preserve existing IDs | PASS: Long private ID, UUID product, FK/unique/NOT NULL, LAZY/no parent cascades |
| IX–X: transactions/concurrency | Default propagation/isolation | PASS: conflict-ignore insertion, separate lookup; no aborted-transaction queries, blanket locks or retries |
| XI–XII: storage/time | Reuse current pipeline | PASS: ephemeral signing, Instant/TIMESTAMPTZ, immutable addedAt |
| XIII–XIV: verification | Existing test stack only | PASS: synchronized add race, real rollback and query checks; no new tooling |
| XV–XVII: scope/safety | Documentation only | PASS: no commit/push/deploy or production edits; existing README changes preserved |

Post-design gate: PASS. The constitution recommends read-only transactions where appropriate; Q3 A justifies a write-capable listing transaction for existing signing, without business writes. No unresolved design clarification or unjustified violation. Pre-existing unrelated CORS/upload/ratification issues are not changed.

## Project Structure

### Documentation (this feature)

```text
specs/004-product-wishlist/
  spec.md
  checklists/requirements.md
  plan.md
  research.md
  data-model.md
  contracts/http.md
  contracts/domain-integration.md
  quickstart.md
```

`tasks.md` belongs to the next command and is not created here.

### Source Code (repository root)

Planned paths, not files delivered now:

```text
demo/src/main/java/com/example/demo/
  wishlist/
    controller/WishlistController.java
    service/WishlistService.java
    service/impl/WishlistServiceImpl.java
    repository/WishlistRepository.java
    entity/WishlistItem.java
    dto/response/WishlistItemResponse.java
    dto/response/WishlistPage.java
    converter/WishlistConverter.java
    validator/WishlistValidator.java
    exception/InvalidWishlistRequestException.java
    exception/WishlistConflictException.java
  catalog/service/ProductCatalogService.java             # extend
  catalog/service/ProductCatalogServiceImpl.java         # extend
  auth/config/AuthSecurityConfiguration.java             # exact route grants
  common/exception/ApiErrorFactory.java                   # targeted safe FK mapping if needed
demo/src/main/resources/db/migration/V10__create_wishlist_items.sql
demo/src/test/java/com/example/demo/
  wishlist/
    controller/WishlistControllerTest.java
    controller/WishlistControllerIT.java
    service/WishlistServiceImplTest.java
    service/WishlistServiceIT.java
    repository/WishlistRepositoryIT.java
    validator/WishlistValidatorTest.java
    converter/WishlistConverterTest.java
  catalog/service/ProductCatalogServiceImplTest.java
  common/exception/WishlistPersistenceErrorTest.java
  fixture/wishlist/WishlistFixtures.java
  testdata/wishlist/WishlistTestData.java                  # only if reusable in-memory factories needed
```

**Structure Decision**: Extend demo's package-by-feature layout. Retain catalog's existing service implementation path; use service/impl for the new domain. Add only necessary internal result/projection types.

## Phase 0: Research

[research.md](research.md) resolves ID choice, permission rollout, atomic duplicate insertion, catalog boundaries, pagination/signing consistency and verification. Delegated read-only research inspected actual source; PostgreSQL 17 primary references verified concurrency assumptions.

## Phase 1: Design

[data-model.md](data-model.md) defines membership and migration requirements. [HTTP contract](contracts/http.md) and [domain integration](contracts/domain-integration.md) define external/internal behavior. [quickstart.md](quickstart.md) specifies later validation.

Later implementation order: migration/permission/entity, catalog extensions, wishlist persistence/service/mapping, controller/security, meaningful unit/MVC/integration/concurrency verification, regression documentation. Detailed tasks are deferred to `$speckit-tasks`.

## Complexity Tracking

No constitution violations. PostgreSQL-specific atomic insertion is justified by the approved uniqueness/idempotence invariant; no general retry, locking or idempotency framework.

## Setup and Verification Record

Bash setup-plan.sh --json failed on Windows /c path permissions. The PowerShell twin succeeded with a process-local execution-policy setting and resolved this feature's absolute paths. Its BRANCH output was 004-product-wishlist (helper fallback); actual `git branch --show-current` is feature/wishlist. No branch switch occurred.

No .specify/extensions.yml exists, so no before_plan/after_plan hooks apply. Application tests/build were not run for document-only planning. Dependencies/plugins, source, migrations and tests remain unchanged.


## Business and Engineering Separation (2026-10-31)

At the user's request, spec.md now defines observable business requirements without prescribing HTTP routes/status codes, languages/frameworks, persistence/locking mechanisms or test infrastructure. All 17 FR and six SC identifiers remain. [contracts/engineering.md](contracts/engineering.md) preserves the relocated sections and original technical decisions, including HTTP/schema/type constraints, targeted conflict-ignore insertion, the narrow write-capable listing exception, bounded SQL and the four remediations. Existing http.md, domain-integration.md, data-model.md, research.md and tasks.md remain binding; no approval gate or test requirement is waived. Technical evidence previously cited in spec.md now resolves to engineering.md and these design artifacts. No source or runtime behavior changes.
