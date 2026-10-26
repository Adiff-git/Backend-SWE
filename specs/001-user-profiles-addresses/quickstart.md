# Quickstart Validation Guide

## Current status and prerequisites

This is a future implementation validation guide, not evidence that endpoints/tests exist. The current app is a scaffold. Run no database-changing command against a developer or production database.

Prerequisites: approved implementation completed, JDK 25, Maven wrapper, Docker running, approved validation/Lombok/Failsafe changes, reconciled Boot version, and real auth/User/media prerequisites from [contracts/integration.md](contracts/integration.md). Docker Testcontainers must use a disposable PostgreSQL database with Flyway-created schema. No external AWS credentials or real S3 resources are required for this feature's database validation.

## Commands after implementation

From repository root in PowerShell:

```powershell
Set-Location demo
.\mvnw.cmd -B -ntp -Dspring.profiles.active=test test
.\mvnw.cmd -B -ntp -Dspring.profiles.active=test verify
Get-ChildItem target/surefire-reports/TEST-*.xml
Get-ChildItem target/failsafe-reports/TEST-*.xml
Get-Content target/failsafe-reports/failsafe-summary.xml
```

Expected: unit/controller Test reports from Surefire; RepositoryIT/ServiceIT/API IT from Failsafe; reports contain actual non-zero executed integration cases and zero failures/errors. A successful verify with absent IT reports is not sufficient. Before running, confirm the test profile points exclusively to the disposable Testcontainers database and Flyway migrations, not environment-overridden shared credentials. No Maven commands were run during planning.

## Validation scenarios

| Scenario | Procedure | Expected proof |
| --- | --- | --- |
| Profile and privacy | Seed two users via approved test fixtures; retrieve self and other's limited profile | Exact distinct DTO allowlists; no password or public private fields |
| Authentication | Request each route without valid Bearer credentials; repeat validly authenticated | 401 without auth; access with valid identity and no extra feature role/status restriction |
| Rename continuity | Obtain real JWT through the auth prerequisite, change username, reuse original JWT | Same immutable user and owned-address access until normal token expiration |
| Presence/normalization | Exercise omitted/null/value cells, padded maxima, blank strings, email case and protected fields | Exact preservation/removal/400 semantics; lowercase email, preserved username casing, no truncation |
| Uniqueness | Same-user equivalent names then two-user case variants and concurrent claims | Unchanged values succeed; duplicates/races 409 and rollback |
| Address state machine | Empty list, first create with all flag variants, later creates, updates, default/non-default/final deletes | 201+Location, 200/204 contracts; exactly one default, highest-ID fallback |
| Ownership | Missing and foreign address IDs through read/update/delete | Indistinguishable 404, no foreign state mutation |
| Capacity | 19-to-20 create, create at 20, delete then create | At most 20; overflow 400; updates/deletes still allowed |
| Avatar | Omit, null, valid/foreign/unavailable ID against real media prerequisite | Preserve/remove/replace; invalid 400; no storage deletion |
| Errors | Invalid input, invalid token, genuine authorization failure, missing record, conflict | Same ProblemDetail/custom fields and safe messages from advice and filter handlers |

## Real concurrency and rollback checks

Use independent connections and transactions coordinated by latches/barriers, not timing sleeps. No test-level transaction may hide service commit/rollback.

- Two first creates: one default after both commits; no empty-book locking gap.
- Two creates from 19: only one commits and the other returns capacity 400.
- Competing default switches and default-delete/create: final committed book satisfies invariant and fallback.
- Profile writes to same field: later lock-ordered successful write wins. Different-field selective writes retain both changes; omitted fields use latest state.
- Different user writes: locking one user does not impose a global application lock on another.
- Hold one user's lock, use a short test lock_timeout and attempt profile/address writes on another connection: 409, safe retry message, zero automatic retry and no partial writes.
- Inject a genuine failure after old-default clear/delete has flushed: real rollback restores the prior committed default and address book.
- Race two users for the same normalized identity: database constraint protects uniqueness even though they hold different user locks.

Clean committed disposable test data after each scenario; do not use fixed generated IDs. Fetch-plan checks should prove converters perform no queries and unrelated associations are not eagerly loaded. Keep distinct scenarios separate, parameterize only equivalent input classes.

## Acceptance exit

All [spec.md](spec.md) scenarios and [contracts/http.md](contracts/http.md) tables pass, Test/IT discovery is demonstrated, no unavailable prerequisite is replaced by permissive behavior, and no shared database was accessed. Report missing static-analysis gates honestly; configuring unrelated quality tooling or deployment is outside this planning delivery.

## Implemented core validation (2026-10-26)

Core User/Address code and repository/service tests are now present. Authentication and Media are not prerequisites for these core tests; older full API prerequisites above apply only to deferred integration flows. Core tests use controlled identities/null images and disposable PostgreSQL/Flyway. Core integration classes are UserRepositoryIT, AddressRepositoryIT, UserServiceIT, AddressServiceIT, PublicProfileServiceIT and ApiErrorSerializationIT. No protected feature routes are active.

Run Maven verify from demo/ using Java 25 and Docker. This session used the cached Maven 3.9.16 bin/mvn.cmd with an explicit local Maven repository because the wrapper has an environment-specific empty-Target-array startup failure. Inspect both Surefire and Failsafe XML; do not equate passing core tests with real JWT/API/media readiness. Do not point test profile/environment overrides at an external database.
