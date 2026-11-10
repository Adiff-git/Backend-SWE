# Planning validation record

> Implementation revision (owner MISSION,2026-11-09): see [mission-scope.md](mission-scope.md) for authorized approvals,local best-effort fact policy and explicit planning deltas. Four keys/five family bindings are approved;source model Rank7 and battery remain deferred. Runtime taxonomy uses actual Backend UUIDs and disables missing/ambiguous family bindings. Historical pending-approval observations below are retained as history,not current authorization. Actual execution evidence is recorded separately;this notice is not a test PASS.

Date: 2026-11-08. Branch: features/SearchAndFilter. Scope: documents only. Revised after design-quality review; D1–D7 supersede the earlier planning gap conclusions.

## Artifact checks performed

- plan.md follows resolved plan template sections: Summary, Technical Context, Constitution Check, Project Structure and Complexity Tracking, with delivery/query/test details added.
- research.md records decisions, rationale, alternatives and explicit evidence limitations. A read-only research agent independently verified registry inventory/current migration facts and reviewed index/vocabulary consistency; its finding was corrected by separating immutable structural normalization from runtime vocabulary exclusions.
- OpenAPI3.1 YAML parses with installed PyYAML; all local schema references resolve; nine parameter names are distinct. This is structural validation, not execution of a full OpenAPI validator.
- Relative Markdown file links in the new planning artifacts resolve. Template placeholders and unresolved template markers are absent.
- git diff --check passes for tracked changes; new artifacts were separately checked for trailing whitespace. Existing source/.gitignore changes were preserved; no application/test/migration file was edited by planning.
- .specify/extensions.yml absent before and after planning; no hooks dispatched. No commit, push, plan-to-tasks handoff, runtime registry activation, migration or benchmark executed.

## Requirement coverage

| Requirements | Design coverage | Validation intent |
| --- | --- | --- |
| FR-001,FR-017,FR-020 | Existing public security and ProblemDetail; strict input contract, explicit fuzzy mode header/503 | Controller/security/API cases |
| FR-002,FR-016 | Pageable inputs, distinct eligibility count/page same statement, empty later-page totals | Repository/page oracle and legacy regression |
| FR-003,FR-015 | Listing-only DTO, cheapest eligible variant tie by UUID, SellingPrice reuse | Repository/service/converter and other consumer regression, D7 legacy orphan null pair and price null ordering |
| FR-004,FR-005 | Per-field matching, approved-only technical values, versioned Vietnamese normalization | D1 typed value views; D2 lexical boundaries; Unicode/đ/NFC-NFD corpus |
| FR-006,FR-014 | Token AND over Product shared fields plus one Variant; nested OR within key | Split-variant negative cases and exhaustive oracle |
| FR-007,FR-008 | Structural/vocabulary identifier protection, name-only fuzzy, token recheck, tier tuple | Short4 boundary, typo, identifier, phrase, threshold/candidate recall tests |
| FR-009 | Explicit price/name order using selected price and normalized name, UUID tie | Stable asc/desc and tied-page tests |
| FR-010 | Recursive category dedup and repeated brandId selection | Deep hierarchy, siblings, cycles and unknown IDs |
| FR-011 | Whole VND bounds, inclusive and exact decimal comparisons | Price boundaries, reversed/fraction/empty rejection |
| FR-012,FR-013 | Versioned approved registry, subtree compatibility, guarded JSON and no conversion | Registry startup/request validation, known unsupported category400, malformed legacy facts; blocked by B1 |
| FR-018 | Additive schema proposals only; no source/catalog mutation | Migration upgrade and protected-field regression |
| FR-019 | Recorded performance/EXPLAIN protocol and bounded SQL budget | p95 and statement measurement after implementation |

## Post-design conclusion

Reevaluated all40 design-quality criteria after D1–D7: **32 PASS / 0 FAIL / 8 NEEDS VERIFICATION** (previous25/7/8). All seven documentation gaps are resolved; the unchanged8 evidence dependencies are CHK005/009/020/026/028/034/036/037. Their methods, artifacts and blocking phases appear in design-resolutions.md. PASS means documented requirements quality only.

