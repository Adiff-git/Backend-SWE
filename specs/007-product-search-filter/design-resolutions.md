# Design gap resolutions and verification register

Date: 2026-11-08. Scope: documentation corrections requested after the40-item review. This record is normative for the revised design; no application behavior is claimed implemented. No registry approval, migration execution or measured result is created by this document.

## D1 — Typed specification keyword views (CHK003)

Only approved, category-applicable definitions with searchable=true contribute. filterable=true alone never enables keyword matching. Bad document version/shape/type/unit, null/missing/unapproved facts contribute no text. Do not index key names, raw payloads, provenance or inferred aliases.

| Approved scalar type | Canonical keyword view | Matching on this typed branch |
| --- | --- | --- |
| Number | Exact finite base10 plain decimal; remove insignificant trailing fractional zeros and trailing dot; normalize negative zero to0; no grouping/exponent | A whole query token in signed decimal notation (including .5 and -.5) compares by exact numeric value.16 and16.0 match the same fact. No prefix/partial/fuzzy numeric match;1 does not match16 |
| Quantity | Canonical decimal, one space, exact approved stored unit token; keyword normalization then applies to the text unit | Amount and unit are separate complete tokens; each can be searched alone, or together under token AND. Unit matching is case/accent-insensitive whole-token equality; explicit specFilters units remain exact case-sensitive canonical tokens. No prefix/partial/fuzzy amount or unit match |
| Boolean | true or false, lowercase ASCII | Whole-token equality only; tru does not match true. No numeric0/1 or translated/synonym labels |
| Text | Normalized literal value, with D2 token boundaries; preserve identifier punctuation/digits | Existing exact/prefix/partial rules; no fuzzy technical specification branch. identifier=true also protects it as vocabulary under B3 |

To enable a quantity definition as searchable, its approved unit must normalize/tokenize as exactly one fully preserved D2 token; otherwise that searchable registry configuration is rejected rather than dropping unit information. This is a capability constraint, not approval of an extra unit. Canonical decimal comparison is local to typed numeric branches. Do NOT rewrite general query tokens16.0 into16: that could change a model/version in a name or SKU. Scientific query text1e3 remains a protected textual identifier, not numeric1000. No unit stripping, synonym expansion, concatenation inference or conversion:16 GB is not16MB,0.016TB or16GiB.16GB is a single identifier token, not inferred to mean two tokens16 andGB. A differently stored unit never matches by conversion.

Typed facts are separate searchable field views. A phrase boost requires a contiguous canonical view, e.g.16 gb on a16GB quantity; query16.0 GB may match its two typed tokens by numeric equality but is not a canonical textual phrase16 gb. Phrase equality does not bridge facts/fields/variants. AND tokens may hit different approved facts on ONE variant, consistent with existing requirements; use specFilters to bind a number to a particular key. These examples describe synthetic typed facts, not new approved keys or proof of approval for physical_ram.

## D2 — Token grammar and protection (CHK007)

Version tokenization together with normalization. Apply Vietnamese normalization from researchR2, then scan Unicode letters and decimal digits. UTF8 and SQL/Java parity remain CHK034. Whitespace separators are the Unicode White_Space set: U+0009–000D,0020,0085,00A0,1680,2000–200A,2028,2029,202F,205F,3000; collapse them to one ASCII space. Non-whitespace controls are invalid400 as already contracted.

A token starts with a Unicode letter/digit, or one ASCII+/- immediately followed by a digit. A leading decimal dot immediately followed by digits, optionally preceded by one sign, is also retained as a numeric token (.5,-.5,+.5); it is not silently reduced to5. Continue letters/digits. Preserve each ASCII connector '-', '_', '/', '.', apostrophe and '+' only when directly flanked by letters/digits; a run of connectors is not an internal connector. Exception: retain a trailing run of one or two '+' immediately after an alphanumeric token (C+,C++); three or more '+' terminate the token and are separators. All other punctuation/symbols, including comma, colon, semicolon, brackets, quotes other than an internal ASCII apostrophe, Unicode dash/typographic apostrophe, backslash, percent and star, are separators. No transliteration of punctuation or numeric thousands separators is inferred. A single leading signed decimal is retained, but bare signs are separators. A decimal dot is internal only between digits; the general connector rule also preserves dots in alphanumeric identifiers.

