# Tasks: Image Storage and Profile References

**Input**: Design artifacts in `specs/003-image-storage/`: [plan](plan.md), [specification](spec.md), [research](research.md), [data model](data-model.md), [contracts](contracts/http.md), [quickstart](quickstart.md).
**Prerequisites**: Read all linked artifacts and `.specify/memory/constitution.md`. Task generation authorizes no implementation or dependency installation.
**Tests**: Explicitly required by the specification. Write story tests first, establish meaningful failing behavior, implement, then rerun the affected checks. Unit tests do not load Spring; MVC uses WebMvcTest/MockMvc; persistence uses PostgreSQL Testcontainers; storage integration uses isolated LocalStack. Use Test/IT suffixes and methodName_shouldExpectedBehavior_whenCondition.
**Organization**: Four user-story increments, with shared setup/foundation first. Paths below are repository-relative and refer to planned files unless already present.

## Format and execution rules

Every executable task uses `- [ ] Tnnn [P?] [USn?] description with exact path`. `[P]` indicates tasks in the same explicitly identified parallel batch can proceed independently after their batch prerequisites; it never bypasses the dependency approval or foundation gates. Story labels appear only in story phases. No automatic commit/push/deployment. Preserve unrelated README.md and .gitignore edits.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Establish concrete dependency approval and reusable verification infrastructure.

- [X] T001 Prepare an exact dependency/version proposal for AWS SDK v2 BOM/S3 and synchronous HTTP transport, TwelveMonkeys imageio-webp:3.15.0, LocalStack Testcontainers support and pinned container image in `specs/003-image-storage/research.md`; check compatibility with existing Java 25/Boot/Testcontainers management, record dependency rationale and obtain developer approval before T002 changes `demo/pom.xml` (constitution II). Do not treat this task list as dependency approval.
- [X] T002 After T001 approval, add only approved dependencies and any necessary scoped Maven discovery configuration in `demo/pom.xml`; retain existing Surefire/Failsafe phases and record resolved versions in `specs/003-image-storage/research.md` without adding unrelated quality plugins.
- [X] T003 Add reusable isolated PostgreSQL/LocalStack setup and controllable-clock support in `demo/src/test/java/com/example/demo/media/integration/MediaInfrastructureTestSupport.java`, domain fixtures in `demo/src/test/java/com/example/demo/media/testdata/MediaTestData.java`, and actual JPEG/PNG/lossy-WebP/lossless-WebP/alpha-WebP/corrupt/truncated fixtures under `demo/src/test/resources/media/`; enable S3_SKIP_SIGNATURE_VALIDATION=0, use per-test buckets/cleanup, and never connect to developer databases or AWS.

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Create common metadata and storage boundaries. Complete this phase before story implementation; none of these tasks grants customer media access.

