---

description: "Dependency-ordered implementation tasks for Backend Product Catalog"
---

# Tasks: Backend Product Catalog

**Input**: `specs/001-product-catalog/{spec.md,plan.md,research.md,data-model.md,contracts/openapi.yaml,quickstart.md}` and `.specify/memory/constitution.md`.

**Tests**: Required by the specification and user request. Write each story's behavior tests before its implementation and verify they fail for the missing behavior. Use meaningful JUnit 5, MockMvc, PostgreSQL Testcontainers and LocalStack checks; do not treat a green build as evidence that `*IT` ran.

**Organization**: Shared setup and persistence first, then US1–US4 in spec priority order, followed by cross-story verification. The US4 phase contains S3, secure upload, and recovery work. `[P]` means separate files can be worked on concurrently after their listed prerequisites. `PREPARABLE: AUTH` means isolated tests, DTOs or inert repository support can be completed before external authentication; it does not enable a POST route. `BLOCKED: AUTH` means implementation/activation requires a separately approved authentication implementation supplying a verified stable ADMIN principal ID and authorities.

**Current ownership and status (2026-10-28 cleanup)**: Public-read implementation and its tests remain complete. Upload-only code and tests were removed from this branch by explicit owner decision; the approved upload requirements remain. T006, T014–T015, T017, T019, T045–T050 and T056 are now incomplete because their full acceptance criteria include removed upload work. Their surviving public-read slices are verified; the teammate owns restoring product-specific upload behavior and evidence. T063, T065 and T066 remain open shared integration gates. See `upload-handoff.md` for recovery paths and the previous commit. Historical verification notes below describe the pre-cleanup snapshot, not the current checkout.

## Phase 1: Backend Foundation (Setup)

**Purpose**: Prepare only the dependencies and configuration needed by this backend feature.

- [X] T001 Resolve constitution-required developer approval for the exact Spring Boot 4.1.1 versus target 4.1.2 decision, AWS SDK S3, WebP decoder, Bean Validation, Lombok, LocalStack test support, and Failsafe changes before editing `demo/pom.xml`. Approved by the developer in the 2026-10-27 Phase 1 implementation request, limited to the eight listed dependency/build changes.
- [X] T002 After T001, add only approved runtime/test dependencies and the approved Boot version decision in `demo/pom.xml`; keep existing JPA, Flyway, PostgreSQL, WebMVC, Security, and test dependencies.
- [X] T003 After T002, configure Maven Surefire for `*Test` and Failsafe or an approved equivalent for `*IT` in `demo/pom.xml`; verify both discovery phases with one small test of each naming form. Java 25 Surefire and Failsafe XML reports dated 2026-10-27 show BuildConfigurationTest and BuildConfigurationIT each ran once with zero failures/errors; the Lombok fixture test passed.
- [X] T004 [P] After T002, externalize PostgreSQL connection/Flyway settings and set JPA schema validation without `ddl-auto=create/update` or an active shared profile in `demo/src/main/resources/application.properties`.
- [X] T005 [P] After T002, retain disposable PostgreSQL Testcontainers JDBC settings and add only necessary test profile overrides in `demo/src/test/resources/application-test.properties`.
- [ ] T006 After T005, create reusable valid PUBLISHED/DRAFT/ARCHIVED product, category, variant, image and upload fixtures with generated UUIDs in `demo/src/test/java/com/example/demo/catalog/CatalogFixtures.java`; do not add runtime seed data or an admin API.

**T006 verification status (2026-10-27)**: The current host `clean verify` reports are newer than `CatalogFixtures.java`. Its fresh product graphs pass the no-Spring validator tests, and `CatalogConstraintsIT` commits fixture-created products, variants, images, and uploads on PostgreSQL Testcontainers without failures.

**Checkpoint**: The project compiles with approved dependencies/configuration; no authentication capability is presumed.

---

## Phase 2: Domain Model and Persistence (Foundational)

**Purpose**: Create a PostgreSQL-backed catalog model shared by all stories. This phase does not enable image uploads.

- [X] T007 Create a new Flyway migration under `demo/src/main/resources/db/migration/V1__create_catalog_core.sql` for `categories(id UUID, name TEXT NOT NULL, parent_category_id UUID NULL)`, `products(id UUID, category_id UUID NOT NULL, name TEXT NOT NULL, description TEXT NULL, status VARCHAR NOT NULL CHECK DRAFT/PUBLISHED/ARCHIVED)`, and `product_variants(id UUID, product_id UUID NOT NULL, sku TEXT NOT NULL, option_combination JSONB NOT NULL, selling_price NUMERIC NOT NULL)`; add named FKs, nonblank name/SKU checks, JSON-object and finite whole-đồng price checks, `UNIQUE(product_id, option_combination)`, `UNIQUE lower(sku)`, and status/category/variant indexes. Do not add a product price, per-variant currency, Inventory table, price positivity, or implicit PUBLISHED default.
- [X] T008 After T007, create a new Flyway migration under `demo/src/main/resources/db/migration/V2__create_product_images.sql` for `product_images` and `product_image_uploads` exactly as in `data-model.md`: UUID IDs; required product links; globally unique private object/staging keys; image alt text, verified JPEG/PNG/WebP type, byte size 1–10485760, positive dimensions, nonnegative per-product unique display order and primary flag; upload preparer subject, proposed type/size/alt text, PREPARED/VERIFIED/PUBLISHED/EXPIRED/FAILED state, expiry, nullable current durable key, claim_token, claim_expires_at and final image reference. Add named FKs, paired-claim and PUBLISHED `published_at < expires_at` CHECKs, unique/partial-primary and lease/cleanup indexes; `published_at` records final server-side eligibility-check time, not physical commit time; store no image bytes or signed URLs.
- [X] T009 After T008, add deferred constraint functions/triggers in `demo/src/main/resources/db/migration/V3__enforce_catalog_relationships.sql` that reject a committed product with zero variants, multi-variant empty/duplicate or nonflat/blank option maps, and a nonempty gallery without exactly one primary or with more than ten images. Acquire affected Product parent rows `FOR NO KEY UPDATE`, run a fresh count query after lock acquisition, use ascending UUID lock order at READ COMMITTED, allow parent/first child and primary switch in one transaction, and abort rather than permit a concurrent invalid state. Do not use a deferred trigger to define the upload deadline or claim that it measures physical commit time.

