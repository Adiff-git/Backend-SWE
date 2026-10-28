# Ownership cleanup note (2026-10-28)

The current branch implements public Product Catalog reads, including five-minute signed image GET URLs. Upload-only Java code and tests were removed for teammate ownership; approved upload design and acceptance criteria below remain unchanged. Recover removed code and tests from `62980e03e86018a52731c405a5e5d32987a7096c` and reconcile with generic Media/S3 infrastructure before implementation. See [upload handoff](upload-handoff.md). Historical completion statements below refer to that pre-cleanup snapshot.

# Implementation Plan: Backend Product Catalog

**Branch**: `main` (actual checkout; Spec Kit feature `001-product-catalog`) | **Date**: 2026-10-27 | **Spec**: [spec.md](spec.md)

**Input**: Approved Feature 1 specification and clarifications. This document is design only; the separate task breakdown is in [tasks.md](tasks.md). No application code is included.

## Summary

Add a domain-oriented catalog module to the existing Spring Boot application. PostgreSQL/Flyway hold categories, products, variants, image metadata, and durable upload sessions. Two anonymous GET endpoints read only PUBLISHED products through DTOs. A separate, fail-closed ADMIN upload flow prepares a 15-minute private S3 staging PUT, validates bytes at completion, copies them to a distinct private durable key, and publishes metadata atomically in PostgreSQL. Public responses generate five-minute signed image URLs. Product reading can be completed and tested independently of upload activation.

## Team Responsibility and Storage Handoff

The Product Catalog backend owner now owns public-read functionality: listing/detail APIs, category/variant/product/image projections, PUBLISHED visibility, PostgreSQL read and integrity behavior, signed public image URLs, and the public-read portions of final verification. The Product Image Upload teammate owns all remaining upload work T051–T055 and T057–T062: secure upload and its S3 adapter integration, LocalStack infrastructure/configuration and tests, preparation/completion, lifecycle, recovery/cleanup, and upload-related security integration. Previously completed T001–T050, T056 and T064 remain completed on this branch and are handoff inputs; existing upload code and tests stay in place. The separate authentication owner must supply verified JWT/ADMIN identity before the teammate can activate upload routes. This change assigns work, without changing the approved S3 behavior, security rules, or API contract.

The upload teammate's T061 evidence must include a reachable LocalStack endpoint and region, private test bucket setup, disposable credentials supplied through environment or the AWS SDK credential chain (never committed), and the adapter's `staging/{uploadId}/{randomKey}` and `final/{uploadId}/{randomKey}` key conventions. It must state the bucket/public-access and IAM or equivalent policy, how signature validation is enabled, presigned URL hostname/header requirements, test setup and execution commands, test reports for signed PUT/GET expiry and real PUT/copy/delete/cleanup, overwrite and conditional-copy behavior, and anonymous object-key denial in an enforcing environment. Record any LocalStack versus AWS S3 differences; where LocalStack cannot enforce a policy, use another appropriate enforcing environment and leave that claim pending until demonstrated. The teammate owns T062 upload-flow verification. Both owners review merged upload evidence before the shared T063/T065/T066 final gates can claim integrated coverage. Existing public image reads and LocalStack tests remain intact.

T063 splits into independently preparable public GET contract checks and teammate-owned POST/security contract checks; its complete four-route gate waits for both. T065 splits into public-read build/discovery evidence and teammate-supplied upload/LocalStack/CI inputs; final `clean verify` and CI alignment require integration. T066 splits into public listing/detail, catalog integrity, stable page counts and field-matrix evidence versus teammate-owned secure lifecycle, private storage, failure cleanup and concurrency evidence. Public slices may be completed and recorded independently, but the three task checkboxes remain open until the full shared acceptance criteria pass.

## Technical Context

**Language/Version**: Actual `demo/pom.xml`: Java 25, Spring Boot 4.1.1, Maven. Constitution target is Boot 4.1.2; discrepancy requires a separately authorized version change, not an implicit upgrade.

**Primary Dependencies**: Present: Spring Data JPA, WebMVC, Security starter, Flyway plus PostgreSQL module, PostgreSQL driver, Springdoc. Proposed with constitution approval: AWS SDK for Java 2.x S3/presigner, a WebP-capable decoder, Bean Validation starter, Lombok for constitution-prescribed DTO style, and LocalStack test support if selected. No authentication implementation exists.

**Storage**: PostgreSQL catalog schema via new Flyway migration; private AWS S3 deployed and LocalStack S3 locally. Persist keys and metadata, never signed URLs or image bytes.

**Testing**: Present: JUnit 5/Spring Boot test starters, MockMvc support, Mockito, PostgreSQL Testcontainers JDBC test profile. Current only test is context load. Plan unit, MVC, repository, service, API and S3 integration tests. Maven Surefire does not discover `*IT` by default; Failsafe configuration needs approval or another verified discovery mechanism.

