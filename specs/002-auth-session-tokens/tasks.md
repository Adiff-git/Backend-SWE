# Tasks: Authentication and Session Tokens

**Input**: `specs/002-auth-session-tokens/` specification and design artifacts.
**Prerequisites**: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md), [data-model.md](data-model.md), [contracts.md](contracts.md), [HTTP](contracts/http.md), [domain services](contracts/domain-services.md), [transactions](contracts/transactions.md), [quickstart.md](quickstart.md).
**Tests**: Explicitly requested. Write story tests before implementation, observe meaningful failures once shared types compile, then implement and pass. Unit/slices Test, integration IT; never use a passing sequential unit suite as concurrency proof.
**Organization**: Setup -> shared foundation -> US1-US5 (P1) -> US6 (P2) -> cross-cutting completion. All tasks currently unimplemented.

## Format and paths

Tasks use unchecked boxes, sequential IDs, [P] only for disjoint ready work, and story labels only within story phases. All paths are repository-relative to the actual demo module. New migration filenames are assigned after history inspection; directory paths do not permit overwriting V1/V2. Referenced multi-file tasks are cohesive changes, executed serially within that task.

## Gates

Custom checklist state remains reviewer-owned. New Resource Server/Jose dependency approval is required before build edits; prior unrelated build approval is not reused. Deployment secret/issuer/origins must be externally supplied without defaults. Legacy @ usernames/unsupported credential encodings, if discovered, require separately approved remediation; no shared DB query or automatic rename/delete. Current-email trust is explicitly provisional; real Google/verification and Media remain excluded. Permission amendment requires self-profile GET/PUT and all five address operations to enforce their exact permission plus ownership; public-profile viewing remains authentication-only.

## Phase 1: Setup

Resolve implementation gates before build changes; do not change reviewer-owned checklist markers or assume prior build approval covers new resource-server support.

- [X] T001 Record reviewer disposition and any unresolved findings from `specs/002-auth-session-tokens/checklists/auth-security.md` in `specs/002-auth-session-tokens/plan.md`; custom checklist review is not implementation completion and unchecked markers are not silently approved.
- [X] T002 Inspect effective managed dependencies in `demo/pom.xml` and record developer approval for missing Resource Server/Jose support in `specs/002-auth-session-tokens/plan.md`; use Boot 4.1.2-compatible coordinates, no new JWT library, Redis or provider SDK.
- [X] T003 After approval, add only missing managed Resource Server/Jose dependencies in `demo/pom.xml`; retain Java25/Boot4.1.2, existing validation/Lombok, Surefire Test and Failsafe IT discovery.
- [X] T004 Reinspect `demo/src/main/resources/db/migration/` and record migration ordering/legacy-username and credential-format compatibility gates in `specs/002-auth-session-tokens/plan.md`; no shared DB access or automatic legacy rename/delete/hash fallback.
- [X] T005 Extend disposable auth test configuration in `demo/src/test/resources/application-test.properties` using existing PostgreSQL/Flyway support, ephemeral test secret/issuer/origins and deterministic clock overrides; never disable production security or target external databases.

## Phase 2: Foundational infrastructure

Shared authority/session/credential primitives are needed by all stories. Implement them once with acyclic auth -> User services. No endpoint may be exposed with permissive placeholder authentication.

