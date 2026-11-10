# T015 — search-support migration verification

Status: PASS for T015 only. UTC:2026-11-09T07:24:13.120149+00:00. Branch:features/SearchAndFilter;HEAD:c5bd4e7eeb39d9b06a0c7e36af9a65635058613f plus preserved dirty worktree.

## Prerequisites and scope

T010,T002,T013,T007 were checked complete before work. Their evidence files exist;T013 recorded53/53 parity and immutable primitive audit. Its prototype and Java normalizer hashes matched current files. V15 was unoccupied at start. No T003/T004/T006/T014/T018 or other task was executed. Existing reviewer checklist markers remain requirements16/16,custom design0/40;the user explicitly authorized this isolated migration task with those known pending evidence gates.

## Implemented functionality

- Added only `V15__catalog_search_support.sql`;existing migrations V1–V14 remain byte-identical to pre-task and HEAD. V16 is not created.
- Migration preflight requires UTF8,deterministic ICU `pg_catalog."und-x-icu"` with stored/actual version153.136,available pg_trgm1.6 and immutable built-in primitives. Different/missing/unverified capability stops migration rather than picking another normalization or silently degrading.
- If pg_trgm is absent,require database CREATE privilege and install version1.6 in public via Flyway transaction. Existing incompatible version/schema fails;no automatic extension upgrade/relocation or request-time install.
- Reuse the five T013 primitive bodies with explicit public function/pg_catalog collation namespaces and fixed search_path=pg_catalog,public. All immutable/parallel-safe functions are versioned. No new Java normalization/validation/DTO logic.
- Add the planned structural natural-name view by composing the verified tokenizer and immutable string_agg in source token order;exclude structurally protected tokens only. Java parity tests derive its expected output from the existing Token structural flag. No registry/dictionary/vocabulary lookup,fuzzy scorer,threshold,ranking or filter is implemented. T004/T014 remain required before actual name matching.
- No catalog table/index/data rewrite;no Product Detail/Wishlist/API behavior or production code change. Schema-support installation is not production rollout approval.

## Test-first history and final commands

The five assertions were authored before V15. First run failed as expected with missing migration target15 (5 cases,3 failures/2 errors);PostgreSQL/Flyway infrastructure was functioning. After adding V15,functional assertions passed but evidence serialization of JDBC proconfig arrays failed in2 cases. The reporter was corrected to capture proconfig as text;no production assertion or migration integrity check was weakened.

Final command (offline,existing Maven/dependencies only):

```sh
mvn -o -f demo/pom.xml -Dtest=ProductSearchNormalizerTest,ProductSearchRequestTest,ProductSearchParameterParserTest,ProductCatalogConverterTest,ProductListingControllerTest,ProductDetailControllerTest,ProductCatalogServiceImplTest,WishlistConverterTest -Dit.test=CatalogSearchMigrationIT,CatalogEnhancementMappingsIT,ProductDetailRepositoryIT,ProductListingRepositoryIT test failsafe:integration-test failsafe:verify
```

**BUILD SUCCESS:50 unit/MVC tests +11 integration tests =61;zero failures/errors/skips.** Surefire/Failsafe3.5.6 discovery and case identities are recorded in [search-support-migration-results.json](search-support-migration-results.json),with report/source hashes and actual Flyway history checksums. No complete feature/regression/performance acceptance is inferred.

## Focused disposable scenarios

| Case | Actual assertion/result |
| --- | --- |
| Fresh | Flyway applies1–15;validate succeeds;pg_trgm1.6,operator support and all6 immutable/parallel-safe functions exist;53/53 Java normalization/token/protection/natural-view cases pass |
| V14 upgrade | Flyway first applies1–14;seed committed synthetic Product/Brand/Variant/Image/Wishlist/source-link fixtures;apply exactly V15;validate succeeds;all business row hashes,old history rows/checksums and old constraint/trigger/index definitions unchanged;53/53 parity passes |
| Missing install privilege | Separate role has schema/history permissions but no database CREATE;Flyway fails with explicit prerequisite error;no V15 history/functions/extension;successful version remains14 |
| Missing extension package | Dedicated container only:move its pg_trgm.control to a temporary location;Flyway fails package prerequisite;no V15 artifacts or silent fallback |
| Missing pinned collation | Dedicated container only:remove its unused ICU collation before V15;Flyway fails capability check before install;no V15 artifacts |

Every test owns a distinct PostgreSQLContainer(postgres:17-alpine),PGDATA tmpfs,label speckit.task=T015,reuse=false;class execution explicitly SAME_THREAD. They do not use application DB_URL or a populated/shared target. Source migrations are applied exclusively through Flyway. Direct SQL is limited to disposable fixtures,assertions and isolated fault setup. No clean/repair/baseline/history edits or unsafe retry procedure was used. Regression repository/entity tests use the existing test-profile Testcontainers JDBC URL,not development DB credentials.

