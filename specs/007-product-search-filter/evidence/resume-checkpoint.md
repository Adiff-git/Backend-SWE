# Resume checkpoint — recovered baseline

Recovered previous mission: complete four-story local MVP;no commits/push/deployment or edits outside Backend. Branch features/SearchAndFilter,HEAD c5bd4e7eeb39d9b06a0c7e36af9a65635058613f.

Previous full functional run:341 unit/MVC +346 integration tests PASS (`/tmp/search-final-functional.log`). Later supplemental run:24 tests,one optional10k fixture error;other23 PASS. Historical performance1085ms was actually on1500 synthetic products,before optimization—not a10k measurement. It is preserved as historical evidence,not a current acceptance blocker.

Current task:fix optional fixture string option values without changing DB constraints;then rerun final functional regression serially and required1500 benchmark. Source code implements all four stories;task statuses still reflect16 prior completed tasks and need evidence reconciliation. Final benchmark after hit-materialization optimization was interrupted/not executed.

Known failures:
- com.example.demo.catalog.repository.SearchQueryPlanIT.exploratoryTenThousandAndVariantSkew_shouldKeepSelectiveResultsAndRecordPlans: Hibernate transaction: Unable to commit against JDBC Connection; ERROR: Product 43c492a7-a20d-0add-4d68-0e7dff5fc6c3 has invalid variant options
  Where: PL/pgSQL function check_catalog_variant_invariants() line 55 at RAISE
- com.example.demo.catalog.ProductSearchPerformanceIT.benchmark_shouldMeasureFullServiceAndAllJdbcStatements: [p95 for sony alpha]
Expecting actual:
  1085.065758
to be less than or equal to:
  1000.0

Exact next action:use string JSON option values for scale fixture;run `mvn -o -f demo/pom.xml '-Dit.test=*IT,!ProductSearchPerformanceIT,!CatalogPublicReadPerformanceIT' -Djunit.jupiter.execution.parallel.enabled=false verify`. Then run both performance ITs serially. Review all5 fuzzy false positives;write final evidence/task matrix.