**Target Platform**: Existing `demo/` Spring Boot backend; Docker-backed PostgreSQL for integration tests; LocalStack for local S3; AWS S3 in deployment. No production datasource, S3, or principal configuration currently exists.

**Project Type**: Backend web service in an existing domain-oriented modular monolith.

**Performance Goals**: Spec SC-003: at least 1,000 products, p95 listing (20 items) and detail under one second in an agreed measured environment. Up to 100 summaries per page without N+1 database queries.

**Constraints**: Public reads only PUBLISHED; UUID wire IDs; 10 MiB and 10 completed images per product; staging URL/session 15 minutes; signed read URL five minutes; cleanup within 24 hours; no image write endpoint enabled without verified ADMIN identity. No Inventory, Search, Filter, Sort, frontend, or product administration.

**Scale/Scope**: Five catalog tables, two public GET endpoints, two ADMIN upload endpoints, one image lifecycle, no additional category or signed-URL endpoint.

## Constitution Check

*GATE: evaluated before research and again after Phase 1 design.*

| Gate | Pre-research assessment | Post-design assessment |
|---|---|---|
| Domain-first package, service interface/implementation, DTO boundary | Pass as design constraint; no catalog code yet | Pass: proposed `catalog` package below |
| HTTP convention, validation, centralized ProblemDetail | Pass as design constraint | Pass as design: MVC advice handles application errors; Security AuthenticationEntryPoint/AccessDeniedHandler handle filter-chain 401/403 ProblemDetail |
| JWT/RBAC and ownership | Dependency: absent in checkout; upload activation blocked | Pass conditionally: routes deny access until verified ADMIN principal contract exists; no bypass or JWT system in this feature |
| PostgreSQL/Flyway, named constraints, UUID feature IDs | Pass as design constraint; no schema exists | Pass: [data model](data-model.md) specifies migration and checks |
| LAZY JPA, bounded reads, transactions and lock order | Pass as design constraint | Pass as design: page IDs then batch fetch; short status locks surround local URL signing, and no S3 network call occurs under DB locks |
| Private S3 staging/publication/recovery | Dependency: SDK/config absent | Pass as design; conditional-copy validation and recovery specified in [research](research.md) |
| Meaningful tests and CI discovery | Gap: only context-load test; `*IT` discovery absent | Pass as plan; Failsafe/discovery is prerequisite to claim integration CI coverage |
| Dependency/build-plugin approval | Gap: approved-scope components undeclared | Pass as plan; additions identified as approval gates, not applied |
| Scope safety | Pass | Pass: no application code or tasks generated |

There are no unresolved business clarifications blocking this design. The upload capability has an external prerequisite: separately implemented authentication must provide verified ADMIN identity. Missing dependencies/configuration and the Boot discrepancy are implementation blockers, not reasons to weaken the contract.

## Project Structure

### Documentation (this feature)

```text
specs/001-product-catalog/
├── spec.md
├── checklists/requirements.md
├── plan.md
├── research.md
├── data-model.md
├── contracts/openapi.yaml
├── quickstart.md
└── tasks.md
```

### Source Code (planned; none of these catalog files currently exists)

```text
demo/
├── pom.xml
├── src/main/java/com/example/demo/
│   ├── DemoApplication.java
│   ├── catalog/
│   │   ├── controller/        ProductCatalogController, ProductImageUploadController
│   │   ├── service/           interfaces and implementations for reads/uploads/recovery
│   │   ├── repository/        JPA repositories and bounded catalog queries
│   │   ├── dto/request/       prepare/complete requests
│   │   ├── dto/response/      page, summary, detail, upload responses
│   │   ├── entity/            five JPA entities and status enums
│   │   ├── converter/         entity/projection to DTO mapping
│   │   ├── validator/         image and catalog invariant validation
│   │   └── storage/           S3 adapter and presigner
│   └── common/exception/     shared exception base and ProblemDetail advice
├── src/main/resources/db/migration/   new catalog migration(s)
└── src/test/java/com/example/demo/catalog/   matching unit/MVC/integration tests
```

The shared exception area is only the minimum cross-domain HTTP error boundary required by the constitution; no unrelated refactor is planned. Exact class names and dependency versions are implementation choices.

## Architecture and Interfaces