- [X] T004 Define validated properties in `demo/src/main/java/com/example/demo/storage/config/S3StorageProperties.java` and singleton client/presigner lifecycle in `demo/src/main/java/com/example/demo/storage/config/S3StorageConfiguration.java`: configured region/bucket, optional service/signing endpoints, path style, local-only dummy credentials, production default credential chain, 900s upload/download durations, 60s total/30s attempt timeouts, disabled automatic SDK retries and no hostname rewriting, following `specs/003-image-storage/contracts/configuration.md`.
- [X] T005 Define storage service interface and implementation in `demo/src/main/java/com/example/demo/storage/service/ObjectStorageService.java` and `demo/src/main/java/com/example/demo/storage/service/impl/ObjectStorageServiceImpl.java`, plus explicit metadata/closeable-content values under `demo/src/main/java/com/example/demo/storage/dto/`: signed PUT/GET, HEAD, conditional GET, source-conditional copy with REPLACE approved metadata and fully parsed result, final existence check and staging deletion; keep SDK objects internal and close streams/temp files on every path.
- [X] T006 Add `demo/src/main/resources/db/migration/V5__image_uploads.sql` after rechecking the next migration number, preserving existing users/data and applied migrations: id BIGINT identity "Primary key"; owner_user_id BIGINT "NOT NULL, FK users(id), immutable"; staging_key VARCHAR(512) "NOT NULL, nonblank, UNIQUE uk_image_uploads_staging_key"; final_key VARCHAR(512) "Nullable, nonblank when present, UNIQUE uk_image_uploads_final_key". Use uppercase SQL and required owner/expiry indexes from `specs/003-image-storage/data-model.md`.
- [X] T007 Complete metadata constraints in the new migration `demo/src/main/resources/db/migration/V5__image_uploads.sql`: content_type VARCHAR(100) "NOT NULL; CHECK exact three allowed MIME values"; status VARCHAR(16) "NOT NULL; CHECK PENDING/COMPLETED/EXPIRED; JPA EnumType.STRING" (the enum mapping belongs to T008); expires_at TIMESTAMPTZ "NOT NULL; later than created_at"; created_at TIMESTAMPTZ "NOT NULL, immutable"; version BIGINT "NOT NULL, initial zero; technical optimistic @Version". Enforce "COMPLETED requires nonblank final_key; PENDING and EXPIRED require final_key IS NULL"; immutability of ownership/creation is enforced by application mappings, not falsely attributed to CHECK constraints.
- [X] T008 Map all T006/T007 fields with their exact constraints in `demo/src/main/java/com/example/demo/media/entity/ImageUpload.java`, define PENDING/COMPLETED/EXPIRED in `demo/src/main/java/com/example/demo/media/enums/ImageUploadStatus.java` and owner-scoped retrieval in `demo/src/main/java/com/example/demo/media/repository/ImageUploadRepository.java`; use Instant, @Version, immutable owner/creation mappings, safe Lombok/protected constructor and no consumer back-references or persisted URLs/bytes.
- [X] T009 Add domain exceptions under `demo/src/main/java/com/example/demo/media/exception/` and applicable mappings in `demo/src/main/java/com/example/demo/common/exception/ApiErrorFactory.java` and `demo/src/main/java/com/example/demo/common/exception/GlobalExceptionHandler.java`; preserve existing ProblemDetail extensions and security handlers, map 400/404/409/422/503 and unexpected 500 safely, including optimistic conflicts and source 412-to-409, without blanket exception wrapping or sensitive data disclosure.
- [X] T010 Verify foundation through `demo/src/test/java/com/example/demo/media/repository/ImageUploadRepositoryIT.java` and `demo/src/test/java/com/example/demo/storage/config/S3StorageConfigurationTest.java`: real Flyway upgrade retaining existing users, all required/null/length/unique/FK/type/state-key/deadline constraints, immutable mappings, stale-version rejection using controlled sequential transactions, positive duration/config checks, prod credential selection and endpoint separation. Run targeted checks and keep all story tests gated on this foundation.

**Checkpoint**: Shared metadata/storage boundaries work, but media routes remain inaccessible until US1 security and controllers are complete.

## Phase 3: User Story 1 - Upload and view an image (Priority: P1)

**Goal**: Complete the standalone safe upload/view/renewal journey without a profile consumer.
**Independent Test**: Using disposable infrastructure, create, raw-PUT, complete and view each supported actual format; repeat completion after the original deadline and renew access while retaining one stable ID. Missing/foreign IDs disclose nothing.

### Tests first

- [X] T011 [P] [US1] Add MVC contract tests in `demo/src/test/java/com/example/demo/media/controller/ImageUploadControllerTest.java` for three exact media routes, 201/200 DTO shapes, invalid declarations/IDs/unknown fields, 401, no-store, and eligible callers lacking MANAGE_USER_PROFILE; assert no client owner/key/bucket authority and retain header-only authentication/appropriate CSRF behavior.
- [X] T012 [P] [US1] Add pure command-service unit tests in `demo/src/test/java/com/example/demo/media/service/MediaCommandServiceTest.java` for server-generated owner/keys, aligned 900-second expiry, lifecycle branches, conditional-copy coordination and completed idempotence without recopy/revalidation. Add facade unit tests in `demo/src/test/java/com/example/demo/media/service/MediaServiceTest.java` only for its own decisions: ambient-transaction rejection, expired-outcome-to-error mapping and post-commit access preparation/renewal; omit redundant tests of simple delegation. Keep response-mapping tests in `demo/src/test/java/com/example/demo/media/converter/MediaConverterTest.java` separate. Mock dependencies rather than the component under test, use no Spring context, and rely on T026 for actual commit/rollback behavior.
- [X] T013 [P] [US1] Add real-flow integration tests in `demo/src/test/java/com/example/demo/media/integration/ImageUploadFlowIT.java` for all supported fixtures, required PUT Content-Type, actual final bytes/approved metadata, fresh GET access, sequential repeat completion, no additional media permission, and completed-image usability after upload expiry.

