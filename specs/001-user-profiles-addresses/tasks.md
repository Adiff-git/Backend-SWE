# Tasks: User Profiles and Address Management

**Input**: Design artifacts in `specs/001-user-profiles-addresses/`.
**Prerequisites**: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md), [data-model.md](data-model.md), [HTTP contract](contracts/http.md), [integration contract](contracts/integration.md), [quickstart.md](quickstart.md).
**Tests**: Explicitly requested by the feature specification. Unit/slice classes end Test; integration classes end IT. Core tasks T001-T045 are complete; future integration tasks T046-T058 remain deferred and unchecked.
**Organization**: Foundation then US1 (P1), US2 (P1), US3 (P2); independent acceptance checkpoints. `[P]` means disjoint-file work within a ready group, not permission to bypass prerequisites.

## Format and Path Conventions

Every task has an unchecked checkbox, sequential T ID, optional [P], story label only in story phases and repository-relative file paths. Java sources live in `demo/src/main/java/com/example/demo/`; tests mirror them under `demo/src/test/java/com/example/demo/`. Migration directory tasks intentionally select a fresh version after inspecting history; do not guess or overwrite an applied version.

## Execution Gates

The custom checklist is reviewer-owned and fully checked by the reviewer; implementation did not change its markers. Task generation does not approve it or modify its markers. Dependency approvals remain gates for build edits; Authentication/Media availability gates only future-dependent tasks, not core work; a task list is not authorization to implement out-of-scope prerequisites. If an external gate is unmet, report the blocker and continue only independent authorized work; do not mark gated tasks complete or silently reduce the contract.

## Phase 1: Setup

Complete these gates before changing build configuration or implementing feature code.

- [X] T001 Record reviewer disposition of `specs/001-user-profiles-addresses/checklists/profiles-addresses.md` and the two documented style exceptions in `specs/001-user-profiles-addresses/checklists/requirements.md`; do not mark reviewer-owned items yourself without explicit review authorization.
- [X] T002 Inspect `demo/pom.xml` effective dependencies and record approval for actually missing validation starter, Lombok and Failsafe plus reconciliation of Boot 4.1.1 with target 4.1.2 in `specs/001-user-profiles-addresses/contracts/integration.md`; do not add dependencies/plugins before approval.
- [X] T003 Document core-first and future integration contracts in `specs/001-user-profiles-addresses/plan.md` and `specs/001-user-profiles-addresses/spec.md` FR-018/FR-019: core components proceed now, no production identity/media substitutes, future JWT and media adapters blocked. Do not wait for real auth/media contracts before independent work.
- [X] T004 After approval, update `demo/pom.xml` for the approved Boot baseline, missing validation/Lombok dependencies and annotation processing, and Surefire `*Test` / Failsafe `*IT` integration-test/verify discovery; avoid duplicate dependencies or unrelated tooling.
- [X] T005 Configure disposable PostgreSQL 17 Testcontainers/Flyway and schema validation in `demo/src/test/resources/application-test.properties`; ensure no environment override targets a developer/production database and no ddl-auto create/update substitutes for migrations.

## Phase 2: Foundational (Blocking Prerequisites)

Shared User infrastructure serves all stories. Requires applicable Phase 1 build approvals, not existing Authentication or Media. Do not proceed past an unmet gate. Migrations use the next available Flyway version after reinspection, never rewrite an applied migration.

