# Implementation Plan: Authentication and Session Tokens

**Branch**: `feature/user-address` | **Date**: 2026-10-26 | **Spec**: [spec.md](spec.md)

**Input**: `specs/002-auth-session-tokens/spec.md`, binding [contracts.md](contracts.md).

Setup reported feature key 002-auth-session-tokens as BRANCH; actual Git branch remains feature/user-address. No branch/code/build/database change is performed by planning.

## Summary

Add registration, username/current-email login, HS256 access tokens, seven-day absolute security sessions, hashed opaque refresh rotation/reuse revocation, credential logout and atomic password-change/global-session revocation. Reuse User, its parent-row lock, identity boundary and shared errors. Use framework resource-server bearer processing, two intentionally ordered exact-route chains, and a separate transaction service returning outcomes so reuse revocation commits before its 401 response. Current email is provisionally trusted by explicit decision; actual verification/Google and Media remain out of scope.

## Technical Context

**Language/Version**: Java 25, Spring Boot 4.1.2, Maven wrapper 3.9.16; use managed Security/Jackson versions.

**Primary Dependencies**: Existing MVC/Security/JPA/validation/Flyway/Lombok/PostgreSQL/test starters. The developer approved managed Resource Server/Jose dependencies on 2026-10-26 before build edits. Resolve compatible Boot 4.1.2 module/starter coordinates from its effective BOM; required modules are spring-security-oauth2-resource-server and spring-security-oauth2-jose. No second JWT library, Redis, mail or Google SDK.

**Storage**: PostgreSQL; preserve V1 users/V2 addresses. New migrations add user-owned roles/permissions/no-@ invariant and auth-owned session/credential history. UTC Instant/TIMESTAMPTZ. No raw refresh persistence.

**Testing**: Existing JUnit5/Mockito/AssertJ/MockMvc, PostgreSQL Testcontainers/Flyway, Surefire Test and Failsafe IT discovery. Extend User/Address regression coverage and use real signed credentials in API/flow acceptance.

**Target Platform**: JVM HTTP service, Windows commands, Docker disposable verification. Deployment issuer/origins/secret are external inputs, not hosting choices.

**Project Type**: Single-module domain-oriented backend monolith under demo/.

**Performance Goals**: No approved latency/throughput/volume SLA or session cap. Current-state checks cannot use stale credential/authority/session caches. History grows with legitimate rotation and must preserve active-session reuse evidence.

**Constraints**: BCrypt strength 10; eight-code-point minimum and 72 UTF-8-byte maximum without normalization. HS256 only; raw UTF-8 JWT_SECRET -> security.jwt.secret, minimum 32 bytes and fail-closed configuration. Access <=900s and session cap; session creation+604800s. REQUIRED/READ COMMITTED, no automatic retries or unusual propagation/isolation. Existing user lock timeout applies. Generated refresh: 32 random bytes -> 43 Base64URL characters without padding; request refresh bound 1024 characters, unknown well-shaped credentials fail generically.

**Scale/Scope**: Five auth endpoints plus existing protected User/Address flows. Exactly one current refresh per active session, independent devices, no arbitrary device cap. Daily bounded cleanup removes expired security records within thirty days; monitoring/retry handles operational failure, while expiry enforcement never depends on job timing.

## Constitution Check

Pre-research and post-design gates pass without exceptions; implementation approvals and compatibility gates remain explicit.

| Principles | Pre-research | Post-design evidence |
| --- | --- | --- |
| I-III domains/naming/DTOs | PASS | auth -> User service; User owns identity/authorities and never calls auth. Interfaces/ServiceImpl; mapping-only converters, business validators, typed DTOs. |
| IV-V HTTP/errors | PASS | Confirmed action routes and statuses; shared ApiError/ProblemDetail; filter errors remain in security handlers. Public body never contains access token. |
| VI authentication/authorization | PASS | Explicit refresh/revocation requirement authorizes scope; real framework bearer authentication, current DB state, ownership, no cookie/form/Basic bypass. |
| VII-VIII persistence/JPA | PASS with rollout gate | Additive migrations, LAZY relations, constraints; no media cascade or V1/V2 rewrite. Legacy @ names/invalid credential encodings require approved remediation if found. |
| IX-X transactions/locks | PASS | User -> session -> credential lock order; REQUIRED/READ COMMITTED; rejected reuse outcome commits before facade raises 401; no REQUIRES_NEW/SERIALIZABLE. |
| XI-XII storage/time | PASS | No S3/Google work; UTC timestamps and immutable absolute expiry. |
| XIII tests | PASS design | Real commit/rollback/race acceptance and Test/IT discovery; no runtime tests executed here. |
| II/XIV dependency/quality | PASS | Managed Resource Server/Jose was explicitly approved before build edits; no unrelated tooling/passing static-analysis claim. |
| XV-XVII scope/environment | PASS | Implementation remains within approved auth/User integration scope; no secret values, shared DB, commit/push/deployment. |

