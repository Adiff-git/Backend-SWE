# HTTP Contract: Authentication

This refines [../contracts.md](../contracts.md) without reopening confirmed decisions. Prefix /api; application/json requests/success TokenResponse and application/problem+json errors. No auth cookies or servlet-session login. Five auth routes only; existing protected User/Address endpoints are wired through real current identity with Media operations still unavailable.

| Method/path | Required body | Auth processing | Success |
| --- | --- | --- | --- |
| POST /api/auth/register | username, password | Body credentials; unrelated bearer ignored | 201 TokenResponse + Authorization + no-store |
| POST /api/auth/login | identifier, password | Body credentials; unrelated bearer ignored | 200 TokenResponse + Authorization + no-store |
| POST /api/auth/refresh | refreshToken | Body refresh credential; unrelated bearer ignored | 200 TokenResponse + Authorization + no-store |
| POST /api/auth/logout | refreshToken | Body refresh credential; unrelated bearer ignored | 204, no body |
| PUT /api/auth/password | currentPassword, newPassword | Valid bearer plus current password | 204, no replacement token pair |

Registration rejects client IDs/status/role/permission/verification/audit values. Password body cannot select userId or sid. Required omission/null and malformed JSON/type is 400. Unknown well-formed credentials produce generic 401, not 404. No identifierType: trimmed identifier containing @ selects email, otherwise username, with no fallback after password failure. Username max 50 Unicode code points after strip, nonblank, no @; preserve display casing/internal whitespace/non-ASCII and database LOWER uniqueness. Email uses current normalized User.email and provisional trust, not proof.

Password values are never stripped or normalized; registration/replacement minimum eight code points and maximum 72 UTF-8 bytes. Current/login input cannot bypass maximum through encoder truncation; values outside applicable policy fail safely without revealing account state. A valid length but wrong currentPassword is 400; wrong login credentials/status are generic 401. Unchanged new password is 400. Refresh input is exact opaque string, no trim/normalization; missing/null/empty/wrong type or oversized (>1024 characters) is 400, well-shaped unknown/expired/reused is 401. Do not log request DTO values.

## Token response

Response headers: Authorization: Bearer <JWT>; Cache-Control: no-store. JSON contains exactly tokenType=Bearer, accessTokenExpiresIn (nonnegative remaining whole seconds computed from issued claims, never mint expired access), refreshToken and refreshTokenExpiresAt (UTC ISO-8601 Z, unchanged family deadline). Never an accessToken JSON field. Refresh token issued as 43 unpadded Base64URL characters. Access input accepted exclusively via Authorization header, never query/body/cookie. Responses never set authentication cookies; browsers must explicitly resend access headers.

JWT claims: sub=canonical positive User Long ID text, sid=canonical UUID session identity, iss=configured issuer, iat and exp=whole-second NumericDates. Validate signature/HS256 allowlist/issuer/required claim types/future iat/exp and session owner. Reject exp<=now, exp beyond session expiry, unknown/revoked/expired session or missing/ineligible User. Audience validation only if an audience contract is configured. No password/refresh secret/hash/personal detail in claims. Current DB authorities replace stale token authority claims.

## Sessions and logout

Session absolute deadline is creation+7 days; access deadline min(issuance+15 minutes, session expiry). No grace extending deadline. Current refresh rotates atomically; consumed reuse revokes whole owning session, persists despite 401 and invalidates even replacement credentials returned by a competing request. Other sessions remain active. A random secret cannot select a session to revoke.

Logout accepts retained recognized current/consumed secret for an unexpired session; revoke own family/access, 204. Recognized already-revoked/unexpired repeated logout remains 204. Unknown/absolutely expired credentials return generic 401 independent of whether cleanup ran. No unexpired bearer is needed. Clients remove local credentials and serialize refresh; never blindly retry consumed refresh after a lost response.

## Authorities, errors and cross-origin behavior

USER -> ROLE_USER exactly once; permissions MANAGE_USER_PROFILE and MANAGE_ADDRESS. Self-profile/address management requires relevant authority plus owner checks; missing/foreign address remains indistinguishable 404. Limited public profile viewing requires authentication only. No admin/foreign ownership grants.

| Status | Meaning |
| --- | --- |
| 400 | Invalid input, wrong currentPassword, unchanged/invalid newPassword |
| 401 | Generic login/account/access/session/refresh/logout authentication failure |
| 403 | Authenticated caller lacks applicable permission |
| 404 | Existing missing/foreign profile/address resource contract, not auth identity enumeration |
| 409 | Normalized username uniqueness conflict; recognized coordination conflict under existing convention |
| 5xx | Unexpected safe infrastructure failure, never silently recast as bad credentials |

ApiError extends ProblemDetail: type/title/status/detail/instance and timestamp/message/path/fieldErrors. Numeric status agrees with HTTP; safe message=detail, path=instance; timestamp UTC Z, fieldErrors map of safe message arrays or {}. Never reflect passwords/credential values/signing material/internal hashes/SQL/stack traces. AuthenticationEntryPoint/AccessDeniedHandler handle filter failures and applicable WWW-Authenticate: Bearer challenge; advice handles application/MVC errors. The nontransactional facade raises reuse 401 only after committed revocation.

CORS explicit configured origins, Authorization request allowed/response exposed, preflight supported; no credentialed mode solely for bearer auth. Chain CSRF checks are disabled only for this explicit header/body-credential API, not generalized to cookie/session chains. No form/Basic login or JSESSIONID dependency. Method/path near-misses must not enter the public credential chain; unknown routes have protected/deny-safe fallback.


## Confirmed permission and ownership amendment (2026-10-26)

Self-profile GET/PUT /api/me requires `hasAuthority('MANAGE_USER_PROFILE')`; all five address operations require `hasAuthority('MANAGE_ADDRESS')`. Both permission and service-level ownership are mandatory; authority never overrides owner scoping. Missing/invalid authentication is 401, missing required authority is 403, and an authorized caller's missing/foreign address remains indistinguishable 404. Limited-public-profile GET /api/users/{userId} stays authentication-only. Permission names are exact, without ROLE_ prefix or hasRole substitution. Use current database authorities; test an owner without authority, an owner with authority, and an authorized foreign caller, including permission revocation between requests. No denied operation may mutate data or disclose ownership. This user-confirmed policy overrides the earlier feature 001 authentication-plus-ownership-only rule and changes no implementation by itself.