- [X] T006 Create/reuse the shared `demo/src/main/java/com/example/demo/user/entity/User.java` with Long/BIGINT id "PK, non-null, immutable; define/preserve core User BIGINT identity generation for later Authentication reuse"; displayName VARCHAR(100) "Nullable; supplied blank rejected after trim"; username VARCHAR(50) "Non-null, nonblank, trimmed; casing preserved"; password TEXT "Non-null encoded credential only; no production password creation workflow in this feature; no response/update binding"; email VARCHAR(254) "Nullable; valid nonblank when supplied; trimmed lowercase"; phoneNumber VARCHAR(20) "Nullable, trimmed/nonblank when supplied, not unique"; accountStatus VARCHAR(16) "Non-null string enum; tests supply explicit status; no invented production provisioning policy"; gender VARCHAR(6) "Nullable string enum"; dateOfBirth DATE "Nullable; not future using UTC clock at validation time"; joinedAt TIMESTAMPTZ "Non-null, set on creation and immutable to profile writes". Use safe Lombok, no @Data; create the core User now; future Authentication reuses it rather than owning a prerequisite duplicate.
- [X] T007 Create `demo/src/main/java/com/example/demo/user/enums/AccountStatus.java` (ACTIVE, INACTIVE, SUSPENDED, BANNED) and `demo/src/main/java/com/example/demo/user/enums/Gender.java` (MALE, FEMALE, OTHER); persist EnumType.STRING without inventing status eligibility rules.
- [X] T008 Add new user-schema migration(s) in `demo/src/main/resources/db/migration/` using the exact User limits/nullability above, enum/nonblank checks, immutable creation timestamp ownership and generated LOWER keys: username_key "Non-null unique key, not an editable/entity business field" and email_key "Nullable unique key; multiple nulls permitted"; named uk_users_username_key / uk_users_email_key, no phone uniqueness; omit the media association/column/FK from the core migration; its later additive migration is deferred. Preserve data, stop for approved remediation of existing collisions, and use UPPERCASE SQL/types. Count all specified string maxima in Unicode code points after trimming, not UTF-16 units; align PostgreSQL character limits and code-point-aware validation.
- [X] T009 Implement `demo/src/main/java/com/example/demo/user/repository/UserRepository.java` and `demo/src/main/java/com/example/demo/user/service/UserService.java` with required entity retrieval and getUserEntityForUpdate using PESSIMISTIC_WRITE; internal lowercase-key conflict queries exclude current ID and compare with PostgreSQL LOWER under the same collation. Use database-authoritative email canonicalization; do not impose ASCII username restrictions.
- [X] T010 Implement shared retrieval/coordination in `demo/src/main/java/com/example/demo/user/service/impl/UserServiceImpl.java`: REQUIRED/READ COMMITTED write transaction, parent user lock before state reads, fresh managed state, and no UserService -> AddressService dependency; reads read-only; missing entity throws a domain not-found exception.
- [X] T011 Configure positive externalized per-transaction lock_timeout (proposed default 5s) in `demo/src/main/resources/application.properties` and `demo/src/main/java/com/example/demo/user/repository/UserRepository.java` support; set before locking, disable feature-path Open EntityManager in View or refresh managed state under lock. Every writer participates; lock user IDs then any address IDs in ascending order, no global lock or network/S3 call under lock.
- [X] T012 Create/reuse `demo/src/main/java/com/example/demo/common/exception/ApplicationException.java`, `demo/src/main/java/com/example/demo/common/exception/ApiError.java`, `demo/src/main/java/com/example/demo/common/exception/ApiErrorFactory.java` and `demo/src/main/java/com/example/demo/common/exception/GlobalExceptionHandler.java`: ApiError extends ProblemDetail with type/title/status/detail/instance plus timestamp/message/path/fieldErrors; map MVC/business 400/404/409 and safe 5xx, known uniqueness and lock timeout only, after transaction rollback; no SQL/credential/internal exception disclosure or automatic retry.
- [X] T013 Add small reusable domain exception categories under `demo/src/main/java/com/example/demo/user/exception/` for missing user/address, identity conflicts and business validation (default/image/capacity); derive business exceptions from the common base, never one class per branch or infrastructure wrappers just for naming.
- [X] T014 [P] Create reusable `demo/src/test/java/com/example/demo/user/testdata/UserTestData.java` and `demo/src/test/java/com/example/demo/user/integration/PostgresTestSupport.java` using core User fixtures with explicit encoded-only password, accountStatus and joinedAt plus disposable Flyway schema, independent of Authentication/Media; deterministic cleanup, no hardcoded generated IDs, no Spring context for unit tests.
- [X] T015 [P] Add `demo/src/test/java/com/example/demo/common/exception/ApiErrorTest.java` for actual Boot/Jackson ProblemDetail extensions and safe error fields; distinguish known conflicts/timeouts from unrelated integrity/outage failures.
- [X] T016 Add `demo/src/test/java/com/example/demo/user/repository/UserRepositoryIT.java` for real generated-key uniqueness/null emails/non-ASCII collation agreement, required fetch plans, user lock coordination and schema constraints; use @DataJpaTest with PostgreSQL/Flyway and do not infer SQL behavior from mocks.

