# Implementation Plan: Image Storage and Profile References

**Branch**: `feature/user-address` | **Date**: 2026-10-27 | **Spec**: [spec.md](spec.md)

**Input**: `specs/003-image-storage/spec.md`

## Summary

Implement authenticated direct-to-staging uploads, actual JPEG/PNG/WebP validation, conditional publication, durable owned image IDs and existing-profile avatar integration. Use a synchronous AWS SDK v2 adapter shared by LocalStack and AWS, optimistic lifecycle version checks and unique final keys per attempt. Persist expiration before returning its error; clean staging only after commit. Upload lifetime is 900 seconds and download lifetime is 300 seconds. No new permission is required for media operations; profile operations retain their existing permissions.

This command delivers design documents only. It does not add dependencies, migrations, production code, tests or infrastructure. Dependency proposals are concrete but not installation authorization.

## Technical Context

**Language/Version**: Java 25; Spring Boot 4.1.2, as declared in `demo/pom.xml`.
**Primary Dependencies**: Existing MVC, Security OAuth2 Resource Server, JPA, Flyway, Jakarta Validation and Lombok. Propose AWS SDK v2 BOM/S3 with synchronous HTTP transport, TwelveMonkeys `imageio-webp:3.15.0`, and LocalStack Testcontainers support; developer approval required before adding them. Pin compatible SDK/container versions during the dependency approval step; do not use floating versions.
**Storage**: Existing PostgreSQL; new image metadata/reference migration after current V4. Private S3 objects; LocalStack for local/test environments.
**Testing**: Existing JUnit/Mockito/AssertJ, WebMvcTest/MockMvc, PostgreSQL Testcontainers and Failsafe; add real LocalStack operations and actual decoding fixtures. Simultaneous-completion integration test deferred; object-replacement test retained.
**Target Platform**: Existing Spring backend on Java 25; Docker Compose local infrastructure. Production compute hosting remains unspecified and is not required for this design.
**Project Type**: Domain-oriented modular-monolith web service in `demo/`.
**Performance Goals**: Correct upload/view/retry journeys and all measurable outcomes in SC-001–006. No unapproved throughput, latency, concurrency target or image-size cap is introduced.
**Constraints**: No URL persistence, public bucket, background media jobs, native decoder requirement, image transformations or authentication changes. Storage calls have explicit finite timeouts; temporary files/streams/readers must close on every path. Service/provider limits remain distinct from application policy.
**Scale/Scope**: One nullable avatar per existing User, reusable owned image records, three media routes, no product/review/list feature.

## Constitution Check

*Pre-research gate: PASS for document-only planning. Post-design gate: PASS for this design, with the explicit dependency approval boundary below.*

| Principles | Design evidence |
| --- | --- |
| I–III: domains and responsibilities | `user → media → storage`; media stores scalar owner ID, never calls user repositories/services. All business services have interfaces/implementations; converters use prepared data only. |
| IV–VI: HTTP/errors/security | [HTTP](contracts/http.md) preserves ApiError as ProblemDetail, Bearer transport, current eligibility, existing profile permissions, owner privacy and presence-aware binding. |
| VII–VIII, XII: data/JPA | [Data model](data-model.md) defines new migration, required constraints, UUID image IDs, Instant/TIMESTAMPTZ and nullable scalar profile FK; no cascaded media deletion or unsafe entity equality. |
| IX–XI: transactions/concurrency/storage | [Consistency](contracts/consistency.md) defines REQUIRED/READ COMMITTED service transactions, optimistic version checks, conditional source reads/copies, durable expiration and post-commit cleanup. No pessimistic locks around media network calls or self-invocation dependency. |
| XIII: testing | [Quickstart](quickstart.md) and consistency verification cover real commit/rollback boundaries, disposable PostgreSQL/LocalStack and explicitly deferred simultaneous testing. |
| II, XIV: dependencies/tooling | New SDK/decoder/test artifacts require developer approval before POM edits; packaged Java 25 decoding must be verified. No claim of active static-analysis gates or passing builds. |
| XV–XVII: scope/environment | Documentation only, no commit/push or deployment. Local/prod properties separate; private buckets and external production credentials. |

Dependency approval is an implementation entry condition, not an unresolved business requirement or a claimed approval. No constitutional MUST violation is authorized. Profile update coordination retains its existing per-user lock; bounded media validation/signing can occur inside that existing atomic update to ensure failed image selection rolls back all edits. This limited retention of the existing lock across storage checks is a recorded SHOULD departure: splitting profile writes would require an unrelated optimistic rewrite and alter approved update coordination. Media completion itself avoids pessimistic locks across storage calls.

## Project Structure

### Documentation (this feature)