**Reads**: Controller validates canonical UUID and strict `page`/`size` binding; the service pages Product IDs over `(status,id)` with fixed `id ASC`. Category and primary-image metadata are fetched in batches; detail fetches one product/category plus variants and ordered images in bounded queries. The service rechecks PUBLISHED for all candidates before DTO projection and supplies already authorized URL values to a mapping-only converter. Before issuing an image URL, a short service transaction obtains `FOR SHARE` on the image-bearing Product rows in UUID order, re-reads their current status, and signs locally while those locks are held. A Product status update uses the same row in its write transaction, so either signing finishes before the unpublish commit or the status check sees the new private state. No S3 network call or credential refresh may occur while holding the lock; prepare the presigner/credentials first. The signing path uses a write-capable transaction because PostgreSQL row locking may not work in a read-only transaction; pure reads may use `@Transactional(readOnly=true)`. This targeted lock is limited to URL issuance, not every catalog query. A missing or unpublished detail returns the same 404. Concurrent changes may alter list counts/page contents, while an unchanged catalog retains stable boundaries; never sign for a row that fails the final status check. No entity or Spring `Page` is serialized.

**Uploads**: Security checks authenticated `ADMIN` and extracts an immutable verified principal identifier. Prepare validates request, product existence and current count, creates a globally unique staging key and PREPARED session, and records `prepared_at` from one PostgreSQL `clock_timestamp()` sample with `expires_at = prepared_at + 15 minutes`; then it returns a presigned PUT URL plus required headers that expire no later than the session. Completion checks the same preparer, product/session association, PUBLISHED replay and first-call expiry before object work. The service validates staged bytes and captures source ETag/version plus a digest. In a first short committed transaction it conditionally claims the session, records VERIFIED, a fencing UUID, a bounded claim lease and a unique attempt-specific durable key. Only after that commit does it conditionally copy the validated source into the private final prefix and verify final bytes/digest. In a second service-owned transaction it locks the Product row, then the upload row, rechecks claim token/lease and gallery limits/order/primary, and stages ProductImage before conditionally setting PUBLISHED in the same transaction. Its final business write is one conditional PUBLISHED update that samples PostgreSQL `clock_timestamp()` once and requires that sampled time to be strictly earlier than `expires_at` and the claim lease expiry while the upload lock and claim fence remain valid; the same sample becomes `published_at`. Zero updated rows abort the whole publication transaction, including the image insert. No transaction-start timestamp or deferred trigger represents the deadline boundary. The service returns success only after commit; physical commit after expiry is permitted when the final check passed before expiry. Repositories provide lock/query/conditional-update operations; they do not orchestrate or own transaction boundaries. No S3 network call occurs while DB locks are held. A stale worker can only copy to its own unreferenced key and cannot publish after losing the claim. A committed publication can be replayed even after session expiry.

**Claim and recovery**: The technical claim lease is configurable with a proposed two-minute default; it has a finite expiry and can be renewed by the owning token before it expires only while the final database-side eligibility check can still pass before the 15-minute session deadline. A live claim does not extend that deadline. Only the same verified preparer may reclaim an expired claim with a new token/key, and only before session expiry. The scheduled worker never supplies an ADMIN identity, reclaims for publication, or publishes a new image. It may detect expired sessions and claims, then under a short service transaction lock the upload row, recheck its state/token/lease and server-side time, and conditionally mark abandoned work EXPIRED (or FAILED for a recorded non-expiry failure). It must defer transition and deletion while a valid claim remains live; a final publication transaction and cleanup serialize on the same upload row so one outcome wins, even if a valid pre-expiry check commits after expiry. A crashed worker's key remains under an upload-ID-scoped private prefix for reconciliation. On invalid content mark FAILED; a first completion attempt after expiry fails, with EXPIRED recorded when no live claim remains. After storage/DB failure, retain committed intent so an authorized retry with a pre-expiry final check or cleanup can find the private object. Outside DB transactions, cleanup may delete keys only after EXPIRED/FAILED is committed, or old unreferenced attempt keys of an already PUBLISHED upload; it scans attempt keys, rechecks that no committed ProductImage references each key, and deletes only unreferenced private objects. Terminal-state fencing prevents later publication of a deleted key; repeated workers and crashes must remain safe and finish within 24 hours of expiration/failure. PostgreSQL rollback never reverses S3.

**Security dependency**: The future authentication component must verify Bearer JWTs and supply a stable principal ID plus trusted ADMIN/CUSTOMER authorities. Public GET matchers permit anonymous access. Both POST matchers require ADMIN and service-level same-preparer comparison. A Spring Security `AuthenticationEntryPoint` produces sanitized RFC 9457 ProblemDetail for unauthenticated 401; an `AccessDeniedHandler` produces it for filter-chain 403. Service-level wrong-preparer 403 uses the central MVC advice. MockMvc tests must distinguish these paths. Until this verified identity contract exists, upload routes remain fail closed; the security starter alone does not fulfill it. No JWT login/issuance is added here.

**API**: [contracts/openapi.yaml](contracts/openapi.yaml) is the detailed request/response/error contract. It includes two GET and two POST endpoints. Image URLs are embedded in public product responses; there is no standalone image URL endpoint.