- [X] T017 Define contract-only `demo/src/main/java/com/example/demo/common/identity/CurrentUserIdProvider.java` with immutable Long getCurrentUserId or unavailable-identity failure, no Spring Security/JWT types and no temporary production implementation; service entry methods take trusted actor IDs and never resolve client claims or SecurityContext.
- [X] T018 [P] Add `demo/src/test/java/com/example/demo/common/identity/TestCurrentUserIdProvider.java` and `demo/src/test/java/com/example/demo/common/identity/CurrentUserIdProviderTest.java` for controlled test identities and unavailable identity; keep all implementations in test sources and assert foreign-address ownership is never bypassed.
- [X] T019 Implement `demo/src/main/java/com/example/demo/user/validator/CodePointLength.java` and `demo/src/main/java/com/example/demo/user/validator/CodePointLengthValidator.java` for post-strip codePointCount; apply to all core bounded DTO strings instead of relying on UTF-16 @Size. Add `demo/src/test/java/com/example/demo/user/validator/CodePointLengthValidatorTest.java` with supplementary characters and combining sequences at/beyond every relevant maximum; test PostgreSQL character-limit agreement in `demo/src/test/java/com/example/demo/user/repository/UserRepositoryIT.java` and later AddressRepositoryIT.

## Phase 3: User Story 1 - Maintain my private profile (P1, MVP)

Goal: view and selectively update self details. Independent criterion: a controlled test actor edits core fields with null images and exact presence/normalization rules, without addresses or JWT; real token/HTTP/media acceptance is deferred. Write the requested tests first against planned contracts, observe meaningful failures once dependencies compile, then implement and pass them.

- [X] T020 [P] [US1] Write `demo/src/test/java/com/example/demo/user/validator/UserValidatorTest.java` for current-ID exclusion, case conflicts, today/tomorrow UTC birth dates and unavailable non-null-image operations before mutation; real valid/foreign/unavailable-media scenarios are deferred to Media integration. Use supplied core facts, no Spring context.
- [X] T021 [P] [US1] Write `demo/src/test/java/com/example/demo/user/converter/UserConverterTest.java` for exact private DTO mapping, null optional values, absent/null image state and no password/internal-key exposure; actual non-null media ID mapping is deferred; converter must not validate or fetch.
- [X] T022 [P] [US1] Write `demo/src/test/java/com/example/demo/user/service/UserServiceImplTest.java` for selective merge, atomic conflict failures and omitted/null-on-absent image no-ops and fail-closed non-null image attempts without partial writes; real stored-reference changes await Media; no context, real DTO/entity fixtures.
- [X] T023 [US1] Create `demo/src/main/java/com/example/demo/user/dto/request/UpdateUserRequest.java` with presence-aware username/email binding; preserve the optional image omission/null contract, reject non-null image attempts as unavailable without inventing an ID type until Media; username max 50, email max 254, displayName max 100, phoneNumber max 20; trim before Bean Validation, preserve internal whitespace, reject supplied blank strings, reject explicit null username/email, preserve null optional scalars, date-only non-future UTC birth date and valid enum; reject protected properties explicitly; do not add a wrapper library. Count all specified string maxima in Unicode code points after trimming, not UTF-16 units; align PostgreSQL character limits and code-point-aware validation.
- [X] T024 [US1] Create `demo/src/main/java/com/example/demo/user/dto/response/PrivateUserResponse.java` and extend `demo/src/main/java/com/example/demo/user/converter/UserConverter.java` for the exact private contract fields, nullable optionals and UTC Z joinedAt; null image state now, defer actual non-null media-ID binding until Media; never password, roles, permissions or internal entity relationships.
- [X] T025 [US1] Implement `demo/src/main/java/com/example/demo/user/validator/UserValidator.java` business validation using loaded facts for normalized uniqueness excluding self and fail-closed unavailable media capability checks; real ownership/existence/availability validation is deferred until Media; do not access cross-domain repositories or duplicate boundary validation.
- [X] T026 [US1] Extend `demo/src/main/java/com/example/demo/user/service/UserService.java` and `demo/src/main/java/com/example/demo/user/service/impl/UserServiceImpl.java` with get-current DTO and atomic selective update using an explicit trusted actor Long ID, no JWT/Security internals; after parent lock, preserve latest omitted values, canonicalize email via PostgreSQL LOWER, retain username display casing, never mutate id/password/status/joinedAt/roles, no token invalidation or image deletion.
- [X] T027 [US1] Add `demo/src/test/java/com/example/demo/user/integration/UserServiceIT.java` using independent committed transactions for case-insensitive identity races, same-field last-write and disjoint-field preservation, rollback, unchanged joinedAt and held-lock 409; barriers/latches rather than ordering sleeps, no test-level transaction masking service behavior.