Files modified before resume (preserved):
```text
 M .gitignore
 M compose.dev.yaml
 M demo/src/main/java/com/example/demo/catalog/controller/ProductCatalogController.java
 M demo/src/main/java/com/example/demo/catalog/converter/ProductCatalogConverter.java
 M demo/src/main/java/com/example/demo/catalog/dto/response/ProductDetail.java
 M demo/src/main/java/com/example/demo/catalog/dto/response/ProductPage.java
 M demo/src/main/java/com/example/demo/catalog/dto/response/VariantSummary.java
 M demo/src/main/java/com/example/demo/catalog/repository/ProductRepository.java
 M demo/src/main/java/com/example/demo/catalog/service/ProductCatalogService.java
 M demo/src/main/java/com/example/demo/catalog/service/ProductCatalogServiceImpl.java
 M demo/src/test/java/com/example/demo/auth/integration/LegacyMigrationIT.java
 M demo/src/test/java/com/example/demo/catalog/CatalogContractIT.java
 M demo/src/test/java/com/example/demo/catalog/CatalogMigrationsIT.java
 M demo/src/test/java/com/example/demo/catalog/CatalogQueryCountIT.java
 M demo/src/test/java/com/example/demo/catalog/ProductDetailApiIT.java
 M demo/src/test/java/com/example/demo/catalog/ProductListingApiIT.java
 M demo/src/test/java/com/example/demo/catalog/controller/ProductDetailControllerTest.java
 M demo/src/test/java/com/example/demo/catalog/controller/ProductListingControllerTest.java
 M demo/src/test/java/com/example/demo/catalog/converter/ProductCatalogConverterTest.java
 M demo/src/test/java/com/example/demo/catalog/repository/ProductDetailRepositoryIT.java
 M demo/src/test/java/com/example/demo/catalog/service/ProductCatalogServiceImplTest.java
 M demo/src/test/java/com/example/demo/wishlist/repository/WishlistRepositoryIT.java
 M specs/001-product-catalog/contracts/openapi.yaml
?? demo/src/main/java/com/example/demo/catalog/dto/request/
?? demo/src/main/java/com/example/demo/catalog/dto/response/BrandSummary.java
?? demo/src/main/java/com/example/demo/catalog/dto/response/ProductListingItem.java
?? demo/src/main/java/com/example/demo/catalog/exception/SearchCapabilityUnavailableException.java
?? demo/src/main/java/com/example/demo/catalog/repository/CatalogSearchSql.java
?? demo/src/main/java/com/example/demo/catalog/repository/ProductSearchRepository.java
?? demo/src/main/java/com/example/demo/catalog/repository/ProductSearchRepositoryImpl.java
?? demo/src/main/java/com/example/demo/catalog/repository/SpecificationKeywordContributor.java
?? demo/src/main/java/com/example/demo/catalog/search/
?? demo/src/main/java/com/example/demo/catalog/validator/ProductSearchValidator.java
?? demo/src/main/resources/catalog/
?? demo/src/main/resources/db/migration/V15__catalog_search_support.sql
?? demo/src/main/resources/db/migration/V16__catalog_search_matching.sql
?? demo/src/test/java/com/example/demo/catalog/ProductSearchPerformanceIT.java
?? demo/src/test/java/com/example/demo/catalog/controller/ProductSearchControllerIT.java
?? demo/src/test/java/com/example/demo/catalog/repository/CatalogSearchMigrationIT.java
?? demo/src/test/java/com/example/demo/catalog/repository/FuzzyCalibrationIT.java
?? demo/src/test/java/com/example/demo/catalog/repository/ProductSearchBaselineRepositoryIT.java
?? demo/src/test/java/com/example/demo/catalog/repository/ProductSearchRepositoryIT.java
?? demo/src/test/java/com/example/demo/catalog/repository/SearchQueryPlanIT.java
?? demo/src/test/java/com/example/demo/catalog/search/
?? demo/src/test/java/com/example/demo/catalog/service/ProductKeywordSearchServiceImplTest.java
?? demo/src/test/java/com/example/demo/catalog/validator/ProductSearchRequestTest.java
?? demo/src/test/resources/catalog/
?? demo/src/test/resources/junit-platform.properties
?? specs/007-product-search-filter/
```

## Milestone — conservative fuzzy guard authored (NOT_VERIFIED until rerun)

Fixed optional10k fixture:base variant now has nonempty string option0;extra options use n::text. DB constraints unchanged. Added V17__catalog_search_fuzzy_typo_guard.sql and production predicate requiring≤one edit/adjacent transpose in addition to threshold0.32/protection. Added actual-pair calibration and repository/migration assertions;V1–V15 unchanged. Intended effect:reject3/5 original multi-edit false positives;remaining canon/canyon and intel/inter stay explicitly ambiguous. No measured improvement is claimed yet.

Current command `/tmp/search-resume-functional.log` was started before the new V17 source changes and cannot certify them. Exact next action after it ends:rerun the same full serial functional command with current sources,then both required performance ITs. Record actual reports rather than reusing stale XML. Optional10k is diagnostic only under resumed owner policy. tasks.md statuses not changed yet.

## Milestone — actual-source workload and malformed-text safety authored

Added frozen1500 source text/category/brand/spec fixture and ProductSourceWorkloadPerformanceIT (separate timing job;synthetic owned UUIDs/prices/publication,not target import evidence). Source description median691/mean903 characters;2 invalid U+0097 controls were found. Added V18 fast normalization plus safe stored-field wrapper;raw source values remain unchanged and invalid query controls still400. Strict normalization parity will be checked against53 primitive cases and4500 frozen source fields. Added malformed stored-description repository test and stricter registry startup schema/approval checks.

Current full run `/tmp/search-resume-final-functional.log` certifies V17 sources compiled before these V18/registry additions;do not reuse it as final V18 acceptance. Next:run current migration/repository/registry tests and actual-source timing job,then final complete regression. All edits remain uncommitted;V1–V15 unchanged. Optional10k fixture now uses string options including base option0;rerun must determine its result.