**T007–T009 verification status (2026-10-27)**: The current Failsafe report for `CatalogMigrationsIT` is newer than the test source and records 14 tests with zero failures, errors, or skips, PostgreSQL 17 Testcontainers startup, and successful Flyway application of V1–V3. Tests exercise named constraints, real commits, and concurrent last-variant/gallery changes; the race helper rejects unrelated SQL errors. The report was produced from the developer's host environment, where Docker is accessible.
- [X] T010 [P] After T007, map UUID `Category` with nonblank name and optional LAZY parent category in `demo/src/main/java/com/example/demo/catalog/entity/Category.java`.
- [X] T011 [P] After T007, map UUID `Product` with required LAZY Category, nonblank name, nullable description and explicit DRAFT/PUBLISHED/ARCHIVED string status in `demo/src/main/java/com/example/demo/catalog/entity/Product.java` and `ProductStatus.java`; no product-level selling price.
- [X] T012 [P] After T007, map UUID `ProductVariant` with required LAZY Product, nonblank catalog-wide case-insensitive unique SKU, flat JSONB option map and exact whole-VND `BigDecimal` price in `demo/src/main/java/com/example/demo/catalog/entity/ProductVariant.java`; do not invent weight units.
- [X] T013 [P] After T008, map UUID `ProductImage` with required LAZY Product, globally unique durable key, nonblank alt text, verified type/size/dimensions, unique display order and primary indicator in `demo/src/main/java/com/example/demo/catalog/entity/ProductImage.java`.
- [ ] T014 [P] After T008, map UUID `ProductImageUpload` and string lifecycle enum with required Product, immutable preparer subject, unique staging/current durable keys, nullable claim_token/claim_expires_at pair, proposed metadata, `Instant` timestamps and optional final-image reference in `demo/src/main/java/com/example/demo/catalog/entity/ProductImageUpload.java` and `ProductImageUploadState.java`; no User FK exists in this checkout.

**T010–T014 verification status (2026-10-27)**: The current host Failsafe report is newer than all entity and `CatalogEntityMappingsIT` sources. It records one executed mapping test with zero failures/errors/skips on PostgreSQL 17 Testcontainers, successful Flyway V1–V3, Hibernate startup with `ddl-auto=validate`, and committed entity round trips covering UUIDs, LAZY relationships, JSONB, whole-value `BigDecimal`, string enums, nullable links, and `Instant` timestamps.
- [ ] T015 After T010–T014, add Spring Data repositories and targeted ownership/batch/lock queries in `demo/src/main/java/com/example/demo/catalog/repository/CategoryRepository.java`, `ProductRepository.java`, `ProductVariantRepository.java`, `ProductImageRepository.java`, and `ProductImageUploadRepository.java` in that same directory; use LAZY mappings and no pageable collection fetch join.
- [X] T016 After T015, implement shared catalog-data validation for required category, ≥1 variant, nonblank SKU/option keys/values, one-variant empty-map allowance, distinct per-product maps, exact whole-VND price, and explicit status in `demo/src/main/java/com/example/demo/catalog/validator/CatalogDataValidator.java`; validate trusted imports without creating CRUD endpoints.
- [ ] T017 [P] After T009 and T015, write PostgreSQL Testcontainers `@DataJpaTest` coverage in `demo/src/test/java/com/example/demo/catalog/repository/CatalogConstraintsIT.java` for UUID/FK/not-null/status, case-insensitive SKU, key-order-independent JSONB uniqueness, nonfinite/fractional price rejection, deferred variant minimum/option rules, image key/order/primary/count, valid same-transaction inserts, concurrent last-variant/gallery changes, and the PUBLISHED `published_at < expires_at` CHECK; explicitly end/commit test transactions for deferred checks (for example `TestTransaction`) and clean committed fixtures instead of relying on default rollback. The conditional publication deadline and commit-after-expiry behavior are tested after T056/T057, not attributed to this deferred-trigger test.
- [X] T018 [P] After T016, write no-Spring unit tests for catalog validation in `demo/src/test/java/com/example/demo/catalog/validator/CatalogDataValidatorTest.java`, including zero-variant, multi-variant empty/duplicate maps, blank keys/values, and fractional price.

