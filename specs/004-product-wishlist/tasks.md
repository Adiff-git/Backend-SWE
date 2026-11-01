# Tasks: Personal Product Wishlist

**Input**: Design artifacts in `specs/004-product-wishlist/`.
**Prerequisites**: [engineering constraints](contracts/engineering.md), [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [HTTP contract](contracts/http.md), [domain integration](contracts/domain-integration.md), [quickstart.md](quickstart.md).
**Tests**: Explicitly required by the specification, including simultaneous-add integration coverage. Write story tests first and establish meaningful failing assertions before implementing that behavior; missing symbols may initially prevent compilation, but do not treat compilation failure alone as behavioral coverage.
**Organization**: Three independently verifiable story increments after shared foundations. Tasks were generated unchecked; current completion markers are supported by [implementation-validation.md](implementation-validation.md).

## Format and Path Conventions

Each executable task uses `- [ ] Tnnn [P?] [USn?] Description with exact paths`. Paths are repository-relative. `[P]` means eligible to run alongside the stated sibling tasks once their shared prerequisites are complete; it never overrides dependencies or authorizes concurrent edits to the same file. Constitution XIII governs organization: packages mirror the owning domain/layer; use one responsibility class per component/category (WishlistServiceImplTest versus WishlistServiceIT, WishlistControllerTest versus WishlistControllerIT, WishlistRepositoryIT). Extend these classes across story phases without duplicating assertions/setup. Unit/MVC classes end Test; repository/service/API integration classes end IT. Repository tests use @DataJpaTest, service/API tests @SpringBootTest (API adds MockMvc), and MVC slices @WebMvcTest. No category-based integration/api packages or omnibus cross-component unit class. Methods follow `methodName_shouldExpectedBehavior_whenCondition` or `methodName_shouldExpectedBehavior`.

No new dependencies/plugins, security chains, frontend or unrelated refactoring. Preserve existing README edits. Do not automatically commit, push, deploy or operate on a developer database. Read relevant current source before editing. Technical-contract requirements formerly in spec.md now reside in contracts/engineering.md; the checklist style exceptions are resolved without changing the work plan or approved scope.

## Phase 1: Setup

**Purpose**: Confirm the existing module and reusable verification boundaries; do not initialize another application.

- [X] T001 Verify Java 25/Boot 4.1.2, installed test dependencies and Surefire/Failsafe discovery configuration in demo/pom.xml against specs/004-product-wishlist/plan.md; inspect current migration versions under demo/src/main/resources/db/migration/ and record any version/path drift in specs/004-product-wishlist/quickstart.md without adding dependencies or modifying applied migrations.
- [X] T002 Create reusable isolated wishlist fixture helpers in demo/src/test/java/com/example/demo/fixture/wishlist/WishlistFixtures.java for persistent integration fixtures with generated IDs, controlled timestamps and committed-data cleanup; use JPA/EntityManager for ordinary graphs and JDBC only for justified database-specific/bulk states. Keep simple helpers local; if reusable in-memory factories are needed use demo/src/test/java/com/example/demo/testdata/wishlist/WishlistTestData.java returning fresh objects without database access; reuse demo/src/test/resources/application-test.properties and mock only external signing/storage for full-service/API tests.

## Phase 2: Foundational Prerequisites

**Purpose**: Shared persistence, authorization and prepared catalog summaries. Complete T003–T010 before beginning any story phase.

- [X] T003 Write PostgreSQL Flyway migration/upgrade and mapping tests in demo/src/test/java/com/example/demo/wishlist/repository/WishlistRepositoryIT.java for all required constraints/indexes, physical-parent dependent cleanup and permission rollout; use @DataJpaTest/PostgreSQL Testcontainers for migration, mapping, constraints and repository behavior; include existing USER/custom-role grant rows and preserve unrelated grants. Do not invoke real registration/authentication in this slice; future registered-account acceptance belongs to T021.
- [X] T004 Create demo/src/main/resources/db/migration/V10__create_wishlist_items.sql (use next available new version if T001 finds V10 occupied): enforce "All four stored fields are NOT NULL", "nondeferrable uk_wishlist_items_user_id_product_id UNIQUE(user_id, product_id)", both named FKs with "ON DELETE CASCADE", idx_wishlist_items_user_id_added_at_product_id with "added_at DESC, product_id ASC", and idx_wishlist_items_product_id; insert MANAGE_WISHLIST and grant only USER via role_permissions by names without changing user_roles or old migrations (depends on T003).
- [X] T005 Map the shared association in demo/src/main/java/com/example/demo/wishlist/entity/WishlistItem.java: id Long/BIGINT identity is "Private generated immutable primary key"; user Long reference is "Required existing user; no ORM parent cascades"; product UUID reference is "Required existing product; no ORM parent cascades"; both associations are LAZY ManyToOne; addedAt Instant/TIMESTAMPTZ is "Required immutable UTC absolute timestamp, updatable=false"; use safe getters/protected no-arg constructor without @Data, relationship equality/toString, updatedAt or copied parent data (depends on T004).
- [X] T006 [P] Define prepared-item response in demo/src/main/java/com/example/demo/wishlist/dto/response/WishlistItemResponse.java and pure wrapping converter in demo/src/main/java/com/example/demo/wishlist/converter/WishlistConverter.java using addedAt and existing ProductSummary only; add demo/src/test/java/com/example/demo/wishlist/converter/WishlistConverterTest.java for exact nested allowlist, null primaryImageUrl, timestamp preservation and no rating/review/internal fields.
- [X] T007 [P] Create applicable domain input validator in demo/src/main/java/com/example/demo/wishlist/validator/WishlistValidator.java and tests in demo/src/test/java/com/example/demo/wishlist/validator/WishlistValidatorTest.java for trusted identity, page >=0 and size 1–100/defaults 0/20; add meaningful common-base exceptions in demo/src/main/java/com/example/demo/wishlist/exception/InvalidWishlistRequestException.java and demo/src/main/java/com/example/demo/wishlist/exception/WishlistConflictException.java without duplicating catalog eligibility or adding global advice.
- [X] T008 [P] Write focused new catalog-boundary tests in demo/src/test/java/com/example/demo/catalog/service/ProductCatalogServiceImplTest.java for eligible entity retrieval, missing/DRAFT/ARCHIVED rejection, <=100-ID bounded summary preparation, empty input, final status revalidation, null-image handling, prepared signing failure and pure converter use without loading variants/gallery.
- [X] T009 Extend demo/src/main/java/com/example/demo/catalog/service/ProductCatalogService.java and demo/src/main/java/com/example/demo/catalog/service/ProductCatalogServiceImpl.java with getPublishedProductEntity(UUID) and getProductSummaries(Collection<UUID>) returning fully prepared DTOs keyed by ID; reuse existing category/primary-image batch queries, converter and prepared signer, resolve credentials before ordered UUID FOR SHARE image-product checks, revalidate no-image publication, and retain existing catalog HTTP behavior (depends on T008).
- [X] T010 Add exact GET /api/me/wishlist and POST/DELETE /api/me/wishlist/* MANAGE_WISHLIST rules before denyAll in demo/src/main/java/com/example/demo/auth/config/AuthSecurityConfiguration.java; retain current identity/account/session/authority evaluation, profile/address grants, catalog chain, Bearer transport and CORS/CSRF settings (depends on T004).

**Checkpoint**: New schema/permission and reusable prepared-summary boundary are verified; no existing HTTP contract changed. T006, T007 and T008 are independent of one another after setup. T009 waits for T008; run foundation checks before story work.

## Phase 3: User Story 1 — Save a Product for Later (Priority: P1, MVP)

**Goal**: Persist one whole-product membership and return a saved item, including safe repeated/concurrent additions.
**Independent Test**: Add a published fixture, repeat, logout/login using real auth, and repeat again; expect 201 then 200, one membership and original addedAt. Inspect committed persistence without requiring list/remove endpoints.

### Tests first

- [X] T011 [P] [US1] Write POST contract/security tests in demo/src/test/java/com/example/demo/wishlist/controller/WishlistControllerTest.java using @WebMvcTest/MockMvc: 201/200 one-item response, canonical UUID, bodyless request, invalid/unknown query and supplied JSON/null/{} rejection, 400/401/403/404/409/503/500 ApiError shape, trusted identity delegation and Cache-Control: no-store.
- [X] T012 [P] [US1] Write isolated service tests in demo/src/test/java/com/example/demo/wishlist/service/WishlistServiceImplTest.java for first add, unchanged duplicate time, whole-product reference, missing/current-user identity failure, missing/DRAFT/ARCHIVED or hidden-duplicate 404, missing post-conflict row 409 and summary/signing failure; no Spring context or direct foreign-domain repositories.
- [X] T013 [P] [US1] Write real PostgreSQL atomic insert/lookup tests in demo/src/test/java/com/example/demo/wishlist/repository/WishlistRepositoryIT.java using @DataJpaTest/Testcontainers for one pair only, distinct users, targeted conflict-ignore row counts and a separate subsequent timestamp lookup; assert successful no-op duplicate handling without transaction failure, preserved UNIQUE(user_id, product_id) and no newly introduced pessimistic duplicate-resolution locks; do not substitute a pre-check or timestamp-changing update.
- [X] T014 [P] [US1] Write real-service rollback tests in demo/src/test/java/com/example/demo/wishlist/service/WishlistServiceIT.java for signing/business failure after insertion leaving no committed membership and known FK failure translation after rollback; preserve existing duplicate membership on failed hidden re-add, without test-level transactions hiding real boundaries.
- [X] T015 [P] [US1] Write required deterministic overlapping-add API test in demo/src/test/java/com/example/demo/wishlist/controller/WishlistControllerIT.java using real services/disposable PostgreSQL, independent transactions/connections and synchronized overlap with bounded waits; assert one 201/one 200, same original addedAt, exactly one row and safe errors, with product status stable; avoid sleep coordination/coordinator connection starvation and use test-local pool=4 only if needed.

### Implementation and integration

- [X] T016 [US1] Implement targeted atomic insert-if-absent and scalar owner/product timestamp lookup in demo/src/main/java/com/example/demo/wishlist/repository/WishlistRepository.java using INSERT ... ON CONFLICT DO NOTHING targeted at uk_wishlist_items_user_id_product_id and affected-row count in the same service transaction; duplicate is successful no-op without transaction failure, rollback or new pessimistic locking, and the pair unique constraint remains authoritative; use a minimal custom fragment at demo/src/main/java/com/example/demo/wishlist/repository/WishlistRepositoryImpl.java only if existing native-query facilities cannot express the result safely (depends on T013).
- [X] T017 [US1] Add addProduct contract in demo/src/main/java/com/example/demo/wishlist/service/WishlistService.java and internal created/item result in demo/src/main/java/com/example/demo/wishlist/service/AddWishlistResult.java; keep status-selection metadata out of the public WishlistItemResponse body (depends on T006).
- [X] T018 [US1] Implement addition in demo/src/main/java/com/example/demo/wishlist/service/impl/WishlistServiceImpl.java with service-owned REQUIRED/READ COMMITTED write transaction, injected existing Clock, UserService/getPublishedProductEntity boundaries, T016 insert then separate scalar lookup, one prepared summary and pure mapping; hidden/missing product ->404, absent conflict-result membership ->409, signing failure ->503 and rollback; never query an aborted transaction or use self-invoked transaction/retry logic (depends on T009, T012, T016, T017).
- [X] T019 [US1] Extend demo/src/main/java/com/example/demo/common/exception/ApiErrorFactory.java only as needed for precise post-rollback SQLSTATE 23503 plus fk_wishlist_items_user_id ->401 and fk_wishlist_items_product_id ->404, preserving common ApiError extensions and safe unknown-failure 500; add targeted tests in demo/src/test/java/com/example/demo/common/exception/WishlistPersistenceErrorTest.java, never broad integrity-to-duplicate success (depends on T014, T018).
- [X] T020 [US1] Implement POST /api/me/wishlist/{productId} in demo/src/main/java/com/example/demo/wishlist/controller/WishlistController.java with CurrentUserIdProvider, strict catalog UUID convention, unknown-query/supplied-JSON rejection, 201/200 from created flag, one-item body and no-store; reuse current handlers, no new global advice (depends on T010, T011, T018, T019).
- [X] T021 [US1] Add real-auth save/logout/login persistence and authorization acceptance in demo/src/test/java/com/example/demo/wishlist/controller/WishlistControllerIT.java: use @SpringBootTest and MockMvc with real application services to register a new account through the real registration endpoint and verify it inherits MANAGE_WISHLIST; also verify existing USER grants, custom-role-only 403 despite profile/address grants, same-token grant/revocation, anonymous/revoked/expired/inactive 401, owner injection rejection, two-account separation and missing/hidden product nondisclosure (depends on T020).
- [X] T022 [US1] Extend demo/src/test/java/com/example/demo/wishlist/service/WishlistServiceIT.java with final summary eligibility failure rollback, controlled duplicate/remove interference returning safe 409, parent-deletion failure outcomes and assertions of zero product/user/stock/price/purchase mutations; verify committed state after service returns (depends on T019, T020).
- [X] T023 [US1] Run US1 unit/MVC/repository/service/API/concurrency verification through demo/mvnw.cmd and inspect actual discovery/results in demo/target/surefire-reports/ and demo/target/failsafe-reports/; repair only feature-scoped failures, confirming T015 executed and T014 rollback assertions reached real boundaries (depends on T011–T022).

**Checkpoint**: Save-only MVP is independently demonstrable and verified, including simultaneous duplicates. No claim that listing/removal is complete.

## Phase 4: User Story 2 — Browse My Saved Products (Priority: P1)

**Goal**: Return bounded published-only owned pages with fixed order, accurate stable-fixture totals and safe prepared image access.
**Independent Test**: Seed memberships directly for two users, including 120 visible/30 hidden products and timestamp ties; traverse sizes 1/20/100 without invoking POST or DELETE.

### Tests first

- [X] T024 [P] [US2] Write GET contract/security tests in demo/src/test/java/com/example/demo/wishlist/controller/WishlistControllerTest.java for page 0/size 20 defaults, 1/100 boundaries, exact public metadata, empty/out-of-range pages, duplicate/unknown/empty/text-null/signed/padded/fractional/overflowing pagination 400, unsupported sort/userId rejection, 401/403/no-store and safe 409/503.
- [X] T025 [P] [US2] Write repository visibility/count/order tests in demo/src/test/java/com/example/demo/wishlist/repository/WishlistRepositoryIT.java proving identical owner/PUBLISHED filtering before limit/count, interspersed hidden rows not consuming slots, equal-time product UUID ASC ties, exact totalItems/totalPages and empty/beyond-last pages using PostgreSQL Testcontainers.
- [X] T026 [P] [US2] Write service/lifecycle tests in demo/src/test/java/com/example/demo/wishlist/service/WishlistServiceIT.java for DRAFT/ARCHIVED hiding with retained rows, republishing with original time, zero hidden metadata/new URLs, null primary image, late selected-ID ineligibility ->409 without partial success, signing ->503, no business writes and DTO serialization after service closure (OSIV=false).

### Implementation and integration

- [X] T027 [US2] Add scalar visible page/count queries using Pageable in demo/src/main/java/com/example/demo/wishlist/repository/WishlistRepository.java and projection in demo/src/main/java/com/example/demo/wishlist/repository/WishlistMembershipView.java; match owner/PUBLISHED predicates, apply addedAt DESC/product UUID ASC in the database, and avoid collection fetch joins or post-page visibility filtering (depends on T025 and completion of US1 shared-file edits).
- [X] T028 [US2] Create application-defined page response in demo/src/main/java/com/example/demo/wishlist/dto/response/WishlistPage.java with items/page/size/totalItems/totalPages, zero totals for empty visible wishlist and accurate beyond-last totals; extend pure collection wrapping in demo/src/main/java/com/example/demo/wishlist/converter/WishlistConverter.java and tests in demo/src/test/java/com/example/demo/wishlist/converter/WishlistConverterTest.java to preserve repository order and exact field allowlist (depends on T006).
- [X] T029 [US2] Add listWishlist to demo/src/main/java/com/example/demo/wishlist/service/WishlistService.java and implement in demo/src/main/java/com/example/demo/wishlist/service/impl/WishlistServiceImpl.java: validate owner/page, use T027 projection/count, one bounded catalog summary call, preserve order, fail missing selected summary as safe 409, fully map inside REQUIRED write-capable transaction solely for approved signing safeguards; no writes, refill loop, extra sorts or stronger isolation (depends on T027, T028, T026).
- [X] T030 [US2] Add GET /api/me/wishlist to demo/src/main/java/com/example/demo/wishlist/controller/WishlistController.java with current identity, strict allowlisted canonical pagination, body-free read contract, 200 page DTO and no-store; reuse existing ApiError/security handling (depends on T024, T029).
- [X] T031 [US2] Write feature query-count integration in demo/src/test/java/com/example/demo/wishlist/service/WishlistServiceIT.java following existing CatalogQueryCountIT statistics/fixture conventions; compare 1/100 imaged items with <=6 feature SQL statements excluding auth/fixtures, assert no variant/gallery/unrelated-user fetches and no post-service serialization queries (depends on T030).
- [X] T032 [US2] Add real-service GET traversal acceptance in demo/src/test/java/com/example/demo/wishlist/controller/WishlistControllerIT.java for 120 visible/30 hidden entries at sizes 1/20/100, equal-time order, exact totals, no gaps/duplicates on unchanged data, empty/out-of-range pages, two-user isolation, null image and absence of review/rating/internal fields; use seeded membership, not the add endpoint (depends on T030).
- [X] T033 [US2] Run US2 and US1 regression verification using demo/mvnw.cmd and check discovered WishlistServiceIT/WishlistControllerIT results in demo/target/failsafe-reports/; confirm approved write-capable listing is non-mutating and stable fixtures meet exact pagination without claiming concurrent snapshot guarantees (depends on T024–T032).

**Checkpoint**: Browse can be validated independently from seeded membership. Save plus browse is the first practical customer-facing increment.

## Phase 5: User Story 3 — Remove a Saved Product (Priority: P2)

**Goal**: Delete only the caller's membership, including hidden products, with idempotent 204 and correct later re-add time.
**Independent Test**: Seed visible/hidden memberships for two users, delete as one user, repeat, and inspect membership/parent records without invoking listing; use existing addition only for the re-add acceptance case.

### Tests first

- [X] T034 [P] [US3] Write DELETE contract/security tests in demo/src/test/java/com/example/demo/wishlist/controller/WishlistControllerTest.java for visible/hidden/absent 204 with no body, malformed UUID/unknown-query/supplied-JSON 400, 401/403 and trusted owner isolation; verify no-store and existing ApiError format.
- [X] T035 [P] [US3] Write ownership/hidden-removal behavior tests in demo/src/test/java/com/example/demo/wishlist/service/WishlistServiceImplTest.java with no Spring context, ensuring no public-catalog lookup prerequisite, no parent deletion and missing-entry idempotence.
- [X] T036 [P] [US3] Write real-service removal/rollback and lifecycle acceptance in demo/src/test/java/com/example/demo/wishlist/service/WishlistServiceIT.java for two-user same-product isolation, visible/DRAFT/ARCHIVED removal, absent/foreign-only 204, unchanged parent/other-user rows, failure rollback at real boundaries and re-add with a new ID and fresh timestamp sample after advancing controlled Clock beyond PostgreSQL microsecond precision; also test that an equal previous/current sample is allowed, without requiring strict increase, and retain deterministic product UUID tie ordering.

### Implementation and integration

- [X] T037 [US3] Add owned conditional membership delete to demo/src/main/java/com/example/demo/wishlist/repository/WishlistRepository.java; add removeProduct to demo/src/main/java/com/example/demo/wishlist/service/WishlistService.java and demo/src/main/java/com/example/demo/wishlist/service/impl/WishlistServiceImpl.java using REQUIRED write transaction, zero-row success and no catalog lookup or parent cascades (depends on T034–T036 and prior shared-file edits).
- [X] T038 [US3] Add DELETE /api/me/wishlist/{productId} in demo/src/main/java/com/example/demo/wishlist/controller/WishlistController.java using current identity, strict UUID/query/body validation, 204 without content even for absent membership and no-store (depends on T037).
- [X] T039 [US3] Add real-auth deletion API coverage in demo/src/test/java/com/example/demo/wishlist/controller/WishlistControllerIT.java across visible/hidden/missing/foreign-only membership, two-user isolation, permission changes, anonymous denial and add/remove/re-add ordering integration using the real application services (depends on T038).
- [X] T040 [US3] Run removal and all prior story regressions through demo/mvnw.cmd; inspect demo/target/surefire-reports/ and demo/target/failsafe-reports/ to confirm committed cleanup, parent survival, idempotent responses and re-add time (depends on T034–T039).

**Checkpoint**: All three stories independently pass their acceptance criteria; combined flow is complete.

## Phase 6: Polish and Cross-Cutting Verification

- [X] T041 Review implemented paths under demo/src/main/java/com/example/demo/wishlist/, demo/src/main/java/com/example/demo/catalog/service/ProductCatalogServiceImpl.java and demo/src/main/java/com/example/demo/auth/config/AuthSecurityConfiguration.java against specs/004-product-wishlist/contracts/domain-integration.md: verify acyclic service ownership, pure mapping, LAZY/fetch boundaries, named-constraint handling, no hidden URLs, no SQL/secrets in errors and no new unapproved framework/dependency/locks/retries.
- [X] T042 Run demo/mvnw.cmd test and demo/mvnw.cmd verify from the intended module directory, inspect demo/target/surefire-reports/ and demo/target/failsafe-reports/ for actual wishlist/concurrency and existing catalog/profile/auth/media regression discovery; retain evidence of compilation, failures/skips and existing static-analysis availability without adding plugins or representing unavailable checks as passing.
- [X] T043 Execute automated/disposable and appropriate local walkthroughs in specs/004-product-wishlist/quickstart.md, then update that guide with actual commands/results and environment limitations; preserve independent parent-lifecycle/bucket initialization policies and never use real AWS/developer databases as automated-test substitutes.
- [X] T044 Record final FR-001–FR-017 and SC-001–SC-006 evidence, discovered test counts, concurrency outcome, migration version and any remaining blockers in specs/004-product-wishlist/implementation-validation.md; reconcile implementation status wording in specs/004-product-wishlist/spec.md only with verified outcomes, leave requirements-quality checklist markers reviewer-owned and review final scope using git diff --check.

## Dependencies and Execution Order

```text
Setup T001 -> T002
  -> Foundation T003 -> T004 -> T005; T006 || T007 || T008 -> T009; T004 -> T010
  -> US1 tests T011 || T012 || T013 || T014 || T015
  -> US1 implementation T016 -> T017 -> T018 -> T019 -> T020 -> T021 -> T022 -> T023
  -> US2 tests T024 || T025 || T026
  -> US2 implementation T027 -> T028 -> T029 -> T030 -> T031 -> T032 -> T033
  -> US3 tests T034 || T035 || T036
  -> US3 implementation T037 -> T038 -> T039 -> T040
  -> Polish T041 -> T042 -> T043 -> T044
```

This graph is a conservative executable schedule; inline dependencies are the minimum prerequisites. Foundation tasks T006–T008 may run together after setup, but all foundation work gates story phases. T009 waits for T008; foundation completion includes passing schema/catalog/validator/converter checks. Test-authoring groups depend on agreed contracts/fixtures, not story behavior already working; run them after minimum signatures exist to demonstrate behavioral failures.

US2 and US3 reuse the shared model/summary contracts but are independently testable through seeded membership. The default schedule completes US1 first because later phases extend the same WishlistRepository, WishlistServiceImpl and WishlistController. US3's re-add acceptance integrates with US1; no need to implement US2 to test removal. Do not execute whole story phases concurrently against shared files.

## Parallel Execution Examples

- **US1**: After foundation, author T011 (MVC), T012 (unit), T013 (repository), T014 (transaction integration) and T015 (concurrency) concurrently; each owns different files. Run database tests using isolated fixtures/databases. Implementation follows their stated dependencies.
- **US2**: After US1, author T024, T025 and T026 concurrently in distinct controller/repository/visibility test files; serialize T027–T030 edits of shared implementation. T031 (WishlistServiceIT) and T032 (WishlistControllerIT) can be split across separate files after T030, although the default schedule is sequential.
- **US3**: After prior shared edits, author T034, T035 and T036 concurrently; merge implementation sequentially. T039 begins only after the endpoint exists.

These are execution opportunities, not instructions to spawn agents automatically.

## Implementation Strategy

1. Complete setup/foundations using existing dependencies and conventions.
2. Deliver US1 as a save-only technical MVP: demonstrate returned item and retained committed membership across logout/login without requiring listing. Complete its required concurrency/rollback/security tests before calling that slice verified.
3. Add US2 for a practical save-and-browse customer increment, then US3 for owned cleanup. Validate each independently with seeded fixtures and preserve earlier behavior.
4. Complete cross-cutting regression, discovery and quickstart verification. Stop with concrete evidence and accurately reported environment blockers; no automatic commit/push/deployment.

## Coverage and Generation Validation

| Requirements/outcomes | Primary tasks |
| --- | --- |
| FR-001, SC-001 persistent save | T011–T023 |
| FR-002/003, SC-002 ownership/permission | T003/004/010/021/024/032/034/039 |
| FR-004/005/006, SC-005 eligibility/uniqueness/idempotence | T009/012–T020/034–T040 |
| FR-007/008/009/010, SC-003 visibility/paging/lifecycle | T024–T033 |
| FR-011/016, SC-006 allowlist/fetch/response usability | T006/008/009/026/028–T032 |
| FR-012/013, SC-004 removal/re-add | T034–T040 |
| FR-014/015 constraints/cleanup/rollback/safe errors | T003–T005/014/018/019/022/036 |
| FR-017 compatibility | T009/041/042 |

44 tasks: setup 2, foundation 8, US1 13, US2 10, US3 7, polish 4. All tasks use checkbox/unique sequential ID, appropriate story label and concrete repository paths. Fourteen tasks have explicit [P] markers. No tasks are checked complete by generation. Windows PowerShell setup-tasks.ps1 -Json resolved the active feature/template and all optional design artifacts; it is the equivalent of the Bash helper that previously failed Windows path handling. No .specify/extensions.yml exists, so before_tasks/after_tasks hooks are inapplicable. Only this task document is generated in this step; application checks described above are future work.