## Milestone — actual1500 performance root cause and V19 (pending verification)

V18 source normalization parity executed:53 primitive cases and4500 source fields;1 invalid stored description field containing2 U+0097 characters excluded without rewriting. Actual-source baseline plans are in evidence/source-plans-before/:laptop5664.764ms,samsnug5219.867ms (EXPLAIN execution times,not p95). Most cost is CTE field_tokens (≈4.5s) and repeated non-fuzzy description context materialization.

Added V19__catalog_description_token_cache.sql:one generated description-token column maintained transactionally by PostgreSQL,derived through immutable pinned normalizers/tokenizer. No search replica/job/index or source rewrite. Query now uses cached description tokens,omits irrelevant non-fuzzy token/context materialization and only builds shared views for eligible Products. Original business-field snapshots exclude ONLY the new derived column and separately verify its backfill/update semantics. Recovery fixture successor moves to V20 on dedicated disposable DB;no V1–V15 edits.

Current command: `/tmp/search-v19-source-verification.log`;registry unit tests,migration/repository/HTTP tests,and actual-source plan+100-sample benchmark. No V19 performance PASS yet. Exact next action:inspect this log/results;if successful run final full serial functional regression plus original two timing ITs;then reconcile task/evidence/handoff. Preserve all previous edits.

## Milestone — V19 verified and actual1500 benchmark PASS

`/tmp/search-v19-source-verification.log` BUILD SUCCESS. V19 fresh/V15-upgrade/backfill/update tests and repository/HTTP tests passed. Actual1500 source workload20 warmups+100 samples/scenario: browse58.805ms,normal laptop594.539ms,fuzzy samsnug624.247ms,brand/category11.822ms,combined105.252ms p95. Normal broad query is above the suggested500ms goal;all scenarios meet original1s limit. No N+1:3/4 data statements,SET separate. Raw source remains unchanged;target imported distribution remains NOT_VERIFIED (source1:1 fanout used as controlled proxy).

Current task:T054 mixed-page publication race regression. New test pauses signer preparation,archives an imageless Product while a pictured Product remains public,and requires that private row to be suppressed. Run `/tmp/search-imageless-race-red.log` before any fix. Next:if reproduced,add one final batched publication recheck for imageless rows,rerun focused tests,then full functional regression and final timing job. No task checkbox updated from incomplete evidence yet.


## Milestone — mixed-page publication race reproduced and fixed (focused verification running)

`/tmp/search-imageless-race-red.log` reproduces a private imageless Product leaking while signer preparation is paused for another pictured Product. Added one final batched PUBLISHED recheck for imageless candidates after signer preparation/locks. No per-product query/refill/global lock;counts remain the candidate snapshot. Focused unit/service/MVC and actual PostgreSQL race/query-count tests now run in `/tmp/search-publication-fix-verification.log`. This intentionally adds one bounded SQL statement to imageless listing journeys;final timing/count evidence must be refreshed.

Exact next action:fix any orchestration assertion mismatch without weakening security checks,then freeze code,run full serial non-benchmark regression and all3 performance classes. Source benchmark prior to this additional check is preserved separately.

## Milestone — publication fix GREEN;production code frozen

`/tmp/search-publication-fix-green.log` BUILD SUCCESS:focused service/unit/MVC plus actual PostgreSQL publication race and SQL-budget tests. One orchestration test now asserts exactly2 batched reads (initial+mandatory final imageless recheck),retaining all membership/order/price/security assertions. The reproduced RED assertion remains enabled and is now green.

Production code/tests are frozen pending final regression results. Current command `/tmp/search-mvp-final-functional.log`:full unit/MVC and every non-performance IT,serial. Exact next action:if green,run `mvn -o -f demo/pom.xml -Dit.test=ProductSearchPerformanceIT,ProductSourceWorkloadPerformanceIT,CatalogPublicReadPerformanceIT -Djunit.jupiter.execution.parallel.enabled=false test-compile failsafe:integration-test failsafe:verify` with exclusive timing resources. Then collect report union,verify V1–V15/read-only repository hashes,update66-task matrix and final handoff. No commit/push/deployment.