**T015–T018 verification status (2026-10-27)**: The current host `clean verify` ran all eight `CatalogConstraintsIT` tests on PostgreSQL Testcontainers with zero failures, errors, or skips. It exercised ownership/batch queries, Product and upload locks, commit-time constraints, and concurrent variant/gallery changes. The eight no-Spring `CatalogDataValidatorTest` cases also passed. All five repository sources and both test sources predate their current reports.
- [ ] T019 [P] Create the constitution's common application exception base, catalog-specific not-found/conflict/invalid-image exceptions, and centralized RFC 9457 `ProblemDetail` MVC advice in `demo/src/main/java/com/example/demo/common/exception/ApplicationException.java`, `demo/src/main/java/com/example/demo/common/exception/ApiExceptionHandler.java`, and `demo/src/main/java/com/example/demo/catalog/exception/`; handle service-level wrong-preparer 403 here, but not filter-chain failures; expose no SQL, S3 keys, tokens, or stack traces.
- [X] T020 After T019, configure anonymous catalog GET and fail-closed upload POST matchers plus sanitized RFC 9457 filter-chain `AuthenticationEntryPoint` (401) and `AccessDeniedHandler` (403) in `demo/src/main/java/com/example/demo/common/security/CatalogSecurityConfiguration.java` and `CatalogSecurityProblemHandler.java` in the same directory; do not add JWT issuance, fake ADMIN identity, or a permissive bypass.
- [X] T021 After T020, use `@WebMvcTest`/MockMvc security-context tests in `demo/src/test/java/com/example/demo/catalog/controller/CatalogSecurityBaselineTest.java` to verify anonymous GET is permitted, unauthenticated upload POST is 401 ProblemDetail, and authenticated but denied POST is 403 ProblemDetail before auth integration; test filter-chain handlers rather than relying on MVC advice.

**Checkpoint**: Migrated PostgreSQL enforces catalog integrity. Public reads can begin without the missing authentication feature.

---

## Phase 3: User Story 1 — Browse products (P1, MVP)

**Goal**: Anonymous bounded listing of PUBLISHED product summaries with stable pagination and a nullable, signed primary image URL.

**Independent test**: Seed mixed statuses and images; `GET /api/products` returns only published IDs/counts, defaults 0/20, accepts size 1–100, returns empty/out-of-range pages as 200, rejects bad or duplicate parameters as 400, and uses bounded relationship queries. Concurrent unpublishing cannot result in a newly signed URL after the status update commits.

### Tests first

- [X] T022 [P] [US1] Write failing `@WebMvcTest`/MockMvc contract tests for `GET /api/products` pagination defaults, boundaries, duplicate/nonnumeric parameters, anonymous access and ProblemDetail in `demo/src/test/java/com/example/demo/catalog/controller/ProductListingControllerTest.java`.
- [X] T023 [P] [US1] Write failing PostgreSQL integration tests for published-only fixed-ID paging/counts and empty/out-of-range pages in `demo/src/test/java/com/example/demo/catalog/repository/ProductListingRepositoryIT.java`.

### Implementation

- [X] T024 [P] [US1] Add Lombok response DTO classes `ProductPage`, `ProductSummary`, and `CategorySummary` with only `items/page/size/totalItems/totalPages`, product ID/name, category ID/name and nullable `primaryImageUrl` in `demo/src/main/java/com/example/demo/catalog/dto/response/`.
- [X] T025 [US1] After T023, implement fixed `id ASC` PUBLISHED ID/count paging and batched category/primary-image lookup without N+1 in `demo/src/main/java/com/example/demo/catalog/repository/ProductRepository.java` and `demo/src/main/java/com/example/demo/catalog/repository/ProductImageRepository.java`.
- [X] T026 [P] [US1] Implement a private-object signed-read URL port and AWS S3/LocalStack signer with a maximum five-minute lifetime, complete environment-provided private bucket/region/endpoint/credential configuration and no persisted URLs in `demo/src/main/java/com/example/demo/catalog/storage/ProductImageStorage.java`, `demo/src/main/java/com/example/demo/catalog/storage/S3ProductImageStorage.java`, `demo/src/main/resources/application.properties`, and `demo/src/main/resources/application-local.properties`; allow a fake signer in read tests without enabling upload POSTs.
- [X] T027 [US1] After T024–T026, map fully fetched page data and service-approved nullable primary image URL to DTOs in `demo/src/main/java/com/example/demo/catalog/converter/ProductCatalogConverter.java`; do not query repositories, decide PUBLISHED eligibility, or call the signer from the converter.
- [X] T028 [US1] After T027, add `ProductCatalogService` and `ProductCatalogServiceImpl` listing in `demo/src/main/java/com/example/demo/catalog/service/` with strict zero-based `page`/size 1–100, defaults 0/20 and internal Spring Data `Pageable`; recheck PUBLISHED for all candidates before mapping, then before any signed URL obtain `FOR SHARE` on image-bearing Product rows in ascending UUID order, recheck PUBLISHED under lock, and sign locally while locks are held. Use a short write-capable service transaction for that lock; pure no-image reads may be read-only. Never sign a newly unpublished product or call S3 over the lock.
- [X] T029 [US1] After T028, expose anonymous `GET /api/products` through `ProductCatalogController` with duplicate-parameter detection, canonical DTO output and 400 ProblemDetail on invalid input in `demo/src/main/java/com/example/demo/catalog/controller/ProductCatalogController.java`.
- [X] T030 [US1] After T029, complete `@SpringBootTest`/MockMvc PostgreSQL API coverage in `demo/src/test/java/com/example/demo/catalog/ProductListingApiIT.java` for mixed visibility, signed/null primary URLs, total counts, empty pages, repeated unchanged page IDs/counts and query-count bounds; race a status update against URL issuance and assert no URL is newly issued after the unpublish commit.

**Checkpoint**: Listing is independently usable with no upload workflow or authentication dependency.