- [X] T006 Create User-owned `demo/src/main/java/com/example/demo/user/entity/Role.java` and `demo/src/main/java/com/example/demo/user/entity/Permission.java` plus join mappings: roles "id BIGINT identity PK; name VARCHAR(50) NOT NULL", "uk_roles_name; nonblank; seed USER only"; permissions "id BIGINT identity PK; name VARCHAR(100) NOT NULL", "uk_permissions_name; nonblank; seed MANAGE_USER_PROFILE and MANAGE_ADDRESS"; user_roles fields "user_id BIGINT; role_id BIGINT", "composite PK, FK users/roles, no duplicate assignment"; role_permissions fields "role_id BIGINT; permission_id BIGINT", "composite PK, FK roles/permissions, no duplicate grant". LAZY/use-case fetches; safe Lombok, no Data/unsafe equality/string rendering.
- [X] T007 Add new authority/no-@ migrations in `demo/src/main/resources/db/migration/` with the exact foundation constraints above, USER and two approved permission seeds, least-privilege USER backfill preserving password/status/profile data, reverse-join indexes only when needed and no V1/V2 rewrite. No-@ CHECK must fail safely on legacy violations pending separately approved remediation, never silently rename/delete.
- [X] T008 Extend `demo/src/main/java/com/example/demo/user/repository/UserRepository.java`, add `demo/src/main/java/com/example/demo/user/repository/RoleRepository.java` and `demo/src/main/java/com/example/demo/user/repository/PermissionRepository.java` for database-LOWER identifier lookup and explicit current-authority fetch plans; no duplicate unique indexes, global EAGER fetching or cross-domain repository access.
- [X] T009 Extend `demo/src/main/java/com/example/demo/user/service/UserService.java` and `demo/src/main/java/com/example/demo/user/service/impl/UserServiceImpl.java` with User-owned candidate lookup/security snapshot, ACTIVE/USER creation with encoded password/joinedAt, required entity retrieval and internal encoded-password replacement participating in the calling transaction. Add `demo/src/main/java/com/example/demo/user/dto/response/UserSecuritySnapshot.java` with current immutable ID/password/status/authorities, never publicly serialized/logged; reuse owner lock and prohibit reverse auth calls.
- [X] T010 Apply shared no-@ username validation to `demo/src/main/java/com/example/demo/user/validator/UserValidator.java` and `demo/src/main/java/com/example/demo/user/dto/request/UpdateUserRequest.java`; keep trim/nonblank/max50 Unicode code points, preserved casing/internal whitespace/non-ASCII and case-insensitive uniqueness, no three-character minimum or lowercase storage; add regression cases in `demo/src/test/java/com/example/demo/user/validator/UserValidatorTest.java`.
- [X] T011 Create `demo/src/main/java/com/example/demo/auth/entity/AuthenticationSession.java`: id UUID "PK; random session identity generated locally; feature-specific, not User ID policy"; user_id BIGINT "NOT NULL FK users.id; owner immutable; no cascade into User"; created_at Instant/TIMESTAMPTZ "NOT NULL; creation instant truncated consistently to token second precision"; expires_at "NOT NULL; fixed created_at+7 days; immutable"; revoked_at "Nullable; once set cannot be cleared through ordinary workflows"; revocation_reason string/VARCHAR(32) "Nullable iff revoked_at null; LOGOUT, REFRESH_REUSE, PASSWORD_CHANGED". LAZY relationships; no unsafe Lombok.
- [X] T012 Create `demo/src/main/java/com/example/demo/auth/entity/RefreshCredential.java`: id Long/BIGINT "PK; internal record ID, never raw secret"; session_id UUID "NOT NULL FK authentication_sessions.id; immutable owner"; token_hash BYTEA "NOT NULL, SHA-256 digest; CHECK OCTET_LENGTH=32; uk_refresh_credentials_token_hash"; issued_at Instant/TIMESTAMPTZ "NOT NULL"; consumed_at "Nullable; set once when rotated". No plaintext, independent expiry or unnecessary successor pointer; LAZY session association.
- [X] T013 Add session/refresh migrations in `demo/src/main/resources/db/migration/` with exact fields/nullability above, fixed 604800-second session-expiry CHECK, revocation-field consistency, idx_authentication_sessions_user_id_id / expires_at, unique token hash, partial unique idx_refresh_credentials_session_id_current WHERE consumed_at IS NULL and idx_refresh_credentials_session_id_id. Revoked-session unconsumed rows are retained but unusable, not active current credentials.
- [X] T014 Implement `demo/src/main/java/com/example/demo/auth/repository/AuthenticationSessionRepository.java` and `demo/src/main/java/com/example/demo/auth/repository/RefreshCredentialRepository.java` for digest candidate lookup, owner-scoped reads, ordered locking/revocation and expiry cleanup; simple derived queries preferred, justified complex queries only; no raw-token lookup/storage.
- [X] T015 Create `demo/src/main/java/com/example/demo/auth/service/AuthSessionService.java` and `demo/src/main/java/com/example/demo/auth/service/impl/AuthSessionServiceImpl.java` owning only auth repositories, session creation/get...Entity retrieval/consumption/revocation helpers and current-state checks; caller acquires User lock first, then sessions by UUID then credentials by ID, rechecking time/state and avoiding repository-level business orchestration.
- [X] T016 [P] Add `demo/src/test/java/com/example/demo/auth/testdata/AuthTestData.java` and `demo/src/test/java/com/example/demo/auth/integration/AuthPostgresTestSupport.java` extending existing disposable support with real BCrypt users, sessions and hashed-secret fixtures; visible scenario inputs, no fixed generated IDs, deterministic cleanup and no production test identity/provider.
- [X] T017 [P] Add `demo/src/test/java/com/example/demo/auth/repository/AuthSessionRepositoryIT.java` and `demo/src/test/java/com/example/demo/auth/repository/RefreshCredentialRepositoryIT.java` for schema/expiry/hash/partial-index constraints, LAZY/fetch plans and ordered lock behavior; @DataJpaTest + PostgreSQL/Flyway, no mocked SQL semantics.
- [X] T018 Add authority/identity compatibility coverage in `demo/src/test/java/com/example/demo/user/repository/UserRepositoryIT.java` and `demo/src/test/java/com/example/demo/user/integration/UserServiceIT.java`: no-@ migration/validation, non-ASCII and case preservation, current DB grants/backfill, encoded-only creation and no converter-triggered queries; preserve original profile/address regressions.
- [X] T019 Implement `demo/src/main/java/com/example/demo/auth/config/JwtProperties.java` and `demo/src/main/java/com/example/demo/auth/config/AuthSecurityConfiguration.java` (CORS validation), mapping `security.jwt.secret=${JWT_SECRET}` without fallback in `demo/src/main/resources/application.properties`; raw UTF-8 secret >=32 bytes, required issuer, optional actual audience contract, explicit origins and fail-closed invalid config; do not log/trim/Base64-reinterpret secret.
- [X] T020 Add shared clock and single BCryptPasswordEncoder(10) configuration plus framework HS256-only JwtEncoder/JwtDecoder in `demo/src/main/java/com/example/demo/auth/config/AuthSecurityConfiguration.java`; no duplicate encoder/provider or custom JWT filter, no network key discovery/default secret. Guard incomplete protected capabilities deny-safe until real current-state integration is complete.
- [X] T021 Define `demo/src/main/java/com/example/demo/auth/service/AuthService.java` and `demo/src/main/java/com/example/demo/auth/service/AuthTransactionService.java`, nontransactional `demo/src/main/java/com/example/demo/auth/service/impl/AuthServiceImpl.java` facade and proxied `demo/src/main/java/com/example/demo/auth/service/impl/AuthTransactionServiceImpl.java` orchestration boundary; typed internal issuance/rejected outcomes contain transient secrets only, no toString/logging. Mutations REQUIRED/READ_COMMITTED, no outer controller/facade transaction, retries, REQUIRES_NEW or blanket noRollbackFor; post-commit facade mapping owns security-rejection exceptions.
- [X] T022 Add minimal auth-domain exceptions under `demo/src/main/java/com/example/demo/auth/exception/` derived from existing ApplicationException and reuse `demo/src/main/java/com/example/demo/common/exception/ApiErrorFactory.java` / GlobalExceptionHandler for safe 400/401/403/409/5xx mappings; never new error hierarchy or accidental raw credential/SQL details; infrastructure errors must still roll back.