### Implementation

- [X] T014 [P] [US1] Create strict Jakarta-validated `demo/src/main/java/com/example/demo/media/dto/request/CreateImageUploadRequest.java`, upload/access/prepared-image Lombok DTOs under `demo/src/main/java/com/example/demo/media/dto/response/`, and `demo/src/main/java/com/example/demo/media/converter/MediaConverter.java`; map supplied data only, reject unknown fields, and expose only documented fields.
- [X] T015 [P] [US1] Implement actual-byte decoder/ownership/lifecycle validation in `demo/src/main/java/com/example/demo/media/validator/ImageContentValidator.java` and `demo/src/main/java/com/example/demo/media/validator/ImageUploadValidator.java`: allow exact image/jpeg, image/png, image/webp, inspect conditional-read metadata, decode pixels and compare actual format; fail closed for missing readers/null/error/corrupt content, dispose readers/temp files, and introduce no resizing/static-only/size-cap policy.
- [X] T016 [US1] Define public/consumer interfaces in `demo/src/main/java/com/example/demo/media/service/MediaService.java` and transactional command interface in `demo/src/main/java/com/example/demo/media/service/MediaCommandService.java`; define typed command outcomes in `demo/src/main/java/com/example/demo/media/service/ImageCompletionOutcome.java` following `specs/003-image-storage/contracts/domain-services.md`, with no media-to-user service dependency.
- [X] T017 [US1] Implement creation in `demo/src/main/java/com/example/demo/media/service/impl/MediaCommandServiceImpl.java`: derive owner from trusted actor, create unpredictable staging key, persist PENDING, sign Content-Type-bound PUT with deadline creation+900s, and roll back on persistence/signing failure without returning 201.
- [X] T018 [US1] Implement safe successful completion in `demo/src/main/java/com/example/demo/media/service/impl/MediaCommandServiceImpl.java`: owner-scoped lookup, conditional read/decode and source-conditional copy to a fresh per-attempt final key, approved metadata replacement, deadline checks and @Version-protected COMPLETED/finalKey flush under REQUIRED/READ COMMITTED; return typed EXPIRED outcome normally so its state can commit. Do not expose a temporary unsafe/unvalidated completion endpoint.
- [X] T019 [US1] Implement nontransactional facade and owner-only renewal in `demo/src/main/java/com/example/demo/media/service/impl/MediaServiceImpl.java`: reject ambient transactions on completion entry, call separately proxied command, raise expiration error only after commit, verify final availability and sign 900-second GET; repeated COMPLETED skips staging work. Register caught/sanitized after-commit staging cleanup in `demo/src/main/java/com/example/demo/media/service/impl/MediaCommandServiceImpl.java`.
- [X] T020 [US1] Implement `demo/src/main/java/com/example/demo/media/controller/ImageUploadController.java` for POST /api/image-uploads, POST /api/image-uploads/{imageId}/complete and GET /api/image-uploads/{imageId}/download-url; use CurrentUserIdProvider, delegate business work, return documented statuses and no-store capability responses without printing URLs.
- [X] T021 [US1] Add only the exact authenticated method/path rules in `demo/src/main/java/com/example/demo/auth/config/AuthSecurityConfiguration.java` before denyAll; impose no MANAGE_USER_PROFILE/media permission on media routes, preserve current JWT eligibility, existing profile/address authorities and CSRF/CORS transport assumptions, and never authorize arbitrary unmatched media subpaths.
- [X] T022 [US1] Implement real signed-request/source-condition coverage in `demo/src/test/java/com/example/demo/storage/integration/ObjectStorageServiceIT.java`: good/wrong Content-Type, expired test-only capability, conditional GET/copy, replaced final metadata, browser-compatible GET and valid post-completion staging PUT unable to modify published content; assert LocalStack signature enforcement is active. Include acceptance of byte-identical overwrite and metadata-only rewrite when source conditions and all other validation checks pass; assert final metadata remains server-approved, following the authoritative FR-008/Content Identity Decision in `specs/003-image-storage/spec.md`.
- [X] T023 [US1] Run T011–T013/T022 and targeted foundation checks from `demo/` using `./mvnw verify` (PowerShell: `./mvnw.cmd verify`); record discovered class counts, outcomes and standalone upload/view/renewal acceptance results in `specs/003-image-storage/quickstart.md`, fixing failures before marking US1 complete.

