## Refactor regression verification (2026-10-28)

After upload-only removal, host-side `mvn -B -ntp clean verify` passed: Surefire 26 tests, zero skipped; Failsafe 52 tests, zero skipped. Previous snapshot had Surefire 51 tests with 13 intentionally skipped and Failsafe 64 tests. The drop reflects removed upload test classes and upload-only cases in shared test classes; deleted tests are not counted as passing. ProductListingApiIT (5), ProductDetailApiIT (4), CatalogContractIT (5), CatalogMigrationsIT (14), CatalogPublicReadPerformanceIT (1), CatalogSecurityBaselineTest (5), and S3ProductImageStorageTest public signer checks (2) passed. Flyway validated V1–V3 and Hibernate validated the mapped public-read schema. Public performance rerun: 1,000 published products, page-20 p95 14.417 ms, detail p95 11.059 ms, bounded queries 5 and 4. LocalStack public-read flow ran; this does not establish upload acceptance.

# Current checkout status (2026-10-28 ownership cleanup)

This branch contains the verified public Product Catalog read implementation and a five-minute private S3 GET signer. Product-specific upload implementation and upload-only tests were removed by explicit owner decision. Upload routes remain denied. Generic Media Upload on `origin/feature/user-address` does not implement the product-scoped upload contract. The approved specification and OpenAPI contract still require that work. See [upload handoff](upload-handoff.md). Earlier upload verification records below describe the pre-cleanup commit `62980e03e86018a52731c405a5e5d32987a7096c`, not the current checkout.

# Quickstart: validate Backend Product Catalog after implementation

This guide covers both implemented catalog reads and future secure upload validation. Existing public reads, Flyway catalog migrations, and the S3 storage adapter do not establish a verified JWT/ADMIN identity or complete upload-flow evidence. Use [API contract](contracts/openapi.yaml) and [data model](data-model.md) for exact expected fields and constraints; report each check as executed, pending, or blocked based on its actual test report.

## Product Catalog and Product Image Upload handoff

The Product Catalog backend owner retains public listing/detail, category/variant/image-read projections, PostgreSQL catalog integrity, and five-minute signed public image URLs. Upload-only source and tests have been removed from this branch for teammate ownership; the approved upload feature remains incomplete. The teammate must restore product-specific upload implementation and evidence, including T059 recovery scenarios from the backup commit. T063, T065 and T066 remain open shared gates; upload POST routes remain fail closed.

Before teammate-owned T062 and the shared T065/T066 integration gates, the upload teammate supplies:

- Reachable LocalStack endpoint and AWS region; disposable private test bucket creation/configuration; credentials through environment or the AWS SDK provider chain, with no committed secret values.
- Storage adapter key conventions: `staging/{uploadId}/{randomKey}` and `final/{uploadId}/{randomKey}`; presigned PUT required headers and a signing hostname reachable by the test client.
- Private bucket/public-access and IAM or equivalent policy configuration. Enable presigned signature validation; demonstrate anonymous unsigned object-key denial in an environment that actually enforces it. Document any behavior LocalStack cannot enforce and the alternative enforcement test environment.
- Reproducible setup and execution commands, test report files/results for real PUT, copy, delete and cleanup; signed PUT/GET success and expiry; overwrite race and conditional-copy failure; and a note on relevant differences from deployed AWS S3.

The upload teammate integrates the supplied configuration and evidence into T062 end-to-end upload tests after verified JWT/ADMIN identity is available. The public-read owner may independently record T063 GET contract checks, T065 current build/test discovery, and T066 public listing/detail, integrity, stable-page SC-004 and backend field-matrix SC-005 evidence. The upload teammate supplies T063 POST/security checks, T065 upload/LocalStack/CI inputs, and T066 secure lifecycle, private storage, cleanup and concurrency evidence. The three full task gates remain open until both branches and auth/storage evidence are integrated. Missing evidence does not change the approved privacy or lifecycle rules.

## Prerequisites

- Java 25, Maven, Docker daemon, disposable PostgreSQL/Testcontainers access, and a configured private LocalStack S3 bucket. No production AWS credentials are needed for local tests.
- Approved POM changes for AWS SDK, WebP decoder, Bean Validation, Lombok, optional LocalStack test support, and verified `*IT` discovery; resolve the Boot 4.1.1 versus constitution 4.1.2 discrepancy through the normal dependency approval process.
- For write-flow validation, a separately implemented verified JWT/ADMIN principal contract. Until present, assert that image upload routes deny all callers. Mock-principal tests and inert storage/recovery components may be prepared, but no POST route is activated; public read tests run independently.
- Trusted test fixtures create explicit DRAFT/PUBLISHED/ARCHIVED statuses, one category and at least one valid variant per product. No product administration endpoint is required.
- LocalStack test setup enables S3 presigned signature validation (for example `S3_SKIP_SIGNATURE_VALIDATION=0`), uses disposable credentials, and returns a URL hostname reachable by the test client/browser.