## Phase 4: User Story 2 - Maintain my address book (P1)

Goal: owner-scoped address CRUD with one default and capacity 20. Independent criterion: provision a core User fixture and controlled actor ID from foundation, exercise address lifecycle without calling profile-update endpoints. Depends on foundation, not US1 completion.

- [X] T028 [P] [US2] Write `demo/src/test/java/com/example/demo/user/validator/AddressValidatorTest.java` covering capacity 20, explicit false on default rejected, non-default false allowed and required field rules; parameterize equivalent variants, keep distinct transitions separate.
- [X] T029 [P] [US2] Write `demo/src/test/java/com/example/demo/user/converter/AddressConverterTest.java` for id/recipientName/recipientPhone/addressText/isDefault only, with no owner relationship or hidden fetching.
- [X] T030 [P] [US2] Write `demo/src/test/java/com/example/demo/user/service/AddressServiceImplTest.java` for every first/later-create, update and default/non-default/final-delete transition, highest-ID fallback, owner scoping and user coordination before address-state reads.
- [X] T031 [US2] Create `demo/src/main/java/com/example/demo/user/entity/Address.java`: id Long/BIGINT "PK, generated; highest remaining value determines fallback"; recipientName VARCHAR(100) "Non-null, trimmed, nonblank"; recipientPhone VARCHAR(10) "Non-null; ASCII pattern `^0[0-9]{9}$` after trim; not unique"; addressText VARCHAR(1000) "Non-null, trimmed, nonblank; internal whitespace preserved"; isDefault Boolean "Non-null"; user LAZY User/BIGINT "Non-null FK to users.id, exactly one owner". Use safe Lombok, no extra fallback timestamp or media cascade. Count all specified string maxima in Unicode code points after trimming, not UTF-16 units; align PostgreSQL character limits and code-point-aware validation.
- [X] T032 [US2] Add a new address migration in `demo/src/main/resources/db/migration/` with the exact Address constraints above, idx_addresses_user_id_is_default_id and UNIQUE partial idx_addresses_user_id_default on user_id WHERE is_default; no redundant index, no rewritten migration and no invented user-deletion cascade. Count all specified string maxima in Unicode code points after trimming, not UTF-16 units; align PostgreSQL character limits and code-point-aware validation.
- [X] T033 [US2] Create `demo/src/main/java/com/example/demo/user/dto/request/CreateAddressRequest.java` and `demo/src/main/java/com/example/demo/user/dto/request/UpdateAddressRequest.java`: recipientName max 100, recipientPhone exactly `0[0-9]{9}`, addressText max 1000, all required/non-null/nonblank after surrounding trim; preserve internal whitespace; isDefault optional/nullable Boolean, body owner ID cannot establish ownership. Count all specified string maxima in Unicode code points after trimming, not UTF-16 units; align PostgreSQL character limits and code-point-aware validation.
- [X] T034 [P] [US2] Create `demo/src/main/java/com/example/demo/user/dto/response/AddressResponse.java` and `demo/src/main/java/com/example/demo/user/converter/AddressConverter.java` with exact address allowlist and no validation/database queries.
- [X] T035 [P] [US2] Create `demo/src/main/java/com/example/demo/user/repository/AddressRepository.java` with owner-scoped target retrieval and default-descending/ID-descending list, bounded count/fallback access and explicit flush support; prefer derived queries, never fetch foreign addresses to reveal ownership.
- [X] T036 [US2] Implement `demo/src/main/java/com/example/demo/user/validator/AddressValidator.java` for capacity and default-change rules against current loaded owner state; missing/foreign scope failures share not-found behavior, reject over-capacity create with 400 without changing defaults.
- [X] T037 [US2] Implement `demo/src/main/java/com/example/demo/user/service/AddressService.java` and `demo/src/main/java/com/example/demo/user/service/impl/AddressServiceImpl.java`: read-only owner-scoped reads; REQUIRED writes call UserService.getUserEntityForUpdate first, then reload count/target/default; first create always default, later null/omitted false, true clears+flushes old default before insert/promotion, null/omitted update preserves latest flag, reject default false, delete+flush before highest-ID promotion, no retries or global lock.
- [X] T038 [US2] Add `demo/src/test/java/com/example/demo/user/testdata/AddressTestData.java` and `demo/src/test/java/com/example/demo/user/repository/AddressRepositoryIT.java` for ownership predicates, sorting/fallback, constraints, partial unique default index and LAZY/fetch mapping on PostgreSQL/Flyway.
- [X] T039 [US2] Add `demo/src/test/java/com/example/demo/user/integration/AddressServiceIT.java` with real commit/rollback boundaries for every transition and failures after clear/delete flush; cover two first creates, count-19 two creates, competing defaults, delete/create, last-write recipient edits, unrelated-user progress and lock timeout 409 with no retries, using independent connections and barriers.