**Checkpoint**: First demonstrable increment is standalone upload/view/renewal, with all safety invariants already implemented; US2 adds exhaustive recovery verification, not permission to ship unsafe interim behavior.

## Phase 4: User Story 2 - Fail safely and recover (Priority: P1)

**Goal**: Prove lifecycle and external-system failures remain accurate and retryable.
**Independent Test**: Seed uploads and inject faults using real transactions/LocalStack; observe persisted states and object contents without a profile consumer.

### Tests first

- [X] T024 [P] [US2] Add invalid-content and boundary unit tests in `demo/src/test/java/com/example/demo/media/validator/ImageContentValidatorTest.java` and `demo/src/test/java/com/example/demo/media/validator/ImageUploadValidatorTest.java`: empty/non-image/corrupt/truncated, missing WebP provider, all declared/stored/decoded mismatches, exact deadline and EXPIRED terminal behavior; use real fixtures and controllable time.
- [X] T025 [P] [US2] Add failure HTTP/security contracts in `demo/src/test/java/com/example/demo/media/controller/ImageUploadFailureControllerTest.java`: 404 privacy, missing staging/state/expiry/conflict 409, bytes 422, outages/signing 503, unexpected DB 500 and existing ApiError extensions; assert safe errors, no false 200 and no secret/capability log disclosure.
- [X] T026 [P] [US2] Add commit-boundary failure tests in `demo/src/test/java/com/example/demo/media/integration/ImageCompletionConsistencyIT.java`: persisted EXPIRED despite 409, validation crossing deadline, copy failure/ambiguity, successful copy then database rollback, stale-version 409, caught post-commit deletion failure yielding success, and post-commit signing failure then idempotent recovery. Avoid test-level transactions concealing commits and clean committed test data.
- [X] T027 [P] [US2] Add retained deterministic object-replacement tests in `demo/src/test/java/com/example/demo/media/integration/ImageObjectReplacementIT.java`: replace staging before conditional GET and pause after actual decode to replace before promotion; use real LocalStack source conditions, assert 409/no COMPLETED/no replacement publication and leave simultaneous-completion integration testing deferred. Add the complementary identical-content and metadata-only rewrite cases from `specs/003-image-storage/spec.md` to this test: neither rewrite alone causes rejection when the source condition passes; a failed source condition still yields conservative 409 without bypassing validation.

### Implementation and recovery

