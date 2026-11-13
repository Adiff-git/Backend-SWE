# T018 — Catalog listing workflow integration

Status: **PASS for T018 only**,2026-11-09. Branch features/SearchAndFilter;existing dirty tree preserved.

## Prerequisites and scope

T012,T007,T016,T017,T006 are checked complete with recorded execution evidence. Their evidence SHA256 values are captured in [listing-integration-results.json](listing-integration-results.json). Parser,normalizer,repository,converter/DTO and migrations remain byte-identical to task-entry snapshot. T006 authorizes the baseline HTTP integration;T003 registry and T004 identifier approval remain blocked and are not bypassed.

Checklist markers remain requirements16/16 and reviewer design0/40 unchanged. The current explicit user instruction authorizes only T018 despite those known pending gates. Pre/post extension hook file is absent. Ignore patterns already cover build/local files;no ignore changes made.

## Behavior and architecture

- Retain listProducts(page,size),add ProductSearchRequest overload. Both public service entry points have write-capable REQUIRED READ_COMMITTED transactions;the legacy entry already owns its transaction before internal delegation (no reliance on proxy self-invocation).
- Controller reuses existing strict parameter parser;service activates existing Bean Validation and normalization,builds immutable typed criteria and calls the existing repository fragment. Raw spec JSON without parsed groups is rejected. All supplied criteria are forwarded;the unchanged baseline repository rejects unsupported nonempty keyword/category/brand/price/spec/sort rather than ignoring them. This increment does not claim full feature support.
- One existing candidate statement supplies ordered Product IDs,selected variant/amount and snapshot total. Category and image loads stay batched;DTO order follows candidates independently from fetch order. Empty later pages retain totals;totalPages uses ceiling division without long-addition overflow.
- Existing converter maps selected metadata into listing-only displayPrice(VND,exact BigInteger) and representativeVariantId. Both null for the legacy orphan;no variant fetch or service-side variant selection. Shared summary/detail/wishlist mapping and Brand/spec exposure unchanged.
- Preserve publication recheck and existing ordered batch FOR SHARE query. Resolve signer credentials first;local signing after locks;close signer on completion. Failed preparation takes no locks;late hidden Product is suppressed without signing. Snapshot total may exceed returned rows under concurrent publication changes,as already documented. No retry,global locks or new network operation introduced.

## Verification

```sh
mvn -o -f demo/pom.xml -Dtest=ProductCatalogServiceImplTest,ProductListingControllerTest,ProductDetailControllerTest,ProductCatalogConverterTest,ProductSearchParameterParserTest,ProductSearchRequestTest,ProductSearchNormalizerTest,TechnicalSpecificationValidatorTest,WishlistServiceImplTest,WishlistConverterTest,WishlistControllerTest test
```

99 tests,0 failures,0 errors,0 skips across11 suites;Maven exit0. Full output: [listing-integration-test.log](listing-integration-test.log);per-suite counts/hashes: [listing-integration-results.json](listing-integration-results.json).

Six new service tests cover reordered batched rows,exact large price/variant projection,paired-null orphan,beyond-last-page totals,signer/lock ordering,late hidden products,signer failure and direct-request validation/criteria forwarding. One additional MVC test checks additive response metadata and null serialization. Existing detail test double gains only the required new overload;old assertions unchanged. Existing Catalog parser/normalizer/validator/converter/controller and Wishlist service/converter/controller tests pass.

These are focused unit/MVC regressions,not database concurrency or query-budget acceptance. T016 already records actual isolated PostgreSQL baseline verification. Do not interpret this result as G1:all fresh/upgrade/query-budget/full integration checks belong to T019,not executed here.

## Preservation and handoff

Task-entry SHA256 comparison permits exactly three production workflow files and three related test files. Migrations,repository,DTO/converter,Product Detail/Wishlist implementation,Brand/specifications,contracts and previous evidence unchanged. No DB command,migration,commit or push. Only T018 becomes checked;13/66 tasks complete. T019 prerequisites T018/T015 are now satisfied,but its own disposable-infrastructure checks must still run. T003/T004 remain pending external approval.
