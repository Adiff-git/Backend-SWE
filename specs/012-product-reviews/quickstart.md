# Quickstart: Product Review Validation

This guide is for the implementation phase. Review endpoints and tests are implemented; see verification.md for executed commands, tested scope and current results. Use [contracts/product-reviews.md](contracts/product-reviews.md) for the approved API and [data-model.md](data-model.md) for persistence rules. The user approved all proposals, including MANAGE_ORDER, on 2026-11-21; no further policy confirmation is required.

## Prerequisites and local setup

Use Java 25, the checked-in Maven wrapper, Docker Desktop supporting PostgreSQL Testcontainers and the existing local storage flow. Work only with isolated test data. Configure the existing ignored .env.local using .env.example; never paste secrets into documentation or logs. The current repository uses Compose backend port 18081 by default; honor SERVER_PORT if overridden.

From the repository root in PowerShell:

```powershell
java -version
docker version
docker compose --env-file .env.local -f compose.dev.yaml up -d --build --wait
Set-Location demo
./mvnw.cmd -DskipTests compile
```

This compile is a fast development check, not final verification. Compose startup is a local implementation validation action, not required during planning. API flow prerequisites: two active users with the confirmed permission, a PUBLISHED UUID product, owned COMPLETED order/item pairs for one user, separate repeated purchase and quantity>1 fixtures, and another user's order. Use existing fixture builders plus focused review fixtures; do not invent a customer completion endpoint. Test fixture status setup is permitted only in isolated test infrastructure, not a production API workflow. Keep deactivation/unpublication cases in deterministic integration fixtures.

## Test execution checkpoints

The class names below exist in this feature. Verify fresh reports actually discover them; selected commands are checkpoints, not the unrestricted final gate.

```powershell
# From demo/: binding/business checks
./mvnw.cmd '-Dtest=ReviewValidatorTest,ReviewConverterTest,ProductReviewControllerTest,OrderItemReviewControllerTest,ReviewControllerTest' test
# Real persistence and transaction checks (Failsafe)
./mvnw.cmd '-Dit.test=ProductReviewRepositoryIT,ReviewServiceIT,ReviewConcurrencyIT' verify
# API/security/storage integration checkpoint
./mvnw.cmd '-Dit.test=ProductReviewControllerIT,ReviewControllerIT,ReviewFlowIT' verify
# Required final gate, no selection or skip flags
./mvnw.cmd verify
```

Add affected existing regression classes to checkpoint selections from plan.md, using actual class names at implementation time. The targeted IT commands still allow normal unit-test execution. Inspect target/surefire-reports and target/failsafe-reports for nonzero expected discovery, meaningful scenarios and failures; build success alone is insufficient. If Docker/JDK/dependency availability blocks execution, record exact failures as unverified. After a localized fix rerun affected tests first; repeat full verify only when its risk trigger applies.

## Manual API smoke path

Set local fixture IDs and an existing test user's valid Bearer token in your shell without committing them. In PowerShell use task-specific variables, not built-in HOME variables. Example requests assume implementation is complete:

```powershell
$reviewBase = 'http://localhost:18081/api'
$reviewToken = Read-Host 'Local active purchaser Bearer token'
$reviewHeaders = @{ Authorization = "Bearer $reviewToken" }
$reviewProductId = Read-Host 'Fixture product UUID'
$reviewOrderId = Read-Host 'Owned COMPLETED order ID'
$reviewItemId = Read-Host 'Owned order-item ID'

# Public reads: no Authorization header
Invoke-RestMethod "$reviewBase/products/$reviewProductId/reviews?page=0&size=20"
Invoke-RestMethod "$reviewBase/products/$reviewProductId/rating-summary"

# Rating-only review
$reviewCreateBody = @{ rating = 5 } | ConvertTo-Json
$reviewCreated = Invoke-RestMethod "$reviewBase/me/orders/$reviewOrderId/items/$reviewItemId/reviews" -Method Post -Headers $reviewHeaders -ContentType 'application/json' -Body $reviewCreateBody

# Partial edit: omitted images preserve references
$reviewPatchBody = @{ rating = 4; comment = '  Updated experience  ' } | ConvertTo-Json
Invoke-RestMethod "$reviewBase/me/reviews/$($reviewCreated.id)" -Method Patch -Headers $reviewHeaders -ContentType 'application/json' -Body $reviewPatchBody
```