## Phase 3: US1 - Register and sign in (P1)

Goal: atomic registration and username login with independent sessions. Independent criterion: register one user, sign in again, inspect two committed sessions and exact pair delivery without email or commerce flows. Tests first, meaningful red after shared types compile, then production implementation.

- [X] T023 [P] [US1] Write `demo/src/test/java/com/example/demo/auth/controller/RegistrationLoginControllerTest.java` slices for POST register/login, 201/200, header-only access, no-store, refresh JSON, no auth cookies, required/protected fields and generic credential/status errors.
- [X] T024 [P] [US1] Write `demo/src/test/java/com/example/demo/auth/validator/PasswordPolicyValidatorTest.java` and `demo/src/test/java/com/example/demo/auth/validator/AuthValidatorTest.java` for eight-code-point/72-UTF8-byte limits, whitespace preservation/no truncation, username strip/max50/no@/non-ASCII/case preservation and wrong/null/blank request values.
- [X] T025 [P] [US1] Write `demo/src/test/java/com/example/demo/auth/converter/AuthConverterTest.java` and `demo/src/test/java/com/example/demo/auth/service/TokenServiceTest.java` for exact TokenResponse allowlist, separate internal access header, 32-random-byte/43-character refresh generation and hash-only storage inputs, capped claim/metadata agreement and no secret rendering.
- [X] T026 [US1] Create `demo/src/main/java/com/example/demo/auth/dto/request/RegisterRequest.java`, `demo/src/main/java/com/example/demo/auth/dto/request/LoginRequest.java` and `demo/src/main/java/com/example/demo/auth/dto/response/TokenResponse.java`; Lombok typed classes without secret-bearing toString/Data, boundary validation, username max50 code points/no@, unchanged passwords >=8 code points <=72 UTF-8 bytes, identifier trim/current-domain comparison, no client role/status/audit fields; TokenResponse exactly tokenType/accessTokenExpiresIn/refreshToken/refreshTokenExpiresAt, never accessToken.
- [X] T027 [US1] Implement `demo/src/main/java/com/example/demo/auth/validator/PasswordPolicyValidator.java`, `demo/src/main/java/com/example/demo/auth/validator/StrictStringDeserializer.java` and request constraints, `demo/src/main/java/com/example/demo/auth/service/TokenService.java`, `demo/src/main/java/com/example/demo/auth/service/impl/TokenServiceImpl.java` and `demo/src/main/java/com/example/demo/auth/converter/AuthConverter.java`: real BCrypt10, SecureRandom32/Base64URL43/SHA256, sub stable Long text/sid UUID/iss/iat/exp, <=900s and session cap, whole-second UTC deadlines, no expired issuance or token-toString/logging.
- [X] T028 [US1] Implement `demo/src/main/java/com/example/demo/auth/security/CustomUserDetails.java` and `demo/src/main/java/com/example/demo/auth/security/DomainUserDetailsService.java` using only UserService security snapshots; configure AuthenticationManager/DaoAuthenticationProvider in `demo/src/main/java/com/example/demo/auth/config/AuthSecurityConfiguration.java`, no credential/authority caches. Credential failure/status distinctions are generic externally; username and email lookup routing is rechecked under owner lock, with email-specific acceptance in US6.
- [X] T029 [US1] Implement register/username-login commands in `demo/src/main/java/com/example/demo/auth/service/impl/AuthTransactionServiceImpl.java` and facade dispatch in `demo/src/main/java/com/example/demo/auth/service/impl/AuthServiceImpl.java`: User-owned ACTIVE/USER provisioning, encoded password, owner coordination, under-lock AuthenticationManager recheck/principal-ID match, fixed seven-day independent sessions, one refresh and locally signed access. Account/role/session/signing failure rolls back; publish pair only after proxy commit.
- [X] T030 [US1] Implement register/login endpoints and exact response mapping in `demo/src/main/java/com/example/demo/auth/controller/AuthController.java`; @Valid DTOs, Authorization Bearer response header plus no-store and TokenResponse, no duplicate access JSON or auth cookie; controller owns HTTP only.
- [X] T031 [US1] Implement exact POST register/login/refresh/logout credential-chain matching in `demo/src/main/java/com/example/demo/auth/config/AuthSecurityConfiguration.java` without bearer processing, form/Basic/session login; protected fallback remains deny-safe until US2. CORS explicit origins/allow-expose Authorization/preflight, no unnecessary credentialed mode, CSRF policy restricted to this explicit header/body-credential chain.
- [X] T032 [US1] Add `demo/src/test/java/com/example/demo/auth/service/RegistrationLoginServiceTest.java`, `demo/src/test/java/com/example/demo/auth/integration/RegistrationLoginServiceIT.java` and `demo/src/test/java/com/example/demo/auth/integration/RegistrationLoginApiIT.java` covering normalization/duplicate races, real BCrypt boundaries, automatic authenticated registration, independent sessions, no partial initialization on signing/assignment failure, case-insensitive/current-principal recheck and stale attached bearer on exact credential POST routes.

