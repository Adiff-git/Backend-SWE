# Wishlist Validation Guide

The wishlist implementation is available. Automated validation uses disposable infrastructure; current execution evidence and environment limitations are recorded in [implementation-validation.md](implementation-validation.md).

## Prerequisites and automated checks

Java 25, existing demo Maven wrapper, running Docker engine and disposable PostgreSQL 17 Testcontainers. Reuse test profile jdbc:tc:postgresql:17-alpine, Flyway enabled/ddl-auto=validate and OSIV=false. No real AWS/developer database. API integration retains real internal services and mocks external storage/signing only where appropriate.

From repository root in PowerShell:

```powershell
Push-Location demo
.\mvnw.cmd test
.\mvnw.cmd verify
Pop-Location
```

After implementation, verify unit/MVC `*Test` discovery in demo/target/surefire-reports and repository/service/API/concurrency `*IT` discovery in demo/target/failsafe-reports. Confirm WishlistControllerIT actually executed; a zero-test success does not verify behavior. Existing full verification may provision disposable LocalStack for existing media tests. Report Docker/network failures accurately.

Focused future wishlist commands:

```powershell
Push-Location demo
.\mvnw.cmd '-Dtest=Wishlist*Test' test
.\mvnw.cmd '-Dit.test=Wishlist*IT' verify
Pop-Location
```

No dependencies/plugins are planned. Flyway tests must cover both a clean disposable database and upgrade fixtures with existing USER assignments/custom-role-only accounts. Only approved permission relationships change.

## Optional local HTTP validation

Use an isolated local/test environment with actual authentication and controlled published product fixtures. Product administration/seeding is not a new public endpoint. A no-image fixture avoids signing setup. For imaged fixtures, initialize existing private LocalStack bucket and matching catalog metadata/object keys using repository setup; deferred bucket initialization/persistence issues are not repaired here.

Supply LOCAL_DB_PASSWORD, JWT_SECRET and JWT_ISSUER privately for the existing local profile. From repository root:

```powershell
docker compose up -d postgres localstack
Push-Location demo
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=local'
Pop-Location
```

In another terminal, substitute real test credentials and a canonical published fixture UUID. Current login returns the access token in the Authorization response header, not an accessToken JSON field.

```powershell
$wishlistBase = 'http://localhost:8080'
$wishlistCredentials = @{identifier='YOUR_TEST_USERNAME'; password='YOUR_TEST_PASSWORD'} | ConvertTo-Json
$wishlistLogin = Invoke-WebRequest -Method Post -Uri "$wishlistBase/api/auth/login" -ContentType 'application/json' -Body $wishlistCredentials
$wishlistHeaders = @{Authorization=[string]($wishlistLogin.Headers['Authorization'] | Select-Object -First 1)}
$wishlistProduct = 'REPLACE_WITH_PUBLISHED_CANONICAL_UUID'
Invoke-WebRequest -Method Post -Uri "$wishlistBase/api/me/wishlist/$wishlistProduct" -Headers $wishlistHeaders
Invoke-WebRequest -Method Post -Uri "$wishlistBase/api/me/wishlist/$wishlistProduct" -Headers $wishlistHeaders
Invoke-RestMethod -Uri "$wishlistBase/api/me/wishlist?page=0&size=20" -Headers $wishlistHeaders
Invoke-WebRequest -Method Delete -Uri "$wishlistBase/api/me/wishlist/$wishlistProduct" -Headers $wishlistHeaders
Invoke-WebRequest -Method Delete -Uri "$wishlistBase/api/me/wishlist/$wishlistProduct" -Headers $wishlistHeaders
```

Expected statuses: 201, 200, 200, 204, 204. First and duplicate addedAt match. Compare [HTTP contract](contracts/http.md). Keep credentials/tokens out of committed scripts/logs.

## Acceptance walkthroughs

1. Save, logout through existing refresh-credential logout contract, login again and verify saved membership/original time. Username changes retain immutable ownership.
2. Verify anonymous 401, custom-role-only permission denial 403, existing/future USER grants and grant/revocation observed with the same otherwise-valid token.
3. Use two accounts: isolated lists/deletes; userId query/body 400; absent owned delete 204 regardless of another user's membership.
4. Seed 120 visible/30 hidden memberships with timestamp ties. Traverse sizes 1/20/100 with exact totals, no hidden entries, fixed ordering and full stable pages. Check empty/out-of-range pages and invalid UUID/query/body boundaries.
5. Controlled status fixtures hide/restore original membership/time; hidden removal works; re-add samples a fresh addedAt without a strict-increase guarantee. Advance a controlled clock for deterministic sample assertions and separately verify product UUID tie-breaking for equal timestamps. No-image URL is null; no review/rating fields appear.
6. Verify unique/FK/NOT NULL constraints, dependent parent cleanup without deleting parents, rollback on signing/business failure, serialization after service closure and bounded SQL for 1/100 entries.
7. Required synchronized real additions: independent connections/transactions, one 201 and one 200, one row, same original time. No sequential-test substitute, sleeps or test-level transaction masking.
8. Exercise late-ineligibility safe 409 and rerun existing catalog HTTP/security/query and profile/auth regression tests. Public catalog responses remain unchanged.

See [data model](data-model.md) and [domain integration](contracts/domain-integration.md) for lifecycle, fetch and transaction requirements. These validation steps were not executed during planning.

## Implementation environment observations (2026-10-31)

Java 25.0.4.1 is installed; pom.xml uses Java 25, Boot 4.1.2 and existing Surefire/Failsafe facilities. V10 was the next source migration version. Existing Java/Docker ignore patterns cover generated artifacts and secrets; no new build dependency or plugin is needed.

The Windows wrapper fails before Maven launches (`Cannot index into a null array`). The matching cached Maven 3.9.16 distribution can be invoked directly with an explicit local repository path. Do not change the wrapper or dependency versions to mask this environment issue. Docker CLI is absent from PATH, but Testcontainers can reach the existing Docker Desktop named-pipe engine when the build has normal host access. Tests use disposable PostgreSQL, never the developer database.

A stale target/classes/db/migration/V10__catalog_administrator_role.sql initially collided with the new V10; Maven clean removes generated resources without editing source migrations. Start validation from clean output when switching branches.

## Automated walkthrough evidence (2026-10-31)

The disposable full-service/API checks cover real registration/login, save (201), duplicate replay (200), paginated browse (200), hidden/absent owned removal (204), logout/login persistence and re-addition. The standard fixture traversal covers 120 visible and 30 hidden memberships at sizes 1, 20 and 100. Service checks cover equal timestamp ordering, fresh re-add samples (including equal values), response serialization after service return and the six-statement listing budget. Two-user/permission denial and actual overlapping duplicate requests are included. These checks use the existing Testcontainers environment, not developer data.

Use the cached-Maven commands in the validation record on this machine while the existing Windows wrapper issue remains. A full verify includes unit/MVC discovery, integration discovery and JAR packaging. The optional local-server/browser commands above were not performed; no production services, real AWS or developer database were needed for acceptance.