## Build and automated checks

From the repository root, once the planned code/config changes are approved and implemented:

```bash
cd demo
mvn -B -ntp clean verify
```

Inspect the Maven output and test reports to confirm both `*Test` and `*IT` classes actually ran; a green `verify` alone does not prove `*IT` discovery. Current CI uses that command but does not configure Failsafe. Unit and MockMvc tests should use mock storage/principals; repository and service tests should use PostgreSQL Testcontainers; S3-specific integration tests should use LocalStack with private objects and signature checks. For deferred trigger assertions, explicitly commit a real PostgreSQL transaction and clean the committed fixture; `@DataJpaTest` default rollback alone does not test commit-time rejection.

## Public catalog checks

1. Seed PUBLISHED, DRAFT and ARCHIVED products, each with a category and valid variants. Anonymous `GET /api/products` returns 200, defaults to page 0/size 20, counts only PUBLISHED products, and presents stable ID ordering. Repeat requests over unchanged data and assert identical ordered IDs and totals (SC-004). A page beyond the end returns 200 with `items: []` and accurate totals. With no PUBLISHED products, totals are zero.
2. Verify page size 1 and 100; reject 0, 101, negative/nonnumeric/duplicate page or size with 400 ProblemDetail. Do not accept caller sort/filter/search controls.
3. Anonymous `GET /api/products/{id}` returns a PUBLISHED detail with its immediate category, only its variants, whole-VND price and flat option maps. Unknown, DRAFT and ARCHIVED UUIDs return indistinguishable 404; malformed/noncanonical UUID returns 400.
4. Product without images has null `primaryImageUrl` and empty detail `images`. With images, summary URL matches the primary, detail images follow ascending display order, and URLs expire within five minutes. A fresh GET can issue fresh URLs. Race a trusted Product status update against catalog reading: an unpublish commit must prevent any later new URL signing for that product; direct object-key access remains denied.
5. Seed at least 1,000 products and record query counts and p95 latency for 20-item page and detail requests in a named environment. Verify no per-product relationship queries and meet the one-second SC-003 target; if missed, correct the query/service path and rerun.
6. Record a field-by-field backend response review for the approved home listing, product listing and product detail journeys (SC-005). Confirm all approved fields appear without inventory, storage keys, status or internal entity data; this review requires no frontend implementation.

### T064 recorded public-read performance check (2026-10-28)

Run `cd demo && mvn -B -ntp clean verify`; Failsafe discovers `com.example.demo.catalog.storage.CatalogPublicReadPerformanceIT`. The test report is `demo/target/failsafe-reports/TEST-com.example.demo.catalog.storage.CatalogPublicReadPerformanceIT.xml` and its `T064_RESULT` line records the measurements. To repeat only this check, run `cd demo && mvn -B -ntp verify -Dit.test=CatalogPublicReadPerformanceIT`. Both commands require Docker access for PostgreSQL Testcontainers.

- **Environment**: Local Ubuntu Linux x86-64 host, Java 25.0.4.1, Maven 3.8.7, Spring Boot 4.1.2, PostgreSQL 17.11 from `postgres:17-alpine` via Testcontainers, 20 JVM-visible processors. Requests use MockMvc in one JVM with real Spring MVC, Security, JPA, Flyway schema and PostgreSQL. The test uses the actual AWS SDK local presigner with disposable test credentials; it makes no S3 network request and does not verify LocalStack policy enforcement.
- **Dataset**: One category; 1,000 PUBLISHED products plus 20 DRAFT and 20 ARCHIVED products. Each product has two variants with distinct flat option maps and whole-VND prices. Every fourth published product has two images; one has ten images, giving 508 completed image records. All rows are committed before timing and removed afterward. Flyway V1–V3 constraints apply.
- **Method**: After 20 warm-up rounds of both endpoints, measure 100 anonymous MockMvc requests per endpoint with `System.nanoTime()` around each complete HTTP exchange. Listing uses size 20 and rotates pages 0, 24 and 49; detail rotates ten image-bearing product IDs, including the ten-image gallery. The reported p95 is the nearest-rank 95th value of each 100-sample set. Hibernate prepared-statement statistics are cleared per request; a separate 100-item page checks query growth. Startup, fixture insertion, JSON assertions and cleanup are outside the timed interval. Requests run sequentially against an unchanged catalog.
- **Full `clean verify` result**: 20-item listing p95 **15.962 ms**, detail p95 **12.960 ms**. Maximum prepared statements: **5** per 20-item listing, **4** per detail, and **5** for a 100-item page. The 20-item page returns exactly the PostgreSQL UUID order and 1,000 visible products in its total; repeated pages have identical IDs. The measured ten-image detail has two variants and one primary image. Both p95 values are below SC-003's **1,000 ms** threshold, and query counts remain bounded rather than growing with page size.