## Phase 5: User Story 3 - View a limited profile (P2)

Goal: the core read service maps only the public allowlist of another user; HTTP authentication is deferred. Independent criterion: two pre-existing fixtures suffice, without profile edits or addresses. Foundation required; shared UserController/UserConverter files are edited sequentially after US1 to avoid conflicts.

- [X] T040 [P] [US3] Write `demo/src/test/java/com/example/demo/user/converter/PublicProfileConverterTest.java` for only id/username/displayName/profileImageId/joinedAt, null fields and UTC timestamps; exclude email/phone/addresses/birth date/gender/status/password/roles/permissions/internal keys.
- [X] T041 [US3] Create `demo/src/main/java/com/example/demo/user/dto/response/PublicUserResponse.java` and extend `demo/src/main/java/com/example/demo/user/converter/UserConverter.java` with the exact public allowlist, null optional image state now and no lazy fetching or validation; null image state now, defer non-null typed binding until Media.
- [X] T042 [US3] Extend `demo/src/main/java/com/example/demo/user/repository/UserRepository.java`, `demo/src/main/java/com/example/demo/user/service/UserService.java` and `demo/src/main/java/com/example/demo/user/service/impl/UserServiceImpl.java` with read-only getUser(id) returning the public DTO using only its use-case fetch plan, missing 404 and no extra role/status restriction; keep entity getter naming distinct. Add core fetch-plan/privacy verification in `demo/src/test/java/com/example/demo/user/repository/UserRepositoryIT.java` and `demo/src/test/java/com/example/demo/user/integration/PublicProfileServiceIT.java` with controlled identities and null images; no authenticated HTTP claim.

## Phase 6: Polish and Cross-Cutting Validation

Run after selected core components pass, without waiting for Authentication/Media; this is future work, not a claim of tests run by task generation.

