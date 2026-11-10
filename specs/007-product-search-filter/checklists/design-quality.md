# Design Quality Checklist: Product Search & Filter

**Purpose**: Comprehensive requirements/design review before tasks, across search correctness, combined filtering, registry, API compatibility, PostgreSQL design and verification readiness.
**Created**: 2026-11-08
**Feature**: [spec.md](../spec.md) · [plan.md](../plan.md)
**Audience/timing**: Feature author and peer reviewer; comprehensive pre-tasks design gate.

**Note**: Generated with `$speckit-checklist`; evaluation was explicitly requested by the user. No clarification questions were necessary because scope and depth were supplied.
**Review Ownership**: This is a reviewer-owned requirements-quality artifact. Every new checkbox remains `[ ]`; the inline PASS/FAIL/NEEDS VERIFICATION is the requested agent assessment, not a checkbox approval.
**Marker Semantics**: A future `[x]` means reviewer approval of requirements quality, never implementation completion.

PASS = wording/design is sufficiently defined and consistent for that criterion. FAIL = a specific documentation gap/conflict needs a correction. NEEDS VERIFICATION = a stated dependency/claim lacks the required approved artifact or empirical evidence; no implementation result is inferred. Current listing/detail/repository/converter and V3 source plus base OpenAPI were inspected only to resolve D7 compatibility; no feature conformance tests or live database checks were run.

## Requirement Completeness

- [ ] CHK001 **PASS** — Are all-token AND eligibility and cross-field Product matching defined without allowing tokens to be assembled across variants? [Completeness]
  - References: [Spec FR-006](../spec.md); [Plan §Query and loading design, steps5–7](../plan.md)
  - Assessment: Every token is required on shared Product fields plus ONE eligible variant. Independent variant EXISTS clauses are expressly rejected. Documentation complete; implementation not assessed.

- [ ] CHK002 **PASS** — Are keyword field coverage, fuzzy field restrictions and approved-only technical search explicitly bounded? [Completeness]
  - References: [Spec FR-004/007](../spec.md); [Research R3/R5](../research.md)
  - Assessment: Name, Brand name/code, SKU, Description and approved technical values are covered. Fuzzy is limited to eligible Product/Brand Name tokens; raw technical documents are excluded.

- [ ] CHK003 **PASS** — Are canonical searchable representations of numeric, quantity and boolean specification values defined? [Completeness]
  - References: [Spec FR-004/005/013](../spec.md); [Data model §Registry model](../data-model.md); [Gap]; [Resolution record](../design-resolutions.md)
  - Assessment: RESOLVED by D1: canonical decimal equality, explicit quantity amount/unit views, whole true/false and text matching are defined with no conversion, no fuzzy technical branch, no key-name indexing and no model-token rewriting. Synthetic examples create no approval. See [D1](../design-resolutions.md).

- [ ] CHK004 **PASS** — Are registry definition fields, explicit category bindings and independent searchable/filterable approval documented? [Completeness]
  - References: [Data model §Registry model](../data-model.md); [Registry inventory](../registry-inventory.md)
  - Assessment: Version, type, unit, operators, identifier flag, category UUIDs and source/approval evidence are defined. Searchable and filterable flags are separate; keyword-only use need not require categoryId.