---

## Phase 4: User Story 2 — View a product (P1)

**Goal**: Anonymous detail lookup by canonical UUID with immediate category, variants and ordered images, hiding DRAFT/ARCHIVED as 404.

**Independent test**: Seed one published product plus unrelated data; detail includes only its category/variants/images and whole-VND prices. Unknown/unpublished IDs yield 404; malformed/noncanonical IDs yield 400.

### Tests first

- [X] T031 [P] [US2] Write failing `@WebMvcTest`/MockMvc tests for `GET /api/products/{id}` canonical UUID binding, 200/400/404 ProblemDetail and anonymous access in `demo/src/test/java/com/example/demo/catalog/controller/ProductDetailControllerTest.java`.
- [X] T032 [P] [US2] Write failing PostgreSQL tests for published detail ownership, immediate category, variants and ascending image order in `demo/src/test/java/com/example/demo/catalog/repository/ProductDetailRepositoryIT.java`.

### Implementation

- [X] T033 [P] [US2] Add Lombok `ProductDetail`, `VariantSummary`, `SellingPrice`, and `ProductImageSummary` response DTO classes in `demo/src/main/java/com/example/demo/catalog/dto/response/`; expose exact JSON integer amount with currency VND, flat options, image URL/alt/dimensions/order/primary, and no status, weight or storage keys.
- [X] T034 [US2] After T032, add a published-only product/category lookup and bounded variant/image queries in `demo/src/main/java/com/example/demo/catalog/repository/ProductRepository.java`, `ProductVariantRepository.java`, and `ProductImageRepository.java` in that directory; absent and unpublished use one not-found path.
- [X] T035 [US2] After T033–T034, extend mapping of nonempty variants and ordered service-approved image URL values in `demo/src/main/java/com/example/demo/catalog/converter/ProductCatalogConverter.java`; keep URL signing and visibility decisions out of the converter.
- [X] T036 [US2] After T035, add detail retrieval to `ProductCatalogService`/`ProductCatalogServiceImpl` in `demo/src/main/java/com/example/demo/catalog/service/`; reject variantless corrupt records and apply the T028 status-lock/signing protocol to detail images.
- [X] T037 [US2] After T036, add `GET /api/products/{id}` to `demo/src/main/java/com/example/demo/catalog/controller/ProductCatalogController.java` with canonical UUID validation and 400 versus 404 behavior.
- [X] T038 [US2] After T037, complete PostgreSQL/MockMvc detail API tests in `demo/src/test/java/com/example/demo/catalog/ProductDetailApiIT.java` for other-product variant isolation, unpublished IDs, zero images, price format and signed URLs.

**Checkpoint**: Detail is independently usable from persisted data; no upload endpoint is needed.

---

## Phase 5: User Story 3 — See category and variant information (P2)

**Goal**: Prove category, variant, option, price and image relationships are correct across both public responses without extra category or image URL endpoints.

**Independent test**: Seed two categories and multiple products/variants/images; listing and detail show only each product's immediate category and associated records, exactly one primary if images exist, and stable image order.

### Tests first

- [X] T039 [P] [US3] Add failing cross-product relationship/response tests for category ownership, multi-variant option maps, whole-VND price, image order, primary URL and no-image cases in `demo/src/test/java/com/example/demo/catalog/CatalogRelationshipApiIT.java`.
- [X] T040 [P] [US3] Add failing no-Spring converter tests that ensure summary omits variants/status/keys, detail includes each variant/image once, and converters only map service-approved URL values in `demo/src/test/java/com/example/demo/catalog/converter/ProductCatalogConverterTest.java`.

### Implementation and integration

- [X] T041 [US3] After T039–T040, complete bounded batch category/variant/image projections in `demo/src/main/java/com/example/demo/catalog/service/ProductCatalogServiceImpl.java` and mapping-only `demo/src/main/java/com/example/demo/catalog/converter/ProductCatalogConverter.java`; retain the US1/US2 service-level status lock before signing and add no category CRUD, hierarchy navigation, search, filter or caller sort.
- [X] T042 [US3] After T041, verify option maps remain distinct regardless of JSON key order, single-variant `{}` is allowed, and malformed relationships fail at an explicit real PostgreSQL commit in `demo/src/test/java/com/example/demo/catalog/repository/CatalogRelationshipsIT.java`; end test transactions and clean committed fixtures.
- [X] T043 [US3] After T041, verify at most a bounded number of relationship queries for page size 100 and detail in `demo/src/test/java/com/example/demo/catalog/CatalogQueryCountIT.java`; do not use pageable collection fetch joins.
- [X] T044 [US3] After T042–T043, run a complete anonymous listing-then-detail journey with signed image retrieval against private LocalStack objects in `demo/src/test/java/com/example/demo/catalog/CatalogReadFlowIT.java`; use a browser-reachable presigned host and do not require upload auth.

**Checkpoint**: All three public-read stories work independently of secure image upload activation.

---

## Phase 6: User Story 4 — Add product images (P2)

**Goal**: Stage private JPEG/PNG/WebP uploads, validate actual bytes and ownership, atomically publish metadata, and clean up failures. S3 adapter/validator work can proceed before auth; ADMIN route activation is blocked by external auth.

**Independent test**: With a verified ADMIN identity, prepare → PUT to staging → complete gives one image (201), replay gives the same image (200), and a published product read yields a five-minute signed URL. Invalid, expired, unauthorized or failed attempts produce no public image.

