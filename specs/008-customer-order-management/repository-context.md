# Repository Context and Planning Constraints

Inspected 2026-11-17 before drafting. These observations are not implementation or passing test evidence.

## Reviewed evidence

- `.specify/memory/constitution.md` v1.1.0; active template resolved with `.specify/scripts/powershell/resolve-template.ps1`. Sequential feature numbering produces 008. No `.specify/extensions.yml`: before/after hooks absent. Branch stays draft/version-1.
- `specs/004-product-wishlist/spec.md`, `specs/007-shopping-cart/spec.md` and cart engineering contract: private bounded pages, exact VND money, current permissions, feature-local synchronized concurrency coverage. Existing shopping-cart working changes were not edited.
- `demo/src/main/java/com/example/demo/auth/security/CurrentStateJwtConverter.java`, `SpringSecurityCurrentUserIdProvider.java`, `auth/config/AuthSecurityConfiguration.java`: Long subject; current account/session/authority validation; MANAGE_* permission checks and deny-all fallback. Preserve approved current authentication evolution rather than reverting to older baseline assumptions.
- `wishlist/controller/WishlistController.java`, `wishlist/dto/response/WishlistPage.java`: strict single parameters, 0/20 defaults, size bound, no-store and application-owned page fields. Status/sort support is new order-local behavior.
- `catalog/entity/ProductVariant.java`, migrations V1–V3, V4 and V21: UUID catalog identifiers, BIGINT users, stored decimal prices and option maps; catalog/cart deletion rules are not historical-order rules.
- `catalog/dto/response/SellingPrice.java`, cart response/spec: amount/currency representation with exact whole VND. Historical orders must not copy cart current-price recalculation.
- `common/exception/ApiError.java`, `GlobalExceptionHandler.java`: ProblemDetail subclass, application/problem+json, timestamp/message/path/fieldErrors, validation mapping and 401 Bearer challenge.
- `wishlist/service/impl/WishlistServiceImpl.java`, `wishlist/converter/WishlistConverter.java`: service orchestration, cross-domain service access, prepared-data mapping. Catalog image preparation may require write-capable transactions; historical reads cannot depend on public-only summary lookup.
- `cart/controller/CartRequestReader.java`: explicit unknown/duplicate/null/shape validation rather than permissive binding.
- `demo/pom.xml`: Java 25/Spring Boot 4.1.2; existing MVC/JUnit/Mockito/PostgreSQL Testcontainers and Failsafe integration-test/verify bindings. Discovery/execution not verified by running tests in this specification step.
- `demo/src/test/java/com/example/demo/fixture/wishlist/WishlistFixtures.java`, catalog fixtures and responsibility-based domain tests: fresh objects, caller-owned persistent fixture boundaries. Shared config contains clock/OIDC helpers; do not assume a universal PostgreSQL configuration class.

No Backend-SWE production order domain was found.

## ProjectCV reference

Local source: `C:/Users/TrinhThanh/Downloads/ProjectCV/Backend/demo/src/main/java/com/example/demo/order/dto/response/`.

Read OrderSummaryResponse, OrderResponse and OrderItemResponse. Adapt summary/detail separation and historical recipient/item/amount concepts. Do not copy full detail items into summaries, Long catalog/image IDs, shop/checkout/review references, payments, shipments, shipping fees or discounts. ProjectCV does not approve Backend-SWE preview shape.

## Required planning constraints

- Domain-oriented controller, service interface/implementation, repository, validator and converter; services access own repositories and other domains through service interfaces. Validators own transitions; converters map prepared DTO data without hidden queries. Return no entities.
- Lazy associations and explicit list/detail fetch plans; prevent per-order/per-item N+1 and collection-fetch pagination. Verify prepared results remain usable after transaction completion.
- Additive Flyway schema/integrity constraints, no shared migration edits or demo order inserts. Recheck migration numbering at implementation: uncommitted cart V22 exists.
- Instant/TIMESTAMPTZ, service transactions and protected owner/state check-write sequences. Planning chooses concurrency mechanisms; no global retry/idempotency-key policy or stronger isolation is introduced.
- Preserve snapshots on catalog deletion/unpublication; nullable references and deletion rules must preserve history without changing catalog lifecycle or cross-domain cascades. Restrict customer deletion where required; broader retention/anonymization is separate.
- Meaningful tests follow constitution packages/responsibilities: plain validator/converter/service unit tests, MVC binding/security/status slices, PostgreSQL repository/service/API coverage for filtered queries, mappings, constraints and real commit/rollback. Extend existing helpers with domain-specific testdata/fixtures where useful, without empty scaffolding or duplicated CRUD tests.
- Fixtures directly create valid snapshots in all five states; no excluded infrastructure. As approved in Q3, synchronize competing cancellations in independent transactions with bounded coordination and assert allowed results/timestamps.
- No application code, tests, migrations, dependencies/plugins or CI changes are included in this specification step.

## Confirmed scope update (2026-11-17)

Cancellation permits PENDING and CONFIRMED with optional reason. Receipt confirmation is deferred entirely: no endpoint or placeholder; COMPLETED remains a historical fixture/read/filter state. Distinct-row counts, first-three-row preview and synchronized competing-cancellation tests are confirmed. Repeated cancellation is confirmed as 204 success, preserving the original cancellation timestamp and reason; authentication, permission, ownership and request validation still apply. Reason trimming/null/blank handling and 500-code-point bound are documented specification defaults.

## Approved analysis remediation (2026-11-17)

Count matching rows first, calculate long offset, and short-circuit offset>=totalElements to an accurate empty page without content/preview/catalog queries. Nonempty offsets above Integer.MAX_VALUE use the planned order custom repository's bound SQL OFFSET parameter, preserving Pageable input and the HTTP contract without JPA int narrowing. No schema/dependency change is needed. HTTP415 unsupported/missing JSON media type and HTTP409 pre-placement eligible first-cancellation clock failures are explicit; already-CANCELLED repeats bypass Clock sampling and stay204. These are design updates, not observations of implemented behavior or executed tests.