- [X] T043 Run core unit/repository/service test groups from `demo/pom.xml` against disposable PostgreSQL/Flyway and inspect actual `demo/target/surefire-reports/` and `demo/target/failsafe-reports/`; report executed Test/IT counts, explicitly identify deferred API/media classes, and do not disable or pretend to pass unavailable integration scenarios. Full quickstart API flows wait for real external integration.
- [X] T044 Review implemented paths under `demo/src/main/java/com/example/demo/user/` and `demo/src/main/java/com/example/demo/common/exception/` against constitution/spec: acyclic services, no converter queries, no entity responses, correct transactions/lock order, no cascade into media, no credentials/log disclosure, no unrelated auth/storage/admin feature or unapproved tooling additions.
- [X] T045 Update core/integration status and validation evidence in `specs/001-user-profiles-addresses/plan.md` and `specs/001-user-profiles-addresses/tasks.md`: distinguish ready work, implemented-awaiting-integration and blocked tasks; no component is currently implemented. Preserve reviewer checklist states and do not claim JWT/API/media production readiness from controlled service-test identities.

## Deferred Integration Work (Does Not Block Core Phases)

These are tracked future tasks, not prerequisites for core completion. Do not activate protected HTTP endpoints without real Authentication, implement JWT/S3 here or create dummy production adapters. Retained final HTTP/media acceptance remains incomplete until the real integrations exist.

- [ ] T046 [US1] Deferred until Media: After Media exists, add the real optional LAZY association and additive FK migration in `demo/src/main/java/com/example/demo/user/entity/User.java` and `demo/src/main/resources/db/migration/`; reuse actual durable ID/schema, validate references via real service, no guessed type or cascade into media.
- [ ] T047 Deferred until Authentication: After the separate Authentication feature exists, integrate its real adapter and filter handlers with `demo/src/main/java/com/example/demo/common/identity/CurrentUserIdProvider.java` and `demo/src/main/java/com/example/demo/common/exception/ApiErrorFactory.java`; protect all eight routes, Bearer JWT immutable ID, 401 for anonymous/invalid tokens, no extra feature restrictions or token issuance here.
- [ ] T048 [US1] Deferred until Authentication (and Media for non-null-image scenarios): Write `demo/src/test/java/com/example/demo/user/controller/UserControllerTest.java` contract/slice tests for GET/PUT /api/me: private allowlist, all omitted/null/value groups, empty no-op, protected-field 400, malformed types/enums/dates, field lengths at/over maxima, trimmed blanks, 401 and standard error schema.
- [ ] T049 [US1] Deferred until Authentication (and Media for non-null-image scenarios): Implement GET/PUT /api/me in `demo/src/main/java/com/example/demo/user/controller/UserController.java` with @Valid, immutable principal ID, 200 DTO responses, no generic wrapper or trusted body user ID; required missing current user is 404.
- [ ] T050 [US1] Deferred until Authentication (and Media for non-null-image scenarios): Add `demo/src/test/java/com/example/demo/user/integration/UserApiIT.java` with real future authentication and media integration prerequisites and MockMvc -> service -> Flyway PostgreSQL: all profile semantics, exact errors, avatar validity and original real JWT reused after username rename; do not claim a mock principal proves token continuity.
- [ ] T051 [US2] Deferred until Authentication: Write `demo/src/test/java/com/example/demo/user/controller/AddressControllerTest.java` for all five address operations, 201+Location/200/204 bodies, ordered empty/nonempty arrays, required recipient fields, exact ASCII phone classes, 401 and indistinguishable missing/foreign 404.
- [ ] T052 [US2] Deferred until Authentication: Implement all /api/me/addresses routes in `demo/src/main/java/com/example/demo/user/controller/AddressController.java` delegating with immutable principal ID and @Valid; empty [], deterministic ordering, exact 201 Location/200/204 and 400/401/404/409 contracts.
- [ ] T053 [US2] Deferred until Authentication: Add `demo/src/test/java/com/example/demo/user/integration/AddressApiIT.java` for real authenticated HTTP -> controller -> service -> database CRUD, exact errors/allowlists, capacity 400, missing/foreign 404, phone normalization, fallback and empty/final state; use Flyway disposable PostgreSQL, clean committed fixture data.
- [ ] T054 [US3] Deferred until Authentication (and Media for non-null-image scenarios): Write `demo/src/test/java/com/example/demo/user/controller/PublicProfileControllerTest.java` slice contract tests for GET /api/users/{userId}: authentication-only policy, 401, malformed 400, missing 404, exact allowlist and sensitive-field exclusions regardless of viewer status/role.
- [ ] T055 [US3] Deferred until Authentication (and Media for non-null-image scenarios): Extend `demo/src/main/java/com/example/demo/user/controller/UserController.java` for authenticated GET /api/users/{userId}, 200 PublicUserResponse, valid Long binding and no public access without authentication.
- [ ] T056 [US3] Deferred until Authentication: add `demo/src/test/java/com/example/demo/user/integration/PublicProfileApiIT.java` for real authenticated HTTP/database public privacy and anonymous 401; no additional feature permissions. Non-null media representation waits for Media; core fetch/converter verification has separate tasks.

