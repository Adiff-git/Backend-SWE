# T019 — Foundation Acceptance Gate G1

**Status: PASS for G1 only.** Executed2026-11-09,branch `features/SearchAndFilter`,HEAD `c5bd4e7eeb39d9b06a0c7e36af9a65635058613f` plus preserved uncommitted changes. Owner:Codex automated local execution under the explicit T019 instruction;no registry/model reviewer sign-off is claimed.

## Prerequisites and authorized scope

T018 and T015 are checked complete with passing execution reports. Their recorded source hashes were verified against current files before execution;receipt hashes are in [foundation-results.json](foundation-results.json). T018 supplied99 passing unit/MVC tests;T015 supplied actual isolated migration evidence. G1 reruns the required integration instead of assuming previous reports suffice.

Checklists are read-only:requirements16 checked/0 unchecked;reviewer-owned design0 checked/40 unchecked with its separate scoped verification statuses. The user's explicit T019 instruction authorizes this gate while T003/T004 remain blocked;no reviewer checkbox/approval is changed. Extension hook file absent before/after. Java/Docker ignore patterns already present;no ignore configuration changes.

## Acceptance matrix

| G1 criterion | Actual evidence/result |
| --- | --- |
| Controller → parser → service → fragment → projection → DTO | PASS:CatalogContractIT and ProductListingApiIT cross the actual layers against disposable PostgreSQL;unit/MVC assertions retain validation and additive shape |
| Legacy defaults/page/size/publication/counts/order | PASS:default0/20,max100,23 published fixture IDs across repeated pages,hidden states excluded,empty/deep/max-offset pages retain distinct total;legacy repository UUID order oracle retained |
| Representative selection and mapping | PASS:cheapest real variant,UUID tie,exact30-digit price,zero price,one row/product despite variants/images;HTTP selected UUID/price checked against fixture DB,not response-derived values |
| Orphan compatibility | PASS:repository and HTTP rollback-only deferred fixtures retain Product/count and exact two null fields;detail409 remains. Constraint trigger stays enabled;no orphan committed or trigger disabled |
| Unsupported criteria | PASS:repository rejects11 invalid/unsupported inputs before SQL with0 active specification providers;HTTP rejects nonempty keyword/category/brand/price/sort/spec criteria in this incomplete increment rather than silently ignoring them |
| Publication/image signing | PASS:committed unpublish before final lock returns no new signed URL;hidden detail404;local signer unit ordering/failure cases;real LocalStack private-byte read200 and tampered signature403 |
| Bounded queries/N+1 | PASS:JDBC execution capture observes4 data statements at size20 and100 for101 Products,303 Variants and303 Images. Direct JdbcTemplate prepared query + ordinary Statement calibration observes2/2. Existing listing≤6 and detail≤5 Hibernate assertions retained |
| Catalog Detail/Brand/specifications/Wishlist | PASS:ProductDetailApiIT and ProductDetailRepositoryIT retain Brand/per-variant technical data checks;contract includes nullable brand and canonical empty technical envelope;WishlistControllerIT9 and WishlistServiceIT15 cases retain commit-boundary/security/concurrency behavior |
| Fresh/upgrade/migration safety | PASS:five CatalogSearchMigrationIT cases;fresh V1–V15 +Flyway validation;V14→V15 preserves every seeded business row,history/checksum and constraint/trigger/index definition;missing privilege/package/collation blocks migration without fallback |
| Source/change preservation | PASS:all application sources,prior uncommitted Brand/spec changes,contracts and V1–V15 are byte-identical to gate entry. Only two test files plus T019 evidence/tasks status changed |

Snapshot totals can exceed returned rows under concurrent publication changes;the existing fail-closed policy is preserved. Unchanged-data counts/pages are independently tested. No keyword/fuzzy/ranking/filter support,registry activation,calibration,benchmark or deployment approval follows from G1.

## Execution and discovery

Final reproducible command from repository root:

```sh
mvn -o -f demo/pom.xml -Dtest=ProductCatalogServiceImplTest,ProductListingControllerTest,ProductDetailControllerTest,ProductCatalogConverterTest,ProductSearchParameterParserTest,ProductSearchRequestTest,ProductSearchNormalizerTest,TechnicalSpecificationValidatorTest,CatalogSecurityBaselineTest,WishlistServiceImplTest,WishlistConverterTest,WishlistControllerTest -Dit.test=CatalogSearchMigrationIT,ProductSearchBaselineRepositoryIT,CatalogContractIT,CatalogQueryCountIT,ProductListingApiIT,ProductDetailApiIT,ProductDetailRepositoryIT,ProductListingRepositoryIT,CatalogReadFlowIT,WishlistControllerIT,WishlistServiceIT -Djunit.jupiter.execution.parallel.enabled=false -DforkCount=1 -DreuseForks=true verify
```