**Pending ownership**: The Product Image Upload teammate owns reopened T006/T014/T015/T017/T019 upload portions, T045–T062, and their verification. T052, T054–T055, T057–T058 and T062 remain blocked by verified JWT/ADMIN integration from the separate authentication owner; preparable tasks still do not activate POST routes. T061 retains its LocalStack-specific verification gate. T056 repository support was removed and is reopened for the upload teammate.

### S3 integration and content validation (no upload route activation)

- [ ] T045 [P] [US4] Write failing no-Spring image-validator tests for JPEG/PNG/WebP actual bytes, claimed-versus-actual type, 10,485,760-byte maximum, positive dimensions, blank alt text and malformed/decompression-bomb inputs in `demo/src/test/java/com/example/demo/catalog/validator/ProductImageContentValidatorTest.java`.
- [ ] T046 [P] [US4] Write failing mocked-storage tests for private staging/final key separation, 15-minute PUT expiry, conditional copy, final-byte digest verification, and five-minute signed GET in `demo/src/test/java/com/example/demo/catalog/storage/S3ProductImageStorageTest.java`.
- [ ] T047 [US4] After T045, implement bounded actual-byte validation with approved WebP decoder and JPEG/PNG readers in `demo/src/main/java/com/example/demo/catalog/validator/ProductImageContentValidator.java`; never trust filename or declared MIME alone.
- [ ] T048 [US4] After T046, complete S3 storage adapter support for unique private staging/final keys, presigned PUT required headers, HEAD/GET, conditional copy by source version/ETag, digest-checked final verification and safe delete in `demo/src/main/java/com/example/demo/catalog/storage/S3ProductImageStorage.java`; no S3 call while holding a Product DB lock.
- [ ] T049 [US4] After T048, add upload-ID-scoped private staging/final prefix listing and safe deletion of old attempt keys to `demo/src/main/java/com/example/demo/catalog/storage/S3ProductImageStorage.java` for recovery; use the bucket/region/endpoint/credential configuration already established by T026 and do not edit the same configuration again.

### Secure upload preparation and completion (tests preparable; route activation BLOCKED: AUTH)

- [ ] T050 [P] [US4] PREPARABLE: AUTH — write failing `@WebMvcTest`/MockMvc tests in `demo/src/test/java/com/example/demo/catalog/controller/ProductImageUploadControllerTest.java` using Spring Security mock principals for filter-chain anonymous 401 ProblemDetail, CUSTOMER 403 ProblemDetail, ADMIN preparation, different-ADMIN service-level 403, and unchanged public GET; mock identity is test-only and no POST route becomes active.
  - Test authoring is complete; the ADMIN success and different-ADMIN service rejection cases remain disabled pending the production actor, service, and controller. The runnable cases verify fail-closed routing only.
- [ ] T051 [P] [US4] PREPARABLE: AUTH — write failing no-Spring service tests in `demo/src/test/java/com/example/demo/catalog/service/ProductImageUploadServiceTest.java` for prepare limits/ownership, final eligibility before expiry, exactly at expiry and after expiry, request begun before expiry but validated afterward, valid pre-expiry validation followed by commit after expiry, rollback after a passing check, first attempt after expiry, committed-result replay after expiry, missing/invalid staged object, finite claim lease, token-owned renewal and same-preparer pre-expiry reclaim, stale worker fencing, count/order/primary collisions, S3/DB failures and no duplicate image; stub only the repository's database-side eligibility outcome and a verified actor port, never replace production PostgreSQL time with an application clock.
  - Pending: T056 now provides the conditional database eligibility method, but no production upload service or verified actor port exists yet. Keep this task unchecked until its test subject and permitted stubs can be wired; do not represent a scenario list as executed verification.