These results characterize this recorded local environment and test workload. They do not establish production latency or real S3 network performance; rerun in the agreed deployment environment before making operational claims.

### T066 public-read verification record (2026-10-28)

Executed from `demo/`:

```bash
mvn -B -ntp verify -Dit.test=ProductListingApiIT
mvn -B -ntp clean verify
```

The focused PostgreSQL run executed all five `ProductListingApiIT` cases. The final clean build succeeded with Surefire **51 tests, 0 failures, 0 errors, 13 expected future-upload skips** and Failsafe **64 tests, 0 failures, 0 errors, 0 skips**. `ProductListingApiIT` ran 5 cases, `CatalogContractIT` ran 5, and `CatalogPublicReadPerformanceIT` ran 1. PostgreSQL 17.11 Testcontainers started, Flyway validated/applied V1–V3, and the existing security baseline continued to allow anonymous GETs while denying upload POSTs. The recorded T064 sample in this run was listing p95 **16.108 ms**, detail p95 **10.114 ms**, at most **5** statements for listing, **4** for detail, and **5** for a 100-item page.

#### SC-004 stable pagination

`ProductListingApiIT.unchangedMixedCatalogHasStableOrderedIdsAndCountsAcrossEveryPage` commits 23 PUBLISHED, one DRAFT, and one ARCHIVED product to migrated PostgreSQL. With page size 7 it requests all four pages twice, then requests page 4 beyond the end. Both passes return the same IDs in PostgreSQL `ORDER BY id ASC` order, the same `totalItems=23` and `totalPages=4`, no duplicate or missing published IDs, and no hidden IDs. The final page contains two items; page 4 is empty but retains accurate totals. Existing controller and contract tests separately verify defaults 0/20, allowed size boundaries 1/100, and rejection of zero, oversized, negative, nonnumeric, duplicate, sort, and filter parameters. Result: **PASS for the implemented public-read scope**.

#### SC-005 approved backend field matrix

“Home” below means the approved home product presentation consuming the listing response; no separate Home endpoint or frontend behavior is claimed. `CatalogContractIT` checks exact object field sets, types/nullability, canonical UUIDs, hidden-status behavior, signed URL projection, and absence of storage/upload internals against `contracts/openapi.yaml`.

