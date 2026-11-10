# Local implementation and validation guide

The local Search & Filter implementation is present. The current execution/acceptance rules are [analysis-remediation.md](analysis-remediation.md) R1–R7,not a blanket all-prerequisites phase gate. Executed local results and exact reproduction commands are recorded in evidence/handoff.md;deployment readiness is separate.

## Prerequisites and boundaries

Use Java25, existing Maven wrapper in demo/, Docker for disposable PostgreSQL17 Testcontainers and existing image test infrastructure. The owner-approved registry/vocabulary receipts are in evidence/. Actual Backend category UUIDs resolve only from unique Laptop/Tablet taxonomy roots;missing/ambiguous bindings are disabled. Source-anchor model rank7 and battery are deferred. Verify pg_trgm availability, UTF8 and privileges in disposable PostgreSQL first. Do not start or migrate a populated catalog to benchmark this plan. No data import or deployment is authorized.

From repository root, verify local toolchain:

```bash
java -version
bash demo/mvnw -f demo/pom.xml -version
```

Meaningful checks after implementation (commands use actual current test classes):

```bash
bash demo/mvnw -f demo/pom.xml -B -ntp test
bash demo/mvnw -f demo/pom.xml -B -ntp verify
```

Inspect demo/target/surefire-reports and demo/target/failsafe-reports for actual discovery, failures and skips. No claim of testing follows from build exit alone. Use existing tests plus mirrored layer packages; no new dependency install assumed.

## Fixture and correctness matrix

Build isolated fixtures via existing Catalog factories, not source imports. Include all three publication states, null Brand/Description, a category chain≥3 levels and siblings, tied names/prices, products with ≥3 variants, false/missing/zero facts, malformed legacy JSON shapes, approved test-only numeric/boolean definitions, and recorded real key/type/unit samples. Test-only registry entries must never be shipped as production approvals.

| Journey | Required result |
| --- | --- |
| Legacy no query and page/size only | Same UUID sequence, envelope/totals/category/image behavior; exactly two additive listing fields |
| Vietnamese `Điện thoại` / `DIEN THOAI`, NFC/NFD, đ/Đ | Same eligible IDs and normalized name ordering |
| Multi-token words split across Product fields | Every token required; same-field exact phrase ranks higher within tier |
| `Samsung` vs `Samsnug`; short3 vs text4 boundary | Exact first, accepted typo discoverable after calibration; no short fuzzy/internal substring |
| WH-1000XM5 / i7-1360P / SKU and punctuation-neighbor negatives | Exact identifiers preserved; no fuzzy technical identifier hit |
| Keywords split across two variants | Exclude; an eligible single variant must satisfy all required keyword/filter conditions |
| Cheap8GB vs expensive16GB with max12m and RAM≥16GB | Exclude; combined16GB with sufficient budget displays cheapest qualifying16GB variant |
| Category subtree, brandsA/B, inclusive price bounds | Correct AND/OR, self+all descendants, boundaries included |
| Unknown category/brand with otherwise valid inputs | 200 empty; unsupported key/unit remains400 |
| Missing categoryId for specFilters; conflicting subtree definitions | 400; never silently coerce unit/type |
| Missing/false/zero/malformed specification | Missing never equals false/zero; no cast exception; no unapproved keyword match |
| All pages and beyond-last page | No duplicates/omissions over unchanged data; exact totals even empty later page |
| Product becomes private during page signing | No private row or usable signed private URL leaks; no unvalidated refill |
| Other consumers | Detail, wishlist and image contract regression suite retains previous fields/behavior |

For a disposable backend exposed on port18081, examples after implementation:

```bash
curl --get 'http://127.0.0.1:18081/api/products' --data-urlencode 'q=DIEN THOAI' --data-urlencode 'size=20'
curl --get 'http://127.0.0.1:18081/api/products' --data-urlencode 'q=WH-1000XM5' --data-urlencode 'sort=price-asc'
curl --get 'http://127.0.0.1:18081/api/products' --data-urlencode 'specFilters=[]'
```

Third command must return400. Category/specification success examples require fixture-approved identities/keys; copy exact UUIDs from the isolated fixture, not presumed live package UUIDs. Full request formats and limits: contracts/products-search.md.

## Migration and SQL validation

Use new migrations only, V16__catalog_search_matching.sql after the unchanged V1–V15. Record checksums before upgrade; migrate fresh and V14 disposable databases; assert unchanged IDs/prices/statuses/options/specification values/gallery/source ownership. Confirm pg_proc volatility for every normalization dependency and index expression, canonical SQL/Java normalization outputs, extension installation failure behavior, vocabulary exclusions without mutable index functions, indexed fuzzy candidate recall against exhaustive per-token verification, and no transaction-local trigram threshold leakage on reused pooled connections. Run registry startup validation with duplicate/conflicting/unsupported config and verify fail-closed behavior.