```text
specs/003-image-storage/
  spec.md
  plan.md
  research.md
  data-model.md
  quickstart.md
  contracts/
    http.md
    domain-services.md
    consistency.md
    configuration.md
  checklists/requirements.md
```

`tasks.md` belongs to the subsequent `$speckit-tasks` command and is not created here.

### Source Code (repository root)

Planned additions/changes, not existing capabilities:

```text
demo/pom.xml
demo/src/main/java/com/example/demo/
  storage/config/                 # properties, S3 client and presigner beans
  storage/service/                # ObjectStorageService and impl
  media/controller/              # ImageUploadController
  media/service/                 # MediaService, MediaCommandService and impls
  media/entity/                  # ImageUpload
  media/enums/                   # ImageUploadStatus
  media/repository/              # ImageUploadRepository
  media/dto/request/             # CreateImageUploadRequest
  media/dto/response/             # upload/access/prepared-image DTOs
  media/validator/               # lifecycle/ownership and actual image validation
  media/converter/               # response mapping only
  media/exception/               # domain failures
  user/                          # scalar avatar FK, presence-aware ID, response mapping
  auth/config/AuthSecurityConfiguration.java
  common/exception/              # existing central mappings, only applicable additions
demo/src/main/resources/
  db/migration/V8__image_uploads.sql
  application-local.properties
  application-prod.properties
demo/src/test/java/com/example/demo/{media,storage,user}/
demo/src/test/resources/media/    # real decoding fixtures
compose.yaml                     # proposed local PostgreSQL/LocalStack services
docker/localstack/init/          # bucket/CORS bootstrap assets
```

**Structure Decision**: Extend the real `demo/` module with two domains; no global layer restructuring. Recheck latest migration number before implementation. Keep `storage` ignorant of ownership and HTTP, and `media` ignorant of consuming domains. No agent-context file is required by the active plan workflow.

## Phase 0: Research

Completed research on source-object conditions, LocalStack signature enforcement/endpoints, decoder discovery and transaction boundaries in [research.md](research.md). All design unknowns are resolved or explicitly bounded as implementation approval/verification steps, not hidden defaults. Two research agents supplied primary-source findings; no code changes were delegated.

## Phase 1: Design and Contracts

[Data model](data-model.md), [HTTP](contracts/http.md), [service boundaries](contracts/domain-services.md), [consistency](contracts/consistency.md), [configuration](contracts/configuration.md) and [validation guide](quickstart.md) define the design. Preserve FR-001–021 and SC-001–006; specifically no media permission, 15-minute upload URLs and 5-minute download URLs, retained replacement test and deferred simultaneous test.

## Complexity Tracking

No unjustified MUST violations. One recorded SHOULD departure is preserving the existing user-lock transaction during bounded avatar checks, as explained above. Optimistic completion and separate attempt keys avoid new lock hierarchies, claim states, schedulers and compensation infrastructure.

## Validation and Handoff

Reviewed document links, confirmed decision consistency, model constraints, transaction outcomes and repository paths. No application test/build or LocalStack run was performed for planning; implementation must establish dependency/runtime compatibility and actual test discovery. Setup returned feature label `003-image-storage` as BRANCH, but `git branch --show-current` verifies the actual branch remains `feature/user-address`; no switch is inferred. No extension hooks are registered because `.specify/extensions.yml` is absent. Ready to generate tasks; adding the proposed dependencies still requires the constitution II approval step.


## Confirmed content identity clarification (2026-10-27)

Authoritative content-identity requirements and acceptance outcomes are defined in [spec.md FR-008 and Content Identity Decision](spec.md#content-identity-decision-2026-10-27). This document references that definition rather than restating it.



## Implementation outcome (2026-10-27)

Core upload/lifecycle/avatar implementation, migrations, actual-byte validation, provider configuration and automated verification are complete. Local Compose upload/view and packaged-JAR decoding passed. Native persistence on the approved community image failed restart acceptance; tasks T044/T046 remain open pending an approved persistence-capable image/account configuration or explicit scope deferral. See quickstart.md and tasks.md for evidence. No change to the approved concurrency-test deferral or cleanup/size/domain exclusions.


### Latest user override ? 2026-10-27

Allow-all API CORS (origins/methods/headers), retaining Bearer JWT authorization and allowCredentials=false, supersedes earlier explicit-origin restrictions. Bucket initialization is deferred; Compose must not reference ignored docker/ files or require an initialized bucket for health. docker/ and the fixture media README are excluded from Git. Future bucket CORS allows all origins/headers and all S3-supported methods while the bucket remains private. See research.md for the decision and fixture attribution. Existing persistence work remains deferred/open.
