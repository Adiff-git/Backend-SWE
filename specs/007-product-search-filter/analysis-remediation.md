# Analysis remediation decisions

Date: 2026-11-08. Resolves D1,A1,I1,D2,V1,V2,D3 from the read-only analysis. This is an authorized document revision, not execution of analyze/implementation. All20 FR and6 SC remain required; eight NEEDS VERIFICATION items remain unverified. No registry approval,capability result or benchmark is inferred.

## R1 — Scoped gates (D1)

Task dependencies, not a blanket phase barrier, determine what may start. A blocked task does not prohibit disjoint ready tasks in the same/later phase.

| Gate | Meaning | Scope |
| --- | --- | --- |
| G0/T010 | Source/scope and verification-protocol review after T001/T009 | Shared workflow safety review, NOT registry approval,SQL capability or fuzzy calibration |
| G-N/T013 | Disposable SQL normalization/volatility and SQL/Java parity after T002/T007 | Required before T015 support migration/index dependence;Java primitive/unit work T007 may precede this proof |
| G1/T019 | Legacy-compatible core foundation after T018/T015 | Core Product/Brand/SKU/Description infrastructure only;does not depend on T003/T008 or approved-key activation |
| G-ID/T004 and G-F/T014 | Reviewed model scope and core corpus/calibration | Required before fuzzy matching T024,not before DTO/parser/structural normalization |
| G-S/T003 | Explicit approved specification matrix/category/units/provenance | Required for T008 runtime registry,T030 approved technical-keyword module,T039/T040/T042/T043 specification work and per-key index branches;never a prerequisite of plain DTO/parser,structural normalization,legacy listing or category/brand basics |
| G-C/T029 | Core keyword production corpus and capability-mode evidence | Reviewable development increment Foundation+US1.core;does not claim FR-004's approved-specification branch or complete US1 |
| G-US1/T031 | US1.core AND US1.specification | Full US1 needs T030 approval-gated typed keyword evidence;cannot silently omit that requirement |
| G-L/T065 | All local story,contract,regression,benchmark and approval evidence | Requires local evidence,not successful T063 deployment inspection |
| G-D/T063 | Intended authorized deployment target capability/taxonomy/history | Separate deployment-only gate;absence is NOT_VERIFIED/BLOCKED and cannot prevent a truthful local handoff |

Registry-independent development builds do not instantiate/activate the unfinished approved-key module. Foundation defines a technical keyword contributor interface,not a dummy approved registry. Generic/core search has zero technical contributors until the approved module exists; it must not claim complete feature acceptance. T008 installs only a genuinely approved runtime file;once the full module is present,missing/invalid configuration fails its startup checks. Unfinished explicit specification requests are rejected,never silently ignored. No production rollout of a partial increment is approved.

Shared query code receives approved contributors through a small extension interface;T030 implements/registers its own contributor and tests after G-S/G-C,without concurrent edits to the core query/service writers. This permits US2 category/brand work after core T029 while registry approval is pending. Price transport and basic category filtering remain independent of G-S. Full combined filtering/integration still requires the approved module.

## R2 — Corpus partitions and semantic dependency order (A1)

T005 freezes the manifest/schema and US1.core partition without requiring T003. Each case records caseId,corpusVersion,story,increment,requiredCapabilities,fixture reference,expected Product/variant IDs and ordering/status. Registry-dependent positive cases also record approval/version scope. Expected answers are independently reviewed,not computed from implementation. Manifest references future partitions as PENDING,never as frozen/passing cases.

| Partition | Freeze/owning task | Verification scope |
| --- | --- | --- |
| US1.core | T005 | Product/Brand/SKU/Description,normalization,token AND,protected names,fuzzy/mode/ranking,D7 Product-only orphan and cross-variant SKU negatives;T021/T027/T029 |
| US1.specification | T030 after T003/T008 | Approved D1 typed facts,searchable/filterable separation and same-variant technical keyword hits;T030/T031 |
| US2.category-brand | T033 before T034 | Descendants,brand OR,unknown/malformed selections;T036/T037 |
| US3.configuration | T039 after T003, before predicate implementation | Price/specification,same-variant conflicts,RK07 exclusion ranking (repository T043 and HTTP T046),D5 support/error outcomes and typed units;T045/T046/T048 |
| US4.listing-core | T050 | Price/name/default/relevance order,all-page/null ordering expectations that do not need approved technical keys |
| US4.combined + FULL manifest | T056 after T031/T048 | Complete approved combinations and every partition;T056/T062 and final T065 require the entire manifest with no PENDING partition |