- [ ] T052 [US4] BLOCKED: AUTH — after the separately approved authentication capability exists, integrate its verified immutable principal ID and ADMIN/CUSTOMER authorities into `demo/src/main/java/com/example/demo/common/security/CatalogSecurityConfiguration.java` and narrow `demo/src/main/java/com/example/demo/catalog/service/CatalogUploadActor.java`; preserve T020 filter-chain AuthenticationEntryPoint/AccessDeniedHandler ProblemDetail 401/403, public GET access, ADMIN-only POST and same-preparer service check; no client ID/test header proves identity and no JWT issuance is added.
- [ ] T053 [P] [US4] PREPARABLE: AUTH — after T050–T051, add Lombok/Jakarta Validation DTOs `demo/src/main/java/com/example/demo/catalog/dto/request/PrepareImageUploadRequest.java`, `CompleteImageUploadRequest.java`, `demo/src/main/java/com/example/demo/catalog/dto/response/PreparedImageUpload.java`, and `CompletedImageUpload.java` for proposed type JPEG/PNG/WebP, proposed size 1–10485760, nonblank alt text, upload URL/headers/expiry and optional completion `isPrimary`/`displayOrder`; omitted values use documented defaults, explicit null is invalid, and first-image explicit false is 400. These DTOs do not activate upload routes.
- [ ] T054 [US4] BLOCKED: AUTH — after T052–T053, implement `ProductImageUploadService`/`ProductImageUploadServiceImpl` preparation: existing product, ADMIN subject from verified principal, current count <10, unique private staging key, PREPARED row whose `prepared_at` is one PostgreSQL `clock_timestamp()` sample and `expires_at = prepared_at + 15 minutes`, presigned PUT URL expiring no later than that session, and required headers in `demo/src/main/java/com/example/demo/catalog/service/`; do not activate a permissive fallback.
- [ ] T055 [US4] BLOCKED: AUTH — after T054, expose `POST /api/products/{id}/images/uploads` with 201 and 400/404/409/503 MVC ProblemDetail mappings in `demo/src/main/java/com/example/demo/catalog/controller/ProductImageUploadController.java`; 401/403 from Spring Security use T020/T052 filter handlers, while wrong-preparer 403 uses MVC advice.
- [ ] T056 [US4] PREPARABLE: AUTH — after T014–T015 and T048, add only persistence operations in `demo/src/main/java/com/example/demo/catalog/repository/ProductImageUploadRepository.java`, `ProductRepository.java`, and `ProductImageRepository.java` in that directory: conditional claim/renew/reclaim by token and finite lease, persist VERIFIED plus new upload-scoped attempt key, lock Product `FOR NO KEY UPDATE` then upload for publication, count/order/primary queries, and a fenced PUBLISHED update that samples PostgreSQL `clock_timestamp()` once as `published_at` and succeeds only when that sample is strictly before both `expires_at` and `claim_expires_at`; also provide conditional terminal-state update for cleanup. Repositories do not validate bytes, call S3, choose business outcomes, own service transaction boundaries, or activate POST routes.
- [ ] T057 [US4] BLOCKED: AUTH — after T009, T047–T048, T051, T054 and T056, implement completion orchestration in `demo/src/main/java/com/example/demo/catalog/service/ProductImageUploadServiceImpl.java`: verify same preparer/product/session and replay/expiry using database-side outcomes; validate staged bytes; in a short committed service transaction claim VERIFIED with fencing token, finite lease and distinct durable attempt key before S3 copy; conditionally copy validated source and verify final digest outside DB locks. In a second service transaction lock Product then upload, recheck token/lease and gallery limits/order/primary, insert/flush ProductImage and any primary changes, then call the T056 conditional PUBLISHED update as the final business write. Zero rows rolls back the whole transaction, including image changes; a passing final check publishes only after successful commit, which may physically occur after expiry. Permit token-owned renewal and same-preparer reclaim only before expiry; replay any successfully committed publication after expiry; stale workers cannot publish, and no S3 call is inside a DB transaction.
- [ ] T058 [US4] BLOCKED: AUTH — after T057, expose `POST /api/products/{id}/images/uploads/{uploadId}/complete` with first 201/replay 200 and 400/404/409/422/503 MVC ProblemDetail mappings in `demo/src/main/java/com/example/demo/catalog/controller/ProductImageUploadController.java`; filter-chain 401/403 and service-level wrong-preparer 403 follow T020/T052/T019.

### Recovery and cleanup

- [ ] T059 [P] [US4] Write failing recovery tests for expired/FAILED staging, live versus expired claims, concurrent cleanup/completion on one upload, VERIFIED retry by the same preparer before session expiry, crash after claim/key commit but before S3 copy, copy before DB failure, worker crash and repeat cleanup, stale worker attempting late publication, multiple cleanup workers, worker cannot impersonate ADMIN or publish, 24-hour deadline and no deletion of referenced objects in `demo/src/test/java/com/example/demo/catalog/service/ProductImageRecoveryServiceTest.java`.
  - Preparation status: eleven scenario assertions are compiled but explicitly disabled until T060 supplies the real recovery service and a test driver backed by it; no fake worker is used. Two executable PostgreSQL repository tests cover terminal-update rollback and cleanup racing a publication that commits after session expiry. T059 remains unchecked until the service scenarios execute and pass.
- [ ] T060 [US4] After T049, T056 and T059, implement retry-safe cleanup-only scheduled reconciliation in `demo/src/main/java/com/example/demo/catalog/service/ProductImageRecoveryService.java` and `ProductImageRecoveryServiceImpl.java` in that directory: detect expired sessions/claims; in a short service transaction lock and recheck upload state/token/lease and server-side time, conditionally mark abandoned work EXPIRED or recorded failed work FAILED without disturbing a live valid claim or reversing PUBLISHED. Never impersonate ADMIN, reclaim for publication, invoke completion, or create ProductImage. Outside DB transactions and only after terminal state commits, scan upload-ID-scoped private staging/final prefixes, recheck committed image references before deletion, and safely retry after crashes or competing workers; for PUBLISHED uploads delete only old unreferenced attempt keys. Finish orphan cleanup within 24 hours.
- [ ] T061 [US4] EXTERNAL: LOCALSTACK — teammate-owned, pending teammate verification. After T048 and T060, verify AWS-compatible private-object behavior, signed PUT/GET expiration with LocalStack signature validation enabled, overwrite race and conditional-copy failure in `demo/src/test/java/com/example/demo/catalog/storage/ProductImageStorageIT.java`. The teammate supplies LocalStack infrastructure/configuration, real PUT/copy/delete/cleanup checks, an environment that enforces private object access and relevant policy, test setup and passing evidence. Backend integration accepts T061 only after its tests run against the agreed storage adapter and the evidence is reviewed; do not mark complete on mock-only results.
- [ ] T062 [US4] BLOCKED: AUTH; INTEGRATION: LOCALSTACK — upload-teammate-owned after T058–T061 and T061 evidence is merged/reviewed. Run PostgreSQL + LocalStack `@SpringBootTest`/MockMvc flow tests in `demo/src/test/java/com/example/demo/catalog/ProductImageUploadFlowIT.java` for filter-chain 401/403 ProblemDetail, wrong-preparer 403, final database-side `clock_timestamp()` validation strictly before, exactly at and after expiry, completion started before but validated afterward, a passing pre-expiry check whose transaction commits after expiry, rollback after a passing check, concurrent completion attempts, same-image replay after expiry, first attempt after expiry failure, expired claims and concurrent cleanup/completion, worker crash/retry and no unauthorized worker publication, missing/invalid objects, DRAFT/ARCHIVED privacy, direct object-key anonymous denial, previously issued five-minute URL after unpublish with no new URL, max ten images, primary/order races, stale claim/worker fencing, S3/DB crash recovery and no public incomplete upload.