Upgrade fixtures include PUBLISHED/DRAFT/ARCHIVED Products,exact30-digit prices,SKU/options,Brand links,typed quantity/text/false/zero values,images/object keys,wishlist membership and source receipts. They are test-only:their synthetic keys/receipt hashes do NOT approve a specification registry or real catalog import. Row digests cover every public business table,not just listed fields. Fractional-price,malformed technical-envelope and orphan-product attempts still reject after upgrade;committed snapshots remain unchanged. No real S3/media operation occurs.

## Old migration file integrity

| File | SHA-256 (pre-task = after = HEAD) |
| --- | --- |
| V1__create_catalog_core.sql | `57335af270525b03f723a14bcadf17c16a2b23c3ce2181061660b17193ddc613` |
| V2__create_product_images.sql | `87e45d16aecb62f4c31c53a71d01173f31e415a9430684dce2d60bcc2c275e09` |
| V3__enforce_catalog_relationships.sql | `d3ad76330c02dba1f9f2a1a973664441986d422ac7a57e1c8e4dd1a8c599d454` |
| V4__create_users.sql | `dd065d0c85e93a87573a9396ec8b427c9a4a9e7a80df272d279c5e412497f628` |
| V5__create_addresses.sql | `a303f220e2c59feb0fc538648ace280a954723e5c89c43ed58fdf7597fb787a2` |
| V6__authorities_and_username_policy.sql | `976182f523d5f8910477ff81ede01d770313204f7109293eaa42f75763a4d6bf` |
| V7__authentication_sessions.sql | `a063ab4e474da4b63187af2653b7b83be9dc1b7b8083da89426479a6fdbf36f0` |
| V8__image_uploads.sql | `3e142dc5084141fd3808b40f0b29de14408380b3c5694d2768738037a1114723` |
| V9__user_profile_images.sql | `795c2571d9ad36ca95f2c9f4a230d9d2ab05a2dcee90e9c83188d2c3db99a5ac` |
| V10__create_wishlist_items.sql | `4643e4fdfeccaf2339dcb922cf3315ffa5973e73833a8a5d0f49ba4b1d9aef47` |
| V11__catalog_data_enhancement.sql | `8d69dc8b8bc5b648c4fd6cfc5e95574c3ebe6c3f29acedcc63f0f459d3d783cd` |
| V12__product_image_attachments.sql | `26b01726a537c448b617e3cba8e76f49c88d92216f03c4b5d10c24e8316574ed` |
| V13__email_google_auth.sql | `8051bb28f137088b0816476fb82373641e10c059f933383deff806a42c3b6085` |
| V14__remove_request_rate_limiting.sql | `79bb9bd5dcc159bd85073d84d3d78736e1cbd008e8a6626d4ef28f65720c55d6` |

These SHA-256 file hashes are distinct from Flyway CRC checksums;actual V1–V14 Flyway rows/checksums are recorded separately in upgrade evidence and compared exactly before/after. New V15 checksum is recorded in both fresh/upgrade history.

## Remaining gates / next boundary

No registry approval T003,identifier sign-off T004,semantic OpenAPI PASS T006,fuzzy calibration T014,target capability T063 or later acceptance is inferred. ICU153.136/pg_trgm1.6 are intentionally the proven local capabilities;an unverified/different deployment must remain BLOCKED until separately reviewed evidence exists. No installation of ICU/new Java dependencies is attempted. Changing function semantics or relaxing pins requires explicit reviewed successor/versioning;do not edit an applied migration.

After T015,T016 is dependency-ready;T018 still needs T016 and T006. This run stops at T015.

## Post-execution scope/preservation checks

Pre/post SHA-256 snapshot confirms only tasks.md changed among pre-existing files. Exactly four files were added:V15,the focused migration IT,and this verification/results pair. Existing Product Catalog/Brand/Specifications/Detail/Wishlist code/tests/contracts,normalizer/parser/DTOs,all other feature artifacts,ignore files,constitution and V1–V14 remain byte-identical. Old migration bytes also match HEAD. No source file was overwritten or discarded.

The ten checked tasks are exactly T001/T002/T007/T009/T010/T011/T012/T013/T015/T017;remaining56 unchecked. git diff --check and new-file whitespace pass;new migration/evidence files are not ignored. All five T015 evidence container IDs are distinct,and post-test docker label inspection finds none remaining. Before/after extensions.yml is absent;no hooks dispatched. No commit/push or task beyond T015 was executed.