Legacy-data presence is a rollout check, not an unresolved design choice: fail migration safely on incompatible usernames, obtain approved remediation, never rename/delete automatically. Deployment values are required operational inputs. No shared database is queried during planning.

## Project Structure

### Documentation (this feature)

```text
specs/002-auth-session-tokens/
  spec.md
  contracts.md
  plan.md
  research.md
  data-model.md
  quickstart.md
  contracts/http.md
  contracts/domain-services.md
  contracts/transactions.md
  checklists/requirements.md
```

tasks.md belongs to the next command and is not generated here.

### Source Code (repository root)

Planned auth types do not yet exist.

```text
demo/src/main/java/com/example/demo/
  auth/
    config/        AuthSecurityConfiguration, JwtProperties, CorsProperties
    controller/    AuthController
    dto/request/   RegisterRequest, LoginRequest, RefreshRequest, LogoutRequest, ChangePasswordRequest
    dto/response/  TokenResponse
    service/       AuthService, AuthTransactionService, TokenService, AuthSessionService
    service/impl/  corresponding ServiceImpl classes
    entity/        AuthenticationSession, RefreshCredential
    repository/    AuthenticationSessionRepository, RefreshCredentialRepository
    validator/     AuthValidator, PasswordPolicyValidator, JwtClaimsValidator
    converter/     AuthConverter
    security/      CustomUserDetails, DomainUserDetailsService, CurrentStateJwtConverter,
                   SpringSecurityCurrentUserIdProvider, ApiAuthenticationEntryPoint, ApiAccessDeniedHandler
    exception/     small auth-domain exception categories
  user/
    entity/        User (existing), Role, Permission
    repository/    UserRepository (existing), RoleRepository, PermissionRepository
    service/       existing UserService extended with user-owned auth operations
    dto/response/  internal UserSecuritySnapshot (not a public response)
    validator/     existing UserValidator plus shared no-@ enforcement
  common/identity/CurrentUserIdProvider.java   # existing contract
  common/exception/                          # existing shared errors
demo/src/main/resources/db/migration/         # new versions only
demo/src/test/java/com/example/demo/auth/
  service/ validator/ converter/ controller/ repository/ security/ integration/ testdata/
demo/src/test/java/com/example/demo/user/     # compatibility/regression additions
```

**Structure Decision**: auth -> user only. User owns Role/Permission/UserRole/RolePermission because these describe current account authorities. Auth owns session/refresh orchestration and repositories. Password change calls User-owned mutation inside an auth transaction, then revokes auth sessions; never reverse-call auth from User. Controllers/converters have no repository access. No duplicate User or encoders/security configuration.

## Phase 0 - Research

[research.md](research.md) resolves framework/provider reuse, strict HS256/time validation, user-first locks, commit-before-error outcomes, opaque hashes, roles, provisional email and legacy migration handling. No technical-choice placeholder remains. Missing deployment values and discovered legacy-data remediation remain rollout gates; the dependency approval gate is resolved.

## Phase 1 - Design and Contracts

See [data-model.md](data-model.md), [HTTP contract](contracts/http.md), [domain interfaces](contracts/domain-services.md), [transaction protocol](contracts/transactions.md) and [quickstart](quickstart.md).

Implementation sequence: approve missing managed resource-server support; add user/authority/no-@ support; add sessions/refresh/hash/clock infrastructure; configure framework credential/JWT providers and current-state checks; implement workflows; activate protected User/Address boundaries; prove header/CORS/race/rollback behavior. No Media or real verification-provider implementation.

Critical decisions:

- Nontransactional AuthService facade calls separate proxied AuthTransactionService. Mutation returns typed success/rejected outcomes. Reuse writes revocation and returns rejected normally; facade raises generic auth failure only after commit. Signing/infrastructure/password/registration failures that must restore state throw inside the transaction. No broad noRollbackFor.
- Candidate identifier/hash lookup only discovers the owner. Lock that User first, then reread authoritative identifier/password/status/session/credential state. AuthenticationManager executes while owner lock is held, bypasses caches and must authenticate the same locked principal ID. Existing profile changes participate in the same user lock.
- Password change rechecks current sid after acquiring the lock. All-session revocation either follows earlier session creation and revokes it, or precedes later login/refresh so it revalidates replacement password/revoked state. Protected read-only authentication does not lock every request or promise cancellation of authenticated work.
- Registration encodes and locally signs before transaction commit, but never publishes response credentials until commit. Refresh signing failure restores prior current secret; a lost committed response still has strict reuse semantics.
- Store newly encoded BCrypt output directly with the single approved encoder. Existing synthetic password hashes are test fixtures, not a legacy production password contract; real auth tests generate actual BCrypt values. No NoOp/delegating fallback is introduced.
- Current User.email is provisionally trusted directly. Do not store emailVerified=true pretending proof exists or carry a flag across replacement. Future verification must deliberately replace this policy.

