# Implementation Plan: User Profiles and Address Management

**Branch**: `feature/initial-project-setup` (actual Git branch) | **Date**: 2026-10-26 | **Spec**: [spec.md](spec.md)

**Input**: `specs/001-user-profiles-addresses/spec.md`

Setup resolved feature key `001-user-profiles-addresses` and reported it as BRANCH; `git branch --show-current` confirms the actual branch above. No branch was created or switched.

## Summary

Implement eight authenticated profile/address endpoints in the existing single-module domain-oriented monolith. Use separate private/public DTO allowlists, presence-aware selective profile PUT, normalized case-insensitive identity uniqueness, and owner-scoped address access. Coordinate all writes through pessimistic locking of the owning user row at READ COMMITTED. Enforce at most 20 addresses, exactly one default for non-empty books, highest-ID fallback, full rollback and 409 lock-timeout failures without retries.

This is a design-only delivery. Authentication and media implementations are absent; their prerequisite contracts are explicit in [contracts/integration.md](contracts/integration.md). Implement independent User/Address components first; complete future integrations through separately authorized work only before claiming their dependent flows work end-to-end. Do not implement registration, token issuance, storage or permissive security substitutes here.

## Technical Context

**Language/Version**: Java 25. Maven currently declares Spring Boot 4.1.1; constitutional target is 4.1.2. Reconcile through an explicit approved dependency change before implementation, not a silent edit.

**Primary Dependencies**: Existing Spring MVC, Security, Data JPA/Hibernate, Flyway, PostgreSQL driver, springdoc 3.1.0 and test starters. Proposed additions requiring developer approval before modification: Boot validation starter, Lombok for constitution-required DTO classes, and Maven Failsafe for `*IT` discovery. Inspect resolved dependencies before adding duplicates. No new JWT, media or presence-wrapper library is proposed.

**Storage**: PostgreSQL; disposable tests currently select 17-alpine through Testcontainers JDBC. Flyway migrations under `demo/src/main/resources/db/migration`; no migration files currently exist. Core migrations/entities proceed without a guessed media reference column/type/FK; add the optional media association in a future additive migration after the real media contract exists.

**Testing**: JUnit 5, Mockito, AssertJ, MockMvc, PostgreSQL Testcontainers; unit/slices `*Test`, integration `*IT`. Existing coverage is a single context-load test; Surefire/Failsafe discovery and all new test outcomes must be verified during implementation.

**Target Platform**: JVM HTTP service; no compute deployment platform selected. Windows developer commands and Docker-backed disposable database verification.

**Project Type**: Spring Boot backend web service, single-vendor modular monolith.

**Performance Goals**: Bounded unpaginated address reads of at most 20 records; use-case fetches without converter queries. No latency, throughput or uptime SLA is approved; do not claim one. Pessimistic lock wait uses an externalized positive duration, proposed default 5 seconds, shortened in timeout tests. This is a technical setting, not an end-user SLA.

**Constraints**: READ COMMITTED/REQUIRED service transactions, no automatic retries, no global lock, no developer/production database verification, no independent-media cascade or S3 call in the mutation transaction.

**Scale/Scope**: Two entities in the user domain, eight endpoints, one address book per user capped at 20. No total-user-volume target is supplied.

## Constitution Check

Pre-research review found no design violation. Missing prerequisites are implementation gates rather than authorization to broaden scope. Post-design review yields the same result.