## Performance and EXPLAIN protocol

Use frozen reproducible datasets: ≥1,000 products baseline,1,500 source-like records and a larger synthetic skewed cohort (10,000 products,1/5/20 variants per sampled product). Record published ratio, missing specs/brands, category fanout, text lengths, key distribution, index sizes, extension/JDK/PostgreSQL versions, CPU/RAM and configuration. Larger scale is exploratory, not a new acceptance SLA. Do not infer correctness from source approval labels.

For each query class—legacy, exact SKU, one/three/fuzzy tokens, accent variants, short prefix, category+brands, broad/narrow price, one/multiple specification groups, no matches, deep page, price/name sort—record20 warmups then≥100 measured requests at concurrency1, plus10-reader diagnostic run. For the owner-approved actual1500 MVP,assert per-scenario FR-019 budgets in two independent serial JVM runs and record p50/p95/max/timeouts separately;see evidence/performance-policy.md. Unrelated legacy/synthetic thresholds remain unchanged. Report cold-start separately and do not hide count-query cost.

On disposable PostgreSQL, execute ANALYZE after fixtures/index creation. Capture EXPLAIN (ANALYZE, BUFFERS, VERBOSE, SETTINGS, FORMAT JSON) using actual bound values for the eligibility/page/count statement, plus category and representative subplans. Store reproducible SQL/params, output and interpretation outside runtime code. Compare baseline versus each index candidate, inspect row-estimate error, rows removed, heap/index reads, sort spill and lateral loop counts. Short tokens may legitimately scan; measure instead of disabling seqscan to claim success. EXPLAIN ANALYZE executes its statement, so use only planned SELECTs on disposable fixtures.

Count ALL JDBC statements, not just Hibernate statements. Proposed budgets≤6 data statements legacy/≤8 filtered and constant across20 vs100 rows; report SET operations separately. No per-product or per-variant repository call. Compare page membership/rank/counts to an exhaustive test oracle; indexes and candidate optimizations must not drop accepted fuzzy matches.

## Additional design-resolution acceptance requirements

[design-resolutions.md](design-resolutions.md) is normative. Include D1 typed numeric16/16.0, quantity16 GB vs16MB/16GiB/16GB and boolean true/false/tru; these are synthetic approved-test definitions, not activated production keys. Include D2 exact tokenizer boundary table, zero tokens, raw token limit before dedup, repeated-token rank vs original phrase semantics, and duplicate JSON members at all nesting levels.

Include known category with zero supported keys400; multiple individually known keys with no common applicable category400; partial subtree applicability; valid supported no-match200; unknown-category existing exception after global validation. Include mixed known/unknown brands with OR semantics.

Rehearse D3 startup capability failure, explicit disabled mode with X-Catalog-Search-Mode:exact-only, normal keyword mode header, enabled capability loss503, and no-keyword paths without fuzzy invocation. Tests must not treat exact-only mode as passing full fuzzy acceptance.

D7 regression must preserve legacy orphan Product ID/count and null pair (Product-only keywords permitted), exclude it for price/spec/SKU requirements, sort null price last in both directions and retain detail409. Real-but-nonmatching variants never get an orphan sentinel. Source-defined constraints remain intact; anomalous fixtures only in disposable test setup, no production constraint bypass. These are required future tests, not evidence of current behavior.

D6 migration failure rehearsal must record successful-prefix/failed-successor history, checksums, object/index state, safe same-artifact transient retry and STOP cases for checksum mismatch or immutable defective predecessor. Use existing approved Flyway tooling; do not invent a Maven plugin command. No routine repair/clean/baseline/reset. Current work runs NONE of these steps. The8-item verification register specifies required exit artifacts and dependencies.

## Evidence and acceptance exit

Attach actual reports, threshold/corpus revision, approved registry hash/version, migration results and benchmark/EXPLAIN artifacts. Record every skipped/unavailable check. Resolve B1–B3 and the post-design gates before implementation completion; this guide alone is not evidence of passing acceptance.

## Revised gates,corpus and operator verification

Primitive Java/DTO/parser/basic listing work may proceed on its real prerequisites without T003. SQL normalization proof is current T013 after Java T007 and PG T002;core fuzzy proof is T014 after protected model/core corpus evidence. Runtime registry is T008 after actual T003 approval. G1 T019 does not depend on those approved-key activation tasks. Core T029 is a development increment;full US1 T031 additionally needs the T030 approved-key contributor. Nothing here activates the9 observed UNVERIFIED keys.

