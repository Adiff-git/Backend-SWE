# Research: Authentication and Session Tokens

Date: 2026-10-26. Based on confirmed spec/contracts, actual demo source, constitution and primary framework/database references. No runtime implementation or database work.

## Framework authentication and exact-route isolation

**Decision**: DaoAuthenticationProvider/AuthenticationManager plus UserDetailsService for username/current-email credentials; framework JwtAuthenticationProvider with JwtEncoder/NimbusJwtDecoder and a current-state JWT converter for bearer requests. First SecurityFilterChain matches only POST register/login/refresh/logout and omits bearer processing; second protects API requests including PUT password and provides a safe fallback. Shared configuration retains CORS/error protections without form/Basic/session authentication.

**Rationale**: permitAll does not prevent an invalid attached bearer token from being processed. First-match chain selection separates credential operations without duplicating JWT filters. Current-state conversion prevents stale authority-claim authorization.

**Alternatives considered**: Custom JWT filter duplicates framework behavior; universal bearer chain blocks public body-credential routes on stale tokens; broad /api/auth/** exclusion accidentally exempts password/unknown methods.

Sources: [Spring servlet architecture](https://docs.spring.io/spring-security/reference/servlet/architecture.html), [chain matching](https://docs.spring.io/spring-security/reference/servlet/configuration/java.html), [Resource Server JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html).

## HS256 and strict time/identity validation

**Decision**: Required raw UTF-8 JWT_SECRET -> security.jwt.secret, at least 32 bytes, no fallback/Base64 reinterpretation/secret normalization. Explicit HS256 encoder/decoder allowlist, required external issuer and optional audience only when there is a configured audience contract. Require canonical sub/sid/iss/iat/exp, session-owner consistency, exp <= session deadline. Use injected Clock and zero positive expiration skew; reject exp<=now, sessionExpiry<=now and future iat. Whole-second NumericDate values and metadata derive from the same issuance/deadline instants; do not mint at expiry.

**Rationale**: Framework defaults must not silently use another algorithm or clock grace extending the approved deadline. A valid signature alone cannot authorize a revoked session.

**Alternatives considered**: Hardcoded/default key violates confirmed policy; external issuer discovery is unnecessary for local HMAC issuance; username subject violates rename continuity.

Sources: [Nimbus secret-key algorithm builder](https://docs.spring.io/spring-security/reference/api/java/org/springframework/security/oauth2/jwt/NimbusJwtDecoder.SecretKeyJwtDecoderBuilder.html), [JWT validation](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html).

## Commit-before-error reuse revocation

**Decision**: Nontransactional facade invokes a distinct REQUIRED/READ COMMITTED transactional service. Reuse sets session revocation and returns a normal rejected outcome. Only after proxy commit does facade raise the safe 401 exception. Enforce the nontransactional production entry path and verify from a fresh transaction; no unexpected outer request/test transaction may defer this commit. Infrastructure/signing failures still throw and roll back; registration/password errors never use an outcome that accidentally commits partial changes.

**Rationale**: Default unchecked-exception rollback can undo revocation if the reuse exception is thrown inside the transaction. A normal return distinguishes committed security rejection from rollback failures without unusual propagation.

**Alternatives considered**: Throwing after a write rolls it back; REQUIRES_NEW requires separate approval; broad noRollbackFor risks partial registration/password commits; controller-owned transactions violate service responsibility.

Sources: [Spring rollback](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/rolling-back.html), [transaction defaults](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html).

## Coordination across password, login, refresh and profile edits

**Decision**: Reuse UserService.getUserEntityForUpdate and existing bounded lock timeout. Resolve candidate IDs without treating lookup as authorization, acquire owning User lock first, then session/credential locks in stable ID order, reload all mutable state and time. AuthenticationManager rechecks exact identifier/password/status under lock with no user-details/provider cache; principal ID must equal locked candidate. Password change rechecks current sid, mutates through User service and revokes all auth sessions in the same transaction. Profile username/email mutations share user coordination. Cleanup follows the same order in bounded batches.

**Rationale**: Locking only existing sessions misses concurrent login/new-session creation. Password comparison before locking can publish stale sessions. Parent locking coordinates cooperating writers; it does not physically lock all children. Ordinary bearer authentication reads fresh state without global pessimistic locking and permits already-authenticated work as specified.

**Alternatives considered**: SERIALIZABLE/global locks are unnecessary; session-only optimistic writes do not solve empty-set/all-session revocation; reverse User->Auth service dependency creates cycles.

Source: [PostgreSQL explicit locks](https://www.postgresql.org/docs/17/explicit-locking.html). Local evidence: UserServiceImpl.getUserEntityForUpdate/UserRepository.findForUpdate.

## Opaque refresh history and invariants

**Decision**: Generate 32 SecureRandom bytes, Base64URL without padding (43 characters); persist SHA-256 digest only with session owner, issuedAt and consumedAt. Unique digest, partial unique session index WHERE consumed_at IS NULL. Flush consumption before inserting successor. Session.revokedAt is authoritative for family revocation; retain consumed digests until absolute expiry. Unknown hashes return no session mutation. Daily bounded cleanup deletes expired credential rows then sessions within thirty days after expiry, not active evidence.

**Rationale**: A high-entropy opaque secret supports deterministic non-reversible digest lookup without password-cost hashing. Preserved digests identify actual reuse; session revocation invalidates the entire family without rewriting history. Cleanup does not define expiry.

**Alternatives considered**: Plaintext forbidden; JWT refresh adds unnecessary signing/claims coupling; deleting consumed digests loses reuse evidence; grace windows contradict strict reuse.

Sources: [SecureRandom](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/SecureRandom.html), [MessageDigest](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/MessageDigest.html), [partial indexes](https://www.postgresql.org/docs/17/indexes-partial.html).

## Passwords, identifiers and provisional email

**Decision**: BCryptPasswordEncoder(10); exact original passwords, eight-code-point minimum, 72-byte UTF-8 maximum checked before encoding/matching. Preserve username casing/internal whitespace and database LOWER comparisons; new no-@ validation at registration/profile plus additive CHECK, not ASCII/three-character restrictions. Email dispatch uses @ and current User.email directly under approved temporary trust; no fake verification evidence or emailVerified=true backfill. Actual auth fixtures use real BCrypt hashes.

**Rationale**: These are confirmed choices. Existing test-only synthetic hashes do not authorize a production compatibility encoder. Existing @ users, if discovered, block migration pending approved remediation; no renaming/deletion is automatic.

**Alternatives considered**: NoOp/delegating fallback unapproved; lowercase stored usernames conflict with display casing; Google verification is future work and assumed trust is not proof.

Source: [Spring password storage](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html).

## Authority ownership and gates

**Decision**: User domain owns Role/Permission and UserRole/RolePermission persistence. Seed USER with MANAGE_USER_PROFILE/MANAGE_ADDRESS and backfill existing users with only this default role, preserving password/status. New registration explicitly initializes ACTIVE/USER. Protected self-profile/address actions require appropriate permission plus owner scope; public-profile viewing authentication-only. No role-management endpoint, other roles or admin privileges. New managed resource-server support requires developer approval before pom edits.

**Rationale**: Existing model is only conceptual. User ownership avoids reverse dependencies and stale hardcoded grants. Future capabilities extend the model separately.

**Alternatives considered**: JWT authority claims alone stale; hardcoding every user's authorities violates current DB enforcement; a new unrelated identity service/module is unnecessary.

All design questions are resolved. Deployment secret/issuer/origins, dependency approval and discovered legacy data are explicit implementation/rollout inputs, not invented defaults. No runtime tests or shared database access occurred during planning.


## Confirmed permission and ownership amendment (2026-10-26)

Self-profile GET/PUT /api/me requires `hasAuthority('MANAGE_USER_PROFILE')`; all five address operations require `hasAuthority('MANAGE_ADDRESS')`. Both permission and service-level ownership are mandatory; authority never overrides owner scoping. Missing/invalid authentication is 401, missing required authority is 403, and an authorized caller's missing/foreign address remains indistinguishable 404. Limited-public-profile GET /api/users/{userId} stays authentication-only. Permission names are exact, without ROLE_ prefix or hasRole substitution. Use current database authorities; test an owner without authority, an owner with authority, and an authorized foreign caller, including permission revocation between requests. No denied operation may mutate data or disclose ownership. This user-confirmed policy overrides the earlier feature 001 authentication-plus-ownership-only rule and changes no implementation by itself.