## Complexity Tracking

No constitution exception requested. Two intentional SecurityFilterChain beans in one config separate exact credential routes from protected APIs; they are not accidental duplicate configurations. The separate transactional service is justified by durable reuse revocation before 401, avoiding self-invocation and unnecessary unusual propagation.


## Confirmed permission and ownership amendment (2026-10-26)

Self-profile GET/PUT /api/me requires `hasAuthority('MANAGE_USER_PROFILE')`; all five address operations require `hasAuthority('MANAGE_ADDRESS')`. Both permission and service-level ownership are mandatory; authority never overrides owner scoping. Missing/invalid authentication is 401, missing required authority is 403, and an authorized caller's missing/foreign address remains indistinguishable 404. Limited-public-profile GET /api/users/{userId} stays authentication-only. Permission names are exact, without ROLE_ prefix or hasRole substitution. Use current database authorities; test an owner without authority, an owner with authority, and an authorized foreign caller, including permission revocation between requests. No denied operation may mutate data or disclose ownership. This user-confirmed policy overrides the earlier feature 001 authentication-plus-ownership-only rule and changes no implementation by itself.


## Implementation record (2026-10-26)

The reviewer checklist gate passed (auth-security 38/38, requirements 16/16); no reviewer markers were changed. The developer explicitly approved Resource Server/Jose dependencies. Added only the Boot-managed `spring-boot-starter-oauth2-resource-server`, which supplies Spring Security Resource Server/Jose and Nimbus; retained Java 25, Boot 4.1.2 and existing test plugins.

Implemented all six workflows, real bearer integration for User/Address, current database authorities/status/session validation, BCrypt(10), strict HS256 claims and expiration, hash-only opaque refresh rotation, durable reuse revocation, credential logout, atomic password/global revocation, explicit CORS and daily bounded cleanup. Auth commands explicitly use REQUIRED/READ_COMMITTED. Owner locking precedes ordered session/credential locks. Candidate entities are refreshed after waiting for locks to avoid stale persistence-context consumption state. Password change validates the authenticating session after the owner lock, then acquires all session locks in UUID order. Cleanup limits each owner transaction to 100 sessions and continues batches until drained.

Implementation placement refinements preserve the contract: CORS validation lives in AuthSecurityConfiguration rather than a separate CorsProperties holder; request constraints, StrictStringDeserializer, the existing UserValidator and PasswordPolicyValidator provide validation without an additional orchestration-free AuthValidator class. Optional entity lookups are named findSessionEntity/findCredentialEntity. Session/credential relationships are LAZY read-only associations alongside immutable scalar foreign keys; current authentication does not initialize associations. Fault-injection rollback tests are consolidated in RegistrationLoginServiceIT; concurrency and acceptance live in dedicated API/race IT classes. Controlled test-only clocks cover exact access/session expiration.

V1/V2 were preserved. V3 adds authorities/default grants/backfill and the no-@ constraint; V4 adds sessions, hash history, fixed seven-day expiry, revocation consistency and partial uniqueness. Disposable PostgreSQL tests cover both upgrades preserving legacy fields and refusal of invalid legacy usernames without rename/delete. No shared database was accessed. Legacy password compatibility must be assessed before deployment; synthetic pre-existing test fixtures are never accepted through a fallback encoder.

Production requires externally supplied JWT_SECRET (at least 32 raw UTF-8 bytes), JWT_ISSUER and CORS_ALLOWED_ORIGINS. Actual email verification/Google and Media remain future work; current-email trust is provisional and non-null image references remain unavailable. No commit, push or deployment is performed by this implementation. No Checkstyle/PMD/SpotBugs gate is configured, so no static-analysis claim is made.


Final validation (2026-10-26): `mvn verify` BUILD SUCCESS; Surefire 73 tests / 29 suites, Failsafe 71 tests / 24 suites, zero failures/errors/skipped (144 total). Inspected actual XML reports and Failsafe summary; original User/Address regressions remain green. `git diff --check` passed. No configured static-analysis gate, commit/push or deployment is claimed. Task completion: T001-T062.