| Input | Normalized tokens | Protection/meaning |
| --- | --- | --- |
| Điện thoại | dien,thoai | Natural tokens; accent-insensitive |
| WH-1000XM5 | wh-1000xm5 | One protected identifier |
| i7-1360P | i7-1360p | One protected identifier |
| USB-C / USB C | usb-c / usb,c | Different token sequences; no automatic alias |
| 3.50mm / 3.50 mm | 3.50mm / 3.50,mm | Preserve decimal and attached unit; no inferred equivalence |
| A_B/C.D+E | a_b/c.d+e | One protected identifier |
| C++ / C+++ | c++ / c | First protected; three-plus run is separator |
| O'Neil / O’Neil | o'neil / o,neil | ASCII internal apostrophe preserved; typographic apostrophe separates |
| A--B | a,b | Repeated connector run splits |
| 1,000 | `1` and `000` (two tokens) | Comma is not a decimal/grouping alias; never automatically1000 |
| -1.50 / +16 / .5 / -.5 | -1.50 / +16 / .5 / -.5 | Signed/leading-dot numeric tokens; protected from fuzzy; typed .5 equals0.5, not5 |

Every token containing a digit or a retained connector/sign is protected from fuzzy in query and candidate names. Natural tokens require≥4 normalized Unicode code points and must also pass approved technical-vocabulary exclusions at query time. Immutable indexed functions use structural rules only. Alphabetic model recognition remains B3/CHK009; these lexical decisions do not claim an unverified vocabulary exists. Corpus cases must prove protected models cannot be admitted through a fuzzy candidate path.

## D3 — Explicit fuzzy capability modes (CHK025)

pg_trgm is a deployment prerequisite for this feature's migration/index set EVEN when fuzzy.enabled=false. Before enabling the feature release, verify extension availability/installation rights, required functions/operators, UTF8 and compatible schema; do not lazily install extensions in a GET. Missing capability during startup/migration fails readiness/rollout. Keep the previous application release serving against its verified compatible schema until an authorized successful rollout. Missing extension is not permission to edit Flyway history or claim exact-only is full feature compliance.

| Operator configuration/capability | Listing contract |
| --- | --- |
| fuzzy.enabled=true and available | Normal complete search; nonblank q response includes X-Catalog-Search-Mode:fuzzy-enabled |
| fuzzy.enabled=false, required DB objects available | Explicit operator diagnostic exact-only mode: exact/prefix/partial remain, no fuzzy. Nonblank q response includes X-Catalog-Search-Mode:exact-only. No client toggle, no silent switch, no change to exact-tier ordering. This mode does not pass full-feature fuzzy acceptance/release gates |
| Either configuration, but required search capability becomes unavailable after successful startup | Nonblank q returns503 ProblemDetail, never a silent exact-only result or200 empty. Set instance readiness unhealthy and surface an operator error. No retry loop or extension installation in the request |
| No keyword (including blank q), legacy or filter-only request | No fuzzy evaluation/SET. Preserve ordinary listing/filter behavior while required normalization/schema objects are healthy; no search-mode header is required. Broader schema/DB failure retains infrastructure failure handling, not a guaranteed successful fallback |

The header is response metadata only; existing JSON shape is unchanged. It explicitly signals operator-disabled search rather than silently weakening a business requirement. It is not an availability guarantee when the application fails startup. Failures unrelated to recognized capability loss retain existing server-error handling.

## D4 — Empty/duplicate inputs (CHK029)

Whitespace-only q remains omitted. A nonblank q producing zero tokens after D2 returns400; it never becomes browse-all. Enforce q raw256-code-point and pre-dedup16-token limits FIRST. Then deduplicate identical normalized token strings, preserving first occurrence, for AND membership and relevance aggregates; repeated tokens cannot inflate rank. The original normalized ordered query, including repetitions, still determines exact phrase boost (samsung samsung only boosts an actual repeated phrase), so phrase meaning is not silently changed. Numeric lexical tokens16 and16.0 are distinct query strings although a typed fact may satisfy both.

