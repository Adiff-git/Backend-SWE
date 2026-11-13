# T009 — integration/performance evidence protocol

Status: PASS for preparation only. T001 baseline is complete. This protocol defines future responsibilities and acceptance;it is not an executed feature test,benchmark,registry approval or CHK036 PASS.

## Ownership and run manifest

The implementing Catalog developer owns test authoring,execution,fixture cleanup and report collection. The feature reviewer owns review of complete FR/SC evidence and skips at T065;the authorized catalog reviewer separately owns registry/model sign-off. These are responsibility roles,not invented named assignments or signed approvals. Record actual operator/reviewer identity in each future report;missing identity/scope blocks acceptance.

Each run must record UTC/run ID,branch/HEAD and dirty patch hashes,Java/Maven/Boot versions,exact command,plugin discovery configuration,selected class list,report paths,test counts/failures/errors/skips and exit status. Record host CPU/RAM/OS/load,container image digest/PostgreSQL/extension/encoding/collation versions,connection/pool/isolation settings,unique DB/schema/container IDs,storage identity and cleanup result. Never record credentials/tokens. Include corpus/identifier/normalization/registry versions and hashes,approval references,fixture generator/version/seed and expected Product/variant UUIDs. Document cache state,index/migration versions and actual SQL EXPLAIN plans;Compose configuration alone is not execution evidence.

## Success criteria and test responsibility

Paths below are under demo/src/test/java/com/example/demo/ unless stated. Proposed tests are not assumed to exist.

| Criterion | Owning tasks and test layers | Required evidence/exit |
| --- | --- | --- |
| SC-001 search/ranking | T004/T005 reviewed inputs;T007/T013 normalization;T014 calibration;T021 catalog/repository/ProductKeywordSearchRepositoryIT;T027 API;T030 approved technical contributor;T029/T031 gates | Expected membership/order100%;core RK01–RK06/RK08–RK10;reviewed model protection;no future-filter dependency at core gate. Full feature requires every approved partition |
| SC-002 same-variant/display | T039/T043 catalog/repository/ProductVariantSearchRepositoryIT;T045 typed-data;T046 catalog/controller/ProductConfigurationSearchControllerIT;T048 | Split price/spec/SKU variants yield no false positives;representative pair belongs to cheapest eligible variant. RK07 price-only,spec-only,combined exclusion at repository and HTTP;D7 null pair |
| SC-003 pages/counts | T033/T036 category/brand;T050/T051/T052/T056 repository/API pagination | Exhaustive unchanged-data oracle:each expected UUID exactly once,distinct totals,repeated order,ties and deep/out-of-range pages |
| SC-004 compatibility/security | T019 baseline;T028 capability;T053–T055 orphan/publication/consumer races;T060 migration;T062 legacy regression | PUBLISHED only,invalid bearer401,detail/image/wishlist unchanged,ordered locks and commit-boundary races;source migrations/data integrity preserved |
| SC-005 validation | T020/T032/T038/T049 MVC;T040/T041 validators;T045/T046 API/data;T056/T062 | Complete invalid-input matrix100% specified errors;valid supported no-match200;unsupported known-category400;no ignored invalid parameter |
| SC-006 performance | T058 EXPLAIN baseline;T059 justified indexes;T061 repository/ProductSearchPerformanceIT and catalog/storage/CatalogPublicReadPerformanceIT | p95≤1second,size20,≥1000 products;query-budget and candidate-recall evidence;environment must be known |

Reports:feature evidence/us1-core-acceptance.md,us1-specification-evaluation.md,us1-acceptance.md,us3-acceptance.md,registry-runtime-verification.md,integration-results.md,performance-results.md,explain-final.json,verification-summary.md. Actual Surefire/Failsafe XML remains in demo/target/surefire-reports and demo/target/failsafe-reports;archive discovery/results hashes in evidence rather than claiming ignored target files are durable. T056/T062/T065 require complete manifest including all RK01–RK10,no PENDING partition. T031 requires core+approved US1 specification partition only.