## Phase 4: US2 - Access protected capabilities (P1)

Goal: real framework bearer authentication enforcing current user/session/authorities. Independent criterion: provision real signed fixtures and exercise protected operations without refresh/logout/password workflows. Integrates US1 issuance for complete flow; no production release until all P1 revocation stories pass.

- [X] T033 [P] [US2] Write `demo/src/test/java/com/example/demo/auth/security/JwtClaimsValidatorTest.java` for signature/HS256-only/issuer/required claim types/sub-sid consistency/future iat/zero-skew expiration equality/session cap and optional-audience contract.
- [X] T034 [P] [US2] Write `demo/src/test/java/com/example/demo/auth/security/CurrentStateJwtConverterTest.java` for missing/non-ACTIVE users, unknown/revoked/expired/foreign sessions, current DB authorities and no stale scope/role-claim grants; domain failures translate to framework invalid_token 401, not leaked 404/500.
- [X] T035 [US2] Implement `demo/src/main/java/com/example/demo/auth/validator/JwtClaimsValidator.java` and `demo/src/main/java/com/example/demo/auth/security/CurrentStateJwtConverter.java` on framework JwtAuthenticationProvider, calling auth/User services for current state, no custom JWT filter, no caching, no locked reads for every bearer request and no promises to cancel already-authenticated work.
- [X] T036 [US2] Implement real `demo/src/main/java/com/example/demo/auth/security/SpringSecurityCurrentUserIdProvider.java` and `demo/src/main/java/com/example/demo/auth/security/ApiAuthenticationEntryPoint.java` / ApiAccessDeniedHandler.java using existing identity interface/error factory; stable user ID, generic 401/403, appropriate WWW-Authenticate, no mutable username/hardcoded identity or controller-advice filter duplication.
- [X] T037 [US2] Complete protected Resource Server chain in `demo/src/main/java/com/example/demo/auth/config/AuthSecurityConfiguration.java`: exact credential POST chain first, PUT password protected, access only from Authorization header (not cookie/query/body), current DB authority conversion and protected/deny-safe unknown-route fallback; disabled form/Basic/servlet-session authentication.
- [X] T038 [US2] Activate `demo/src/main/java/com/example/demo/user/controller/UserController.java` and `demo/src/main/java/com/example/demo/user/controller/AddressController.java` through real CurrentUserIdProvider: GET/PUT /api/me requires exact MANAGE_USER_PROFILE, all five address operations require MANAGE_ADDRESS, both plus owner scope; public GET /api/users/{userId} authentication-only, no hasRole substitution or ROLE_ permission prefix. Preserve original DTO/presence/default/capacity/404 contracts and leave non-null Media operations unavailable.
- [X] T039 [US2] Add `demo/src/test/java/com/example/demo/auth/integration/BearerAuthenticationApiIT.java` and extend `demo/src/test/java/com/example/demo/user/integration/UserApiIT.java` / AddressApiIT.java for real tokens, role/permission/status changes between requests, username-rename continuity, correct ApiError/challenges, owner with/without permission and authorized foreign caller 404, denied requests causing no mutation, no cookies and method/path near-misses.