Freeze the corpus partitions at T005/T030/T033/T039/T050 and require their COMPLETE manifest at T056/T062/T065. Core checks cannot require unfinished future cases. Run golden ranking RK01–RK10 using the seven-tier comparator;whole-query identifiers and Name beat phrases,but a scattered exact SKU token does not. Model classifications and case facts remain reviewed fixture assumptions until supported.

Integration/fault tests default SERIAL across jobs/classes;R5 proven unique containers/databases/pools/storage namespaces are mandatory for parallel execution. Orphan/extension/migration tests require dedicated disposable instances. Controlled within-test concurrency/race scenarios are preserved. T061 benchmarks use exclusive resources;T062 complete non-benchmark regression has no dependency on benchmark PASS. Selection reports must prove both-job union retains every intended assertion/metric.

T026/T028 must demonstrate the future Compose existing-endpoint keyword probe returns200 healthy and503 on required capability loss,in BOTH configured modes,even if the protected probe token matches no product. The probe URL is R6;legacy no-keyword request behavior stays unchanged. Change that healthcheck only with a search-capable backend;current code/Compose remain untouched.

Local summary/handoff T065/T066 may PASS with target state NOT_VERIFIED/BLOCKED;T063 remains a separate mandatory deployment gate. No production rollout is permitted without its real target/recovery/authorization evidence. These are future executable checks,not results produced by this documentation revision.

## T009 verification protocol

Before future integration/performance runs,use [verification-protocol.md](evidence/verification-protocol.md) for SC/edge-case ownership,run manifests,JDBC counting,SERIAL/isolation rules and T061/T062 selection-union proof. Protocol preparation is complete;no feature integration/benchmark result or CHK036 PASS is implied. T002 isolated capability results are scoped to the recorded disposable image;T004 identifier sign-off and T006 full semantic OpenAPI validation remain BLOCKED as recorded in their evidence files.


## Reproduce current implementation verification

From Backend-SWE root (Java25,Docker;all database targets below are Testcontainers):

```bash
mvn -o -f demo/pom.xml '-Dit.test=*IT,!*PerformanceIT' -Djunit.jupiter.execution.parallel.enabled=false verify
mvn -o -f demo/pom.xml -Dit.test=ProductSearchPerformanceIT,ProductSourceWorkloadPerformanceIT,CatalogPublicReadPerformanceIT -Djunit.jupiter.execution.parallel.enabled=false test-compile failsafe:integration-test failsafe:verify
```

Run the two commands sequentially. Do not use a populated database URL. The first performs unit/MVC and non-benchmark integration regression;the second owns exclusive timing resources. `SearchQueryPlanIT` records plans and compares candidate eligibility with the exhaustive matcher. `FuzzyCalibrationIT` replays24 controlled pairs and tests strict-word prefilter recall against tokens from1500 frozen source titles. Those titles are not a target import or manual relevance ground truth.

Output: demo/target/search-performance/,search-plans/,search-calibration/,t015-evidence/,t019-evidence/,surefire-reports/,failsafe-reports/. Use `mvn` online once if existing dependencies are not cached;no new Java dependency is introduced.

Operator settings: `CATALOG_SEARCH_FUZZY_ENABLED=true` and `CATALOG_SEARCH_FUZZY_THRESHOLD=0.32`. Disabled mode still requires installed search capability and returns exact-only for nonblank q. The Compose probe is `/api/products?q=catalog-readiness-probe&page=0&size=1`.

Example approved filter (substitute an actual Backend Laptop/descendant UUID):

```bash
curl --get 'http://127.0.0.1:8080/api/products' --data-urlencode 'q=laptop' --data-urlencode "categoryId=$LAPTOP_CATEGORY_UUID" --data-urlencode 'minPrice=1000000' --data-urlencode 'sort=price-asc' --data-urlencode 'specFilters=[{"key":"physical_ram","alternatives":[{"op":"gte","value":16,"unit":"GB"}]}]'
```

No server or persistent source import is started by these tests. Data can be DRAFT/UNVERIFIED;only PUBLISHED Products with eligible facts appear. Definition approval does not certify source facts or deployment. T063 requires a separately authorized target before rollout.


Current exact commands and measured limits: [handoff](evidence/handoff.md). New migrations end atV19;V1–V15 remain unchanged. Source performance is measured via full MockMvc HTTP;the500ms broad-query goal is not yet met.
