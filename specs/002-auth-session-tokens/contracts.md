# Authentication Interface and Engineering Contract

Binding technical requirements from the supplied request, separated from the business specification. No implementation is delivered.

## Routes and DTOs

| Method | Endpoint | Required request body | Success |
| --- | --- | --- | --- |
| POST | /api/auth/register | username, password | 201, token header/body |
| POST | /api/auth/login | identifier, password | 200, token header/body |
| POST | /api/auth/refresh | refreshToken | 200, token header/body |
| POST | /api/auth/logout | refreshToken | 204, no body |
| PUT | /api/auth/password | currentPassword, newPassword | 204, no body |

Separate typed request DTOs; missing/null required values are 400. Blank identifier/refresh credentials are invalid. Passwords retain exact whitespace/characters: minimum eight Unicode code points for registration/replacement; use BCryptPasswordEncoder strength 10 and enforce a maximum of 72 UTF-8 bytes before encoding/matching, not just a character-count limit. No silent truncation, normalization or new composition rule. Current password is matched exactly. Reject client attempts to choose userId/sessionId, role/status/permission/verification/audit values. Password-change owner derives only from authenticated identity.

Registration reuses the original User rule: trim surroundings, nonblank, maximum 50 Unicode code points, preserve username display casing/internal whitespace and compare case-insensitively using database-authoritative lowercase semantics. No ASCII-only restriction or three-character minimum. Username must not contain @, at registration and profile updates. Login trims the identifier: @ selects the current-email path; no @ selects username. No identifierType field or fallback to another account type is introduced. No existing account renaming or deletion is authorized.

Registration collects no email. Any valid current email is provisionally treated as verified without proof; this explicitly overrides the earlier requirement to wait for real verification. Replacement immediately disables previous-email login and provisionally trusts only the replacement; clients cannot set verification state and old proof must never transfer. Real proof workflow and possible Google integration are future work, not implemented in this feature. No provider/token-expiry workflow is invented and no Google/social login is added.

## Token issuing header and body

Registration/login/refresh return:

- `Authorization: Bearer <access-token>` response header exclusively for access delivery.
- `Cache-Control: no-store`.
- JSON TokenResponse fields: tokenType=`Bearer`, accessTokenExpiresIn (seconds), refreshToken (opaque value), refreshTokenExpiresAt (absolute original session deadline, UTC ISO-8601 Z).

No accessToken JSON field, authentication cookies or JSESSIONID dependency. Clients explicitly resend Authorization on protected requests; browsers do not resend it merely because it was received. Access credentials are never resolved from cookies/query/body. Refresh values are only in designated successful JSON and refresh/logout request bodies, never URLs/query/access claims/logs/unrelated responses. Frontend storage is out of scope.

## JWT and security integration

Required documented JWT claims: sub (canonical text form of existing immutable Long User ID), sid (stable session ID), iss, iat and exp. Any optional separate userId must agree with sub. Validate signature, configured allowed algorithm, issuer, expiration and identities/session-owner consistency; audience only with an approved audience contract. Signing material, issuer and deployment values come from external configuration. No fallback secret. Allow only HS256 (HMAC-SHA256). Bind environment JWT_SECRET to security.jwt.secret and require at least 32 UTF-8 bytes; use the original UTF-8 bytes, not implicit Base64 decoding. No hardcoded/dev fallback, secret logging or algorithm downgrade. Missing/short signing configuration fails startup/issuance closed.

Access expiration is min(issuedAt plus fifteen minutes, session absolute expiry). No positive clock tolerance may allow actual use beyond the session deadline. Current user/account state, current database roles/permissions and current session validity are checked on every protected authentication. Valid signature alone is insufficient. Missing user/session, revocation and absolute expiry reject authentication. No passwords, refresh values or unnecessary personal data in access claims. Username changes retain valid sessions through immutable identity.

Use Spring Security AuthenticationManager/AuthenticationProvider, UserDetailsService/CustomUserDetails, PasswordEncoder, JwtEncoder/JwtDecoder and OAuth2 Resource Server bearer authentication. Reuse actual components if present; currently none is configured in application source. Do not implement a custom JWT filter duplicating framework capabilities. Auth owns its domain controllers, service interfaces/ServiceImpl, entities/repositories, DTOs, converters/validators and domain exceptions. User persistence remains behind User service; no auth-domain direct UserRepository access, duplicate User/configurations/encoders/exception hierarchies or cyclic service dependencies.