- [X] T028 [US2] Complete/correct all failure branches in `demo/src/main/java/com/example/demo/media/service/impl/MediaCommandServiceImpl.java`: preserve PENDING on pre-deadline validation/infrastructure failure, persist EXPIRED at admission/transition deadlines, maintain orphan accounting for successful copy plus failure and never assume DB rollback reverses copy; retain fresh attempt keys and no automatic retries.
- [X] T029 [US2] Complete/correct post-commit failure handling in `demo/src/main/java/com/example/demo/media/service/impl/MediaServiceImpl.java` and staging cleanup registration in `demo/src/main/java/com/example/demo/media/service/impl/MediaCommandServiceImpl.java`; committed state survives signing/cleanup failure, no staging cleanup occurs after rollback, and repeated completion recovers without recopying.
- [X] T030 [US2] Complete/correct source-precondition, full-response, metadata and resource-disposal paths in `demo/src/main/java/com/example/demo/storage/service/impl/ObjectStorageServiceImpl.java`; map actual missing/changed/outage conditions safely, avoid secret-bearing SDK messages/wire logs and surface service limits without silently adding application upload caps or multipart infrastructure.
- [X] T031 [US2] Write executable operator inspection/reconciliation instructions in `specs/003-image-storage/quickstart.md`: coordinate quiescence, inventory keys against database lifecycle/references, wait/recheck before deletion, retain uncertain/completed/referenced objects and retry sanitized cleanup failures manually; add no scheduler/deletion API or automatic compensation.
- [X] T032 [US2] Run T024–T027 plus US1 regressions from `demo/` using `./mvnw verify` (PowerShell: `./mvnw.cmd verify`), inspect real commit/object outcomes and record results in `specs/003-image-storage/quickstart.md`; explicitly report simultaneous-completion integration testing as deferred rather than passed.

**Checkpoint**: Failure/retry behavior is demonstrated against actual persistence and storage boundaries.

## Phase 5: User Story 3 - Manage my avatar (Priority: P2)

**Goal**: Integrate completed owned image references into the existing private/limited-public profile flow.
**Independent Test**: Seed completed images; preserve/clear/replace avatar with atomic profile edits, then read profiles and fresh URLs. No new upload flow is needed to exercise these tests.

### Tests first

- [X] T033 [P] [US3] Add MVC binding/privacy/permission contracts in `demo/src/test/java/com/example/demo/user/controller/UserImageControllerTest.java`: omitted/null/positive Long profileImageId, rejection of empty/string/boolean/fraction/array/overflow, unchanged MANAGE_USER_PROFILE, public-profile allowlist, ID/URL null pairing and no-store.
- [X] T034 [P] [US3] Add pure consumer/converter tests in `demo/src/test/java/com/example/demo/user/service/UserImageServiceTest.java` and `demo/src/test/java/com/example/demo/user/converter/UserImageConverterTest.java`: owner/state checks through MediaService, no media repository access, prepared URL mapping without hidden I/O, and no deletion on replace/clear.
- [X] T035 [P] [US3] Add persistence/API tests in `demo/src/test/java/com/example/demo/user/integration/UserImageApiIT.java`: completed/missing/foreign/pending/expired/unavailable final object cases, atomic rollback of accompanying profile edits on validation/signing failure, fresh URL renewal, and authenticated foreign profile viewing without owner-renewal/attachment rights.

### Implementation

- [X] T036 [US3] Add next available Flyway migration (tentatively `demo/src/main/resources/db/migration/V6__user_profile_images.sql`) and scalar field in `demo/src/main/java/com/example/demo/user/entity/User.java` implementing "nullable `profile_image_id BIGINT` FK to image_uploads(id) with `idx_users_profile_image_id`" from data-model.md; preserve existing rows/IDs, no cascade remove/orphanRemoval/object deletion, and verify constraints/upgrade using `demo/src/test/java/com/example/demo/user/repository/UserImageRepositoryIT.java`.
- [X] T037 [US3] Replace the unavailable-image binding boundary in `demo/src/main/java/com/example/demo/user/dto/request/UpdateUserRequest.java`: "A supplied non-null value must be an integral positive Long JSON number; reject coercion from strings/booleans/fractions/arrays or overflow"; preserve presence flag, omitted preserves latest value, null clears and valid explicit value replaces, retaining all other strict update rules.
- [X] T038 [US3] Implement consumer validation/access methods in `demo/src/main/java/com/example/demo/media/service/impl/MediaServiceImpl.java`: owned COMPLETED image with nonempty finalKey and available final object for attachment, prepared access only for a caller-authorized attached reference; no generic owner-bypassing HTTP endpoint, no media-to-user service call.
- [X] T039 [US3] Integrate scalar reference updates and prepared image access in `demo/src/main/java/com/example/demo/user/service/impl/UserServiceImpl.java` via MediaService only; retain existing per-user transaction/coordination, validate/sign before transaction return so failed selection/URL preparation rolls back all edits, and preserve absence/null behavior without deleting objects.
- [X] T040 [US3] Change profileImageId to Long and add profileImageUrl in `demo/src/main/java/com/example/demo/user/dto/response/PrivateUserResponse.java` and `demo/src/main/java/com/example/demo/user/dto/response/PublicUserResponse.java`; update `demo/src/main/java/com/example/demo/user/converter/UserConverter.java` to map supplied prepared values only and `demo/src/main/java/com/example/demo/user/controller/UserController.java` to return no-store when capabilities are present, retaining public privacy fields and existing endpoint statuses.
- [X] T041 [US3] Run T033–T035/T036 and existing profile/address/authentication regressions from `demo/` using `./mvnw verify` (PowerShell: `./mvnw.cmd verify`); document atomicity, visibility, no-deletion and reference upgrade outcomes in `specs/003-image-storage/quickstart.md`, fixing existing affected fixtures/call sites without unrelated refactoring.