specFilters must be parsed with strict duplicate JSON-member detection at EVERY object nesting level before DTO binding. Duplicate op,value,key or alternatives members return400 even when equal; no first-wins/last-wins policy. Duplicate key groups in the outer array remain400. Semantically duplicate alternatives may collapse after validation and do not increase rank/counts; collection bounds are enforced before collapse. Query singleton duplication and brandId repetition retain existing rules. Bad JSON/unknown object fields/nulls remain400; JSON number exponent syntax for specFilters is allowed only within the existing exact finite precision/scale bounds, while keyword scientific notation and price exponent syntax retain their distinct rules.

## D5 — Category support vs no-match (CHK030)

Validation order: parse syntax and require categoryId for specFilters; resolve global APPROVED definitions and the selected category; validate category support/definition compatibility BEFORE product matching. For a known category, each requested key must have≥1 approved applicable definition in the selected self+descendant subtree, otherwise400. If only some descendants support the key, exclude inapplicable-category products; definitions that apply must agree on meaning/type/unit/operators, otherwise400. For multiple keys a known subtree must contain≥1 category with compatible definitions for ALL keys; otherwise the combination is unsupported400, not a falsely valid no-product filter.

A fully supported, well-typed filter returning no eligible products is200 empty, including a valid contradictory numeric/price combination. This distinction depends on registry/taxonomy definitions, not whether products currently populate the category. Globally unapproved keys/operators/units are400 regardless of product count. A syntactically valid unknown category remains the previously agreed200-empty exception only after globally valid key/type/unit/operator validation; no category support is fabricated for it. A key with incompatible global alternatives and no resolvable category is400 rather than arbitrarily choosing a definition. Unknown brand alternatives contribute no OR match; selecting one valid brand plus unknown brands still returns matches for the valid brand. No approval or category binding is created by these rules.

## D6 — Failure detection and safe migration recovery (CHK031)

This is a future operator procedure, not commands executed or authorization for a populated target. Use installed compatible Flyway integration; do not assume a Maven Flyway plugin/CLI is present. Select the target explicitly and verify identity/version before any future action.

1. Preserve release migration files/checksums, target identity and successful history ordered by installed_rank. Require backup and restore evidence for a populated-target rollout. Obtain Flyway info/validate results using the same migration locations and intended versioned release configuration; distinguish expected pending migrations from applied-checksum/history mismatch. No ignore rule may hide a mismatch.
2. Extension/function/index deployment must pass fresh and upgrade tests on disposable PostgreSQL. Normal transactional per-migration execution is the baseline; no concurrent/nontransactional indexes in the initial rollout. Keep old release compatibility with additive objects. Stop rollout on migration error/nonzero status, validation failure, missing capability, invalid index or readiness failure. Never report partial success as ready.
3. After failure, inspect actual Flyway history plus object/index state; do not infer rollback only from a log or PostgreSQL transaction support. A failed transactional migration should roll back its own changes, but prior committed migrations remain. Example:V15 successful/V16 failed leavesV15 deployed, not an entirely reverted schema. Confirm history/checksums/object state and pg_index validity before retry.
4. If the same unchanged failed/pending migration can succeed after fixing a transient environmental prerequisite (privilege, capacity, extension packaging), document the cause, restore that prerequisite under separate operator authorization, validate intact applied history and retry the SAME immutable release migration through normal Flyway migrate. Revalidate and compare protected catalog values afterward. Do not execute individual SQL fragments manually to mark success.
5. If validation shows an applied checksum mismatch, restore the authentic release artifact to the migration locations; do not edit an applied script or realign history to make changed SQL look original. If a genuinely broken immutable pending migration or a failed-history/partial-object state prevents safe retry, STOP: neither appending a later migration nor repair automatically makes that predecessor safe. Escalate a concrete reviewed recovery plan. Roll back application release only when schema compatibility is demonstrated; backup restore, if necessary, targets a separately authorized replacement/verified target with restore evidence, never an unreviewed destructive reset.
6. A forward-only corrective migration is allowed in a later authorized release only when the original migration history validates and the prerequisite migrations are successful. It must not skip a failed predecessor. Once published/applied, all original files stay unchanged. Do not automatically invoke Flyway repair/clean/baseline/undo, update flyway_schema_history, drop shared extensions, remove volumes or weaken constraints. repair changes history and is not this procedure's default recovery mechanism.
7. Exit evidence: matching release checksums, intended successful history, capability checks, valid indexes, unchanged protected data hashes/counts/IDs/prices/statuses/media relationships, completed fresh/upgrade/failure-rehearsal reports and healthy application readiness. Pending/unrecovered state stays blocked. Record operator, target, release, incident cause and authorized action; preserve logs without secrets.