T014 calibrates only the frozen US1.core natural-name subset;its approval does not imply D1/D5/US4 are implemented. T029 closes only core query calibration/flow. T031 requires exactly US1.core+US1.specification,not future US2–US4 cases. Final complete-corpus checks cannot be replaced by subset results. Synthetic numeric/boolean definitions remain test-only;they never approve a production key.

## R3 — One ranking order and explicit requirement refinement (I1)

Adopt the proposed order as refinements of Exact:

**Exact whole-query SKU/Model > Exact whole-query Product Name > Exact Phrase > Exact Token > Prefix > Partial > Fuzzy.**

First enforce publication,all-token AND and all same-variant filters. Then choose the highest applicable tier for each eligible variant (or D7 shared-only orphan). No ranking bonus can make an ineligible variant/product match.

| Tier | Exact definition |
| --- | --- |
| 7 — whole-query SKU/Model | Original normalized complete query equals an entire eligible SKU,or an explicitly reviewed model identifier token/span/value anchored to the Product or same eligible Variant. Model sources must be in T004 scope;technical-value sources additionally require T003 approval. No invented model column,guessed family extraction,or automatic promotion of every protected numeric/unit token to MODEL |
| 6 — whole-query Product Name | Original normalized complete query equals the entire Product Name |
| 5 — exact phrase | At least two original query token occurrences form the exact contiguous normalized original query in ONE searchable field/view at valid D2 boundaries;not across fields,facts or variants. Repetitions/punctuation are preserved for phrase comparison |
| 4 — exact token | Every deduplicated token has an exact match across shared fields plus this eligible Variant;none of7–5 applies. D1 numeric equality can satisfy a token without rewriting text model IDs |
| 3 — prefix | Every token matches exact/prefix and at least one requires prefix;no partial/fuzzy |
| 2 — partial | Every token matches exact/prefix/partial and at least one requires partial;no fuzzy |
| 1 — fuzzy | At least one token requires allowed name fuzzy matching;every other token still matches and identifiers never become fuzzy |

For a token choose the best class exact4>prefix3>partial2>fuzzy1,then highest allowed source preference:SKU/approved technical identifier6,Product Name5,Brand Name/Code4,approved nonidentifier technical value3,Description1. Count each deduplicated token once. Remaining ties WITHIN a tier:sum best token classes DESC,then sum selected source preferences DESC,then sum per-token best fuzzy similarities DESC,then Product UUID ASC. Quantize each fuzzy tie contribution to six decimal places before exact decimal summation for order-independent deterministic comparison;threshold eligibility still uses the calibrated similarity comparison. No global identifier-count bonus precedes the tier/phrase decision.

Product rank is the maximum whole tuple over eligible variants. Representative remains minimum(price,variant UUID) over eligible variants and may differ from the rank-winning eligible variant. Explicit price/name sort overrides relevance and retains UUID tie-breaking and D7 nulls-last.

**Reported conflict/refinement**: old researchR3/T025 compared exact identifier COUNT before phrase inside one Exact tier;FR-008 could instead require phrase first. That old count-first comparator is superseded. Now a complete query SKU/model wins tier7,complete Product Name wins6,phrase wins5 and a mere exact SKU token in a scattered multi-token match stays4. This preserves broad Exact>Prefix>Partial>Fuzzy and exact technical-identifier protection while making the formerly ambiguous order explicit. It does not bypass same-variant constraints or add an identifier source not backed by reviewed scope.

### Golden ranking scenarios (synthetic reviewed-case requirements,not executed results)