**Checkpoint**: Profiles safely reference images with original omitted/null semantics and unchanged authorization.

## Phase 6: User Story 4 - Run the same workflow locally (Priority: P3)

**Goal**: Deliver reproducible browser-accessible local setup and private production configuration.
**Independent Test**: Follow local instructions from clean infrastructure and upload/view all three formats; inspect prod defaults without contacting AWS.

### Tests first

- [X] T042 [P] [US4] Add endpoint/profile/credential contracts in `demo/src/test/java/com/example/demo/storage/config/S3EnvironmentConfigurationTest.java`: internal service versus external signing hostname, path-style, prod default provider chain/no dummy fallback, explicit durations, private-bucket assumptions and unchanged backend security settings.
- [X] T043 [P] [US4] Add storage CORS/signature contracts in `demo/src/test/java/com/example/demo/storage/integration/StorageCorsIT.java`: allowed browser PUT/GET preflights with Content-Type, rejected unapproved origins/incorrect signed headers, active expiry enforcement and browser-compatible GET; use isolated LocalStack, not developer Compose/AWS.

### Implementation and validation

- [ ] T044 [US4] Add health-checked PostgreSQL/LocalStack services and optional persistent volumes in `compose.yaml`, pinned approved images, dummy-only local credentials and S3_SKIP_SIGNATURE_VALIDATION=0; add private bucket/CORS initialization in `docker/localstack/init/10-init-s3.sh` and `docker/localstack/init/bucket-cors.json`, matching the explicit frontend origin without broadening backend CORS or making storage public.
- [X] T045 [US4] Add environment profiles in `demo/src/main/resources/application-local.properties` and `demo/src/main/resources/application-prod.properties` following `specs/003-image-storage/contracts/configuration.md`: externalized DB/production credentials, distinct optional signing endpoint, 900s lifetimes, finite timeouts and no globally hardcoded active profile; document least-privilege private AWS configuration without provisioning/deploying resources.
- [ ] T046 [US4] Execute T042/T043 and the clean-start browser flow in `specs/003-image-storage/quickstart.md`, correct documented commands to match actual Compose/profile settings, demonstrate JPEG/PNG/WebP raw PUT/completion/GET and hostname/CORS behavior, and document optional persistence/reinitialization without deleting developer data.