## Phase 5: US3 - Renew one session safely (P1)

Goal: strict atomic refresh rotation/reuse, unchanged absolute expiry and durable revocation before 401. Independent criterion: seed two sessions and rotate/reuse one; the other remains usable. Protected rejection proof uses US2.

- [X] T040 [P] [US3] Write `demo/src/test/java/com/example/demo/auth/controller/RefreshControllerTest.java` for POST refresh JSON contract, no access JSON, no-store/header pair, missing/null/empty/wrong-type/oversized 400, unknown/expired/reused 401 and unrelated stale bearer ignored.
- [X] T041 [P] [US3] Write `demo/src/test/java/com/example/demo/auth/service/RefreshServiceTest.java` for the facade rejection/ambient-transaction boundary; verify current/consumed/unknown/expired/revoked cases, candidate-only lookup, user-first ordering, replacement/history and signing-failure rollback through the real integration tests in T045 rather than treating mocks as concurrency proof.
- [X] T042 [US3] Create `demo/src/main/java/com/example/demo/auth/dto/request/RefreshRequest.java` with required exact non-normalized refreshToken, maximum1024 characters and no userId/sessionId; generated secrets remain 43 Base64URL characters.
- [X] T043 [US3] Implement refresh mutation in `demo/src/main/java/com/example/demo/auth/service/impl/AuthTransactionServiceImpl.java` using `demo/src/main/java/com/example/demo/auth/service/impl/AuthSessionServiceImpl.java`: hash lookup only routes ownership, then User -> session -> credential coordination/reload/current time; validate eligibility/expiry; consume+flush before replacement; sign capped access for same sid/deadline. Reuse updates revokedAt/reason and returns rejected normally; unknown input never selects a victim.
- [X] T044 [US3] Extend `demo/src/main/java/com/example/demo/auth/service/impl/AuthServiceImpl.java` and `demo/src/main/java/com/example/demo/auth/controller/AuthController.java` so reuse rejection becomes safe 401 only after transactional proxy commit; successful headers/body sent only after commit, no ambient facade/controller transaction, no broad noRollbackFor or auto-retry/grace window.
- [X] T045 [US3] Add `demo/src/test/java/com/example/demo/auth/integration/RegistrationLoginServiceIT.java` (rollback coverage) and `demo/src/test/java/com/example/demo/auth/integration/RefreshRotationApiIT.java` with independent PostgreSQL transactions/barriers: two same-token requests, reuse persisting after actual HTTP401/new-transaction query, prior replacement access invalid, unrelated session preserved, random input no mutation, original seven-day deadline, lost-response retry semantics and rollback after consumption flush/signing failure.

## Phase 6: US4 - Sign out one device (P1)

Goal: revoke one session using body refresh credential without needing unexpired access. Independent criterion: seed two sessions and expire access for one, logout through refresh, demonstrate only that family rejected.