Expected: initial empty page and zero/null summary for a fresh product; create HTTP 201, Location pointing to /api/me/reviews/{id}, verifiedPurchase true; patch HTTP 200 with trimmed comment, stable createdAt and advanced updatedAt. Count increases once on creation and not on edit. Inspect headers using Invoke-WebRequest where needed; successful public reads include Cache-Control: no-store.

For optional images use the existing upload workflow, with a valid local PNG fixture path:

```powershell
$reviewImagePath = Read-Host 'Absolute path to valid local PNG fixture'
$reviewUpload = Invoke-RestMethod "$reviewBase/image-uploads" -Method Post -Headers $reviewHeaders -ContentType 'application/json' -Body '{"contentType":"image/png"}'
Invoke-WebRequest $reviewUpload.uploadUrl -Method Put -ContentType 'image/png' -InFile $reviewImagePath
$reviewCompletedImage = Invoke-RestMethod "$reviewBase/image-uploads/$($reviewUpload.id)/complete" -Method Post -Headers $reviewHeaders
$reviewImagesBody = @{ imageIds = @($reviewCompletedImage.id) } | ConvertTo-Json -Depth 4
Invoke-RestMethod "$reviewBase/me/reviews/$($reviewCreated.id)" -Method Patch -Headers $reviewHeaders -ContentType 'application/json' -Body $reviewImagesBody
```

Expected: only completed owned UUIDs attach; list returns that identifier and a current finalUrl in submitted position. Completion validates bytes through the existing service; invalid image bytes retain existing 422 behavior. Expired presigned URLs are regenerated by reads, not stored as review identity.

## Acceptance matrix to execute with fixtures

1. All six order statuses; cross-user and mismatched order/item; quantity>1 and repeat purchases. Only COMPLETED owned item creation succeeds; duplicate is 409, including simultaneous creates.
2. Rating missing/null/string/fractional/0/6 rejected; 1/5 accepted. Rating-only and rating+images without comment accepted.
3. Comments omitted/null/blank/plain text; normalized lengths 1,999/2,000/2,001 with supplementary characters. Preserve markup as text. Overlong create/edit rejected without partial changes.
4. Image counts 0/1/10/11; order; duplicate/null/malformed/foreign/pending/expired IDs; invalid last attachment. Verify entire persisted state is unchanged after any failed write. No object deletion on detach.
5. PATCH omissions preserve latest values, null comment clears, null rating/images fails, [] clears, {} fails, effective change advances updatedAt and no-op does not. Old reviews stay editable by active owners after unpublication/product deletion.
6. Known averages including HALF_UP tie and zero/null; rating edits keep count; inactive authors remain counted. Separate transactions prove uncommitted writes are absent.
7. Anonymous published reads succeed; unpublished/removed reads are 404. Supplied invalid/inactive token is 401. All inactive author statuses show “Former customer” without original identity/avatar. Assert forbidden private fields are absent recursively.
8. Missing media metadata/storage signing failure yields null URL without failing the list. Missing physical S3 object may only be detected when downloading; simulate separately without requiring per-image HEAD on listing.
9. Barrier-driven concurrent create, same/disjoint-field patch, image reorder, active-state change and product-link nulling. Assert complete serialized results, no duplicate item review, safe 409 on lock conflicts and consistent summaries. Use bounded waits and independent transactions; never sleep to guess scheduling.
10. Fetch-count checks across 1/20/100 review pages with shared and distinct authors/images. Count remains bounded, converters issue zero queries and listing performs zero per-image object existence requests.

Verify retention/FK semantics and new migration upgrade from V26 in isolated PostgreSQL. Clean committed review/image rows before parent fixtures; do not weaken constraints or blanket-delete external objects to make tests pass. No review deletion API is part of validation.