**Checkpoint**: Mark US4 complete only when verified authentication is integrated and the write routes pass the real security and storage tests. Before then, keep them fail closed.

---

## Phase 7: Testing, Verification and Cross-Cutting Polish

**Purpose**: Verify the full approved contract and repository gates; do not expand feature scope.

- [ ] T063 After T030, T038, T044 and T062, compare the four implemented routes, DTO schemas, MVC and filter-chain ProblemDetail status/error mappings, canonical UUIDs and omitted/null completion semantics with `specs/001-product-catalog/contracts/openapi.yaml` using `demo/src/test/java/com/example/demo/catalog/CatalogContractIT.java`; document only approved contract corrections.
  - **Split ownership**: Public-read owner can independently prepare and verify the two GET routes, public DTOs, canonical UUIDs, anonymous access, 400/404 ProblemDetail and image URL projection. Upload teammate owns the two POST routes, upload DTOs, 401/403/409/422/503 paths and omitted/null completion semantics after T062. The single four-route contract gate stays unchecked until both slices are integrated and checked together.
  - **Public GET verification (2026-10-28)**: `CatalogContractIT` covers both anonymous GET routes against PostgreSQL, including exact response field sets/types/nullability, canonical UUIDs, pagination/default/boundary/duplicate/unapproved parameters, category/variant/VND/image projections, signed five-minute image URLs, PUBLISHED-only visibility, 400/404 RFC 9457 responses and sensitive-field exclusion. The GET slice required one implementation correction: MVC `ProblemDetail` now emits the contract-required explicit `type: about:blank`. The full task remains unchecked pending the upload teammate's POST slice and T062 integration.
- [X] T064 [P] After T043, measure p95 listing/detail latency using ≥1,000 catalog products, including a 20-item page, in a recorded environment and write the reproducible result to `specs/001-product-catalog/quickstart.md`; if the one-second SC-003 target or bounded query count is missed, optimize the relevant catalog query/service within the approved scope and rerun before marking this task complete.
- [ ] T065 INTEGRATION: LOCALSTACK — shared final gate after T003 and all implemented tests, once upload teammate T061 environment/instructions are available: run `mvn -B -ntp clean verify` from `demo/`, inspect Surefire/Failsafe reports for actual `*Test` and `*IT` execution, and align `.github/workflows/test.yml` with approved Docker/LocalStack needs; do not claim unavailable checks passed. Coordinate CI environment inputs with the upload teammate; do not count absent LocalStack tests as passing.
  - **Split ownership**: Public-read owner can run and record current build, Flyway/PostgreSQL, GET and query/performance checks, including actual Surefire/Failsafe discovery. Upload teammate supplies the upload/LocalStack tests, Docker/LocalStack CI inputs and security evidence. CI alignment and the final all-tests result are shared integration work after both branches and authentication are available; the task remains unchecked until that gate passes.
- [ ] T066 INTEGRATION: LOCALSTACK — shared final gate after T063–T065 and upload teammate T061 evidence is reviewed: execute and record the backend-only scenarios in `specs/001-product-catalog/quickstart.md`: public listing/detail, integrity constraints, secure image lifecycle, private signed access, failure cleanup and concurrent completion; repeat unchanged-page requests and assert identical IDs/counts for SC-004, complete a field-by-field home/list/detail journey matrix for SC-005 without frontend code, and report external auth/infrastructure blockers explicitly.
  - **Split ownership**: Public-read owner can independently document listing/detail, catalog integrity, stable unchanged-page IDs/counts (SC-004), and the home/list/detail backend field matrix (SC-005). Upload teammate verifies secure image lifecycle, private object access, failure cleanup and concurrent completion. The complete quickstart record and task checkbox wait for T063–T065 and integrated upload/auth/storage evidence; neither owner may claim the other's unrun scenarios passed.
  - **Public-read verification (2026-10-28)**: The public section of `quickstart.md` records the executed clean build, public listing/detail and integrity evidence, SC-004's repeated four-page PostgreSQL check over 23 visible plus two hidden products, bounded-query/performance results, and SC-005's field-by-field Home/Listing/Detail backend matrix. This slice passed, but T066 remains unchecked pending full T063/T065 and teammate-owned T061/T062 upload, auth, privacy, cleanup and concurrency evidence.

**Checkpoint**: All four stories satisfy their acceptance criteria with evidence; no frontend, Inventory, search/filter/sort, catalog CRUD or unrelated refactoring is added.

---

## Dependencies & Execution Order

### Phase and story graph

```text
T001 approval → T002–T006 setup → T007–T021 foundation
                                     ├→ US1 T022–T030 (public listing MVP)
                                     └→ US2 T031–T038 (detail; shares catalog read service)
US1 + US2 → US3 T039–T044 (relationship/image-read verification)
Upload teammate restores T045–T050 and T056 → T051–T055, T057–T062
External verified auth → upload teammate T052, T054–T055, T057–T058, T062 [BLOCKED: AUTH]
T049 + T056 → upload teammate T059–T060 recovery [no route activation]
T060 + LocalStack environment/evidence → upload teammate T061
Secure work + recovery + T061 → upload teammate T062 [BLOCKED: AUTH]
Public reads + T062 → shared T063/T065/T066 final verification; T064 is completed
```

