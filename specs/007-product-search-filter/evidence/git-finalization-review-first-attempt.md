# Git finalization review — BLOCKED (2026-11-10)

Requested direction: `features/SearchAndFilter` → `draft/version-1`;merge commit,no push. **No staging,commit,branch switch,or merge was performed.** Both local branches and fetched origin target remained `c5bd4e7eeb39d9b06a0c7e36af9a65635058613f` at inspection. Neither branch deleted.

## Review

Reviewed full tracked diff and all untracked source/documentation/evidence payloads,including compressed files. No unrelated payload,secrets,temporary database files,or compiled files identified. Product Catalog,Brand/Specifications,Detail/Wishlist changes are necessary dependencies and preserved. V15 is preexisting untracked foundation,byte-identical to baseline;V1–V15 SHA-256 unchanged. V16–V19 present. Read-only repositories unchanged. `.gitignore` changes preserve shared Spec Kit visibility and machine-local exclusions.

Review corrections only:expanded wildcard imports per constitution in14Java files;both affected production classes have equivalent executable disassembly. Corrected stale design-only OpenAPI metadata;no schema/behavior change. Source/tests were recompiled and relevant regression run. `git diff --check` passes.

## Actual validation

- Full serial functional command: **PASS**,342unit/MVC +359IT,zero failures/errors/skips. Includes migration fresh/upgrade/failure handling,Catalog,Detail,Wishlist,publication,image/signing,mode headers.
- After explicit-import cleanup:342unit/MVC PASS;62of63focusedIT PASS. Source1500 timing test FAIL:normal `laptop` p95 **1193.911921ms** against **1000ms**,20warmups+100samples. Synthetic1500 and legacy Catalog performance tests passed.
- Controlled separate-JVM source1500 rerun: **FAIL**,normal p95 **1017.153493ms** against unchanged **1000ms**,20warmups+100samples;browse104.058832ms. Data statement limits passed (browse4,normal5+1SET). Plan capture test passed. Later fuzzy/category/combined scenarios were not reached by this fresh measurement;historical metrics retained separately.
- OpenAPI3.1 approved pinned validators: **PASS**,41capability probes/60refs.
- Historical705/0/0/0 snapshot remains archived;it must not be presented as the result of this finalization attempt. Cause of new timing difference is not established. No production query or functional assertion was changed.

This is the required1500-source benchmark,not optional10000exploration. Accordingly finalization stops without rewriting behavior or relaxing assertions. Performance is a **FAIL**;commit/merge are **BLOCKED**. Post-merge verification is **NOT_VERIFIED** because no merge exists.

## Tasks and next action

Retain accepted task matrix **64PASS,T059DEFERRED,T063BLOCKED** as requested. These historical statuses do not override the new red finalization gate. T059 remains no justified new index;T063 remains no approved deployment target.

Next:investigate the reproducibility/performance failure within approved change scope,rerun the same assertion unchanged,then stage explicit reviewed paths,commit feature,merge `--no-ff` into target and run post-merge regression. No repeated-until-green measurement,no production/shared DB writes. All tests used disposable containers. All user changes retained;no commit,push,deployment,or destructive Git command.

## Reproduce

```bash
mvn -o -f demo/pom.xml '-Dit.test=*IT,!*PerformanceIT' -Djunit.jupiter.execution.parallel.enabled=false verify
mvn -o -f demo/pom.xml -Dit.test=ProductSourceWorkloadPerformanceIT -Djunit.jupiter.execution.parallel.enabled=false test-compile failsafe:integration-test failsafe:verify
/tmp/search-git-final-validator/bin/python specs/007-product-search-filter/evidence/probe-openapi-capabilities.py --approved-tools
git diff --check
```

Raw logs/metrics:`git-finalization-*.log.gz`,`git-finalization-*-source-performance.json`;suite identities before isolated rerun:`git-finalization-before-isolated-results.json`;migration hashes and outcome:`git-finalization-review.json`. Existing historical artifact indexes describe their original capture;`git-finalization-artifact-index.json` identifies current finalization artifacts.
