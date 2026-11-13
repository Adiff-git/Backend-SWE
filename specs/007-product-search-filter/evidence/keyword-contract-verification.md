# T020 — US1.core HTTP contract tests before implementation

**Status: PASS for TEST AUTHORING;HTTP keyword feature acceptance is NOT PASS.** T019 prerequisite checked complete;its165-test G1 receipt and recorded hashes verified before writing. No T003 registry/T004 vocabulary approval inferred.

## Write-set and prechecks

T020 writes existing `demo/src/test/java/com/example/demo/catalog/controller/ProductListingControllerTest.java`;T022 writes a new service test. Production files do not overlap,but tasks.md,evidence bookkeeping and Maven report/build directories are shared. Execution was SERIAL:T020 authored/run first,then T022 authored/run,then regression. Neither task ran concurrently merely because marked[P].

| Read-only checklist | Checked | Unchecked |
| --- | ---: | ---: |
| requirements.md | 16 | 0 |
| design-quality.md | 0 | 40 |

Explicit user instruction authorizes these selected test-authoring tasks with known pending scoped gates. Reviewer markers remain unchanged. Hooks absent;existing ignore files cover build/local artifacts and were not modified.

## Cases and boundary

The real controller,parser,Bean Validation,security filter/advice and DTO serialization execute through WebMvcTest/MockMvc. Replace the old custom StubService with Spring-managed MockitoBean ProductCatalogService,as required by constitution XIII;preserve all four original baseline cases/assertions. Mock only the service boundary,not the controller/parser under test. This suite cannot establish real repository keyword membership,ranking,capability availability or final search acceptance.

Thirteen new cases define anonymous q/response envelope,Vietnamese NFD/đ/Unicode whitespace,unchanged raw phrase transport,shared-normalizer repeated sequence vs deduplicated membership,structural punctuation/model/numeric preservation,blank/absent q,zero tokens/controls,duplicate q including equal values,raw256-code-point/16-token limits before normalization/dedup and supplementary-letter boundaries,literal null text,safe parameter ProblemDetail,invalid supplied bearer401 and two mode headers.

Query normalization in the MVC test exercises the existing shared primitive on captured raw text;it does not claim normalization occurs in the controller. Actual service-to-criteria normalization is verified independently in T022. Structural identifiers are synthetic/already supported D2 cases;no reviewed model rank or alphabetic identifier vocabulary is activated. No spec-positive/future category/price/sort/corpus partition is an exit requirement here.

## Actual test result

```sh
mvn -o -f demo/pom.xml -Dtest=ProductListingControllerTest -Djunit.jupiter.execution.parallel.enabled=false test
```

**17 tests:15 PASS,2 expected RED;0 errors,0 skips;Maven exit1.** Both mode cases get HTTP200 from the real controller with an explicitly mocked successful service response. Both actual environment property assertions pass (true for normal mode,false in the nested diagnostic context);only the missing response header assertions fail:

| Case | Expected | Actual |
| --- | --- | --- |
| listProducts_shouldReportFuzzyEnabledModeForNonblankKeyword | X-Catalog-Search-Mode:fuzzy-enabled | absent |
| ExactOnlyMode.listProducts_shouldReportExplicitDiagnosticModeForNonblankKeyword | X-Catalog-Search-Mode:exact-only | absent |

This proves intended missing HTTP behavior,not a compile/wiring/infrastructure failure. These are ordinary enabled JUnit assertions,not expected-exception wrappers,Disabled tests or suppressed/skipped failures. Do not call the suite green or change expected headers to null. Resolving them belongs to future T025/T026 after their prerequisites;no endpoint/configuration/capability code is added here. T020's done-when explicitly asks to define/test cases **before changing endpoint keyword behavior**,so its authoring completion does not require implementing these future behaviors now.

The fifteen passing cases include all four prior baseline cases and the real invalid-bearer401/strict-parser/safe-error checks. Per-case traceability/results: [keyword-contract-results.json](keyword-contract-results.json). Full sanitized execution output: [keyword-contract-test-output.txt](keyword-contract-test-output.txt).

## Preservation and handoff

No production code,approved keys/vocabulary,applied migrations or contracts changed. The MVC cases are US1.core transport/security contracts,not T005 frozen ranking answers or T021 repository tests. Normal/disabled headers do not establish pg_trgm readiness;that remains T026/T028 verification. T020 complete as test authoring with explicitly recorded RED;current whole build is not claimed green. Stop at selected tasks.