- [ ] T057 [US1] After Media exists, finalize typed profileImageId request/response binding and real reference validation in `demo/src/main/java/com/example/demo/user/dto/request/UpdateUserRequest.java`, `demo/src/main/java/com/example/demo/user/converter/UserConverter.java` and `demo/src/main/java/com/example/demo/user/service/impl/UserServiceImpl.java`; retain omitted/preserve, null/remove, valid-replace, invalid 400 and atomic rollback, validate ownership/existence/availability through the real service with no S3 calls under user lock.
- [ ] T058 [US1] After Media exists, extend `demo/src/test/java/com/example/demo/user/integration/UserServiceIT.java` and `demo/src/test/java/com/example/demo/user/converter/UserConverterTest.java` for real durable typed references, preserve/remove/replace, foreign/missing/unavailable media and no object deletion; use LocalStack S3 only when the separate Media contract actually requires storage integration, never a temporary implementation.

## Dependencies and Execution Order

Setup/build approvals -> core foundation (User schema/repository/coordination, error support, identity contract, test fixtures, code-point validator) -> US1/US2/US3 core business components -> core Test/IT evidence. Authentication and Media are implemented later as separate features -> deferred integration work -> real authenticated API/media acceptance.

No core task depends on a deferred task. Core schema omits the unknown media association/FK and adds it only later. Tests provide explicit fixture identities, encoded-password values, status and joinedAt without requiring signup/JWT or production identity providers. Business services retain owner scoping from trusted actor IDs. No HTTP route is activated by a test adapter. Core tests use null images; non-null media operations fail closed without fake validation. Approved dependency additions remain subject to constitution approval, not Authentication/Media readiness.

Core story test files may be authored first once shared types/contracts exist. Within each story DTO/entity -> validator/converter/repository -> service -> core integration evidence. Shared User service/repository/converter files must be edited serially across US1/US3; migrations assign versions sequentially. Deferred task numbering is tracking order only, not a gate on earlier core work.

## Parallel Examples

- US1: core UserValidatorTest, UserConverterTest and UserServiceImplTest in disjoint files after core foundation; controller/API tests remain deferred.
- US2: AddressValidatorTest, AddressConverterTest and AddressServiceImplTest can be authored independently; AddressConverter and AddressRepository implementation are disjoint once model/request contracts exist.
- US3: PublicProfileConverterTest can proceed with core public DTO/read service; real HTTP slice/API security acceptance waits for Authentication.

## Implementation Strategy and Status

The first deliverable is core User/Address business behavior and tests, not a deployable authenticated API MVP. Implement foundation, US1 core, US2 core and US3 core before Authentication/Media integration. Core tasks are implemented and verified; they now await future Authentication/Media boundary integration. Deferred integration tasks remain unchecked. A component enters that category only with its own passing core evidence; its deferred integration tasks remain unchecked. Anonymous 401 and authenticated public access requirements are final Authentication contracts with no extra feature restrictions. No fake authentication, hardcoded production IDs, unvalidated references or temporary S3 services.

## Coverage Map