Adapt CurrentUserIdProvider to real validated identity; business services remain independent of JWT parsing/SecurityContext. Preserve profile ownership and privacy while requiring hasAuthority('MANAGE_USER_PROFILE') for self-profile operations and hasAuthority('MANAGE_ADDRESS') for all owned-address operations. Permission and ownership are both mandatory under the confirmed amendment; limited-public-profile viewing remains authentication-only. Current eligibility defaults: ACTIVE allowed, INACTIVE disabled, SUSPENDED/BANNED locked; authentication failures remain generic 401. Role/permission persistence is absent and must implement the approved USER / MANAGE_USER_PROFILE / MANAGE_ADDRESS bootstrap; deduplicate authorities and ROLE_ prefixes.

## Security-chain, CORS and CSRF behavior

Exact POST register/login/refresh/logout matching must omit unrelated bearer processing, not merely permitAll after parsing an attached stale token. Their own request credentials govern authentication. PUT password and protected feature endpoints validate bearer credentials. Logout needs only the refresh credential and remains possible after access expiry. Do not add form login, HTTP Basic or servlet-session authentication.

Configure explicit allowed frontend origins, allow Authorization request header, expose Authorization response header and handle preflight. Do not enable credentialed CORS solely for bearer headers. Actual origins are deployment inputs, not hardcoded wildcard defaults. For this explicit bearer-header/body-credential chain, CSRF-token checks are not required because authentication credentials are not browser-automatically attached cookies. Do not generalize that choice to separate cookie/session chains or weaken unrelated protections.

## Session, rotation and revocation guarantees

Session and family have one immutable absolute deadline seven days after registration/login. Refresh secrets are cryptographically unpredictable opaque values stored only as non-reversible hashes with explicit ownership/history. One current refresh credential per active session; no refresh secret accepted as access bearer. Consumed-secret reuse strictly revokes the owning session and current replacement/access, without affecting unrelated sessions. Retain enough consumed history for the still-live session. Unknown random secrets cannot revoke anything. Strict concurrent refresh or retry after a lost successful response may revoke the session; no grace period or blind retries.

Reuse revocation must commit despite returning 401. Planning must select an actual transaction mechanism that preserves this guarantee rather than accidentally rolling back security changes with the error. Password replacement/all-session revocation is atomic. Login/refresh races must not publish surviving credentials based on pre-change state after password change commits. Logout/reuse races likewise leave no usable session after revocation commits. Already-authenticated executing work is not cancelled. Clients clear local credentials on logout/password change as appropriate.

Provisional logout behavior: recognized current/consumed credentials revoke their unexpired session; recognized revoked/unexpired repeats return 204; unknown/absolutely expired credentials return generic 401, independent of cleanup timing. No caller-provided user/session ID proves ownership.

## Errors

Reuse ApplicationException, ApiError extending ProblemDetail, ApiErrorFactory and GlobalExceptionHandler. Standard type/title/status/detail/instance plus top-level timestamp/message/path/fieldErrors; UTC Z timestamp, HTTP/body status agreement, safe message matching detail, path matching instance and field-to-message-list or empty object. Never expose credentials in failures, signing material, internal hashes, SQL/stack/exception details. Intended successful delivery fields are the only token exposure exception.

| Status | Condition |
| --- | --- |
| 400 | Malformed input, invalid password, incorrect current password, unchanged replacement |
| 401 | Bad credentials, ineligible account, missing/invalid/expired access, missing/revoked/expired session, invalid/expired/reused refresh, unknown/absolutely expired logout secret |
| 403 | Authenticated caller lacks an actual required permission |
| 409 | Duplicate normalized username including database race |
| 5xx | Unexpected infrastructure failure, safely represented rather than disguised as bad credentials |

Security-filter errors use AuthenticationEntryPoint/AccessDeniedHandler; application/MVC failures use advice. Preserve applicable WWW-Authenticate: Bearer challenges without reflecting secrets. Disabled/locked/missing credential identities have generic 401 behavior, not account-enumerating messages.

## Persistence and automated acceptance requirements

Use existing PostgreSQL, Flyway additive migrations, UTC Instant/TIMESTAMPTZ, naming/nullability/uniqueness/FK conventions and immutable account identity. Preserve applied migrations/profile/address data. Concrete session IDs, indexes and lock/update design belong to planning; no Redis requirement. Existing REQUIRED/READ COMMITTED defaults apply; unusual propagation/isolation needs constitution-compliant approval rather than an assumed REQUIRES_NEW/SERIALIZABLE workaround.

Retain consumed history through session expiry. Proposed bound: purge expired security rows within thirty days after absolute expiry, never remove active-session reuse evidence. Authentication/state checks cannot depend on cleanup execution. Signing and encoder/default-role policy is confirmed below. Real verification workflow/expiry/provider is deferred; no Google authentication is implemented here.

