# Domain Service and Security Integration Contracts

## Ownership and dependency direction

Auth owns orchestration/session/refresh persistence and depends on UserService. User owns User, credentials/identity/authority persistence and never calls auth. Shared errors/identity interfaces contain no auth repository dependencies. Controllers delegate; converters only map preloaded values. Avoid introducing direct UserRepository access in any auth service or security adapter.

## UserService additions (user-owned, internal, non-HTTP)

- Resolve a candidate account by username_key or current email_key using PostgreSQL LOWER on the stripped input, preserving database collation semantics and nullable-email uniqueness. Return a user ID/security snapshot, never infer authorization from the candidate lookup.
- Expose getUserSecuritySnapshot(id): immutable ID, display username, encoded password, current status and deduplicated current role/permission names fetched explicitly. It is a sensitive internal DTO, never serialized/logged as a response. No provider/UserDetails cache.
- Reuse getUserEntityForUpdate(id) as the coordination primitive, including existing bounded transaction-local lock timeout. Lock precedes authoritative checks; refresh managed state if necessary as existing code does.
- Create an account through a user-owned method accepting validated username and an already encoded password plus approved internal defaults (ACTIVE, USER). Persist joinedAt, User and assignments atomically within the calling auth transaction. No client-supplied roles/status accepted by the HTTP boundary.
- Persist encoded password replacement through a user-owned internal method under an already-held owner lock. No public profile password binding. Auth orchestrates its own all-session revocation in the same outer transaction.
- Apply no-@ validation to existing profile username changes; preserve casing/max-50 code points/current uniqueness behavior. Email replacement remains User-owned; temporary email trust reads the new current value directly and does not imply a verification flag.
- User-owned role/permission services/repositories seed and fetch USER with MANAGE_USER_PROFILE/MANAGE_ADDRESS. Existing users receive this least-privilege assignment via additive migration. No admin grants or role-management API.

User creation/credential mutation are trusted internal service operations, not a way for profile clients to change protected fields. Entity getters use get...Entity naming where returning entities; get... returns DTO snapshots. Existing UserRepository/validators must be extended rather than duplicated.

## Auth services

- AuthService / AuthServiceImpl: nontransactional facade exposes register/login/refresh/logout/changePassword; performs boundary orchestration and translates committed transaction outcomes into safe application errors. It never publishes tokens before the transactional call returns. Do not annotate this facade/controller with an outer mutation transaction.
- AuthTransactionService / AuthTransactionServiceImpl: proxied REQUIRED/READ_COMMITTED mutation boundary; returns typed internal success or rejection outcome, with raw token material only in transient issuance success. Infrastructure/signing/invalid-password failures roll back. Reuse writes revocation and returns normal rejection so commit completes before facade raises 401.
- AuthSessionService / AuthSessionServiceImpl: owns auth repository access, candidate hash lookup, required session/credential retrieval, ordered locks, creation/consumption/revocation/expiry queries and bounded cleanup. No UserRepository access; caller supplies trusted owner facts obtained through User service.
- TokenService / TokenServiceImpl: local HS256 signing and opaque refresh generation/hash; immutable Clock/config inputs, no database ownership logic or network calls. Validate secret/issuer at startup. Map issuance metadata from exact signed deadlines.
- AuthValidator/PasswordPolicyValidator: business/format constraints separated appropriately; eight-code-point/72-UTF8-byte checks on unchanged password input, status/identity/session consistency, and no normalization/truncation. BCryptPasswordEncoder(10) is the single PasswordEncoder bean.
- AuthConverter maps internal successful issuance to TokenResponse and controller header metadata without copying access into JSON. Never validate, fetch or include sensitive transient result in logs/toString.

## Spring Security adapters

- DomainUserDetailsService uses UserService security snapshots and identifier dispatch; CustomUserDetails includes immutable ID/current status/authorities and encoded password strictly internally. Missing/unverified-proof is not a check in this temporary email policy; missing/incorrect current email still fails generically.
- AuthenticationManager with configured DaoAuthenticationProvider handles credentials; hide disabled/locked/not-found distinctions in external error mapping. Login invokes it under the owner lock and checks returned principal ID equals the candidate being coordinated. No additional production provider/encoder instances or stale caches.
- Resource Server JwtAuthenticationProvider decodes via HS256 JwtDecoder; required claim/time validators run before current-state conversion. CurrentStateJwtConverter calls auth/User services for current session and account authorities; reject unknown/revoked/expired/mismatched session and non-ACTIVE user. Translate missing/ineligible/revoked-state failures into the framework OAuth2 authentication failure so the bearer entry point handles 401 rather than leaking domain not-found as 404/500. Do not trust scope/role claims as current authority grants or accidentally add SCOPE_ prefixes to approved permission names.
- SpringSecurityCurrentUserIdProvider implements the existing common contract using real validated principal ID, not mutable username, hardcoded IDs or client fields. User/Address business services continue accepting trusted actor IDs and know nothing about JWT parsing.
- ApiAuthenticationEntryPoint/ApiAccessDeniedHandler share existing ApiErrorFactory; protected bearer challenges are retained. Extend shared error handling only for actual mappings, not a second exception hierarchy.

## Existing User/Address HTTP boundary

Activate the previously deferred controllers only with real security and identity adapter. Self-profile/address management maps approved permissions plus existing owner scoping, field-presence, no-@ username rule and default/capacity constraints. Limited public profile viewing requires authentication, not an extra role/status rule beyond base authentication eligibility. Address missing/foreign IDs remain 404. Do not activate Media-dependent non-null-image operations; absent/null image behavior remains as feature 001.

## Configuration and dependencies

New managed Resource Server/Jose support requires approval before pom edits. Existing BCrypt/Security primitives are reused. External JWT_SECRET maps explicitly through security.jwt.secret=${JWT_SECRET} with no default, raw UTF-8 >=32 bytes; required external issuer; optional audience only with an approved deployment value; explicit CORS origins. No actual secret values committed. Properties fail closed; no wildcard credentialed CORS or default signing key. No Google/mail/Redis dependency, login cookies or custom JWT filter.


## Confirmed permission and ownership amendment (2026-10-26)

Self-profile GET/PUT /api/me requires `hasAuthority('MANAGE_USER_PROFILE')`; all five address operations require `hasAuthority('MANAGE_ADDRESS')`. Both permission and service-level ownership are mandatory; authority never overrides owner scoping. Missing/invalid authentication is 401, missing required authority is 403, and an authorized caller's missing/foreign address remains indistinguishable 404. Limited-public-profile GET /api/users/{userId} stays authentication-only. Permission names are exact, without ROLE_ prefix or hasRole substitution. Use current database authorities; test an owner without authority, an owner with authority, and an authorized foreign caller, including permission revocation between requests. No denied operation may mutate data or disclose ownership. This user-confirmed policy overrides the earlier feature 001 authentication-plus-ownership-only rule and changes no implementation by itself.