All FR-001 through FR-019 and SC-001 through SC-006 are mapped below. Core and deferred tasks are distinguished by their phase and explicit dependency wording; coverage is not implementation completion.

| Requirement | Task coverage |
| --- | --- |
| FR-001 | T003, T044 |
| FR-002 | T017-T018, T026, T047-T056 |
| FR-003 | T021, T024, T026 |
| FR-004 | T040-T042, T054-T056 |
| FR-005 | T022-T027, T048-T050 |
| FR-006 | T008-T010, T016, T020, T025-T027 |
| FR-007 | T022-T026, T046, T057-T058 |
| FR-008 | T030, T035-T039, T051-T053 |
| FR-009 | T028, T031-T033, T038 |
| FR-010 | T030-T032, T036-T039 |
| FR-011 | T009-T011, T027, T037, T039 |
| FR-012 | T024, T034, T041-T042, T048-T056 |
| FR-013 | T012-T015, T047-T056 |
| FR-014 | T006-T008, T016, T031-T032, T038, T046 |
| FR-015 | T026-T027, T037, T039 |
| FR-016 | T028, T036-T039 |
| FR-017 | T011-T012, T027, T039 |
| FR-018 | T003, T014, T017-T018, T026, T037, T043-T045, T047 |
| FR-019 | T003, T008, T022-T026, T046, T057-T058 |
| SC-001 | T020-T042, T043, T048-T058 |
| SC-002 | T021, T030, T038, T040-T042, T051-T056 |
| SC-003 | T020-T027, T048-T050 |
| SC-004 | T030, T037-T039 |
| SC-005 | T012-T015, T027, T039, T047-T056 |
| SC-006 | T037-T039, T052-T053 |

## Notes

Historical ancillary contracts/research/data-model/quickstart remain unchanged per the requested scope. For integration order and code-point counting, use revised spec.md/plan.md/tasks.md instead of their superseded broad prerequisite statements. Core production code, tests and migrations are implemented; no Authentication/Media integration or protected HTTP endpoint activation is claimed. No checklist approval, commit, push or deployment is implied.

## Implementation progress and verification (2026-10-26)

- **Completed**: T001-T045 (45 tasks). Core entities/schema, repositories, presence-aware DTOs and Unicode validation, services, ownership/default/capacity rules, common errors, identity contract, unit and real PostgreSQL integration tests, approved build updates and evidence documentation.
- **Deferred**: T046-T058 (13 tasks). Real media ID/association/FK/reference operations, real Authentication adapter/filter handlers, protected controllers and HTTP/token/media acceptance. These remain unchecked; none is simulated or silently considered complete.
- **Reviewer/build approval**: user confirmed all source specifications and checked all checklist items, then explicitly approved validation starter, Lombok, Failsafe and Boot 4.1.2. Existing checklist files were read only during implementation.
- **Full verification**: Maven verify succeeded with 44 Surefire Test cases and 23 Failsafe IT cases. After adding two address concurrency/rollback cases, only affected AddressServiceIT was rerun through test-compile/failsafe integration-test/verify: all 10 cases passed. Combined current XML reports contain 44 Test + 25 IT, zero failures/errors/skips across 12 Test and six IT classes.
- **Evidence**: demo/target/core-verify.log, demo/target/address-final.log, demo/target/surefire-reports/TEST-*.xml and demo/target/failsafe-reports/TEST-*.xml. The final targeted failsafe-summary describes that targeted run; aggregate case totals come from individual XML reports, not an invented single 69-case invocation.
- **Environment**: Java 25; cached Maven 3.9.16 invoked directly with explicit local repository. The repository wrapper fails before Maven starts because its PowerShell empty Target array access is invalid in this environment; it was not modified. Docker Desktop is available through the elevated process's named pipe even though docker CLI is not on PATH. All database execution used PostgreSQL 17-alpine Testcontainers and Flyway, never a shared database.
- **Limits**: no real JWT adapter, protected feature controller, Media service, durable non-null media ID/FK or S3 storage code exists. No production API/media readiness is claimed. Static-analysis tools were not introduced or represented as passing; no commit, push or deployment performed.
