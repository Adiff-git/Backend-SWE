# Quickstart validation guide

This guide applies after implementation. None of these endpoints, migrations, tests or human results is certified complete by document consolidation. Detailed fields/protocols: [data-model](data-model.md), [HTTP contract](contracts/http.md).

## Prerequisites and commands

Java 25, Maven wrapper, Docker/PostgreSQL Testcontainers, existing local LocalStack requirements, isolated capture SMTP, controlled OIDC fixture and uncommitted Google/SMTP/JWT/HMAC/AEAD configuration. The Boot-managed mail starter has explicit developer approval.

From repository root in PowerShell:

```powershell
Push-Location demo
.\mvnw.cmd test
.\mvnw.cmd verify
Pop-Location
```

Inspect actual Surefire/Failsafe discovery and exit codes, not names alone. Existing local infrastructure can use:

```powershell
docker compose --env-file .env.local -f compose.dev.yaml up -d postgres localstack image-storage
Push-Location demo
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=local'
Pop-Location
```

.env.local is user-supplied/uncommitted; Maven does not load it automatically. Configure process environment through normal local secret handling. New property names must match contracts/config docs without real values. Automated scenarios use fixed time, controlled signed OIDC and capture-mail service, never real consent/recipients.

## 1. Pending registration and legacy access

POST /api/auth/register {email,password} → 202 credential/challenge metadata, no Authorization/TokenResponse. Assert zero users/official-email reservations/sessions/credentials. Password is validated but not persisted. Confirm with initiating registrationCredential, challengeId, six-digit code and explicitly supplied password → 201 normal TokenResponse/Authorization after verified-account/session atomic creation. Only confirmation's password works. Before confirmation no local account exists.

Wrong/missing/expired/replayed/superseded proof creates no user. Include leading-zero code. Immediate resend rotates secret/code atomically; old credential fails. Independent same-destination attempts neither reserve email nor supersede each other. Lost current credential requires independent initiation, not public-ID recovery. Concurrent confirmations yield at most one account and one successful response. Google may claim email while local registration is pending; local confirmation then conflicts without logging into/linking that Google account. SMTP outage leaves pending state only and can recover within challenge deadline or by immediate resend. Legacy username/email-less accounts retain access; historical unverified users use authenticated verification routes, not public account-creation proof.

## 2. Google account login

Generate/retain frontend S256 verifier; initiate one browser attempt, follow provider, redeem application code. New validated verified-email identity → one ACTIVE/default-role account with absent local password and verified official email. Callback alone creates no session. Returning issuer/subject resolves same owner even with changed/missing provider email; official email/profile/challenges unchanged. New missing/unverified email fails. Matching existing local email requires ordinary sign-in plus explicit Connect Google, never auto-link. Current ineligibility, refresh rotation/reuse, seven-day session expiry and logout retain behavior.

## 3. Explicit linking and optional first password

Authenticate local owner and obtain CONNECT_GOOGLE recentAuthProof through current-password reauthentication. Start LINK with proof and frontend challenge, separately prove Google identity, exchange using original bearer session/verifier. Expect connected response and no new session. Check same-link idempotence with new proofs, different-link/owner conflict, and no official-email mutation.

Expired/replayed/wrong-owner/wrong-session/wrong-operation/concurrently consumed proof must never leave association. Test revocation between initiation/completion and two owners racing one subject. New provider identity cannot establish local ownership itself.

Google-only owner uses already linked fresh Google authentication for INITIAL_PASSWORD and optionally sets local password in profile. Success 204, all current/other access and refresh credentials rejected, fresh Google/email-password login succeeds. Concurrent setup cannot overwrite password; failed setup preserves sessions. Existing password changes retain prior contract.

## 4. Existing-user email change

Obtain EMAIL_CHANGE proof using current password when set or already linked fresh Google when absent. Request destination code; old official email/evidence stays unchanged. Confirm with still-fresh proof and code → new verified email, unchanged username/provider metadata. If five-minute proof expires before ten-minute code, reauthenticate. Direct supplied PUT profile email fails; omitted email preserves value. Concurrent destination claims yield one winner; earlier registration code cannot verify a replaced official address.

## 5. Unrestricted repeated requests

Exercise more than the former limits: at least 25 Google initiations, 30 invalid exchanges, 12 wrong password/code submissions and repeated immediate sends/resends. Verify normal 201/202/400/401 outcomes without 429 or Retry-After, valid authentication after repeated failures, secret/code rotation, unchanged official email before successful confirmation and preserved challenge/outbox rollback. V14 removes counters and leaves existing accepted business records intact.

## 6. Browser, cleanup and failure recovery

Real filters cover state/nonce/signature/issuer/audience/expiry/auth_time, cancelled consent/outage, public invalid unrelated bearer versus protected original-session checks and exact origins/routes. Serialize every initiation while in progress, including authenticated starts and coordinated tabs. Delay an old callback response until after a newer initiation sets its cookie; callback must emit no cookie write/deletion and newer flow remains valid. Superseded/replayed callbacks fail while cookie still exists. Deliberately overlap initiations and reorder responses: server generations reject stale correlation; wait until requests settle and restart serially. Cookie expiry/missing state safely restarts, never authenticates from stale state. Same-site deployment, Secure/HttpOnly/SameSite=Lax/300s cookie, natural five-minute cookie expiry with no callback cookie mutation, no application JWT cookie, no-store/no-referrer and frontend URL secret removal are explicit integration evidence.