## Milestone — final full functional regression PASS

`/tmp/search-mvp-final-functional.log` BUILD SUCCESS on V19+publication fix,all unit/MVC and non-performance integration tests serial,zero failures/errors/skips. Last supplemental test authoring adds filter/combined every-sort/page matrix and connection-local invalid-legacy-document views;public DB constraints/rows are never weakened. These two classes are rerun in the current final measured job before/alongside serial timing classes.

Current command `/tmp/search-mvp-final-measured.log`:ProductSearchRepositoryIT,ProductSearchControllerIT,ProductSearchPerformanceIT,ProductSourceWorkloadPerformanceIT,CatalogPublicReadPerformanceIT. This executes all original timing assertions plus real-source1500 scenarios and both final supplements. Exact next action:inspect full result/individual XML,then copy durable evidence and reconcile tasks/docs. No more production changes unless a genuine failing requirement is found.


## Current milestone — final family-boundary correction awaiting acceptance

All prior704 tests and actual1500 HTTP measurement passed. Final code review found that Tablet nested under Laptop must not inherit Laptop-only RAM;registry recursion now stops at another canonical family root. Added a regression proving Tablet RAM400,ancestor valid filter200 empty,and Tablet diagonal still active. Plain category recursion remains unchanged.

Current `/tmp/search-mvp-family-final-functional.log` runs complete serial regression. Affected task closures and dependent gates are temporarily NOT_VERIFIED/unchecked in tasks.md and the matrix;prior accepted states are retained in task-state-before-family-recheck.json. Handoff documents reflect the previous measured revision until refreshed. Exact next action:after this full run succeeds,rerun ProductSourceWorkloadPerformanceIT,collect fresh evidence,restore only evidenced PASS states,render final docs and verify preservation/dependency graph. No production changes beyond this small registry boundary fix are planned.

## Milestone — full family-boundary regression PASS;last scoped verification running

`/tmp/search-mvp-family-final-functional.log` BUILD SUCCESS on full unit/MVC/non-performance suite including Laptop/Tablet boundary regression. Final boundary list conservatively stops inheritance at recognized unsupported catalog families too (including explicitly excluded Smartphone);normal category descendant browsing is unchanged. The same regression now proves Smartphone exclusions. V19 upgrade test additionally executes directV14→V19 as well as V15→V19/fresh,without changing migrations.

Current `/tmp/search-mvp-family-final-http-performance.log` runs all relevant unit/parser/normalizer/vocabulary/registry/header tests,complete repository/API/race/query-count/Catalog/Wishlist/migration suites and refreshed actual1500 HTTP timing. These are scoped final supplements to the full suite,not an assertion that unrelated Auth tests were rerun after a registry-only predicate extension. Latest per-suite report union must include every discovered case. All affected closures remain NOT_VERIFIED until this succeeds;then restore reviewed accepted states,collect/render evidence and finish. No further implementation changes planned.


# FINAL CHECKPOINT — LOCAL MVP COMPLETE

All final jobs succeeded. Current task:none;implementation and local handoff complete. Completed task IDs:T001–T058,T060–T062,T064–T066 (64 PASS). T059 DEFERRED/no demonstrated additional index;conditional no-index prerequisite resolution documented. T063 BLOCKED/intended deployment target,capabilities,taxonomy/imported distribution NOT_VERIFIED. Source model-rank7,battery,whole-dataset semantic/manual relevance review and normal500ms tuning remain deferred subscopes. No pending implementation task required for the authorized local MVP.

Final suite union:342 unit/MVC+363 integration=705 tests,0 failures/errors/skips. Full functional run plus scoped final family-boundary/API/migration/Wishlist/query-count supplements and3 timing classes are recorded;latest per-suite XML is counted once. Actual1500 full HTTP p95:64.3ms browse,600.8ms laptop,631.8ms fuzzy,12.1ms brand/category,121.6ms combined.20 warmups/100 samples,concurrency1. Suggested normal500ms goal not met;retained1s/fuzzy1s pass. No unexpected timeout/N+1;4–5 data SQL,SET separate.