These safeguards align with official [Flyway validate](https://documentation.red-gate.com/flyway/reference/commands/validate), [transaction handling](https://documentation.red-gate.com/fd/migration-transaction-handling-273973399.html) and [repair semantics](https://documentation.red-gate.com/flyway/reference/commands/repair). No recovery command was run.

## D7 — Missing variants: source evidence and compatibility resolution (CHK040)

Read-only source inspection on2026-11-08: ProductRepository.findPublishedIdPage selects/counts published Product IDs without joining Variant. ProductCatalogServiceImpl.listProducts maps ProductSummary without querying/asserting a variant. ProductSummary/toSummary contain no price/variant requirement. getProduct detail explicitly throws CatalogConflictException when its variant list is empty. The current base OpenAPI lists200/400 for listing and does not promise listing409. V3 deferred constraint triggers reject zero variants at commit in the source-defined schema; this does not prove an applied live trigger or absence of legacy corrupt rows. No executable database or endpoint test was performed. Source references: `demo/src/main/java/com/example/demo/catalog/repository/ProductRepository.java` (`findPublishedIdPage`); `catalog/service/ProductCatalogServiceImpl.java` (`listProducts` and `getProduct`); `catalog/dto/response/ProductSummary.java`; `catalog/converter/ProductCatalogConverter.java` (`toSummary`); `demo/src/main/resources/db/migration/V3__enforce_catalog_relationships.sql`; `specs/001-product-catalog/contracts/openapi.yaml`. The older catalog specification calls an orphan invalid data but does not establish listing409; this design exception preserves read compatibility without making orphan creation valid.

**Conflict**: The earlier new data-model/API listing409 was an unsupported generalization of detail behavior. An inner eligible-variant query would also silently change legacy Product membership/counts on corrupt rows. Resolution preserves the existing public listing behavior rather than introducing a global orphan preflight/409.

| Product condition | Listing rule |
| --- | --- |
| At least one qualifying real variant | Existing nonnull representative/price rule remains unchanged |
| No variants at all, no active price/spec conditions, and all keyword tokens (if any) satisfied by Product-level fields | Keep the published Product; both added fields are PRESENT but null. Legacy UUID membership/count preserved; category/brand/name/relevance sorting still applies |
| No variants and active price/spec filters, or a token needing SKU/technical values | No eligible variant, so product does not match; normal200 empty/filter counts, never409 |
| Real variants exist but none satisfy filters/keywords | No orphan fallback; exclude as normal nonmatch |

Price sort places null displayPrice LAST for both directions, then Product UUID ASC. Never invent zero price or representative UUID. No listing409 is introduced for missing variants. Existing detail409 remains unchanged; no product repair or schema weakening occurs.

Query design: one branch computes real eligible variants. A separate synthetic null-variant branch is allowed ONLY for published products with NOT EXISTS(any owned variant), zero active price/spec groups, and all tokens satisfied by shared fields. UNION the branches before distinct product reduction, rank, count and page; a LEFT JOIN after filtering alone is unsafe because it also produces nulls for real-but-nonmatching variants. Protect visibility and image checks as before. Nullable listing-only DTO fields are an explicit compatibility exception for malformed catalog data, not an expansion of supported product creation policy.

Regression requirements: legacy orphan listing retains ID/count and returns null pair; Product-only keyword retains it; SKU/spec/price excludes it; price null ordering stable; real-but-nonmatching variants never receive fallback; existing detail still409. Rehearse orphan state only in a disposable fixture using reviewed constraint-bypass/test setup, never by disabling production constraints. Healthy-schema migration invariants must remain intact. Source findings are verified; live/empirical evidence remains CHK036/026.

## Eight verification items retained

No item below is marked empirically passed by editing prose. All remain NEEDS VERIFICATION with blocking exit evidence.

| Item | Verification method and evidence required | Dependencies / gate |
| --- | --- | --- |
| CHK005/B1 | Reconcile observed inventory against category UUID taxonomy; obtain reviewed registry version/hash, key meaning/type/unit/operator/searchable/filterable/identifier matrix and approval receipts | Blocks enabling technical keys and full-feature implementation sign-off; no inferred approvals |
| CHK009/B3 | Freeze reviewed protected vocabulary/rule scope; label alphabetic/digit/punctuation models and misspellings; compare natural-vs-protected eligibility against expected negatives | Depends on D2 and approved semantic vocabulary; blocks claiming identifier-protection acceptance |
| CHK020 | Versioned corpus of query, fixture facts, expected Product/variant IDs, rank tiers/phrases, positive and negative examples including D1–D5/D7 | Depends on approved semantics/test-only fixtures; freeze before matching implementation; must not turn synthetic key fixtures into production approvals |
| CHK026 | Read-only capability/version/privilege/UTF8/history inventory on the intended authorized target; record extension function/operator availability and current history; test rollout capability failure on disposable PG | Depends on target access and extension packaging; blocks that deployment, no live introspection performed now |
| CHK028 | Semantic OpenAPI3.1 validation with existing approved tooling; reviewed request/response examples including strict JSON/null pair503/header and category support cases; cross-check prose-only constraints | Depends on revised contract and actual tooling availability; no new unapproved dependency or claimed formal validator run |
| CHK034 | Actual versioned SQL definitions and pg_proc volatility audit; SQL/Java parity on Unicode whitespace/decimals/connectors, immutable structural index vs runtime vocabulary tests | Depends on PostgreSQL prototype/implementation; blocks indexes relying on unproved functions; no executable functions created now |
| CHK036 | Actual Surefire/Failsafe discovery/results, API/repository/race/nullable-orphan tests, migration failure rehearsals and protected-data checks, p95/budget/EXPLAIN reports | Depends on subsequent implementation and disposable infra; blocks feature completion, not document/task generation |
| CHK037/B2 | Calibrate threshold/ranking on frozen corpus; report false positives/negatives, exact identifier priority, strict prefilter recall vs exhaustive token oracle and threshold/pool boundaries | Depends on CHK020/009, PG and query prototype; blocks fuzzy/index acceptance;0.30 remains proposed |

## Review and handoff

The seven prior documentation FAIL items are resolved by D1–D7; their status can become PASS for design clarity only. Retain all eight NEEDS VERIFICATION states and reviewer-owned checkbox markers. Tasks may now be generated with explicit dependency gates, starting with corpus/approval/capability evidence and later implementation/verification work. This does not authorize execution, registry activation, migrations or release. No tasks.md is created in this revision.

## Superseding analysis remediation

[analysis-remediation.md](analysis-remediation.md) resolves the subsequent D1/A1/I1/D2/V1/V2/D3 findings. R1–R7 refine execution gates and operational probing without removing D1–D7 business semantics. Ranking now has whole-query SKU/Model,whole-query Name,phrase,token exact subtiers;the old identifier-count-first comparator is obsolete.

The eight verification rows above remain open. CHK005 gates only approved-key work;CHK020 freezes/verifies partitions by increment;CHK026 target evidence gates rollout separately from local acceptance. CHK036 requires BOTH independent functional-regression and benchmark reports. CHK009/037 still require protected-model/core calibration evidence,not a fabricated universal vocabulary or threshold. Corpus gate references are T005 manifest/core,T030 approved specification subset,T033 US2,T039 US3,T050 US4 core,T056 complete manifest;local summary T065 does not depend on T063 completing.

## Final owner-authorized implementation evidence

See [mission-scope.md](mission-scope.md),[local verification](evidence/verification-summary.md),[coverage](evidence/implementation-coverage.md) and [handoff](evidence/handoff.md). New V16–V19 and generated description cache/conservative typo guard are reported design deltas;the original20FR/6SC and all RK expectations remain. Actual1500 performance is required;10k is exploratory under resumed owner policy. T059 optional index work is deferred with no-index dependency resolution;T063 deployment remains blocked. Historical proposal/pending notes above are retained as history,not current approval or execution state.