D7 specifically inspected current ProductRepository.findPublishedIdPage, ProductCatalogServiceImpl.listProducts/getProduct, ProductSummary/toSummary, source-defined V3 triggers and base OpenAPI: listing does not assert variants or document409, detail does. The revised listing preserves legacy orphan membership/count with a paired-null display exception and excludes orphans only when real variant conditions are required; existing detail409/integrity invariants remain unchanged. This is source verification, not proof of applied live schema or endpoint execution.

D1 typed keyword views, D2/D4 lexical/duplicate handling, D3 explicit capability modes, D5 unsupported-category vs no-match and D6 safe failure/recovery now have deterministic written outcomes. Nullable-pair schemas and mode headers/503 are reflected in both API artifacts. No registry approvals or actual functions/indexes were created.

The earlier phase was ready for a gated tasks backlog;current scoped readiness is recorded below. Full feature acceptance still requires actual approval and verification evidence. B1–B3 remain open; target extension/privilege/UTF8/history, semantic OpenAPI verification, normalization equivalence and later executable acceptance results remain explicit. No tasks.md generated, code changed, migration run or commit made.

## Revision validation evidence

- 40 stable checklist IDs, all reviewer-owned markers unchanged, same8 NEEDS VERIFICATION IDs retained;7 FAIL→PASS transitions explicitly mapped to D1–D7.
- OpenAPI YAML parsing/local refs, planning Markdown links and whitespace checks are document checks only; full semantic OpenAPI/tool/runtime verification remains CHK028.
- Source/config/migration checksums protected by a pre-edit snapshot; no changes outside feature documents are attributable to this revision.
- No extensions.yml exists, so no hooks dispatched. Tasks generation was explicitly deferred by the user and its setup script was not invoked.

## Current analyze-remediation validation (2026-11-08)

The later read-only analyze reported4 HIGH (D1,A1,I1,D2) and3 MEDIUM (V1,V2,D3). [analysis-remediation.md](analysis-remediation.md) R1–R7 records their document remediation. The subsequent read-only analyze found A1 partially unresolved because T005/T021 still required future-US3 RK07;the targeted correction below addresses that remaining semantic dependency without claiming runtime verification.

- 20FR/6SC IDs retained;FR-008 explicitly refines Exact into whole-query SKU/Model,whole-query Name,phrase and token,then prefix/partial/fuzzy. Golden RK01–RK10 settle the prior identifier-count precedence conflict. No inferred model column or key approval.
- 66 task count retained. T007/T013 and T008/T014 responsibilities are swapped to preserve numeric dependency order;oldT023 moves toT030 and oldT024–T030 becomeT023–T029. Other IDs,including G0/G1/T063/T065/T066,stay stable.
- G0 is no longer an all-Phase1 barrier;G1 and core/basic branches have no T003 ancestor. Registry approval still blocks runtime registry,typed keyword/specification predicates and dependent full integration.
- US1.core,US1.specification,US2,US3 and US4 cases have independent freeze/verification scope;T056/T062/T065 require every functional partition. No core completion criterion requires unfinished future features.
- T065/T066 local acceptance does not depend on T063;deployment is explicitly NOT_VERIFIED/BLOCKED until its separate evidence passes. Full local acceptance still requires approved specifications and every20FR/6SC.
-[P] means authoring by default;integration jobs/classes execute serially without proved instance/database/storage isolation. Controlled race-test concurrency remains. Real keyword HTTP probe status complements internal availability;no new route/dependency/actual Compose change.
- T061 performance and T062 non-benchmark regression do not depend on each other's PASS. T065 needs both,with test-selection/discovery union retaining all required assertions/metrics.

Mechanical verification checks current task IDs/dependency references/acyclicity,registry-free primitives/G1,scoped corpus gates,local-target separation,independent verification branches,FR/SC traceability,OpenAPI/YAML local refs and document links/whitespace. It does not verify SQL feasibility,approve a registry,measure latency or claim platform readiness. All8 NEEDS VERIFICATION states remain. Source/config/constitution/migration checksums are compared to the pre-edit snapshot;no application/test/Compose/migration file is edited. No commit,push,migration or implementation occurs. Ready for another `$speckit-analyze`,not automatic execution.