- [ ] CHK005 **NEEDS VERIFICATION** — Is the proposed initial supported specification matrix backed by an authoritative approved artifact rather than observed or illustrative keys? [Completeness]
  - References: [Registry inventory](../registry-inventory.md); [Research B1](../research.md)
  - Assessment: Nine observed keys are labelled NOT APPROVED; all1500 source specification records are UNVERIFIED. No active approved registry is supplied. This is an explicit implementation dependency, not evidence that supported keys have been approved.
  - Verification method/dependencies: [Eight verification items retained](../design-resolutions.md#eight-verification-items-retained), row CHK005. Status remains NEEDS VERIFICATION; no approval or empirical result is inferred.

## Requirement Clarity

- [ ] CHK006 **PASS** — Are exact, token-prefix, partial and fuzzy classes distinguished with explicit short-token behavior? [Clarity]
  - References: [Spec FR-007/008](../spec.md); [Research R2/R3](../research.md)
  - Assessment: Exact means whole field or complete token; prefix means token start; partial requires length≥4; short tokens permit exact/prefix only. This classification is documented independently from algorithm calibration.

- [ ] CHK007 **PASS** — Is tokenization sufficiently precise to give one expected interpretation for punctuation, separators and model boundaries? [Clarity]
  - References: [Research R2](../research.md); [Spec FR-006/007](../spec.md); [Ambiguity]; [Resolution record](../design-resolutions.md)
  - Assessment: RESOLVED by D2: Unicode whitespace set, letter/digit scanner, explicit connector/sign/decimal/C++ rules and a boundary table determine punctuation handling. Structural protection is specified; actual alphabetic vocabulary remains CHK009 and SQL parity CHK034. See [D2](../design-resolutions.md).

- [ ] CHK008 **PASS** — Are Vietnamese normalization, Unicode composition, đ/Đ handling and nontranslation scope specified? [Clarity]
  - References: [Spec FR-005](../spec.md); [Research R2](../research.md); [Quickstart §Fixture and correctness matrix](../quickstart.md)
  - Assessment: NFD, named Vietnamese combining marks, đ/Đ mapping, case/whitespace normalization and NFC/NFD examples are specified. No stemming, translation or inferred synonyms are promised. Exact SQL/Java equivalence remains validation work under CHK034.

- [ ] CHK009 **NEEDS VERIFICATION** — Is complete model/technical-identifier protection supported by a bounded classifier and reviewed vocabulary? [Clarity]
  - References: [Research R2/B3](../research.md); [Spec FR-007](../spec.md)
  - Assessment: Digit/punctuation tokens have structural protection. Alphabetic models have no authoritative dedicated model field/vocabulary, and misspelled alphabetic identifiers may escape an exact-vocabulary classifier. B3 explicitly remains blocking; approve scope and representative positive/negative examples before claiming full coverage.
  - Verification method/dependencies: [Eight verification items retained](../design-resolutions.md#eight-verification-items-retained), row CHK009. Status remains NEEDS VERIFICATION; no approval or empirical result is inferred.

- [ ] CHK010 **PASS** — Is relevance ordering deterministic across tiers, exact identifiers, phrases, fields, variants and final ties? [Clarity]
  - References: [Spec FR-008/009](../spec.md); [Research R3/R4](../research.md)
  - Assessment: R3 in analysis-remediation.md now defines whole-query SKU/reviewed Model7,whole-query Name6,phrase5,token4,prefix3,partial2,fuzzy1 after eligibility,with exact deterministic tier-local ties and RK01–RK10. The obsolete identifier-count-before-phrase tuple is removed across current spec/research/contract/tasks. Model/vocabulary/calibration evidence remains CHK009/020/037,not approved by this wording.

- [ ] CHK011 **PASS** — Are similarity configuration scope, limits and final candidate rechecking documented without implying a proven threshold? [Clarity]
  - References: [Research R3](../research.md); [Quickstart §Migration and SQL validation](../quickstart.md)
  - Assessment: Starting threshold0.30 is explicitly unmeasured; transaction-local same-connection configuration and final per-token similarity recheck are required. Threshold boundary-equal cases and pool leakage are included in required evidence. No client-controlled threshold.

## Requirement Consistency

- [ ] CHK012 **PASS** — Are keyword, price and every specification condition consistently bound to the same eligible variant across all artifacts? [Consistency]
  - References: [Spec FR-006/014/015](../spec.md); [Plan steps5–8](../plan.md); [API §Keyword and ordering](../contracts/products-search.md)
  - Assessment: The earlier keyword/representative contradiction is removed. Keyword matching a SKU constrains eligible variants; Product-only matching leaves otherwise eligible variants available. Cross-variant negatives are explicitly documented.

- [ ] CHK013 **PASS** — Are AND between groups, OR within selections and inclusive range-internal AND consistent in request grammar and query design? [Consistency]
  - References: [Spec FR-010/011/014](../spec.md); [Data model §Request and internal query types](../data-model.md); [API §Query parameters](../contracts/products-search.md)
  - Assessment: Brands and alternatives within one key OR; different keys and price bounds AND. between expresses a bounded range; separate gt/lt alternatives OR rather than accidentally AND.

- [ ] CHK014 **PASS** — Are parent/self/descendant categories and multiple-brand selection defined with deterministic deduplication? [Consistency]
  - References: [Spec FR-010](../spec.md); [Plan step4](../plan.md); [API §Query parameters](../contracts/products-search.md)
  - Assessment: Recursive self+descendants, terminating UNION dedup and repeated brandId OR remain documented. D5 now explicitly states unknown brand alternatives contribute no match and never invalidate the valid-brand matches in a mixed selection.

- [ ] CHK015 **PASS** — Are representativeVariantId, displayPrice and explicit price sorting derived from the identical eligible-variant set? [Consistency]
  - References: [Spec FR-003/015](../spec.md); [Data model §Query projections and selection](../data-model.md)
  - Assessment: Representative price ASC then variant UUID ASC is defined. Ranking may come from another eligible variant; this is explicit, not cross-variant eligibility leakage. No unrestricted MIN(price) after filtering.

- [ ] CHK016 **PASS** — Are default relevance, legacy UUID order and explicit name/price sort tie-breakers consistent? [Consistency]
  - References: [Spec FR-009](../spec.md); [Research R3](../research.md); [API §Keyword and ordering](../contracts/products-search.md)
  - Assessment: Keyword defaults to relevance; blank/absent keyword defaults UUID ASC. Explicit sort overrides relevance. Normalized name COLLATE C and final UUID ASC apply in either direction; relevance without a keyword is400.

- [ ] CHK017 **PASS** — Are distinct-product counts, page selection and empty out-of-range page totals based on the same complete eligibility set? [Consistency]
  - References: [Spec FR-002/016](../spec.md); [Plan step7](../plan.md); [Data model §Query projections and selection](../data-model.md)
  - Assessment: Count/page share a statement snapshot after product reduction; total CTE LEFT JOIN page preserves totals without result rows. No fuzzy top-K truncation or post-pagination filtering. SQL implementation is not yet verified.

- [ ] CHK018 **PASS** — Are concurrent-publication and pagination accuracy guarantees scoped consistently rather than claiming a frozen catalog? [Consistency]
  - References: [Spec §Edge Cases/SC-003](../spec.md); [Plan step9](../plan.md); [API §Responses and compatibility](../contracts/products-search.md)
  - Assessment: Counts describe candidate-statement snapshot; final publication checks can suppress newly private rows. Complete-page/repeatability guarantees assume unchanged data. This is expressly documented, while no transaction-wide final-state count guarantee is promised.

- [ ] CHK019 **PASS** — Are legacy request behavior and response DTO compatibility isolated from detail, wishlist and image consumers? [Consistency]
  - References: [Spec FR-002/003/018](../spec.md); [Plan §Structure Decision](../plan.md); [OpenAPI ProductListingItem/ProductPage](../contracts/openapi.yaml)
  - Assessment: Listing-only DTO preserves shared consumers and existing envelope/fields. D7 explicitly adds a paired-null compatibility exception for invalid orphan data; normal representative values remain required/non-null. Source-based legacy membership/count is preserved rather than adding an unsupported listing409.

## Acceptance Criteria Quality

- [ ] CHK020 **NEEDS VERIFICATION** — Is a frozen representative search corpus actually available with expected membership, tiers and protected negative examples? [Measurability]
  - References: [Spec SC-001](../spec.md); [Research B2/B3](../research.md); [Quickstart §Fixture and correctness matrix](../quickstart.md)
  - Assessment: Required classes/examples are listed, but no versioned corpus with query-to-expected-results is present. Freeze field/phrase/typo/short-token/identifier/Unicode/cross-variant cases and their expected order; a test matrix alone is not this artifact.
  - Verification method/dependencies: [Eight verification items retained](../design-resolutions.md#eight-verification-items-retained), row CHK020. Status remains NEEDS VERIFICATION; no approval or empirical result is inferred.

- [ ] CHK021 **PASS** — Are performance goals and benchmark conditions measurable without confusing targets with measured outcomes? [Measurability]
  - References: [Spec FR-019/SC-006](../spec.md); [Plan §Technical Context](../plan.md); [Quickstart §Performance and EXPLAIN protocol](../quickstart.md)
  - Assessment: p95≤1s, size20,≥1000 products, warmups/sample count, baseline concurrency1, diagnostic10-reader run and larger exploratory corpus are specified. No performance result is claimed; count cost and environment metadata are included.

- [ ] CHK022 **PASS** — Are verification responsibilities and expected evidence defined for repository, service, controller, converter and integration boundaries? [Measurability]
  - References: [Plan §Delivery sequence and verification](../plan.md); [Quickstart §Evidence and acceptance exit](../quickstart.md)
  - Assessment: Responsibilities, meaningful test layers, actual Surefire/Failsafe discovery, skips and report artifacts are specified. No empty test scaffolds or implementation-mirroring tests are required.

## Scenario Coverage

- [ ] CHK023 **PASS** — Are specification operator/type/unit rules and missing/invalid/false/zero cases complete for supported scalar shapes? [Coverage]
  - References: [Spec FR-012/013 and operator table](../spec.md); [Research R5](../research.md); [Data model §Existing authoritative entities](../data-model.md)
  - Assessment: Numeric/quantity/text/boolean operators, finite exact decimals, quantity units and CASE-guarded parsing are covered. Missing never equals false/zero. Stored arrays/ranges remain unsupported; between compares supported scalar values.

- [ ] CHK024 **PASS** — Is specFilters transport and object grammar explicitly documented with limits, alternatives and error semantics? [Coverage]
  - References: [API §Query parameters](../contracts/products-search.md); [OpenAPI SpecFilters/SpecGroup/*Alternative](../contracts/openapi.yaml)
  - Assessment: Singleton URL-encoded JSON, collection limits, type/operator-specific object fields, mandatory categoryId and unsupported/null handling remain defined. D4 now explicitly rejects duplicate JSON members at every nesting before DTO binding; duplicate groups remain invalid.

- [ ] CHK025 **PASS** — Is missing-extension and explicitly disabled-fuzzy behavior defined without silently breaking required search or legacy listing? [Coverage]
  - References: [Research R1/R3/R6](../research.md); [Plan §Technical Context](../plan.md); [Gap]; [Resolution record](../design-resolutions.md)
  - Assessment: D3 plus remediationR6 define startup requirements,explicit disabled-mode metadata and runtime capability503. The existing HTTP healthcheck must use a nonblank keyword so the legacy no-keyword path cannot mask capability loss;no new endpoint/dependency. Future T026/T028 verify real HTTP outcomes as well as internal availability. Capability evidence remains CHK026.

- [ ] CHK026 **NEEDS VERIFICATION** — Are target pg_trgm availability, installation privilege, encoding and actual migration history supported by current environment evidence? [Coverage]
  - References: [Research §Readiness and unresolved evidence](../research.md); [Quickstart §Prerequisites and boundaries](../quickstart.md)
  - Assessment: Requirements are documented but the actual target was not inspected. PostgreSQL17 in Compose and migration filenames do not prove installed extensions, UTF8, privileges or live Flyway state.
  - Verification method/dependencies: [Eight verification items retained](../design-resolutions.md#eight-verification-items-retained), row CHK026. Status remains NEEDS VERIFICATION; no approval or empirical result is inferred.

- [ ] CHK027 **PASS** — Are no-result, unknown identity, malformed singleton, unsupported filter and infrastructure-error classes distinguished? [Coverage]
  - References: [Spec FR-017](../spec.md); [API §Category and registry validation/Responses](../contracts/products-search.md)
  - Assessment: 200 supported no-match differs from400 unsupported/malformed inputs and401 invalid supplied bearer. D5 resolves known-subtree zero/partial applicability and unknown-category validation precedence. D3 adds explicit503 and mode metadata for recognized capability states; internal details remain protected.

## Edge Case Coverage

- [ ] CHK028 **NEEDS VERIFICATION** — Has the machine-readable API contract received semantic validation beyond YAML parsing and local-reference checks? [Edge Case]
  - References: [Planning validation §Artifact checks performed](../planning-validation.md); [OpenAPI](../contracts/openapi.yaml)
  - Assessment: Only structural checks are recorded. Future evidence must cover parameter serialization/content, schema examples and prose-only cross-field constraints with existing approved tooling. This is not a claim that a validator has found an error or that runtime contract tests pass.
  - Verification method/dependencies: [Eight verification items retained](../design-resolutions.md#eight-verification-items-retained), row CHK028. Status remains NEEDS VERIFICATION; no approval or empirical result is inferred.

- [ ] CHK029 **PASS** — Are punctuation-only/zero-token queries and duplicate JSON object-member inputs assigned explicit interpretations? [Edge Case]
  - References: [Spec §Input and comparison semantics](../spec.md); [Research R2](../research.md); [API §Query parameters](../contracts/products-search.md); [Gap]; [Resolution record](../design-resolutions.md)
  - Assessment: RESOLVED by D4: whitespace-only omitted; nonblank zero-token400; raw token/collection bounds precede dedup; normalized tokens dedup for membership/rank while original phrase sequence is preserved. Duplicate JSON members at every level are400 even when equal; group duplicates remain400. See [D4](../design-resolutions.md).

- [ ] CHK030 **PASS** — Is a globally approved key with no applicable definition anywhere in a known selected category subtree distinguished from a key applicable to only some descendants? [Edge Case]
  - References: [Spec FR-012/017](../spec.md); [Data model §Registry model](../data-model.md); [API §Category and registry validation](../contracts/products-search.md); [Ambiguity]; [Resolution record](../design-resolutions.md)
  - Assessment: RESOLVED by D5/FR-012: for a known subtree, zero applicability for a requested key or no category supporting all requested keys is400, based on definitions rather than product counts. Partial applicability excludes inapplicable products; supported no-match is200. Unknown category keeps the previously agreed200 exception after global approved syntax/type/operator/unit checks; ambiguity remains400. See [D5](../design-resolutions.md).

- [ ] CHK031 **PASS** — Are migration failure and recovery requirements defined for the proposed multi-migration extension/function/index rollout? [Edge Case]
  - References: [Research R6](../research.md); [Quickstart §Migration and SQL validation](../quickstart.md); [Gap]; [Resolution record](../design-resolutions.md)
  - Assessment: RESOLVED by D6: immutable release/checksum capture, target/backup/restore prerequisites, info/validate, successful-prefix vs failed-successor inspection, transient same-artifact retry, checksum authenticity restoration and explicit STOP cases are defined. No routine repair/clean/baseline, manual history edit, skipped predecessor or destructive reset. This is a documented future procedure, not executed recovery evidence. See [D6](../design-resolutions.md).

## Non-Functional Requirements

- [ ] CHK032 **PASS** — Are query safety requirements defined for all client-controlled text, identifiers, operators, JSON paths and sorting? [Non-Functional]
  - References: [Plan step3](../plan.md); [Research R2/R5](../research.md); [Spec §Edge Cases](../spec.md)
  - Assessment: Values are bound; operators/order fragments and registry-resolved paths come from trusted allowlists. Wildcards are literal user input and stored type casts are guarded. This passes documentation quality, not a security test.

- [ ] CHK033 **PASS** — Are index purposes, migration versioning and rejected performance shortcuts documented without guaranteeing unmeasured index usage? [Non-Functional]
  - References: [Research R6](../research.md); [Quickstart §Performance and EXPLAIN protocol](../quickstart.md)
  - Assessment: New versions tentatively followV14; applied migrations are untouched. Existing indexes are reused; proposed expression/trigram indexes and approved-key-only range indexes have stated purposes. EXPLAIN comparison, no forced seqscan disabling and no prelimited fuzzy set are required.

- [ ] CHK034 **NEEDS VERIFICATION** — Is indexed normalization determinism established for the exact SQL primitives, collation and versioned vocabulary boundary? [Non-Functional]
  - References: [Research R2/R3](../research.md); [Quickstart §Migration and SQL validation](../quickstart.md)
  - Assessment: Versioned immutable structural functions and query-time vocabulary exclusions are correctly separated. Actual function definitions, pg_proc volatility and SQL/Java Unicode equivalence do not exist yet. Resolve evidence before indexes depend on them; do not relabel mutable dictionary reads immutable.
  - Verification method/dependencies: [Eight verification items retained](../design-resolutions.md#eight-verification-items-retained), row CHK034. Status remains NEEDS VERIFICATION; no approval or empirical result is inferred.

- [ ] CHK035 **PASS** — Are N+1 prevention, statement budgets and representative performance scenarios explicitly measurable? [Non-Functional]
  - References: [Plan §Query and loading design/Delivery sequence](../plan.md); [Quickstart §Performance and EXPLAIN protocol](../quickstart.md)
  - Assessment: ID-first/batch loading and≤6 legacy/≤8 filtered data statements remain measurable. R5 defines SERIAL default execution,proved worker-instance isolation and exclusive timing resources. T061 timing/T062 non-benchmark regression are independent readiness branches;final acceptance requires the complete report union.

## Dependencies & Assumptions

- [ ] CHK036 **NEEDS VERIFICATION** — Are implementation and benchmark conclusions supported by actual test reports and recorded query plans? [Dependency]
  - References: [Planning validation §Post-design conclusion](../planning-validation.md); [Quickstart §Evidence and acceptance exit](../quickstart.md)
  - Assessment: The plan explicitly says no implementation/tests/benchmarks were performed. Evidence remains absent by design. PASS items above validate writing only; they do not imply repository query correctness, endpoint availability or latency achievement.
  - Verification method/dependencies: [Eight verification items retained](../design-resolutions.md#eight-verification-items-retained), row CHK036. Status remains NEEDS VERIFICATION; no approval or empirical result is inferred.

- [ ] CHK037 **NEEDS VERIFICATION** — Have fuzzy threshold and indexed-candidate recall dependencies been resolved against representative queries? [Dependency]
  - References: [Research R3/B2](../research.md); [Quickstart §Performance and EXPLAIN protocol](../quickstart.md)
  - Assessment: 0.30 is a starting point. No accepted precision/recall corpus results, candidate-vs-oracle comparison or score boundary results are supplied. The proposed removal of a lossy prefilter is documented; choose final calibration only from evidence, retaining tier and identifier rules.
  - Verification method/dependencies: [Eight verification items retained](../design-resolutions.md#eight-verification-items-retained), row CHK037. Status remains NEEDS VERIFICATION; no approval or empirical result is inferred.

- [ ] CHK038 **PASS** — Are registry approval, protected vocabulary and corpus dependencies explicitly separated from document completeness and feature activation? [Dependency]
  - References: [Plan §Constitution Check](../plan.md); [Research B1–B3](../research.md); [Registry inventory](../registry-inventory.md)
  - Assessment: All8 evidence dependencies remain open with scoped gates:registry approval blocks only approved-key work,core corpus/normalization/calibration have their own prerequisites,and deployment evidence does not block local handoff. No unverified inventory/threshold/capability becomes approved.

- [ ] CHK039 **PASS** — Are feature scope, ownership and next-phase boundaries clear without automatically authorizing implementation or unrelated services? [Dependency]
  - References: [Spec §Scope boundaries](../spec.md); [Plan §Constitution Check/Complexity Tracking](../plan.md); [Planning validation](../planning-validation.md)
  - Assessment: The7 analysis findings are remediated in documents in analysis-remediation.md. Local full acceptance requires all20FR/6SC and approved technical specifications;Foundation+US1.core is only an incremental review target. T063 is deployment-only;T065/T066 local handoff can record target NOT_VERIFIED/BLOCKED without authorizing rollout. Repeat analyze is still required;no implementation is executed.

## Ambiguities & Conflicts

- [ ] CHK040 **PASS** — Is missing-variant listing behavior grounded in existing source/contracts and consistent with eligibility, nullable response fields and legacy membership/counts? [Conflict]
  - References: [Data model §Query projections and selection](../data-model.md); [Plan steps5–7](../plan.md); [OpenAPI responses409](../contracts/openapi.yaml); [Conflict]; [Resolution record](../design-resolutions.md)
  - Assessment: RESOLVED by D7 after read-only current source/base-contract inspection: existing listing counts/pages Product without requiring Variant; detail alone checks empty variants. Remove the unsupported proposed listing409. Guarded orphan branch retains legacy membership/count and projects two nulls only when all conditions are shared-field/no price-spec; real-but-nonmatching variants never receive fallback. Price nulls last both directions; detail409 and V3 integrity remain. Source evidence is verified; runtime regressions remain CHK036. See [D7](../design-resolutions.md).

## Notes

- Reevaluation2026-11-08: **32 PASS / 0 FAIL / 8 NEEDS VERIFICATION**,40 criteria. Previous assessment:25/7/8. All7 FAILs now PASS for documented design clarity; the same8 verification states remain open. All40 reviewer-owned checkbox markers stay unchecked; no implementation completion is asserted.
- Every item CHK001–CHK040 was re-read against the revised artifacts; all original25 PASSs remain PASS. Evidence-only criteria are CHK005/009/020/026/028/034/036/037; see the [verification register](../design-resolutions.md#eight-verification-items-retained) for methods, required outputs and gates.
- `$speckit-implement` reads checkbox state as a gate and must not modify markers. Built-in requirements.md has a separate lifecycle; its16/16 is specification-writing history, not proof of feature implementation or approval.
- D1–D7 expressly document all design corrections, including the formerly unsupported listing409 and the nullable-pair exception. Existing Product Catalog create/data invariants, detail409, wishlist and image workflow scope remain unchanged. spec status and planning-validation are aligned to current review.
- No application code, tests, migration files, data or registry approval were changed. No migrations or feature tests were run. No commit, task generation or extension hook was executed; extensions.yml is absent.

## Resolved documentation gaps

| Original failure | Resolution | Current state |
| --- | --- | --- |
| CHK003 typed keyword values | D1 canonical scalar/quantity/boolean views without conversion | PASS |
| CHK007 lexical boundaries | D2 deterministic scanner, preserved connectors/decimals and protected tokens | PASS |
| CHK025 capability/fallback | D3 startup prerequisite, explicit operator-disabled header and runtime503 | PASS |
| CHK029 empty/duplicates | D4 strict duplicate members, zero-token400 and bounded dedup | PASS |
| CHK030 category support | D5 unsupported known-category key/combination400 vs valid no-match200 | PASS |
| CHK031 migration recovery | D6 immutable history, partial-state detection, retry/STOP/recovery safeguards | PASS |
| CHK040 missing-variant contradiction | D7 source-grounded legacy membership, guarded null pair, no listing409 | PASS |

## Tasks readiness decision

**Ready for `$speckit-tasks` as a gated, dependency-ordered design backlog.** Documentation FAILs are resolved. Initial approval/corpus/identifier/capability tasks must preserve B1–B3 and the remaining8 verification dependencies; SQL normalization, semantic contract validation and later test/performance reports have their own appropriate gates. No unverified key may be activated and no performance/extension result may be invented. This is not implementation or release readiness. No tasks.md was created.

## Analyze-remediation follow-up

D1/A1/I1/D2/V1/V2/D3 are documented in [analysis-remediation.md](../analysis-remediation.md),including scoped gates,partitioned corpus,seven-tier ranking,local-vs-target readiness,isolated/serial test execution,keyword HTTP health probing and independent regression/benchmark branches. This note records remediation,not an independent repeat analyze result. Existing32 PASS/0 FAIL/8 NEEDS VERIFICATION writing assessments and every reviewer-owned unchecked marker are retained;the same8 evidence criteria still require actual results.