| Principles | Pre-research | Post-design evidence / gate |
| --- | --- | --- |
| I-III domain boundaries, naming, mapping | PASS | User/Address services in user domain own their repositories; validators receive facts from services, not cross-domain repositories. Entity getters use get...Entity; converters never fetch. |
| IV-V HTTP and errors | PASS | Approved selective PUT exception retained; ApiError extends ProblemDetail; filter failures remain in Security handlers; exact contracts below. |
| VI identity and authorization | PASS design; integration prerequisite | Every route requires authentication, ownership uses immutable ID; no new role/status restrictions. Real JWT integration is absent and must precede full API completion. |
| VII-VIII schema and JPA | PASS design; media prerequisite | New migrations only, VARCHAR limits, TIMESTAMPTZ, LAZY to-one references, named indexes/checks, no cross-aggregate cascade. Core schema proceeds without Media; its optional FK/association is added later. |
| IX-X transactions and locking | PASS | Parent user lock precedes address state reads; all writers participate; ordered flushes prevent default-index collisions; timeout 409 after rollback. |
| XI-XII storage and timestamps | PASS | No upload/storage work; durable media IDs only; joinedAt Instant immutable and dateOfBirth LocalDate. |
| XIII meaningful tests | PASS design; tooling approval gate | Layered tests, real commit/rollback/concurrency boundaries, disposable PostgreSQL/Flyway. Add Failsafe only after approval. |
| II, XIV dependency/quality approval | PASS design; approval gate | Validation/Lombok/Failsafe and Boot discrepancy recorded; no build changes made. Static-analysis stack absent; no passing gates claimed or unrelated tooling introduced. |
| XV-XVII scope, environments, Git | PASS | Documentation only; no commits/pushes, CI/Docker changes, production DB access, secrets or deployment. |

No constitutional exceptions are requested. The former specification implementation-detail exceptions are resolved by moving engineering decisions into contracts/engineering.md; no constitutional exception is introduced. Missing Authentication/Media blocks only dependent integration tasks, never core implementation. Dependency/build-plugin approvals remain applicable before their respective changes. No temporary production adapters are permitted.

## Project Structure

### Documentation (this feature)

```text
specs/001-user-profiles-addresses/
  spec.md
  plan.md
  research.md
  data-model.md
  quickstart.md
  contracts/http.md
  contracts/integration.md
  checklists/requirements.md
```

`tasks.md` belongs to the subsequent speckit-tasks command and is not generated here.

### Source Code (repository root)

Planned locations; currently only DemoApplication and its context test exist.

```text
demo/src/main/java/com/example/demo/
  user/
    controller/       UserController, AddressController
    service/          UserService, AddressService
    service/impl/     UserServiceImpl, AddressServiceImpl
    entity/           User, Address
    repository/       UserRepository, AddressRepository
    converter/        UserConverter, AddressConverter
    validator/        UserValidator, AddressValidator
    dto/request/      UpdateUserRequest, CreateAddressRequest, UpdateAddressRequest
    dto/response/     PrivateUserResponse, PublicUserResponse, AddressResponse
    enums/            AccountStatus, Gender
    exception/        resource-not-found, identity-conflict, business-validation categories
  common/identity/   CurrentUserIdProvider (contract only; no temporary production implementation)
  common/exception/   ApplicationException, GlobalExceptionHandler, ApiError, error factory
demo/src/main/resources/db/migration/
demo/src/test/java/com/example/demo/user/
  service/ validator/ converter/ controller/ repository/ integration/ testdata/
demo/src/test/resources/application-test.properties
```

**Structure Decision**: Extend the existing `demo` module. User and address belong to one domain so AddressService may use UserService's coordination method but never bypass it to UserRepository. Keep UserService independent of AddressService to avoid cycles. User services may depend on media service; the media contract must avoid a reverse service dependency.

## Phase 0 - Research Decisions

Resolved decisions and primary sources are in [research.md](research.md). The design selects parent-row pessimistic coordination, database-authoritative lowercase identity keys, explicit request presence flags, normalized validation before Bean Validation, ordered default flushes and shared ProblemDetail serialization. No unresolved technical-choice placeholder remains.

## Phase 1 - Design

### Core-first integration contracts and readiness

The authoritative updated integration contracts are FR-018/FR-019 in spec.md and this section. Prior ancillary contracts/research/data-model/quickstart are unchanged under the requested edit scope; their broad auth/media prerequisites and real-media field definitions apply only to the later integration increment.

