# Quickstart Validation: Customer Order Management

The feature is implemented. Actual executed results, including the full regression run and focused permission-expectation rerun, are recorded in [implementation-validation.md](implementation-validation.md). The scenarios below are a reproducible validation guide, not a substitute for those results. Contracts: [HTTP](contracts/api.md), [internal boundaries](contracts/internal-services.md), [data model](data-model.md).

## Prerequisites and isolated setup

- JDK 25 and demo/mvnw.cmd; Docker running for PostgreSQL 17 Testcontainers. Existing Maven dependencies may need network access on first run.
- Existing test profile at demo/src/test/resources/application-test.properties uses jdbc:tc:postgresql:17-alpine:///backend_test, Flyway and ddl-auto=validate. Do not point tests at a shared/local customer dataset.
- OrderFixtures creates committed independent orders and complete snapshot items for two users, all five states and timestamp/amount ties. Reuse fresh existing account/catalog helpers. No demo orders are added to application migrations.
- The additive migration is V23__customer_order_management.sql, following the preserved V22 cart migration. Clean and V22-to-V23 upgrade checks have executed; do not renumber or edit applied migrations.
- Core order tests need no checkout, inventory, payment or shipping service. Mock only the external image-signing boundary for deterministic failure cases. Actual media URL-access tests use isolated LocalStack following existing media IT patterns, never deployed AWS.
- Concurrency IT overrides Hikari pool to >=4 and order lock timeout appropriately; clean committed data after every case. Do not run destructive cleanup against a shared database.

From repository root in PowerShell:

```powershell
Set-Location demo
.\mvnw.cmd -version
docker info
```

Run the feature checks (all classes below exist):

```powershell
.\mvnw.cmd '-Dtest=OrderValidatorTest,OrderConverterTest,OrderServiceImplTest,OrderReadServiceImplTest,OrderControllerTest,OrderRequestReaderTest,CatalogOrderReadServiceImplTest' test
.\mvnw.cmd test-compile failsafe:integration-test failsafe:verify '-Dit.test=OrderRepositoryIT,OrderItemRepositoryIT,OrderMigrationIT,OrderServiceIT,OrderControllerIT,OrderFlowIT,CatalogOrderReadServiceIT'
```

Then run the full existing suite once after focused checks pass:

```powershell
.\mvnw.cmd verify
```

Inspect target/surefire-reports and target/failsafe-reports for actual class discovery, nonzero expected test counts and failures/skips. A successful build with missing classes/tests does not prove acceptance. Record Docker/environment failures separately from test failures; preserve unrelated dirty cart work and do not weaken tests or migrations.

The completed full run discovered 486 unit/MVC and 447 integration cases. Three default-permission exact-set expectations needed the newly approved MANAGE_ORDER value; after updating only those expectations, all 18 cases in RegistrationLoginApiIT, UserServiceIT and UserRepositoryIT passed a targeted Failsafe rerun. The full 19-minute command was not repeated after these test-only corrections; the evidence records this explicitly. Both existing catalog performance benchmarks passed during the full run.

## Validation scenarios and expected results