## Phases and Dependencies

1. **Catalog data and migration**: Obtain approval for needed dependencies/version changes. Add Flyway tables/constraints and entity mappings. Establish trusted fixture/import validation for required variants and product statuses; no product administration API.
2. **Independent public reads**: Add repositories, service/DTO mapping, validation, ProblemDetail handling, GET endpoints, targeted status locking before URL signing, PostgreSQL/MockMvc tests, and published-only performance checks. This phase does not depend on S3 upload activation; image signing can be tested with a fake signer.
3. **Image metadata and S3**: Completed adapter/validator work is preserved. The upload teammate owns remaining S3 integration and T061 storage behavior verification; the public-read owner retains signed GET projection and its read tests.
4. **Authorized upload lifecycle**: Upload teammate integrates only after the separate verified principal/ADMIN contract exists. Prepare/completion retains ownership, expiry, conditional object publication, row-level count/order/primary serialization, idempotent replay, and failure responses.
5. **Cleanup and recovery**: Upload teammate owns scheduled, retry-safe reconciliation of expired/failed sessions, staging keys and unreferenced final keys, including DB/S3 failure and multiple-worker tests.
6. **Integrated verification**: Public-read owner supplies GET, PostgreSQL integrity, SC-003/SC-004/SC-005 and public contract evidence. Upload teammate supplies POST, security, lifecycle and LocalStack evidence. Both owners complete T063/T065/T066 together after authentication and branch integration, including final `mvn -B -ntp clean verify` and CI alignment.

Phase 2 is independently deliverable and testable. Phases 4–5 are not securely activatable before the external authentication dependency. These are planning phases, not a generated `tasks.md`.

## Complexity Tracking

| Decision | Why needed | Simpler option rejected |
|---|---|---|
| Separate upload-session table/state | Completion crosses requests and DB/S3 failure boundaries; idempotency and cleanup need durable state | In-memory sessions would lose ownership/recovery state |
| Separate staging and durable object keys | Reusable presigned PUT cannot mutate a published image | Publishing the staging key would expose later overwrites |
| Batch queries for paged reads | Avoid collection fetch-join pagination and N+1 at size 100 | One query per product fails performance requirement |
| S3 adapter boundary | Enables AWS/LocalStack configuration and deterministic unit tests | Direct SDK calls in service couple business transactions to storage |

## Risks and Implementation Gates

- **External auth**: No verified principal or JWT/RBAC implementation currently exists. Upload endpoints must fail closed until a separately approved integration supplies it.
- **Storage race**: A presigned PUT may overwrite staging before expiry. Compute a digest of validated bytes, conditionally copy the same source version/ETag, and verify the final bytes/digest. If a storage backend cannot establish the same bytes were copied, fail completion.
- **Cross-system consistency**: S3 and PostgreSQL have no shared transaction. Persist recoverable session/final-key state, make completion replay safe, and reconcile private orphans within 24 hours.
- **Schema cross-row rules**: Foreign keys/unique indexes cannot alone guarantee a product has a variant or that a nonempty gallery has one primary. Deferred constraint triggers must acquire the Product parent row `FOR NO KEY UPDATE` and run a fresh count query after acquiring it at READ COMMITTED; all writers lock affected Product rows in UUID order, then upload rows when needed. Keep service/import validation for useful errors. Test valid same-transaction creation, invalid direct SQL at real commit, and concurrent last-variant/gallery writes; a deadlock must abort rather than permit invalid state.
- **Version/build gaps**: Boot 4.1.1 vs constitution 4.1.2; AWS SDK, decoder, validation, Lombok, LocalStack test support and Failsafe are absent. Obtain constitution-required approval before any implementation dependency/plugin changes.
- **Local tests**: LocalStack signature validation is off by default; configure it for expiry/security tests. Browser-facing presigned hosts must be reachable by the frontend. Docker/LocalStack availability and production configuration are not established by this repository.
- **Team integration**: The upload teammate owns pending T051–T055 and T057–T062, including T059's prepared disabled scenarios and T061's enforcing private-access check. Shared T063/T065/T066 consume reviewed upload evidence after branch integration; a mock S3 test or existing signed-read check cannot replace it. No completed catalog task is reopened by the handoff.
- **Exact JSON amounts**: The approved contract uses JSON integer amounts without an agreed upper price limit. JavaScript consumers must parse them without loss of integer precision; do not add an unapproved price cap or silently round values.

## Post-design Constitution Recheck

The design meets applicable constitution rules as a plan. Authentication, dependency approval, and integration-test discovery are explicit implementation gates; they are not claimed as working features. No unrelated refactoring, Inventory persistence, catalog CRUD, or new role was introduced.