**105 unit/MVC tests in12 Surefire classes +60 integration tests in11 Failsafe classes =165 tests;0 failures,0 errors,0 skips;exit0.** Both plugins3.5.6 actually discovered the listed suites/test identities;see [foundation-results.json](foundation-results.json). Output: [foundation-test-output.txt](foundation-test-output.txt). Per-test identities and XML hashes are archived in the JSON;original runtime XML is under demo/target/{surefire,failsafe}-reports.

Selected integration classes:CatalogSearchMigrationIT,ProductSearchBaselineRepositoryIT,CatalogContractIT,CatalogQueryCountIT,ProductListingApiIT,ProductDetailApiIT,ProductDetailRepositoryIT,ProductListingRepositoryIT,CatalogReadFlowIT,WishlistControllerIT,WishlistServiceIT. No tests were silently omitted/skipped to obtain PASS. This is the scoped G1 suite,not the later whole-repository T062 regression gate.

Default execution was SERIAL,one reused fork per phase and JUnit parallel=false. Controlled concurrent transactions inside existing publication/Wishlist tests remain active. Existing shared test JDBC URL is not claimed safe for parallel workers.

## Actual infrastructure and cleanup

- PostgreSQL17.11,UTF8,pg_trgm1.6,deterministic und-x-icu collation153.136,READ COMMITTED default;actual runtime and successful V1–V15 history in [foundation-runtime-environment.json](foundation-runtime-environment.json).
- Integration profile uses `jdbc:tc:postgresql:17-alpine:///backend_test`,pool2 (WishlistControllerIT overrides4);Testcontainers random loopback ports. Migration/fault tests own five separate nonreused tmpfs PostgreSQL instances. No shared/development datasource is used.
- CatalogReadFlowIT owns LocalStack4.14.0 with S3 signature validation;its private bucket is `catalog-private-images`. Other selected API/query suites use existing local fake signers and never AWS. Source/profile hashes plus exact test identity fix fixture behavior;generated UUIDs are not assumed stable across runs.
- Host/tool versions,actual observed container/image IDs,remaining IDs from final logs,migration instances and post-run removal checks are in [foundation-environment.json](foundation-environment.json). Cached image metadata is labelled separately from runtime proof. All final-run container IDs were absent after completion;no user container/volume cleanup command was issued.

## Corrections and failed attempts

The initial gate run failed3 of57 integration cases,all due to stale exact-field assertions in CatalogContractIT. Old fields and assertions remain;the test now expects exactly two additive listing fields plus the already existing nullable Brand and per-variant technicalSpecifications. Actual HTTP metadata checks were added,not a broad allow-extra-fields relaxation. No application behavior or API design changed.

New query capture and orphan/unsupported HTTP tests exposed two mistakes in newly authored test assertions:Hibernate counters accumulated between size20/100,and an existing empty technical document was incorrectly assumed null/empty instead of `{schema_version:1,values:{}}`. Reset counters per measured request and assert the exact existing envelope. The≤6 budget was never raised. One compile-only focused command accidentally used skipTests;its zero executions are explicitly excluded from acceptance. All selected tests were subsequently executed in the final passing verify. See [foundation-attempts.json](foundation-attempts.json).

## Durable evidence and remaining gates

- [foundation-migration-results.json](foundation-migration-results.json):five actual fresh/upgrade/fault reports,container IDs and row/history/function proof.
- [foundation-repository-observations.json](foundation-repository-observations.json):candidate pages/counts/one-statement observations and unsupported criteria proof.
- [foundation-listing-20.json](foundation-listing-20.json),[foundation-listing-100.json](foundation-listing-100.json),[foundation-jdbc-calibration.json](foundation-jdbc-calibration.json):actual SQL execution lists and counts,excluding setup work.
- [foundation-results.json](foundation-results.json):task receipt,source/migration hashes,selection/discovery and per-suite/per-test results.

T019 is complete;14/66 tasks checked. T020 and T022 now satisfy their listed prerequisites and may start independently as scoped test authoring;they were NOT executed. T021 still requires T005,which waits for T004 final vocabulary sign-off. T003 registry approval remains independently blocked. Full US1/feature acceptance,performance p95 and production deployment readiness remain pending. No commit,push,migration edit or task outside T019 occurred.
