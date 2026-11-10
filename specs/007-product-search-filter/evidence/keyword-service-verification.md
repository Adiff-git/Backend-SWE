# T022 — keyword listing service orchestration tests

**Status: PASS —8/8 focused tests,0 failures/errors/skips.** T019 prerequisite verified independently;execution began only after T020's author/run finished. Source path:`demo/src/test/java/com/example/demo/catalog/service/ProductKeywordSearchServiceImplTest.java`.

## Scope and implementation

New JUnit test class instantiates actual ProductCatalogServiceImpl with actual converter,DTOs,entities and Bean Validator. Reuses existing CatalogFixtures,request/criteria,normalizer and candidate projections. Repository/image/storage collaborators are mocked;no Spring ApplicationContext or database is started. No production selection/query algorithm is implemented. Every case asserts no ProductVariantRepository call and no detail fetch,preventing service-level cheapest-overall reloading/recalculation.

| Case | Verifiable result |
| --- | --- |
| Product-only keyword | Maps repository-selected cheapest eligible pair;existing higher-priced variant is not selected by service |
| Synthetic SKU-specific keyword | Maps costlier eligible SKU pair and exact30-digit price despite cheaper overall fixture;structural token remains protected |
| Query order vs batch order | Reassembles candidate order with correct per-row pair;batched category/image loads |
| Actual normalization | NFD/accents/đ and original repeated phrase sequence retained independently of token dedup;empty later page preserves snapshot total/page count |
| D7 shared-field projection | Real Product UUID with exactly two null metadata values;no fake zero/variant |
| Publication/signing order | Prepare signer → ordered-lock repository method → sign visible only → close;late hidden row not signed/returned;count keeps statement snapshot |
| Prepare failure | Safe storage failure propagates;no publication lock/retry |
| Local sign failure | Signer closed and safe failure propagated;no silent empty success |

Repository eligibility in these tests is a **mocked input contract**,not proof of actual Product/SKU matching,orphan database membership,model vocabulary,fuzzy/ranking or specification support. The latter tasks and approvals remain pending. The new tests cover service responsibilities only;T019 retains historical actual DB/orphan/publication evidence.

## Reproduction and results

```sh
mvn -o -f demo/pom.xml -Dtest=ProductKeywordSearchServiceImplTest -Djunit.jupiter.execution.parallel.enabled=false test
```

8 tests,0 failures/errors/skips;exit0. [keyword-service-results.json](keyword-service-results.json) includes test identities,source hash,prerequisite receipt and coverage. [keyword-service-test-output.txt](keyword-service-test-output.txt) records the actual output.

Relevant Catalog/Brand-spec converter/validation/Detail/security/Wishlist unit/MVC regression command and discovery are in [keyword-foundation-regression-results.json](keyword-foundation-regression-results.json):**109 tests in12 classes,all pass,no skips**,including the8 T022 tests. Output:[keyword-foundation-regression-output.txt](keyword-foundation-regression-output.txt).

The T020 class is executed separately and retains2 verified RED header assertions;it is explicitly outside that named green regression selection. Its other15 tests,including four unchanged baseline scenarios,pass. Thus unique combined executed outcomes are124 green +2 expected RED;do not misreport the complete suite as green. No PostgreSQL integration rerun was needed for these test-only changes;no T019/T062 gate was re-executed or newly approved.

## Handoff

No application/migration/contract changes or dependencies;all existing uncommitted work preserved. T022 complete independently from T020 RED behavior. Both tasks are test-authoring tasks only. T025 still waits T024;T021 waits T005,and T005 waits explicit final T004 sign-off. T003 approval is separate. After completing these tasks,there is no new application coding task ready;review/approval work on T003/T004 can continue. No future task,commit or push executed.