## Edge-case responsibility matrix

| Cases | Mandatory owner/verification |
| --- | --- |
| NFC/NFD,Vietnamese accents/đ,whitespace,punctuation,C++,decimals,model numeric tokens,3/4-character boundary | T004/T007/T013/T021/T024;SQL/Java parity and protected negative cases;no unit/model rewriting |
| Empty/blank/literal null,zero tokens,duplicate normalized tokens/original repeated phrase | T020/T021/T025/T027;raw limits before dedup |
| Missing brand/description;technical searchable vs filterable;missing/malformed facts,false/zero | T030/T039/T045/T046;approved-only values,no cast exceptions |
| Category descendants/overlapping paths/cycles,multiple brands/repeats,unknown UUIDs | T033–T037;no duplicate Products/inflated totals |
| Incompatible/missing key definitions,unsupported operator/unit,category requirement,no conversion | T038–T047;approval scope and valid no-match distinct from invalid request |
| Cross-variant SKU/price/spec false positives;best-rank vs cheapest;excluded rank7 RK07 | Core SKU-only T021/T027;configuration T039/T043/T046/T048;final T056 |
| Duplicate JSON members at every nesting,even equal;unknown fields;limits;SQL/wildcard literals | T020/T038/T040/T041/T046;strict raw duplicate detection before DTO binding,parameter binding,no wildcard interpretation |
| Orphans,paired nulls,null-price ordering,tied sorts,total preservation,out-of-range pages | T050–T053/T056;constraints retained;no listing409 |
| Fuzzy unavailable/startup,disabled-mode/probe,pool threshold leakage | T026/T028;dedicated PG instance,actual HTTP200/503 not merely internal readiness |
| Publication/gallery races,consumer compatibility,connection reuse | T053–T055/T062;controlled concurrent transactions and real commit boundaries |

## Measurement and instrumentation

- Fixture cohort:at least1000 products;record published/hidden counts,categories,brands,variants/product and skew,spec missing/invalid rates,images and text distribution. Add source-like1500 cohort and exploratory larger cohort;no fabricated data counts or approval. Test fixtures cannot activate production keys.
- Query manifest must include legacy browsing,exact SKU/model,whole name/phrase,token AND,Vietnamese/fuzzy typo,category descendants/multi-brand,price/spec combinations,same-variant negatives,empty matches/deep pages and selective/nonselective predicates. Record exact requests/expected results and number of samples per journey;do not dilute a slow journey with fast queries.
- Warm JVM/Spring/query paths for20 iterations,then collect≥100 measured size20 journeys at concurrency1. Record each latency;nearest-rank p95 is sorted sample[ceil(0.95*N)-1]. Require p95≤1second for each agreed acceptance journey. Record cold startup separately. Also run diagnostic10 readers with recorded schedule/contention;it adds diagnosis,not an invented throughput SLA. Record frontend/network vs MockMvc boundary honestly;preserve existing performance assertions.
- Count ALL JDBC statements on the actual request connection(s),including native count/page,projection,image/publication and capability checks. Existing CatalogQueryCountIT uses Hibernate statistics,but that alone may miss non-Hibernate JDBC. Future test-only connection/DataSource instrumentation must intercept prepare/execute paths through existing JDBC interfaces without an unapproved dependency;calibrate with a known direct JDBC statement to prove inclusion. Do not count fixture setup as request work or omit capability/count cost.
- Data statement budgets:≤6 legacy,≤8 filtered;report transaction-local SET separately AND total observed round trips. Compare size20 vs100 and variant skew to prove no N+1. Report zero-row/deep-page count cost too. Preserve existing detail≤5 assertion and existing public-read functional checks.
- Record EXPLAIN(ANALYZE,BUFFERS) for representative queries on isolated fixture DB only;note parameter values,cardinality,execution buffers/index usage. Compare indexed candidate membership/ranking to exhaustive corpus;no top-K prelimit allowed to hide recall loss. Neither index use nor a default similarity0.30 is approved without measured evidence.