**Checkpoint**: Documented local workflow is reproducible; production storage configuration retains private access and external credentials.

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T047 Review touched sources under `demo/src/main/java/com/example/demo/media/`, `demo/src/main/java/com/example/demo/storage/` and existing user/auth integration against `specs/003-image-storage/contracts/domain-services.md`; correct only feature-related violations of interfaces, domain direction, prepared converters, validators, safe JPA/Lombok, exception privacy, no URL persistence and no accidental new permission/size/cleanup feature.
- [X] T048 Verify provider discovery and actual JPEG/PNG/WebP decoding in the packaged Java 25 runtime using the JAR produced by `demo/pom.xml`; repeat the local upload/completion smoke flow and record runtime/dependency versions/results in `specs/003-image-storage/quickstart.md`, failing closed if any reader is absent.
- [X] T049 From the repository root change into `demo/` (`cd demo`), then run `./mvnw verify` (PowerShell: `./mvnw.cmd verify`), inspect `target/surefire-reports/` and `target/failsafe-reports/` relative to `demo/` for expected Test/IT classes and record actual counts/failures/skips in `specs/003-image-storage/quickstart.md`; execute only already configured quality checks, report missing gates honestly and never use real AWS/developer DBs or disable security to pass.
- [X] T050 Review FR-001–021 and SC-001–006 against implementation/test evidence in `specs/003-image-storage/spec.md` and `specs/003-image-storage/quickstart.md`, update stale document status/actual paths and mark only completed tasks in `specs/003-image-storage/tasks.md`; explicitly retain the deferred simultaneous-completion test and all cleanup/size/domain exclusions, with no automatic commit/push/deployment.

## Dependencies & Execution Order

### Phase dependencies

```text
Setup T001 -> T002 -> T003
  -> Foundation T004 -> T005 -> T006 -> T007 -> T008 -> T009 -> T010
    -> US1 T011-T023
      -> US2 T024-T032
      -> US3 T033-T041
      -> US4 T042-T046
        -> Cross-cutting T047-T050 (after US2, US3 and US4)
```

Foundation is intentionally conservative/sequential; do not begin customer story delivery until it passes. US2, US3 and US4 are independently verifiable after US1, but their default execution order is US2 then US3 then US4. Shared MediaServiceImpl/MediaCommandServiceImpl changes in US2/US3 must be serialized; isolated tests may be authored in parallel. US4's environment configuration can proceed independently of US2/US3 implementations. Full release validation waits for all stories.

T014 and T015 start after US1 test batch; T016 follows both. T017-T021 execute sequentially; T022 follows implementation and T023 verifies the increment. Each later story writes its test batch first, then executes implementation tasks in order, then verifies. The two avatar migrations intentionally separate shared image metadata (V5) from consumer relationship (V6); recheck actual numbering before each addition and update documentation if another migration has landed.

### Parallel execution examples per story

| Story | Batch prerequisites | Parallel work on distinct files |
| --- | --- | --- |
| US1 | Foundation complete | T011 MVC contracts, T012 service/converter tests, T013 real-flow tests |
| US1 | T011-T013 authored | T014 DTO/converter implementation and T015 validators |
| US2 | US1 complete | T024 validators, T025 MVC failures, T026 consistency IT, T027 object-replacement IT |
| US3 | US1 complete; shared implementation edits serialized after US2 | T033 MVC binding, T034 service/converter tests, T035 avatar API IT |
| US4 | US1 complete | T042 environment contracts and T043 CORS/signature IT |

`[P]` authoring can use shared T003 support without editing that support concurrently; coordinate any necessary shared fixture change first. Parallel opportunities are scheduling guidance, not authorization to spawn agents.

## Traceability and Independent Verification

| Story | Tasks | Primary requirement coverage | Independent outcome |
| --- | --- | --- | --- |
| US1 | T011-T023 (13) | FR-001-012, FR-016, FR-018-020 with foundation | Standalone create/raw PUT/validate/complete/view/renew, stable IDs and privacy |
| US2 | T024-T032 (9) | FR-006-011, FR-016-017, FR-019, FR-021 | Seeded uploads plus faults prove expiry, validation, replacement, rollback and post-commit behavior |
| US3 | T033-T041 (9) | FR-012-015, FR-019 | Completed fixtures prove atomic avatar preserve/clear/replace and authorized viewing |
| US4 | T042-T046 (5) | FR-018-020 | Clean local browser workflow plus configuration review proves private-storage/environment behavior |

Foundation T006-T010 enforces every metadata field constraint; US3 T036-T037 enforces the scalar relationship and binding rules. T047-T050 consolidate SC-001-006 evidence. Summary: 50 tasks = 3 setup + 7 foundation + 13 US1 + 9 US2 + 9 US3 + 5 US4 + 4 polish. Four parallel test batches plus one parallel implementation pair are explicitly identified (14 [P] tasks total).