Fuzzy0.32+one-edit guard:15TP/2FP/0FN,88.24% precision/100% recall on24 controlled pairs;not perfect real relevance. All20FR/6SC mapped,all RK cases mandatory and verified,66-node dependency DAG valid. Original review checklist markers unchanged.

V16–V19 fresh and directV14/V15 upgrades,recovery/failure/checksum-stop,53 primitive+4500 source-field normalization parity and generated-cache maintenance verified on disposable targets. V1–V15 hashes unchanged;Catalog-Data/AI-SWE snapshot hashes unchanged;no preexisting file deleted. No shared/prod database access/migration,commit,push or deployment.

Files:see preservation-final.json for baseline-relative modifications/creations and final Git state;handoff.md,implementation-coverage.md,test-results-final.json,task-completion-matrix.json and compressed logs/plans contain durable evidence. The original stopping point and intermediate failures remain above as history;they are not current failures.

Exact next action:review handoff.md or reproduce the commands below. No coding task is pending. Production work requires a separately approved target;do not infer rollout readiness.

```bash
# Backend-SWE root;serial disposable Testcontainers only.
mvn -o -f demo/pom.xml '-Dit.test=*IT,!*PerformanceIT' -Djunit.jupiter.execution.parallel.enabled=false verify
mvn -o -f demo/pom.xml -Dit.test=ProductSearchPerformanceIT,ProductSourceWorkloadPerformanceIT,CatalogPublicReadPerformanceIT -Djunit.jupiter.execution.parallel.enabled=false test-compile failsafe:integration-test failsafe:verify
python3 specs/007-product-search-filter/evidence/collect-mvp-evidence.py
```

Git branch:features/SearchAndFilter;HEAD:c5bd4e7eeb39d9b06a0c7e36af9a65635058613f,unchanged. Working tree remains dirty with prior and mission changes;none discarded.


## Git finalization checkpoint — validation in progress

Owner now authorizes feature commit and features/SearchAndFilter→draft/version-1 merge,no push. Original feature/target HEAD c5bd4e7eeb39d9b06a0c7e36af9a65635058613f;both local/remotes existed and target fetch was equal. Complete diff/file review found no unrelated payload or secrets. V15 is an untracked preexisting byte-identical dependency;V1–V15 baseline hashes and read-only repositories unchanged. Imports were made explicit per constitution;production disassembly unchanged. OpenAPI stale design-only metadata corrected,semantics unchanged.

Fresh full functional validation passed. Mixed-context revalidation currently reports source-workload broad-query p95≈1194ms over its1s assertion;no implementation change or assertion weakening is permitted. A separate JVM source benchmark from handoff will be run after the current job ends;keep both outcomes. No commit/staging/merge yet. Exact next action:inspect /tmp/search-git-final-revalidation.log,then run isolated ProductSourceWorkloadPerformanceIT. If validation remains red,stop Git finalization and report the actual blocker without discarding changes. Historical64 PASS/T059DEFERRED/T063BLOCKED describe the accepted implementation snapshot;new timing evidence must be disclosed.


## Git finalization stopped safely — 2026-11-10

Status: **BLOCKED before staging/commit/merge**. Separate-JVM required source1500 benchmark also FAIL:normal laptop p95=1017.153493ms>1000ms (mixed-context1193.911921ms). Full functional342+359PASS;post-import342unitPASS/62of63focusedITPASS;OpenAPI41probesPASS. No behavior or assertions changed. Cause not established;do not retry solely for a green timing sample. Full report/raw evidence:git-finalization-review.md and git-finalization-* artifacts.

Current branch features/SearchAndFilter;HEADc5bd4e7eeb39d9b06a0c7e36af9a65635058613f. Target remains draft/version-1 at same HEAD;both branches retained. All pending changes remain unstaged. V1–V15 and readonly repositories preserved. No commit/push/deployment. Historical64PASS,T059DEFERRED,T063BLOCKED unchanged;fresh finalization acceptance is blocked.