- [X] T046 [P] [US4] Write `demo/src/test/java/com/example/demo/auth/controller/LogoutControllerTest.java` and `demo/src/test/java/com/example/demo/auth/service/LogoutServiceTest.java` for current/consumed recognized credentials, revoked-unexpired idempotent204, expired/unknown401, malformed400 and no required valid bearer or user/session ID trust.
- [X] T047 [US4] Create `demo/src/main/java/com/example/demo/auth/dto/request/LogoutRequest.java` with required refreshToken, no normalization, maximum1024 characters and no client-selected ownership.
- [X] T048 [US4] Implement logout coordination/state transitions in `demo/src/main/java/com/example/demo/auth/service/impl/AuthTransactionServiceImpl.java`, `demo/src/main/java/com/example/demo/auth/service/impl/AuthServiceImpl.java` and `demo/src/main/java/com/example/demo/auth/controller/AuthController.java`; recognized unexpired current/consumed credential revokes only its family, repeated revoked/unexpired success, absolute expiry wins independently of cleanup, 204/no body, stale bearer ignored.
- [X] T049 [US4] Add `demo/src/test/java/com/example/demo/auth/integration/LogoutApiIT.java` and `demo/src/test/java/com/example/demo/auth/integration/LogoutRefreshRaceIT.java` for access-expired logout, unaffected other sessions, repeated/expired behavior before-after cleanup and refresh/logout races through real commits; no post-logout surviving credentials or reliance on clearing SecurityContext.

## Phase 7: US5 - Change password and end all sessions (P1)

Goal: atomically replace password and revoke all device sessions. Independent criterion: seed multiple sessions, change with valid bearer/current password, reject all old credentials, authenticate replacement password; no new token pair.

- [X] T050 [P] [US5] Write `demo/src/test/java/com/example/demo/auth/controller/PasswordControllerTest.java` and `demo/src/test/java/com/example/demo/auth/service/PasswordChangeServiceTest.java` for valid bearer/principal-derived user, wrong current400, unchanged/invalid new400, exact password rules, 204/no pair and failure preserving old password/session state.
- [X] T051 [US5] Create `demo/src/main/java/com/example/demo/auth/dto/request/ChangePasswordRequest.java` with required currentPassword/newPassword, minimum8-code-point/maximum72-UTF8-byte replacement, no trimming/normalization/truncation or userId/sid binding; current-password matching cannot use truncation.
- [X] T052 [US5] Implement password change in `demo/src/main/java/com/example/demo/auth/service/impl/AuthTransactionServiceImpl.java`, `demo/src/main/java/com/example/demo/auth/service/impl/AuthServiceImpl.java` and `demo/src/main/java/com/example/demo/auth/controller/AuthController.java`: lock User, revalidate authenticating sid/status/current password after lock, reject unchanged new password, User-service encoded replacement + auth all-session revocation in one REQUIRED transaction; preserve other profile fields, no replacement pair or reverse User->auth call.
- [X] T053 [US5] Add `demo/src/test/java/com/example/demo/auth/integration/PasswordChangeApiIT.java` and `demo/src/test/java/com/example/demo/auth/integration/PasswordRevocationRaceIT.java` for all-devices revocation, current included, old/new login behavior, rollback after password write/revocation failure and real login/password plus refresh/password interleavings; verify subsequent attempts after commit, not cancellation of in-flight authenticated work.

## Phase 8: US6 - Sign in with current email (P2)

Goal: current-email login under explicit provisional trust, without fake proof or a Google workflow. Independent criterion: set current valid email, login, replace email, reject old address and accept new current address with exact password; username remains available without email.

- [X] T054 [P] [US6] Write `demo/src/test/java/com/example/demo/auth/controller/EmailLoginControllerTest.java` and `demo/src/test/java/com/example/demo/auth/service/EmailLoginServiceTest.java` for @ dispatch, current provisional email, absent/previous/wrong email generic failure, no cross-type fallback and username login without email.
- [X] T055 [US6] Complete email route in `demo/src/main/java/com/example/demo/auth/security/DomainUserDetailsService.java` and under-lock recheck in `demo/src/main/java/com/example/demo/auth/service/impl/AuthTransactionServiceImpl.java` using User-owned current email_key lookup; do not add emailVerified=true, provider SDK or actual verification claim. Current-email replacement immediately removes old login; no account-level proof transfers.
- [X] T056 [US6] Add `demo/src/test/java/com/example/demo/auth/integration/EmailLoginApiIT.java` for current-email temporary trust, no-email username login, replacement/previous-address rejection and profile email/username mutation versus login races under existing owner coordination; no unapproved Google/S3 workflow or stale candidate authenticating a different unlocked User.

## Phase 9: Polish and cross-cutting verification

Complete only after all six stories; no production-ready claim for partial rotation/revocation or simulated security.