## Isolation and test-selection manifests

Default SERIAL across integration classes/jobs. Existing shared jdbc:tc backend_test URL is not per-worker isolation. Authoring[P] is not permission for parallel execution. Opt-in parallel IT execution requires proof of unique container AND database/schema per worker,independent pools/fixture namespaces and LocalStack/bucket/object identities. Extension/trigger/orphan/migration/fault workers require a dedicated PostgreSQL instance,not just schema isolation. If isolation is unproved,serialize. Keep explicit concurrent transactions within isolated race tests;do not serialize away the race under test. Cleanup committed fixtures at actual transaction boundaries;no test-level rollback hiding commits. Never target a populated/shared/deployment database.

Before T061/T062 execution,freeze a selection manifest of every intended *Test/*IT class and test identity from current sources and actual plugin discovery. T061 contains proposed catalog/repository/ProductSearchPerformanceIT and existing catalog/storage/CatalogPublicReadPerformanceIT,including their functional assertions. T062 contains the complete non-benchmark suite,excluding ONLY those measured classes. Use existing approved Maven/Surefire/Failsafe selection facilities;record exact commands and effective plugin configuration before execution. Do not install a plugin or assume -Dtest/-Dit.test alone covers both phases. Fail if a selector produces zero/unexpected discovered tests. No test is deleted by selection.

T061 and T062 are independent after their own prerequisites;neither waits for the other's PASS. Benchmarks use exclusive CPU/DB/storage resources;resource constraints may require serial scheduling without creating a PASS dependency. Prove set union(actual test identities T061,T062)=full intended suite and report overlaps/missing identities. Preserve all existing assertions/metrics. A prior partial report is not full-suite evidence.

## Final acceptance gate

T065 requires full20FR/6SC traceability,every story/corpus partition,all golden cases,actual registry/scope/calibration/normalization/contract evidence,T061 benchmark AND T062 complete regression plus T064 compatibility review. Every skip must be listed with reason/owner and reviewed;unexplained skips fail acceptance,and missing mandatory golden/scope/capability/benchmark evidence is BLOCKED,not accepted by explanation alone. Failed latency/query budgets fail acceptance. No unverified approval or benchmark may be marked PASS.

CHK036 remains NEEDS VERIFICATION until real complete execution evidence is reviewed. CHK005/009/020/026/028/034/037 retain their scoped gates. T063 deployment target is separate;local T065 may pass with deployment NOT_VERIFIED/BLOCKED,but cannot authorize production rollout. T009 authoring does not execute T010 or any later task.

## T009 preparation validation

Protocol reviewed against T009 done-when:all6 SC and listed edge groups have owners/layers/report destinations;measurement parameters,all-JDBC counting,environment/fixture identity,SERIAL/fault isolation,selection-union proof and final acceptance are specified. Current source confirms CatalogQueryCountIT uses Hibernate statistics and the existing timing test is catalog/storage/CatalogPublicReadPerformanceIT;neither alone proves coverage of future native/JDBC search statements. No benchmark/test suite was run for this protocol.

Post-execution document checks:66 task IDs retained;only T001/T002/T009 checked,T004/T006 remain unchecked;19 OpenAPI local references resolve;case JSON parses;10 structural schema examples and URL roundtrip match expectations;Markdown links/whitespace and git diff --check pass. Pre/post snapshot proves only tasks.md and quickstart.md changed among pre-existing files;all application,Brand/Specifications,test source,ignore,constitution,existing feature documents and applied migration files remain byte-identical. New artifacts belong only to the four selected tasks. No hooks before/after because .specify/extensions.yml is absent. No commit/push/shared migration or task beyond selected scope executed.