Exact next action:investigate reproducibility/performance within separately authorized scope;keep assertion1000ms and logs;when validated,continue explicit staging→featurecommit→target--no-ffmerge→postmergeregression. Reproduction command is in git-finalization-review.md. No application redesign is authorized by this Git-only finalization task.


## Owner-approved performance revision — validation running

2026-11-10:owner authorized per-scenario actual1500 budgets,explicit test instrumentation changes,featurecommit and feature→draft/version-1merge. No production logic/migrations changed. Run1PASS(all7scenarios,20warmups/100samples,p50/p95/max recorded);run2 running independently. Four-test count discrepancy proven:PerformanceIT classes1+2+1 explain363vs359integration. Next:preserve run2,run authoritative serial functional suite and remaining synthetic/legacy performance,OpenAPI;review exact manifest,commit feature,fetch/inspect target,--no-ffmerge,postmerge regression. Historical failure logs retained;task64PASS/T059DEFERRED/T063BLOCKED unchanged. No staging/commit/merge yet.


## Performance policy acceptance — PASS;regression running

Both independent JVM runs passed all7scenarios,20warmup+100samples each. PIDs61093/79003;run1broad601.10ms/run2broad1049.65ms;fuzzy632.40/1159.76ms;zero timeouts,4or5dataSQL+0or1SET. See performance-policy-run1.json/run2.json and raw logs/plans. Production snapshot unchanged;FR019/SC006/T061 policy synchronized;20FR/6SC/66taskIDs preserved. OpenAPI41probes60refsPASS. V1–V15/external1605file preservationPASS.

Current job:full functional command (`-Dit.test=*IT,!*PerformanceIT`) at /tmp/search-policy-final-functional.log;Maven session33811. Next:archive full results,run ProductSearchPerformanceIT and CatalogPublicReadPerformanceIT serially,confirm complete705testunion,no failures/skips;then explicit staging/commit/targetfetch/--no-ffmerge/postmerge regression. Git unchanged and unstaged.


## Full functional revalidation — PASS

Authoritative serial functional job completed342unit/MVC+359IT,zero failure/error/skip;raw log and case identities saved in performance-policy-functional*. Source snapshot unchanged. Both actual1500 independent performance runs alreadyPASS. Remaining measured job:ProductSearchPerformanceIT,CatalogPublicReadPerformanceIT(session17820,/tmp/search-policy-synthetic-legacy-performance.log),unchanged assertions. Next:verify2remainingcases,705unique-caseunion,finalreview/docupdate/staging/featurecommit,targetfetch/--no-ffmerge and postmerge verification. No staging or commit yet;branch/head unchanged.


## Final precommit acceptance — PASS (2026-11-10T03:26:09.440114+00:00)

All705unique tests PASS(342unit/MVC+363IT),case identities unchanged,0failure/error/skip. Both source performance JVMs passed individually;synthetic/legacy assertions unchanged andPASS. OpenAPI41/60PASS,migrationsfresh/V15upgrade/failure/recoveryPASS,V1–V15/externalrepospreserved. Source snapshot unchanged.64PASS/T059DEFERRED/T063BLOCKED.

Next action:review exact manifest,gitdiffcheck/stagedcheck,commit feature,fetch/inspect target,clean switch and --no-ffmerge;postmerge focused regression. Current branchfeatures/SearchAndFilter,HEADc5bd4e7eeb39d9b06a0c7e36af9a65635058613f;nothing staged/committed yet. Handoff and Git review updated;old failed attempt archived. No push/deploy.


## Staging presentation repair

Cached diff check exposed trailing whitespace in4historical logs andcheckpoint. Exact original bytes retained in*.before-whitespace-cleanup.gz;presentation whitespace cleaned only. CheckPASS;source snapshot/705testacceptance unaffected. Re-review explicit staging manifest before featurecommit.