Define `common/identity/CurrentUserIdProvider.java` as a minimal `Long getCurrentUserId()` contract: immutable ID or identity-resolution failure, no JWT/Security types. No production implementation exists until the separate Authentication feature. Business service methods accept a trusted actor Long ID, do not resolve SecurityContext and scope ownership from that argument; only a real boundary adapter may supply it in production. Test-only implementations live under src/test and cannot enable production routes. Do not activate controllers or dependent filter integration until Authentication exists; ordinary unrelated Spring Security protection remains intact. Later JWT uses Authorization: Bearer, immutable ID and unchanged-token continuity on username edits.

Define the media integration service contract in documentation now, not a dummy runtime bean, generic Object ID or permissive validator. It will resolve the real media ID and validate ownership/existence/availability and reference lifecycle. Core User has no physical media association yet; return/describe null optional image state without accepting any non-null candidate. Core omitted/null-image updates proceed, null-on-absent is no-op; any non-null internal attempt fails closed before mutation as unavailable capability, with no fake validation outcome. Image ID typed binding, FK and stored-reference changes are future Media work; use additive migration, no independent-resource cascade. Media owns LocalStack S3 locally/AWS S3 remotely; core tests need neither.

Ready now: schema/model, generated lowercase keys, owner-scoped repositories, typed core DTOs, validators, services, common errors, pessimistic coordination and unit/repository/service IT with explicit controlled actor IDs. User creation fixture values satisfy encoded-only password/non-null accountStatus/joinedAt; no production signup or account defaults are added. Proposed code-point validator at `user/validator/CodePointLength.java` and `CodePointLengthValidator.java` applies String.codePointCount after strip; do not use UTF-16 length @Size as sole enforcement. PostgreSQL VARCHAR/CHAR_LENGTH semantics must agree; test supplementary characters and combining sequences.

The core now occupies implemented-but-awaiting-integration status, supported by the implementation evidence below; it cannot mark JWT/API/media acceptance complete. Blocked work is listed separately in tasks.md; full HTTP/real-token/media tests wait without stopping core tests. Existing build approvals and Boot-version reconciliation remain gates for those build edits, not requests to implement auth/media.

### Write orchestration

Services start REQUIRED read-write transactions. Set a transaction-local PostgreSQL lock_timeout from validated configuration before the first lock. UserService exposes an internal `getUserEntityForUpdate(id)` using PESSIMISTIC_WRITE. AddressService calls that service first, then reads its own owner-scoped address state. An authentication principal contains the ID only for this purpose; it must not preload a stale managed User. Disable Open EntityManager in View for these paths and load current entities inside the service transaction; if an already-managed entity is possible, refresh it under the lock before merge.

After obtaining the user lock, query current address count/default/target; validate against these facts. At most 20 addresses makes a bounded owned collection viable for transition decisions; fetch no User.addresses graph and no foreign ownership information. For a default change, clear and flush the old default before promoting/inserting the new one. For default deletion, delete and flush before promoting the highest remaining ID. Complete all phases in one transaction; any failure rolls them back. No parent lock is assumed to physically lock children: correctness requires every feature writer to take the same parent lock. User ID ascending, then address ID ascending if extra locks are necessary; no cross-user business operation is introduced.

Profile updates merge only supplied allowed fields after locking. Normalize text before HTTP boundary validation. Keep explicit presence flags for username/email (null rejected) and profileImageId (null is a no-op while no image exists; real stored-reference clearing is Media integration); optional scalar null preserves. Use typed setters/custom binding on the request DTO, not a generic untyped map or new wrapper library. Reject protected properties explicitly even if framework unknown-property defaults would ignore them.

### Read and mapping design

Reads use read-only transactions. Private/public user queries fetch only required scalar fields and avatar identity with a use-case projection or LAZY to-one identifier fetch plan proven by RepositoryIT. Address list uses an owner predicate and default-descending/id-descending derived query. Required entity getters throw not-found; DTO methods use get... naming. Core DTO/converter work uses absent/null images without a guessed non-null media type; real binding and fetch plans wait for Media. Validators perform business decisions using loaded facts and services arrange data before conversion. Converters cannot initialize lazy relationships.