1. **Migration and permission rollout**: Apply migration to an isolated prior-schema database and a clean database. Existing customer/catalog/cart data remains intact, no orders appear automatically, new tables/constraints exist, existing USER receives MANAGE_ORDER and future USER registration inherits it. Custom role without the permission receives 403. Same valid token follows next-request permission revocation/grant.
2. **Private pages**: Seed 125 owned and 25 foreign orders with all statuses and ties. For every status and supported sort/direction, traverse sizes 1/20/100. Verify exact owned matching set, counts, stable ID tie-break, empty status matches and beyond-last pages. Invalid/blank/repeated status/page/size/sort or unknown query fields fail 400. Verify count executes before content; offset>=totalElements executes count only with accurate requested page/size and filtered metadata, no content/preview/catalog calls. Boundary matrix: empty dataset/offset0; offset=totalElements; offset>totalElements; JPA dispatch below/at Integer.MAX_VALUE; nonempty long dispatch above it. page=2147483647,size=100,totalElements=125 must compute 214748364700 exactly and return items=[],totalItems=125,totalPages=2 without a JPA content query. Synthetic page=1073741824,size=2,totalElements=2147483649 must use long offset2147483648 and totalPages1073741825. Use isolated unit-test synthetic counts/rows for huge nonempty datasets, plus direct custom repository calls on a small PostgreSQL fixture to prove long SQL OFFSET binding and no setFirstResult narrowing; do not create billions of rows. Test the existing totalPages representation safeguard separately when its int range is exceeded.
3. **Preview/detail split**: Prepare an order with five rows and quantities 2/3/1/4/1. itemCount=5, exactly three previews, remainingItemCount=2; itemCount is not total quantity 11. Preview uses historical name/SKU/options/quantity/optional image only. Detail returns all five rows and recipient/full stored amounts. No full address or line prices leak into summary.
4. **Historical integrity**: Change current profile address, product name, SKU/options, price and publication; remove optional image and, in a valid isolated catalog fixture, variant/product. Snapshot names/options/prices/address/totals remain unchanged; missing live refs/images become null; historical order never disappears. Image prepare/sign/close failure still permits full successful list/detail with null affected images. Current hidden product images are not signed.
5. **Access and nondisclosure**: Test list/detail/cancel under anonymous, expired/revoked session, inactive account and missing permission. For each relevant route, compare a valid unknown ID with a foreign ID: both 404 with identical safe outcome. Body/query owner overrides fail validation and do not change ownership. All responses are private; no token, stack trace, SQL or storage key appears.
6. **Cancellation matrix**: PENDING and CONFIRMED each return 204, persist CANCELLED with one timestamp and normalized optional reason, and preserve every item/recipient/amount field. SHIPPING/COMPLETED return 409 unchanged. Already-CANCELLED returns 204 unchanged, including a new valid reason. An eligible first cancellation with current Clock preceding placement returns 409 ORDER_CONFLICT with every field unchanged; Clock equal to placement is valid. A valid CANCELLED repeat is classified before Clock sampling and stays 204 even after the clock moves before placement, preserving the winning timestamp/reason.
7. **Reason validation**: Accept absent body, {}, omitted reason, explicit null and whitespace-only reason string as no reason. Trim nonblank strings; accept exactly 500 code points including supplementary Unicode characters; reject 501. Reject malformed/trailing JSON, top-level null/array/scalar, whitespace-only bytes, unknown/duplicate fields and non-string reason with 400. Nonempty unsupported/missing media type returns 415. Apply the same validation to repeats; invalid repeats never overwrite reason/timestamp.
8. **Atomicity/constraints**: Reject fractional/nonfinite/negative stored money, invalid quantity, status/timestamp mismatch, oversized reason, malformed option maps, empty committed order and merchandise sum mismatch. Valid fixtures can insert parent and children before committing. Verify invalid/deferred commit rolls back the entire operation and all historical fields remain unchanged after rejected mutations.
9. **Synchronized cancellation**: Commit a PENDING fixture, hold its owned row lock on one real connection and start another cancellation on a separate connection. Use bounded coordination/observed lock waiting, not sleeps alone, to prove overlap. Release the first successful cancellation within the normal timeout; both calls succeed, only one timestamp/reason wins, and repeats never replace it. Repeat for CONFIRMED and competing different valid reasons. Use fresh post-commit reads. No worker errors may be discarded.
10. **Timeout**: Hold the target row beyond a deliberately short order.lock-timeout-ms, observe 409 on the competing call and confirm that losing request commits no fields. Release/clean up locks in finally blocks. Separately verify a normal later repeat returns 204; no automatic application retry is introduced.
11. **Query boundaries**: Measure SQL in the order-data read boundary (existing Hibernate statistics/test instrumentation, no new dependency). Count-shortcut list executes exactly one count and no content/preview/catalog query. Nonempty list executes count first then one chosen header path then preview, <=3 order statements regardless of page size and without an automatic second count; preview materialization <=3*pageSize. Detail uses header+items, no per-item lazy loads. Catalog queries are chunked and measured separately; detail with >100 distinct products spans chunks without rejecting history. Validate publication locking/reread prevents newly hidden URLs.
12. **Real user flow and deferred function**: Use real bearer/current-state authentication to list, inspect, cancel, repeat, then read in a later valid session. No client-supplied owner, external-domain setup or admin endpoint is required. Confirm no confirm-receipt/status-setting controller or placeholder is registered and attempted customer completion never changes SHIPPING/COMPLETED state.

## Optional HTTP smoke checks

Use only the isolated implemented OrderControllerIT/OrderFlowIT fixture environment, which supplies a valid bearer token and owned order IDs; do not invent an order-creation API or add manual demo seeds. The variables below are intentionally supplied by that harness/user, not stored credentials:

```powershell
Invoke-RestMethod -Uri "$baseUrl/api/me/orders?status=CONFIRMED&page=0&size=20&sort=placedAt,desc" -Headers @{Authorization="Bearer $accessToken"}
Invoke-RestMethod -Uri "$baseUrl/api/me/orders/$orderId" -Headers @{Authorization="Bearer $accessToken"}
Invoke-WebRequest -Method Post -Uri "$baseUrl/api/me/orders/$orderId/cancel" -Headers @{Authorization="Bearer $accessToken"} -ContentType 'application/json' -Body '{"reason":"Changed my mind"}'
Invoke-WebRequest -Method Post -Uri "$baseUrl/api/me/orders/$orderId/cancel" -Headers @{Authorization="Bearer $accessToken"}
```

Both valid cancellation responses must be 204 with empty content; a later detail read retains the first timestamp/reason. Do not log or commit accessToken. Automated flow tests are the authoritative reproducible setup.

## Handoff evidence

Implementation records focused/full test commands and outcomes, actual Surefire/Failsafe discovery, migration version, PostgreSQL constraint checks, query counts and synchronized-lock evidence. Report any unexecuted/skipped check explicitly. This plan itself changes documentation only and does not claim runtime success.