| Case | Query/eligible candidates | Required expectation | Owning increment and verification |
| --- | --- | --- | --- |
| RK01 | Same complete query is a whole eligible SKU or reviewed model vs only an entire ordinary Product Name | Tier7 before6;whole SKU/model equality remains punctuation/digit preserving | US1.core:freeze T005;repository T021;HTTP T027;acceptance T029 |
| RK02 | sony alpha:synthetic ordinary whole Product Name (no reviewed whole-query model/SKU equality) vs Description exact phrase vs Sony in Brand and alpha in eligible SKU | 6>5>4;the scattered exact SKU token does NOT beat the phrase | US1.core:freeze T005;repository T021;HTTP T027;acceptance T029 |
| RK03 | Two original query words form a phrase in one field vs separated exact tokens in different fields | 5 before4;no synthetic cross-field phrase | US1.core:freeze T005;repository T021;HTTP T027;acceptance T029 |
| RK04 | Exact-token,prefix,partial and allowed name-typo candidates | 4>3>2>1 regardless of additive lower-tier scores | US1.core:freeze T005;repository T021;HTTP T027;acceptance T029 |
| RK05 | One exact model/SKU token plus another fuzzy-required token vs all-token exact candidate | All-exact4 wins;one exact identifier cannot promote a fuzzy-dependent result to7 | US1.core:freeze T005;repository T021;HTTP T027;acceptance T029 |
| RK06 | WH-1000XM5 vs a misspelled/different identifier | Preserve exact value;no fuzzy admission for protected model/SKU;nonmatching identifier has no eligible rank | US1.core:freeze T005;repository T021;HTTP T027;acceptance T029 |
| RK07 | A rank7 variant fails price/spec filter;another variant is eligible at4 | Rank product only from eligible4;no promotion from excluded variant | US3.configuration:freeze T039;repository T043;HTTP T046;acceptance T048 |
| RK08 | Identical tier/token/source/fuzzy totals on unchanged data | Product UUID ASC deterministically | US1.core:freeze T005;repository T021;HTTP T027;acceptance T029 |
| RK09 | Repeated normalized token query | Membership/rank sums dedup;phrase uses original sequence and only real repeated source phrase qualifies | US1.core:freeze T005;repository T021;HTTP T027;acceptance T029 |
| RK10 | Highest-ranked and cheapest variants differ but both qualify | Rank from best eligible tuple;display pair from cheapest eligible(price,UUID);never mix in an ineligible variant | US1.core:freeze T005;repository T021;HTTP T027;acceptance T029 |

**Explicit partition boundary (A1/RK07 correction):** US1.core requires exactly RK01–RK06 and RK08–RK10. RK01 uses whole SKU or a T004-reviewed model anchored in Product/SKU fields,without an approved technical-value source. RK10 uses q-only eligibility:both differently ranked/priced variants satisfy the keyword,with no category,brand,price,specification or explicit-sort parameter. It therefore tests the existing core rank/representative distinction without future story functionality. No core case requires US2/US3/US4.

RK07 is owned by US3.configuration and remains PENDING until T039 freezes price-only,approved-specification and combined exclusion fixtures. T043 executes repository cases;T046 executes HTTP cases;T048 requires both reports. Each excludes the otherwise rank7 variant,keeps another variant at tier4,and includes a competitor proving that the excluded tuple cannot promote the Product. Specification cases require genuine T003/T008 approval;synthetic fixtures do not grant it. T005/T021/T029 do not execute or gate on RK07. This removes the semantic edge from core acceptance to future US3 while keeping the approved ranking algorithm and every original golden expectation unchanged.

**Final acceptance:** T056 reruns ALL RK01–RK10 against the assembled endpoint,including all RK07 exclusion forms. T062 retains their functional regression coverage;T065 requires the full reports with no missing,PENDING or skipped golden case. Per-increment ownership never makes a golden case optional for the full feature.

Case fixtures/expected IDs must be frozen in the appropriate corpus partitions;model facts/vocabulary here are test assumptions requiring sign-off,not evidence of registry approval or measured calibration.

## R4 — Local acceptance vs deployment readiness (D2)

T065 requires T031/T037/T048/T057,T061,T062,T064 and approved-registry/runtime evidence;T063 is NOT its dependency. Local PASS is possible with deployment NOT_VERIFIED/BLOCKED. T066 can issue a scoped local handoff while T063 remains unchecked/blocked. It must never call the system deployed or production-ready in that state.

T063 retains the read-only intended-target verification requirement and its actual target/authorization prerequisites. A later production rollout requires G-D PASS,matching approved registry/category/version scope,valid target migration/recovery prerequisites and separate rollout authorization. Local PostgreSQL/Testcontainers proof cannot stand in for a target. T065 records the available target result or explicit NOT_VERIFIED with timestamp/reference;it cannot fabricate or auto-close that deployment task. Full local functional scope still includes approved specifications;this is not approval-free acceptance.

## R5 — Parallel test isolation (V1)

[P] permits disjoint-file authoring after dependencies;it does not by itself permit concurrent test processes. Default integration/migration/benchmark execution is SERIAL across jobs/classes with the existing test tooling;controlled concurrent transactions INSIDE an isolated publication/race test remain mandatory and are not serialized away. Current shared jdbc:tc backend_test URL is not evidence of per-worker isolation.