### Error and integrity design

Common ApplicationException supports a small set of user-domain categories. GlobalExceptionHandler maps application/MVC failures to ApiError; a shared safe factory is reused by security entry-point/access-denied handlers. Identify known username/email constraint violations specifically; do not turn all integrity failures into identity conflicts. Database errors must escape the write transaction so rollback completes before HTTP mapping; never continue using an aborted transaction. Recognized lock-timeout failures map to 409 without retry; unrelated outages are safe 5xx, not disguised input errors.

### Validation strategy and completion gates

Unit tests cover mapping/validators/orchestration without Spring. Controller slices cover normalization, JSON presence and protected fields, all statuses/allowlists and Security. RepositoryIT verifies lower-key queries, constraints, fetch plans and no hidden converter reads. ServiceIT uses independent real transactions and latches/barriers for same-owner ordering, unrelated-owner progress, empty-book race, 19-to-20 capacity race, default swaps, rollback and lock timeout. No fixed sleeps establish ordering. API IT verifies real bearer-token continuity after rename once auth prerequisites exist, with real internal services and Flyway PostgreSQL. Fixtures provision users through approved test support, not a production registration endpoint added for tests.

Core completion requires core business scenarios passing with controlled test identities and null images, plus explicit Test/IT discovery. Production/API/media completion separately requires real future integration and the corresponding unchanged final acceptance scenarios. This planning pass runs document checks only; it makes no build/test or deployment claim.

## Complexity Tracking

No constitutional violations or extra architectural layers are introduced. One owning-user row acts as the coordination point across same-user profile/address writes; cross-user uniqueness remains protected by database indexes.


## Specification and Engineering Separation (2026-10-26)

At the user's request, spec.md now states business capabilities, exact field/privacy rules, normalization, concurrent-write outcomes and acceptance criteria without selecting frameworks, packages, endpoints or persistence mechanisms. All 19 FR and six SC identifiers are retained. The original binding engineering decisions are relocated to [contracts/engineering.md](contracts/engineering.md); [contracts/http.md](contracts/http.md) remains the exact eight-route, status and error-schema contract. No technical constraint, dependency approval gate or deferred integration acceptance is waived.

The core-first identity/media decisions in this plan and engineering.md remain authoritative over older ancillary prerequisites. References in existing tasks/checklist evidence to engineering details formerly in spec.md now resolve to engineering.md and this plan; their business FR references still resolve to spec.md. No production code, dependencies, migrations or runtime tests changed.

## Core implementation evidence (2026-10-26)

Core User/Address persistence, DTOs, converters, validation, services, common errors and current-user boundary are implemented. Boot 4.1.2, validation starter, Lombok and Failsafe were explicitly approved and applied. No feature controller, production identity provider, JWT implementation, media association/provider or S3 code was introduced. The independent core is implemented awaiting future boundary integrations; full customer HTTP/media flows remain blocked, not production-ready.

Flyway V1/V2 were applied only to disposable PostgreSQL 17 Testcontainers databases during verification. Repository/service integration exercises uniqueness, Unicode limits, owner scoping, default transitions, empty-book and capacity races, rollback, lock timeout, independent-user progress and last-successful-write ordering. Unit tests do not load Spring; integration tests use real internal dependencies and service commit/rollback boundaries.

Maven's cached 3.9.16 distribution was used directly because mvnw.cmd fails at startup in this PowerShell environment (empty Target array). Docker's named pipe is available to the elevated test process despite no docker command on PATH. The full verify log and Surefire/Failsafe reports are under demo/target and are ignored. No shared/developer/production database was accessed. Final case counts and task dispositions are recorded in tasks.md. Existing static-analysis tooling was not configured or represented as passing.

Final verified result: 45 core tasks complete; 13 future-integration tasks deferred. Current reports contain 44 passing Test cases and 25 passing IT cases (full verify plus targeted final address rerun); zero failures/errors/skips.