| Approved field | Journey | Source endpoint | Actual response location | Type | Nullable | Automated evidence | Result |
|---|---|---|---|---|---|---|---|
| Product ID | Home, Listing | `GET /api/products` | `items[].id` | canonical UUID string | No | `CatalogContractIT.listProducts_shouldMatchOpenApiContract_forAnonymousPublishedPage` | PASS |
| Product name | Home, Listing | `GET /api/products` | `items[].name` | nonblank string | No | same listing contract case | PASS |
| Category ID | Home, Listing | `GET /api/products` | `items[].category.id` | canonical UUID string | No | same listing contract case | PASS |
| Category name | Home, Listing | `GET /api/products` | `items[].category.name` | nonblank string | No | same listing contract case | PASS |
| Primary image URL | Home, Listing | `GET /api/products` | `items[].primaryImageUrl` | URI string | Yes, when no image | listing contract case; `ProductListingApiIT` | PASS |
| Page items | Listing | `GET /api/products` | `items` | array of summaries | No; empty array allowed | `CatalogContractIT.listProducts_shouldEnforceOpenApiPaginationAndApprovedParameters` | PASS |
| Page index | Listing | `GET /api/products` | `page` | integer, minimum 0 | No | same pagination contract case | PASS |
| Page size | Listing | `GET /api/products` | `size` | integer, 1–100 | No | same pagination contract case | PASS |
| Total items | Listing | `GET /api/products` | `totalItems` | nonnegative integer/int64 | No | stable-pagination case | PASS |
| Total pages | Listing | `GET /api/products` | `totalPages` | nonnegative integer | No | stable-pagination case | PASS |
| Product ID | Detail | `GET /api/products/{id}` | `id` | canonical UUID string | No | `CatalogContractIT.getProduct_shouldMatchOpenApiContract_forPublishedProduct` | PASS |
| Product name | Detail | `GET /api/products/{id}` | `name` | nonblank string | No | same detail contract case | PASS |
| Category ID/name | Detail | `GET /api/products/{id}` | `category.id`, `category.name` | UUID string, nonblank string | No | same detail case; `CatalogRelationshipApiIT` | PASS |
| Primary image URL | Detail | `GET /api/products/{id}` | `primaryImageUrl` | URI string | Yes, when no image | detail contract case; no-image contract case | PASS |
| Description | Detail | `GET /api/products/{id}` | `description` | string | Yes | detail and no-image contract cases | PASS |
| Variant ID | Detail | `GET /api/products/{id}` | `variants[].id` | canonical UUID string | No | detail contract case | PASS |
| Variant SKU | Detail | `GET /api/products/{id}` | `variants[].sku` | nonblank string | No | detail contract case | PASS |
| Variant options | Detail | `GET /api/products/{id}` | `variants[].options` | flat string-to-string object | No; may be empty for one variant | detail contract case; `CatalogRelationshipApiIT` | PASS |
| Selling amount | Detail | `GET /api/products/{id}` | `variants[].sellingPrice.amount` | exact whole-VND integer | No | detail contract case verifies a value beyond JavaScript-safe integer range without fractional serialization | PASS |
| Selling currency | Detail | `GET /api/products/{id}` | `variants[].sellingPrice.currency` | constant string `VND` | No | detail contract case | PASS |
| Image ID | Detail | `GET /api/products/{id}` | `images[].id` | canonical UUID string | No | detail contract case | PASS |
| Signed image URL | Detail | `GET /api/products/{id}` | `images[].url` | private-object signed URI | No for returned images | detail contract case verifies `X-Amz-Expires=300`; `CatalogReadFlowIT` verifies signed retrieval | PASS for URL projection; IAM/policy enforcement remains teammate evidence |
| Image alt text | Detail | `GET /api/products/{id}` | `images[].altText` | nonblank string | No | detail contract case | PASS |
| Image width/height | Detail | `GET /api/products/{id}` | `images[].width`, `images[].height` | positive integers | No | detail contract case | PASS |
| Image display order | Detail | `GET /api/products/{id}` | `images[].displayOrder` | nonnegative integer | No | detail contract case; `ProductDetailApiIT` | PASS |
| Primary indicator | Detail | `GET /api/products/{id}` | `images[].isPrimary` | boolean | No | detail contract case; `ProductDetailApiIT` | PASS |
| Image collection | Detail | `GET /api/products/{id}` | `images` | ordered array, maximum 10 | No; empty array allowed | detail and no-image contract cases | PASS |

The exact-field assertions confirm that public responses omit Product status, inventory quantities, product/variant weight, S3 object and staging keys, upload ownership/claims, credentials, timestamps, and persistence entities. This satisfies the approved backend field coverage for Home, Listing, and Detail. It does not verify a frontend rendering, real AWS IAM enforcement, or teammate-owned upload behavior.

#### Public-read scenario evidence

| Scenario | Evidence | Result / boundary |
|---|---|---|
| Anonymous listing/detail, PUBLISHED-only visibility, invalid/missing IDs, RFC 9457, OpenAPI shapes | `CatalogContractIT`, controller tests, `ProductListingApiIT`, `ProductDetailApiIT` | PASS |
| Category/variant ownership, flat options, exact VND, ordered/primary images | `CatalogRelationshipApiIT`, `ProductDetailApiIT`, `CatalogContractIT` | PASS |
| Signed URL projection, unpublish race, no private metadata | `ProductListingApiIT`, `ProductDetailApiIT`, `CatalogReadFlowIT`, `CatalogContractIT` | PASS for backend projection and existing signed-read test; does not prove AWS IAM policy |
| PostgreSQL schema/integrity/concurrency | `CatalogMigrationsIT`, `CatalogConstraintsIT`, repository integration tests | PASS |
| Bounded queries and SC-003 performance | listing/detail API tests and `CatalogPublicReadPerformanceIT` | PASS in the recorded local environment; not a production SLA |
| SC-004 stable pagination | strengthened `ProductListingApiIT` plus T064 representative dataset | PASS |
| SC-005 backend field coverage | matrix above and `CatalogContractIT` | PASS for backend responses; no frontend implementation required or verified |