Clock tests enforce 60-second handoff/five-minute handshake and proof/ten-minute code; expired requests fail without waiting for cleanup. Bounded workers purge secrets within 24 hours; interrupted outbox payload purge within 15 minutes of expiry while running and before dispatch after recovery. Test duplicate mail, superseded in-flight code, FK-safe cleanup, restarts and backlog deadline reporting. Inspect application/proxy logging for redacted callback/code/OTP/token/secret payloads.

## Evidence and completion

Record commands, discovery/exit codes, migration inventory, controlled fixtures, races and failures. Run populated legacy upgrade and existing auth/user/catalog/media regressions. Record SC-001–SC-008 automated evidence separately from five-person SC-006 walkthrough. If participants or production ingress configuration are unavailable, record that external validation as incomplete; do not fabricate results. Task checkboxes reflect implementation work, checklist markers reflect document-quality review only.

Removal validation (2026-11-05): offline `mvnw.cmd clean verify` with the Windows socket-path option below completed successfully: 277 unit/MVC tests across 69 Surefire classes and 302 integration tests across 73 Failsafe classes; zero failures, errors or skips. The clean build removes deleted limiter bytecode. V13-to-V14 populated upgrade preserves legacy account, pending registration, challenge and accepted outbox data while removing accounting tables/reference columns and failed-attempt state. The packaged JAR contains V14 and no limiter/filter/IP-resolver classes.

The additional `mvnw.cmd -o -q '-Dtest=EmailVerificationFlowIT' test` run with the same socket-path option passed all six scenarios, including eight immediate authenticated challenges to the same destination followed by successful confirmation. This test was added after the full build compiled its tests, so its six results are recorded separately rather than added to the full-build total. Repeated pending sends/resends, 25 Google starts, 30 invalid exchanges, and valid password/code proofs after 12 failures are covered by the clean regression run. Existing ownership, one-use, expiry, atomicity, delivery and catalog/media/wishlist regressions remain passing. No live Google/SMTP, production deployment or human walkthrough is claimed.

On this Windows/JDK installation, supply the test JVM option below to keep local HTTP/Testcontainers Unix-domain temporary sockets within the supported path length:

```powershell
Push-Location demo
.\mvnw.cmd test '-DargLine=-Djdk.net.unixdomain.tmpdir=C:\Users\TrinhThanh\Downloads\SE_Project\Backend-SWE\demo\target'
.\mvnw.cmd verify '-DargLine=-Djdk.net.unixdomain.tmpdir=C:\Users\TrinhThanh\Downloads\SE_Project\Backend-SWE\demo\target'
Pop-Location
```

External production validation remains incomplete: no five-person SC-006 walkthrough, real-recipient SMTP delivery or production ingress/header-sanitization inspection has been claimed. Real Google consent and local Mailpit delivery were validated on 2026-11-08 as described below. Configure exact frontend origins and both frontend destinations, independent external proof keys, Google credentials and SMTP sender through the documented local/prod environment names. Production enforces HTTPS destinations and SMTP STARTTLS. The frontend must coordinate and serialize all Google initiation responses across tabs before replacement; this backend repository does not contain that frontend implementation.


## Local Compose verification - 2026-11-08

Started the actual `compose.dev.yaml` stack with the developer-supplied `.env.local`, enabled email verification/Google, and Mailpit. Backend, PostgreSQL, LocalStack, signed image gateway and Mailpit all reached healthy state. Mailpit is version/digest pinned, its UI binds only to loopback (default port 8025), SMTP stays inside Docker, and local SMTP auth/STARTTLS are disabled explicitly. Secret values were not printed. Corrected the mounted S3 init script's Windows CRLF shebang to LF; `.gitattributes` preserves shell LF on subsequent checkout. No volumes were deleted.

The live HTTP check passed 23 assertions: pending-only registration (202), login before confirmation denied (401), immediate resend/credential rotation (202), superseded proof rejected (400), real code delivery to Mailpit, confirmation issuing credentials (201), verified profile (200), confirmation replay and initial non-authoritative password rejected, email/password login, refresh rotation/reuse rejection/session revocation, destination change with fresh proof and mailed code, unchanged email before confirmation, wrong-code rejection, verified replacement-email login, logout and rejection of logged-out access. Google initiation returned 201, real-provider authorization redirect returned 302 with configured client/redirect/scopes, and uncorrelated callback failed safely without cookie mutation. Test accounts use unique `example.test` mailboxes captured only by Mailpit; test data was left in the development database.

A temporary loopback-only frontend callback helper exercised real Chrome/Google consent with the developer selecting their account. The actual backend callback produced an application handoff; code exchange, profile read and connection read each returned 200. The resulting account had `emailVerified=true`, `connected=true` and `hasLocalPassword=false`, as required for a new Google-only account. No access/refresh token, code, account address or client secret is recorded here. The temporary browser tab and callback helper were closed after verification; the Compose stack remains running. This helper is not a production frontend implementation.

Focused controlled-provider integration command: `mvnw.cmd -o -q '-Dtest=GoogleLoginFlowIT,GoogleConnectionFlowIT,GoogleBrowserFlowIT' test`, with the Windows socket-path argLine above. All 32 tests passed (6 login, 14 connection, 12 browser), zero failures/errors/skips. These use controlled signed OIDC responses and are separate from the real Google browser result. No full regression rerun is claimed for this Compose-only change; T070 still retains its remaining human/production/frontend obligations.

Local diagnostic artifacts in ignored `demo/target`: `live-auth-report.json`, `google-browser-report.json`, and focused Maven reports. Recreate/run the backend with the documented Compose command; inspect Mailpit at `http://localhost:8025`.