## Implementation Strategy

### MVP first

Complete setup/foundation and US1 for a safe, independently demonstrable upload/view capability. Validate US1 before adding consumers; never interpret the MVP checkpoint as permission to bypass mandatory publication/ownership safeguards or the US2 recovery release checks. Full feature release additionally requires US2, US3, US4 and final evidence review. No deployment is authorized by these tasks.

### Incremental delivery

Prove standalone upload, then failure recovery, then avatar integration using completed fixtures, then environment parity. Reuse existing authentication rather than inventing roles/permissions. Preserve strict profile presence semantics. All completion attempts use distinct final keys and @Version even though simultaneous-completion integration testing is postponed. Retain the deterministic object-replacement test. No background expiration, deletion, resizing, compression, thumbnail, application-defined size cap, review/product feature or list ordering is added.

## Validation and Hooks

All executable entries use unchecked boxes, sequential IDs, exact paths and correct phase labels. Tests are included because the feature explicitly requests them. No application tests/builds were run during task generation. `.specify/extensions.yml` is absent; no before_tasks/after_tasks hooks apply. This command changes only this task artifact; dependency approval remains a future T001 requirement, not a request to approve an unprepared change now.



## Confirmed content identity clarification (2026-10-27)

Authoritative content-identity requirements and acceptance outcomes are defined in [spec.md FR-008 and Content Identity Decision](spec.md#content-identity-decision-2026-10-27). This document references that definition rather than restating it.







## Implementation disposition ? 2026-10-27

48/50 tasks complete. T044 and T046 remain unchecked for native LocalStack persistence; all other listed work and final review are complete. The initial lifecycle/decoder and avatar-binding tests were run red before their implementation, then green; not every MVC test was authored before its controller.

Maven default verify passed 140 Surefire tests and 117 Failsafe tests. Three subsequently added boundary cases were verified in a focused rerun (5 configuration tests and 8 consistency IT cases). Latest per-class XML totals: 43 Surefire classes / 141 tests and 34 Failsafe classes / 119 tests, zero failures/errors/skips. No coverage-percentage claim is inferred from those counts.

T044 partial: health-checked Compose, private bucket bootstrap, CORS, pinned digests, profiles and optional volume wiring are present. T046 partial: isolated Compose + executable-JAR raw PUT/completion/view passed JPEG/PNG/WebP, with all temporary containers/volumes removed. However, native S3 persistence failed two isolated restart checks with LOCALSTACK_PERSISTENCE=1, including ON_REQUEST/ON_STARTUP plus a wait beyond the normal flush interval. The selected community image returns 404 for the snapshot endpoint; a named volume alone did not preserve the object. This requirement is not waived or marked complete.

A concrete alternative was inspected read-only: localstack/localstack-pro:4.14.0@sha256:5f1fb6c73ccf053e3748d57d5c43b9cd3dba109bb2ae5b7096d2cf5155082a6c. It has not been pulled/run or substituted. The official persistence example requires an externally configured LOCALSTACK_AUTH_TOKEN; a suitable account entitlement and user approval of the different image are needed before using that alternative. No token is requested in chat or committed files. Alternatively, the user may explicitly defer local persistence; that changes the feature requirement and must not be assumed.

Execution stops at the failed non-parallel local validation checkpoint under speckit-implement. No checklist markers were modified, no production AWS resources were accessed, and no commit/push/deployment occurred. Existing README.md/.gitignore edits were preserved.


### Latest user override ? 2026-10-27

Allow-all API CORS (origins/methods/headers), retaining Bearer JWT authorization and allowCredentials=false, supersedes earlier explicit-origin restrictions. Bucket initialization is deferred; Compose must not reference ignored docker/ files or require an initialized bucket for health. docker/ and the fixture media README are excluded from Git. Future bucket CORS allows all origins/headers and all S3-supported methods while the bucket remains private. See research.md for the decision and fixture attribution. Existing persistence work remains deferred/open.