### Mechanical checks completed for this revision

- 66 sequential task IDs,24 conditional authoring[P] markers;every dependency exists and points to an earlier ID,so no cycle/forward reference. No unordered[P] listed-path collision was found. This does not authorize concurrent shared-DB execution.
- T003 descendants reduced from59 to29;G1,DTO/parser/normalization/core/category/brand and price-shape tasks are registry-independent,while every approved-specification branch retains T003 ancestry.
- T061 and T062 are independent;both are ancestors of local T065. T063 is not an ancestor of T065/T066. Full local/story/corpus/approval requirements remain present.
- All20FR/sixSC definitions and traceability retained;the exact8 NEEDS VERIFICATION IDs and reviewer-owned unchecked markers remain unchanged.
- OpenAPI YAML and local schema refs parse/resolve;only the existing /api/products path remains. Markdown links/whitespace checks and git diff --check pass. Full semantic OpenAPI validation remains pending,not inferred from these checks.
- 597 protected source/config/constitution/migration files match the pre-edit snapshot. No application,Compose,test or SQL migration file changed;no migration/test benchmark/commit/push was run. Repeat read-only analyze is still pending.

## A1/RK07 targeted correction (2026-11-08)

- All ten golden scenarios have explicit owning increments in analysis-remediation.md R3. US1.core:T005/T021/T027/T029 require exactly RK01–RK06 and RK08–RK10. RK01 uses reviewed Product/SKU model sources;RK10 uses keyword-only eligibility without future filter/sort parameters.
- US3.configuration owns RK07:T039 freezes independent price-only,approved-specification and combined exclusion cases before implementation;T043 verifies repository results,T046 HTTP ordering/counts/display pair,T048 requires both. An excluded rank7 variant must not promote the Product whose remaining eligible variant ranks4.
- T056 reruns every RK01–RK10 against the assembled endpoint;T062 regression and T065 final local acceptance retain complete golden evidence. No golden case or ranking rule is removed or weakened. T003 approval remains mandatory for specification-dependent forms.
- Task count/IDs/dependency edges remain unchanged. The correction removes the implicit US1.core→US3 dependency rather than adding an ID-level edge. All20FR/6SC remain mapped;FR-008 additionally maps to the explicit US3 RK07 tasks. The eight NEEDS VERIFICATION states and deployment gate remain unchanged.
- Validation results are document/graph checks only;no registry approval,corpus review,capability test,benchmark or implementation result is inferred. A repeat independent analyze may review this correction.

### Targeted correction validation results

- PASS:66 unique sequential task IDs;every dependency exists and points to an earlier ID;no ID-level cycle. T029 has no T003 or US2–US4 ancestor. T048 retains T039/T043/T046 ancestors;T065 retains T048/T056/T062 and does not depend on T063.
- PASS:all10 golden cases have one explicit owning increment (nine core,one US3);the core cases use only core capabilities,and RK07 depends only on earlier ranking plus US3 predicates,so the removed semantic back-edge is not reintroduced. Original golden expectations and seven-tier comparator are unchanged.
- PASS:20 FR/six SC definitions still match the complete traceability table;all local Markdown file links resolve. All9,621 non-feature files match the pre-edit aggregate checksum;application/config/test/migration/constitution files are unchanged. Whitespace checks cover the four edited feature documents,including untracked files.
- These are documentation checks;the eight evidence dependencies remain NEEDS VERIFICATION. No implementation,migration,benchmark,commit or push was performed.

## Final owner-authorized implementation evidence

See [mission-scope.md](mission-scope.md),[local verification](evidence/verification-summary.md),[coverage](evidence/implementation-coverage.md) and [handoff](evidence/handoff.md). New V16–V19 and generated description cache/conservative typo guard are reported design deltas;the original20FR/6SC and all RK expectations remain. Actual1500 performance is required;10k is exploratory under resumed owner policy. T059 optional index work is deferred with no-index dependency resolution;T063 deployment remains blocked. Historical proposal/pending notes above are retained as history,not current approval or execution state.