The independent public-read portion of T066 is complete. Full T066 remains pending and unchecked until full T063 and T065 pass after teammate-owned upload/auth/storage integration, including T061 evidence for enforced private access, secure upload lifecycle, failure cleanup, and concurrent completion.

## Persistence checks

Use migrated PostgreSQL, not an in-memory database, to confirm required FKs and NOT NULL fields, allowed status values, UUID mappings, JSONB option-map uniqueness regardless of key order, catalog-wide case-insensitive SKU uniqueness, whole-value price rejection, unique image key/order and at-most-one-primary index. Commit transactions containing a Product plus first variant, a Product without variants, a primary switch, and invalid final variant/gallery counts; verify acceptance or rejection at commit. Check that PUBLISHED rows require `published_at < expires_at`; `published_at` is the final database-side eligibility-check time rather than physical commit time. Test the conditional `clock_timestamp()` publication update separately with controlled short test-only deadlines; runtime sessions remain 15 minutes. Race two transactions deleting the final variants or changing gallery count/primary to verify parent-row locking preserves invariants or aborts a conflicting transaction. Clean committed data. Confirm no Inventory schema is added.

## Image upload checks (only after verified ADMIN auth is available)

1. Anonymous prepare/complete → 401 ProblemDetail from the security entry point; CUSTOMER → 403 ProblemDetail from the access-denied handler; another ADMIN attempting completion → 403 ProblemDetail from MVC advice. A valid ADMIN prepares upload for an existing product: PostgreSQL sets `prepared_at` from `clock_timestamp()` and `expires_at` exactly 15 minutes later; the private staging PUT URL expires no later than that session and includes required headers. The ADMIN uploads JPEG/PNG/WebP bytes to private LocalStack storage. The final locked and fenced database-side eligibility check must sample PostgreSQL `clock_timestamp()` strictly before `expires_at`; first completion returns 201 only after the transaction commits. A passing check may commit physically after expiry. A check exactly at or after expiry returns 409 without a new gallery image; starting the request earlier does not change this. Replay of a committed publication after expiry returns 200 with the same image ID.
2. Exercise all approved limits: unsupported type, byte size above 10,485,760, blank alt text, missing object, wrong actual format, invalid dimensions, gallery already containing ten images, occupied display order, and explicit false for a first primary. Each rejects without a new public image.
3. Verify first image becomes primary; a later selected image replaces it atomically; an omitted display order appends. Concurrent completions for one product preserve count, distinct order and exactly one primary. A repeated successful completion after session expiry returns the original image.
4. For PUBLISHED product, retrieve a signed URL and fetch bytes during validity. Unpublished product remains 404 through public catalog, and object-key-only anonymous S3 GET fails. After unpublishing, no fresh URL is issued; a previously issued URL may work only until its five-minute expiry.
5. Verify final validation before, exactly at, and after expiry; completion begun before but validated afterward; a passing validation followed by physical commit after expiry; and rollback after a passing validation. Only successful commit yields one image. Verify concurrent completion of one session: only one fenced claim publishes one image; another caller sees conflict or the same completed image, not a duplicate. Crash after claim/key commit but before copy, after copy but before DB publication, and after DB publication but before response. An expired claim is reclaimable by the same verified preparer only before session expiry with a new attempt key; a live lease cannot extend the final eligibility deadline. A stale worker cannot publish or overwrite the winner. A completed retry after expiry remains idempotent.
6. Inject S3 HEAD/GET/COPY failure and DB publication failure after a durable copy. Confirm no gallery record or public URL appears, retries respect session state, and reconciliation removes expired/failed staging and all unreferenced upload-scoped attempt objects within 24 hours without deleting a referenced image. Race scheduled cleanup against a live claim and completion: it must leave the live claim alone, never assume ADMIN identity or create a ProductImage, and conditionally mark only abandoned work EXPIRED or recorded failed work FAILED. Crash and rerun cleanup with multiple workers; verify fencing, referenced-image protection, and the 24-hour deadline. Test an overwrite attempt on a reusable staging PUT and confirm the final object contains only bytes that were validated.

Record test environment, test class discovery, latency data, and any unavailable dependency when reporting verification. This guide does not initiate implementation or create tasks.

## Draft version 1 integration

See [integration decisions](../draft-version-1.md) for the shared bucket, storage configuration, CORS and deferred product-upload scope that supersede the earlier handoff assumptions.