Parallel integration execution is opt-in only after proof of unique container ID AND database/schema identity per worker/run,independent connection pools and fixture namespaces. Capability-loss/extension tests and orphan/trigger/migration tests require a dedicated disposable PostgreSQL INSTANCE,not just a schema in a shared database. Storage/signing integration workers also require separate LocalStack instance/bucket/object namespaces or serial execution. Never run destructive fixture/failure setup on a shared/deployment DB. A failed isolation proof means serial execution,not weaker assertions.

T009 defines the ownership/identity protocol;T019 verifies the default serial baseline. T028 capability/connection tests,T045/T046 and T053/T054/T055 must record isolation or run sequentially. Benchmark timing uses exclusive measured resources;regression and benchmark have independent dependency readiness but need not run simultaneously on the same host. No new plugin/framework is preapproved.

## R6 — Operational probe using the existing endpoint (V2)

No new endpoint,Actuator dependency or infrastructure is needed. For the NEW feature-capable backend,define the existing Compose backend healthcheck URL as:

`http://127.0.0.1:8080/api/products?q=catalog-readiness-probe&page=0&size=1`

A nonblank keyword must check required PostgreSQL search capability before candidate matching EVEN if it is a protected token,has zero matches,or fuzzy.enabled=false. Healthy capability/schema yields ordinary200 (empty results are healthy);recognized capability loss yields503 and ApplicationAvailability REFUSING_TRAFFIC. Blank/absent keyword listing retains its approved behavior;the operator probe intentionally uses a nonblank keyword so it cannot mask the loss with the legacy path. Other DB/startup failures are non-success and fail the existing HTTP healthcheck too. Do not claim ApplicationAvailability alone changes Docker health.

Update the Compose healthcheck ONLY together with the backend version supporting the new q contract;the old backend rejects q and must keep its old healthcheck until that coordinated rollout. This revision changes only documents. The T026 capability implementation,now including the future compose.dev.yaml healthcheck change,binds internal state and real probe HTTP status. T028 verifies both configured modes,capability loss and healthy recovery after an explicitly restored capability is proven by a later probe;no request-time extension install,automatic repair or retry loop. Diagnostic exact-only200 is not full-feature release approval.

## R7 — Independent regression and benchmark gates (D3)

T061 performance and T062 functional regression both become ready after T060/T057 (plus their own protocol/tool prerequisites),not after each other's PASS. Final T065 requires BOTH. Failures are reportable independently;one does not prevent collecting the other's evidence.

Use existing approved test selection facilities to separate timing-sensitive ProductSearchPerformanceIT and existing CatalogPublicReadPerformanceIT into the T061 measurement job. T062 runs the complete NON-BENCHMARK functional regression/discovery set;no test/metric is deleted. Functional assertions co-located in performance tests remain in T061 and all required outcomes are covered by the final union of both reports. Record exact discovery/inclusion/exclusion commands and demonstrate the union covers the whole intended suite. Do not add a plugin/dependency silently or mislabel a selected subset as every configured test. Scheduling may serialize both on a resource-constrained host to protect valid latency measurements without creating a PASS dependency.

## Verification and scope retained

All eight NEEDS VERIFICATION IDs remain CHK005/009/020/026/028/034/036/037. Methods in design-resolutions.md still apply,with the scoped gates/partitions above. Approval,protected-model scope,corpus answers,normalization proof,capabilities,semantic contract validation and measured test/calibration reports remain future evidence tasks. This revision resolves seven documentation findings;it does not claim a new read-only analyze has passed or that implementation may bypass its prerequisites.

## Task ID migration for this remediation

Task count remains66. To retain numeric dependency order:previous T007 SQL proof→T013;T008 fuzzy prototype→T014;T013 Java primitive→T007;T014 registry loader→T008. Previous typed contributor T023→T030;previous T024–T030→T023–T029. Other IDs stay stable. Current references use new IDs;historical analysis findings refer to their earlier IDs as reported. No task/FR/SC is deleted.

## Final owner-authorized implementation evidence

See [mission-scope.md](mission-scope.md),[local verification](evidence/verification-summary.md),[coverage](evidence/implementation-coverage.md) and [handoff](evidence/handoff.md). New V16–V19 and generated description cache/conservative typo guard are reported design deltas;the original20FR/6SC and all RK expectations remain. Actual1500 performance is required;10k is exploratory under resumed owner policy. T059 optional index work is deferred with no-index dependency resolution;T063 deployment remains blocked. Historical proposal/pending notes above are retained as history,not current approval or execution state.