- [X] T057 Implement bounded daily expiry cleanup through `demo/src/main/java/com/example/demo/auth/service/AuthSessionService.java` / impl/AuthSessionServiceImpl.java and `demo/src/main/java/com/example/demo/auth/config/AuthCleanupScheduler.java`: expired users/session batches in user-first lock order, credentials before sessions, purge within30 days of absolute expiry, no active-session consumed history removal and no validation dependence on cleanup; expose safe failure signal/monitoring without secrets.
- [X] T058 [P] Add `demo/src/test/java/com/example/demo/auth/integration/AuthCleanupIT.java` for expiry-before/after cleanup equivalence, preserved active-session consumed evidence, bounded batches/ordering and safe cleanup rollback; no arbitrary device/session cap or token history deletion for live sessions.
- [X] T059 [P] Add `demo/src/test/java/com/example/demo/auth/security/AuthConfigurationTest.java` and `demo/src/test/java/com/example/demo/auth/integration/AuthCorsApiIT.java` for missing/short secret fail-closed, issuer/optional-audience contract, explicit origins/header allow-expose/preflight, no unnecessary credentialed CORS and chain-specific CSRF policy; no real secrets committed or logged.
- [X] T060 Add `demo/src/test/java/com/example/demo/auth/integration/AuthenticationFlowIT.java` across real registration/login/bearer/renew/reuse/logout/password/current-email workflows using PostgreSQL/Flyway and actual filter chains; assert no-store/header/body/no-cookie contracts and zero secret/hash/key leakage in errors/log capture.
- [X] T061 Run `specs/002-auth-session-tokens/quickstart.md` test/verify commands and inspect actual `demo/target/surefire-reports/` and `demo/target/failsafe-reports/` nonzero Test/IT counts, including original User/Address regressions; fix failures without weakening security/constraints or accessing shared databases.
- [X] T062 Update `specs/002-auth-session-tokens/quickstart.md` and `specs/002-auth-session-tokens/plan.md` with actual paths, approval/migration compatibility outcomes and validation evidence; review domain cycles, token secrecy, transaction outcomes, current-state enforcement and absent static-analysis gates. Do not change reviewer markers, auto-commit/push/deploy or claim real email verification/Media delivery.

## Dependencies and execution order

```text
Setup approvals and compatibility gates -> shared foundation
  -> US1 registration/username issuance
  -> US2 real bearer current-state enforcement (fixture-seeded independently; integrates US1)
  -> US3 rotation/reuse -> US4 device logout -> US5 password/all-session revocation
  -> US6 current-email flow (data contract foundation; complete HTTP acceptance uses US1+US2)
  -> cleanup/configuration/CORS/full-flow/regression evidence
```

US2 can validate seeded real sessions without the US1 registration endpoint; US3-US5 can be exercised independently using real fixtures but depend on US2 to prove subsequent bearer rejection. No production release before all P1 security stories are complete. US6 remains current-email provisional trust, not a blocked real-verification workflow.

Within setup, dependency approval precedes pom edits. Within foundation, authority/entity contracts precede migrations/repositories/User-service additions; session entity/migration contracts precede auth repository/service use; common errors precede facade mapping. [P] fixture/repository-test tasks are authored only when their referenced contracts/shared types are available. No foundational task is waived because endpoints are not yet present.

Within stories, tests precede implementation; DTOs/entities and helpers precede services; services precede endpoints; real integration acceptance follows. AuthController, AuthTransactionServiceImpl, AuthServiceImpl, UserServiceImpl and AuthSecurityConfiguration are shared files: edits across stories must be sequential. Database migrations are assigned/written sequentially; never parallelize modifications to the same file.

## Parallel examples per story

- US1: RegistrationLoginControllerTest, PasswordPolicyValidatorTest/AuthValidatorTest and AuthConverterTest/TokenServiceTest are separate ready test groups after foundation contracts.
- US2: JwtClaimsValidatorTest and CurrentStateJwtConverterTest can be authored independently; shared production security configuration changes remain sequential.
- US3: RefreshControllerTest and RefreshServiceTest are disjoint; transactional mutation/facade/controller edits proceed in order.
- US4: independent logout slice/service test authoring may use separate files; production edits wait for shared US3 files and preserve strict reuse behavior.
- US5: password slice/service tests can be split by file after contracts stabilize; password and refresh transaction-service edits cannot overlap.
- US6: email slice/service tests use separate files; actual identifier/provider and transaction edits wait for earlier shared credential changes.

[P] marks indicate only file-disjoint work within a dependency-ready group, not authorization to skip prerequisites or spawn agents.

## Implementation strategy

