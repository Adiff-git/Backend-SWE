# Authentication Quickstart Validation Guide

## Status and prerequisites

Authentication is implemented in demo/src/main/java/com/example/demo/auth, with real bearer integration in user/controller. The managed Resource Server/Jose dependency was explicitly approved. Existing User/Address core and shared tests remain regression coverage. This guide documents verification and remaining deployment prerequisites.

Prerequisites for verification: Java 25, Maven 3.9.16 and Docker for disposable PostgreSQL. Set deployment JWT_SECRET externally (raw UTF-8 >=32 bytes), required security.jwt.issuer and explicit CORS origins; never commit or print secret material. Automated tests supply isolated ephemeral/test configuration, including valid test issuer and secret; production properties have no fallback. Real Google/email verification, S3 and Redis are not prerequisites for the approved provisional-email authentication flow.

Run from repository root in PowerShell:

```powershell
Set-Location demo
.\mvnw.cmd -B -ntp test
.\mvnw.cmd -B -ntp verify
Get-ChildItem target/surefire-reports/TEST-*.xml
Get-ChildItem target/failsafe-reports/TEST-*.xml
Get-Content target/failsafe-reports/failsafe-summary.xml
```

Use the existing test profile/Testcontainers support for database-backed tests, confirming every datasource targets only a disposable PostgreSQL container. The wrapper had an empty-Target-array startup failure in the previous environment; if it persists, invoke the already-installed/cached Maven 3.9.16 distribution directly from demo with an explicit local Maven repository. Do not change dependency/security behavior just to work around shell startup. A successful verify without actual nonzero Test/IT discovery is not sufficient.

## Contract scenarios

| Scenario | Expected acceptance evidence |
| --- | --- |
| Registration | 201; normalized/case-preserved unique username without @; real BCrypt; ACTIVE/USER and approved permissions; one session/pair; no partial state on failure |
| Login | Username and current provisional email work with exact password; @ dispatch has no fallback; generic 401 for wrong/missing/ineligible identities; independent device sessions |
| Password limits | Eight Unicode code points minimum, 72 UTF-8 bytes maximum; exact whitespace retained; overflow rejected before BCrypt rather than truncation |
| Delivery | Authorization response Bearer only for access; no accessToken JSON/auth cookies; no-store; refresh and metadata in specified JSON |
| JWT validation | HS256, issuer, sub/sid/iat/exp types, missing/future/expired claims, owner match, account/session existence; signature alone insufficient |
| Current state | Role/permission/status/session changes affect next authentication; no stale claims/cache grant; ROLE_ once; ownership and foreign-address 404 preserved |
| Rotation | One replacement for same session, consumed old secret retained, original seven-day deadline unchanged; access capped to remaining session lifetime |
| Reuse | 401 while session revocation is committed and queryable afterward; previous/new access and current refresh fail, other sessions survive |
| Logout | Refresh credential works after access expiry; retained consumed works; recognized revoked/unexpired repeat 204; unknown/absolute expiry 401 independent of cleanup |
| Password change | Valid current password and different valid new password; 204 no pair; every prior session revoked; failure preserves password and all session state |
| Route isolation | Attach stale/invalid bearer to each exact public credential POST and still process its body credentials; PUT password/protected endpoints reject invalid bearer; near-miss routes are not public |
| CORS/CSRF | Explicit origin preflight, allow/expose Authorization, no unnecessary credentialed CORS, no auth-cookie dependence or weakening other chains |
| Provisional email | Current address provisionally works without real verification; replacement disables old address; report never calls this proof-based verification or Google login |
| Compatibility | Reject @ at registration/profile update; retain non-ASCII/casing/internal-whitespace and max-50 code-point rules; fail migration safely if incompatible existing names require approved remediation |
| Errors | Existing ApiError/ProblemDetail shape across filter/advice, appropriate bearer challenge, no credential/hash/key/SQL/stack disclosure |

Use [contracts/http.md](contracts/http.md) for exact fields/statuses and [data-model.md](data-model.md) for relationships/constraints. Manual HTTP clients must keep credentials out of shell history/log output; automated MockMvc/API flows are preferred for verification. This guide intentionally omits full implementation/test bodies.

## Real concurrency and rollback

Use independent PostgreSQL connections, real service proxies and latches/barriers, not fixed sleeps or a test-level transaction that conceals commits. Verify from a fresh transaction after HTTP responses:

- Two same-token refreshes: at most one current row; detected reuse ultimately revokes family, including any pair one response already returned.
- Refresh/logout and refresh/reuse: final committed revocation prevents subsequent authentication.
- Login/password change: old-password snapshot cannot create surviving post-change session; login ordered later must use replacement password.
- Refresh/password change: earlier refresh is revoked, later refresh fails against revoked session.
- Profile email/username changes versus login: under-lock identifier recheck prevents old email or different candidate owner authentication.
- Reuse-triggered 401 retains revocation; random unknown tokens cause no unrelated mutation.
- Signing/insertion/assignment failure rolls back registration/rotation; password persistence/revocation failure restores both old password and session state.
- Held-owner lock times out safely without automatic retry or partial writes; another owner's operations are not globally locked.
- Cleanup never deletes active-session consumed evidence; expiry rejection is identical before/after cleanup.

## Completion evidence and limits

Report actual unit/MVC/repository/service/API/E2E class discovery and results, plus persisted-state race evidence. Validate secret/configuration failures without logging values. No production/dev database, Google provider, Media/S3 integration or frontend storage implementation is part of these checks. Existing synthetic UserTestData password strings must be replaced with real encoder-generated credentials only in fixtures requiring actual login. Missing static-analysis gates are reported honestly rather than configured through unrelated changes.


## Confirmed permission and ownership amendment (2026-10-26)

Self-profile GET/PUT /api/me requires `hasAuthority('MANAGE_USER_PROFILE')`; all five address operations require `hasAuthority('MANAGE_ADDRESS')`. Both permission and service-level ownership are mandatory; authority never overrides owner scoping. Missing/invalid authentication is 401, missing required authority is 403, and an authorized caller's missing/foreign address remains indistinguishable 404. Limited-public-profile GET /api/users/{userId} stays authentication-only. Permission names are exact, without ROLE_ prefix or hasRole substitution. Use current database authorities; test an owner without authority, an owner with authority, and an authorized foreign caller, including permission revocation between requests. No denied operation may mutate data or disclose ownership. This user-confirmed policy overrides the earlier feature 001 authentication-plus-ownership-only rule and changes no implementation by itself.


## Verified implementation and deployment boundaries

Surefire discovers `*Test` (unit, MVC slices and the existing context smoke test); Failsafe discovers `*IT` (PostgreSQL repository/service/API/flow tests). V1-V4 are applied by Flyway to a disposable PostgreSQL 17 container. The wrapper still encounters the Windows empty-Target-array startup issue; the installed Maven 3.9.16 distribution with the explicit existing local repository runs the same test/verify lifecycle successfully. No shared developer/customer database is used.

The real HTTP endpoints are POST /api/auth/register, /login, /refresh, /logout and PUT /api/auth/password, plus authenticated User/Address routes. Token issuance returns Authorization: Bearer in the response header and only the four metadata/refresh fields in JSON. Browser origins must be explicitly configured and Authorization is exposed by CORS.

Expiry equality and the seven-day cap use a controlled clock only in AuthSessionExpiryApiIT. RegistrationLoginServiceIT injects controlled signing/revocation/cleanup failures to prove real transaction rollback. RefreshRotationApiIT, LogoutRefreshRaceIT, PasswordRevocationRaceIT and EmailLoginApiIT prove coordination across independent committed transactions. LegacyMigrationIT checks both successful backfill and safe refusal of legacy @ usernames. UserApiIT preserves the Media-unavailable 503 even when Jackson wraps the request-setter exception; rejected updates leave the profile intact.

Before deployment, supply JWT_SECRET, JWT_ISSUER and CORS_ALLOWED_ORIGINS from the intended environment and assess any existing @ usernames/unsupported password encodings. Such data requires an approved remediation; no automatic rename/hash fallback exists. Actual verification/Google and Media upload/reference validation are separate future features. Omitted/null image references remain supported; non-null image selection is unavailable. No deployment, commit or push is part of verification. No static-analysis gate is configured or claimed.


Final validation (2026-10-26): `mvn verify` BUILD SUCCESS; Surefire 73 tests / 29 suites, Failsafe 71 tests / 24 suites, zero failures/errors/skipped (144 total). Inspected actual XML reports and Failsafe summary; original User/Address regressions remain green. `git diff --check` passed. No configured static-analysis gate, commit/push or deployment is claimed. Task completion: T001-T062.