Use existing JUnit/Mockito/AssertJ/MockMvc and Test/IT organization, PostgreSQL Testcontainers/Flyway, actual Surefire/Failsafe discovery, reusable domain fixtures with visible scenario inputs. Required appropriate unit, MVC slice, repository, service, API and E2E acceptance covers registration normalization/uniqueness/atomic automatic login; username/provisional current-email login and explicit separation from future actual verification; exact password code-point and encoder byte bounds; header-only access/no cookies/no access JSON; refresh JSON; JWT signature/algorithm/issuer/expiry/user/session/owner validation; current DB authorities/status; independent devices; seven-day non-extension; strict reuse durable despite 401; logout/access expiry/repetition; password all-session revocation/rollback; real refresh/logout/password/login races; ownership/permissions; CORS header exposure and exact-route stale-bearer behavior. No sequential unit suite alone proves concurrency. No developer/production database access during verification.

## Confirmed scope versus unresolved dependencies

Feature 001 preserves username display casing and broader nonblank names; no arbitrary legacy rename/deletion/migration is authorized here. The confirmed no-@ amendment resolves dispatch while retaining all other original User rules. Planning must detect legacy @ usernames and report any required approved remediation, without silent renaming/deletion or insecure fallback. Real email verification and role/permission persistence are absent. The confirmed provisional email policy and bootstrap configuration below define required supporting work rather than pretending it already exists. This request explicitly authorizes refresh/revocation under constitution VI. Media remains a separate feature; authentication does not authorize fake image validation or image-storage implementation.

## Confirmed bootstrap configuration (2026-10-26)

- Default role name USER, emitted as ROLE_USER once. Initial account status ACTIVE.
- Initial permission identifiers MANAGE_USER_PROFILE and MANAGE_ADDRESS correspond to the user's requested manage-profile/manage-address capabilities. They authorize self-profile and owned-address actions only, never administration or foreign-user ownership. Permissions have no ROLE_ prefix. No additional role/permission is introduced; future features may extend them. Authenticated limited-public-profile viewing has no extra feature-specific permission requirement.
- BCryptPasswordEncoder, strength 10. Password minimum eight Unicode code points, maximum 72 UTF-8 bytes of the unmodified original value, with no normalization/truncation. Test multibyte boundaries explicitly. Exact current-password checks must not accept equality caused by truncation.
- JWT signing algorithm HS256 (HMAC-SHA256), exclusively allowlisted. External environment variable JWT_SECRET binds to security.jwt.secret. Require at least 32 UTF-8 bytes; use those UTF-8 bytes directly, not implicitly Base64-decoded data. Do not trim/alter the signing secret, hardcode it, log it or provide a development fallback. Missing/short configuration fails startup/issuance closed. Issuer remains externally configured, required validation unchanged; audience is used only when a real audience contract exists.
- Username policy is the original User contract, not the original pasted lowercase-ASCII registration proposal. Current email is temporarily trusted without real verification. These are explicit later user overrides. Identifier dispatch is confirmed: @ means email, otherwise username; usernames cannot contain @.

## Confirmed username amendment (2026-10-26)

Reject @ in both registration and profile username updates. All other original User rules remain: trim surrounds, preserve display casing/internal whitespace, nonblank, at most 50 Unicode code points, case-insensitive database uniqueness and no blanket ASCII restriction. Reject the whole mutation with 400 before state changes. Do not introduce a three-character minimum or lowercase username storage. Login identifier containing @ follows email only; otherwise username only, without password-failure fallback or a new identifierType property.

Existing usernames containing @, if any, require a separately approved remediation before claiming full legacy-account login coverage; planning must inspect compatibility without querying shared databases or renaming/deleting data automatically. Add only new migrations if stronger persistence constraints are planned. Existing immutable-subject sessions remain valid under the established rules. User-domain normalization/validation requires an explicit integration task for the new prohibition; no User code is changed by this specification update.


## Confirmed authority and ownership enforcement (2026-10-26)

Use `hasAuthority('MANAGE_USER_PROFILE')` for GET/PUT /api/me and `hasAuthority('MANAGE_ADDRESS')` for GET/POST /api/me/addresses and GET/PUT/DELETE /api/me/addresses/{addressId}. Use exact permission names, with no ROLE_ prefix and no substitution with hasRole. Enable the applicable framework authorization support during implementation; do not replace current database authorities with fixed grants.

Authorize the operation before accessing its owned resource: invalid/missing access credentials yield 401; an authenticated eligible caller lacking the required authority gets 403 without a mutation or resource disclosure. Once authority passes, User/Address services still enforce immutable actor identity and ownership; foreign/missing addresses yield the same 404. GET /api/users/{userId} requires authentication only and retains its public allowlist. These are binding requirements, not a claim of implemented configuration. This confirmed decision supersedes earlier feature 001 authentication-plus-ownership-only wording for self-profile/address access.