- **Approval gate**: T002/T003 cannot edit `demo/pom.xml` until T001 records constitution-required approval. The same applies to any added build plugin or test dependency.
- **Authentication gate**: T050 and T056 are reopened; upload-teammate-owned T051 and T053 remain PREPARABLE: AUTH. T052, T054–T055, T057–T058 and T062 remain BLOCKED: AUTH until the separate authentication owner supplies verified Bearer JWT, stable principal ID and trusted ADMIN/CUSTOMER authorities. The upload teammate integrates that contract without implementing login or JWT issuance here. No route is activated by preparable tasks.
- **Read independence**: US1–US3 do not depend on T050–T062. T026 establishes all read-signing S3 configuration needed by T044; upload POSTs remain fail closed. US2 may reuse US1 controller/service files, so their edits are sequential even though their tests can be prepared in parallel.
- **Storage order**: Staging/final adapter and validation precede service publication. The service commits fenced claim/attempt key before S3 copy, verifies final bytes, then commits image plus PUBLISHED state; recovery precedes end-to-end claims. Product rows are locked in UUID order before upload rows when both are needed.
- **Upload handoff**: The upload teammate owns pending T051–T055 and T057–T062, including recovery scenarios retrievable from the backup commit and T061's LocalStack infrastructure/tests. T061 still follows T048 and T060. Shared final T063/T065/T066 consume the teammate's merged upload, security and storage evidence. LocalStack evidence must include real PUT/copy/delete/cleanup, signed PUT/GET expiration, overwrite/conditional-copy behavior and private bucket/policy denial in an enforcing environment. Existing T044 read integration remains completed; its result alone is not T061 evidence.
- **Independently available public-read work**: T063's two-GET contract slice, T065's current public-read build/discovery evidence, and T066's public listing/detail, integrity, SC-004 and SC-005 records can be prepared without JWT/ADMIN or new LocalStack work. Their complete task checkboxes still depend on the four-route or all-tests integration gates. T064 performance measurement is already completed. No remaining upload task is assigned to the public-read owner.
- **Final gate**: T063/T066 for the full feature await T062. Public read verification and T064 performance can finish earlier.

### Parallel opportunities

- After T002, T004 and T005 touch different configuration files. After migrations exist, T010–T014 map separate entity files; T017/T018/T019 touch separate test/exception files.
- In US1, T022/T023 can be written together; T024 and T026 touch separate DTO/storage files. In US2, T031/T032 and T033 can proceed in separate files. In US3, T039/T040 can be prepared together.
- In US4, T045–T050/T056 are reopened for the upload teammate. The upload teammate can prepare T051, T053 and T059–T061 without activating upload routes where their stated prerequisites permit; the public-read owner can prepare the public slices of T063/T065/T066 while secure upload is blocked.
- Shared files `demo/pom.xml`, `ProductCatalogController.java`, `ProductCatalogServiceImpl.java`, `ProductCatalogConverter.java`, `S3ProductImageStorage.java`, and `ProductImageUploadController.java` must be edited serially across their tasks.

### Parallel execution examples by story

- **US1**: Write T022 controller contract tests and T023 paging repository tests concurrently; implement T024 DTOs and T026 read signer concurrently after prerequisites, then T025/T027–T030 in dependency order.
- **US2**: Write T031 controller and T032 repository tests concurrently; create T033 DTOs while T034 queries are developed, then T035–T038.
- **US3**: Write T039 relationship API and T040 converter tests concurrently; then T041 and independent T042/T043 checks, followed by T044.
- **US4**: T045–T050 and T056 are reopened. The upload teammate takes T051–T062, recovering T059's prepared tests from the backup commit; T053 follows T051, T060 follows T059 test preparation and enables/verifies those scenarios before either task is marked complete, and T061 follows T060. Resume auth-blocked tasks only after the separate authentication owner provides verified identity; T062 also waits for T061 evidence. Public-read owner works only on the separable public portions of T063/T065/T066.

## Implementation Strategy

1. Complete approval, setup and foundational schema. Verify migrations on real PostgreSQL before public endpoints.
2. Deliver US1 listing as the first backend MVP, then US2 detail. Use fake signed-read URL provider in isolated tests and the private S3 signer for integrated reads; no upload authorization dependency.
3. Complete US3 relationship and image-read evidence, including bounded queries and LocalStack private-object access.
4. Build the S3 adapter/validator, mock-principal tests, DTOs, inert repository support and recovery components that do not require caller identity. Keep write routes denied.
5. When separately approved JWT/ADMIN integration exists, complete US4 ownership, preparation, completion and security tests. Finish contract, performance, Maven/CI discovery and quickstart verification.

## Notes

- Task paths are relative to repository root. Files under `demo/src/main/java/com/example/demo/catalog/` are proposed, not existing implementation.
- New SQL must use plural snake_case, named constraints, uppercase SQL keywords/types and `TIMESTAMPTZ`; JPA entities never become API DTOs.
- The reference combined spec is absent in this checkout. Preserve `specs/001-product-catalog/spec.md` as the requirement source; do not create product/category CRUD, Inventory persistence, frontend work, or search/filter/sort tasks.