First internal increment is foundation plus US1, demonstrating atomic account/session issuance. Add US2 before exposing protected User/Address operations. Complete rotation/reuse, logout and global password revocation before treating the authentication feature as production-ready; an issuance-only MVP cannot be deployed as the full promised security contract. Add current-email flow under its confirmed temporary trust policy and finish cleanup/configuration/CORS/full-flow evidence. Every story has fixture-based independent acceptance, but the complete deliverable must preserve all existing profile/address ownership/default rules and current DB authority enforcement.

No implementation, runtime tests, deployment or checklist review occurred during task generation. Record real evidence and task completion during implementation; never mark missing auth behavior complete based on fixtures alone.

## Requirement coverage

Coverage indicates planned work, not completed behavior.

| Requirement | Task IDs |
| --- | --- |
| FR-001 | T001, T062 |
| FR-002 | T007-T010, T024, T026, T032 |
| FR-003 | T006-T009, T027-T032 |
| FR-004 | T023, T028-T032, T054-T056 |
| FR-005 | T009, T054-T056 |
| FR-006 | T020, T024, T026-T027, T032, T050-T053 |
| FR-007 | T023, T025-T026, T030, T040, T045, T060 |
| FR-008 | T019-T020, T025, T027, T033-T035, T039 |
| FR-009 | T009, T017, T034-T039 |
| FR-010 | T011-T014, T016-T017, T029, T045 |
| FR-011 | T012-T017, T025, T027, T043, T045 |
| FR-012 | T014-T017, T040-T045 |
| FR-013 | T021-T022, T041, T043-T045, T049 |
| FR-014 | T046-T049 |
| FR-015 | T009, T050-T053 |
| FR-016 | T009, T017, T021, T029, T043, T049, T053, T056 |
| FR-017 | T006-T009, T018, T028, T034-T039 |
| FR-018 | T031, T036-T040, T046, T050, T059 |
| FR-019 | T022-T023, T036, T039-T040, T046, T050, T060 |
| FR-020 | T002-T005, T011-T017, T019-T020, T057-T059 |
| FR-021 | T009-T010, T018, T036-T039, T062 |
| FR-022 | T016-T018, T023-T025, T032-T034, T039-T041, T045-T046, T049-T050, T053-T054, T056, T058-T062 |
| SC-001 | T023, T029-T032 |
| SC-002 | T025, T033, T035, T045 |
| SC-003 | T039, T045, T049, T053, T060 |
| SC-004 | T041, T043-T045 |
| SC-005 | T050, T052-T053 |
| SC-006 | T054-T056 |
| SC-007 | T023, T025, T030, T040, T046, T050, T059-T060 |
| SC-008 | T032, T039, T045, T049, T053, T056, T060-T061 |

## Implementation record

Checklist gate passed 38/38 and 16/16; markers unchanged. The developer approved managed Resource Server/Jose dependencies before build edits. V1/V2 are preserved; V3/V4 were verified only with disposable PostgreSQL. No legacy data renaming or credential fallback. Validation uses existing User/DTO constraints plus PasswordPolicyValidator and StrictStringDeserializer; CORS validation remains in the single security configuration. Rollback cases for refresh, password revocation and cleanup share RegistrationLoginServiceIT; clock boundaries use AuthSessionExpiryApiIT; upgrade compatibility uses LegacyMigrationIT. These consolidate fixtures without dropping planned scenarios.


## Final verification evidence (2026-10-26)

Completed T001-T062. The final `mvn verify` lifecycle finished BUILD SUCCESS: Surefire 73 tests across 29 suites and Failsafe 71 tests across 24 suites; zero failures, errors or skipped tests. Counts were read from actual TEST-*.xml reports and the Failsafe summary. The 144 tests include existing User/Address regressions, real MVC/filter-chain acceptance, PostgreSQL/Flyway constraints/upgrades, transaction rollback, independent-transaction races, exact expiry, CORS, permission/ownership and captured-log secrecy checks. The cached Maven 3.9.16 distribution was used because the Windows wrapper has the previously observed empty-Target-array issue. No Checkstyle/PMD/SpotBugs gate is configured.

All requirements retain task coverage above. Validation placement and consolidated integration fixtures are recorded in plan.md. The HTTP integration uncovered and fixed Jackson wrapping a safe MediaUnavailableException as a generic 400; GlobalExceptionHandler now preserves the domain 503 without exposing binding/SQL details. No reviewer checklist markers changed. No extension hooks exist. No commit, push, deployment or shared-database access occurred.

Remaining rollout prerequisites: external JWT_SECRET/JWT_ISSUER/CORS_ALLOWED_ORIGINS and approved remediation if real existing accounts violate the username/password compatibility gates. Actual Google/email verification and Media remain excluded future features; current email is provisionally trusted and non-null media references remain unavailable.
