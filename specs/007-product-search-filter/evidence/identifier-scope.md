# T004 — identifier vocabulary approval proposal

**Status: BLOCKED — reviewer sign-off required.** No vocabulary,model anchor or recognition rule is enabled or self-approved. T001 dependency is satisfied.

Proposal:`identifier-scope-proposal-2026-11-09-v1`;JSON SHA-256:`a63b535518041fd2012b0a98b171c152f0231286d6dd4f739fe689af40af5ec0`.
Review cases SHA-256:`d69627c35a87507b7a9b42610f984bd882d58d35b11e2db034e070ad6b4813f5`.

## Evidence boundary

Audited all1500 products,1500 variants,1500 specifications and1500 import-approval rows from sibling Catalog-Data files. Source paths/hashes,line numbers,ASINs,proposed Product UUIDs,SKU observations and excerpts are in [identifier-vocabulary-proposal.json](identifier-vocabulary-proposal.json). Java T007 tokenizer was executed context-free over4500 Product Name/SKU/Brand Name inputs;all1500 Product Names normalized successfully. Counts below are exact D2-token membership counts,not substring counts;compound/slash tokens remain structurally protected instead of being split into invented aliases.

All1500 specification rows are UNVERIFIED. All1500 proposed SKUs agree with the import CSV approved_sku;that approves an import SKU policy,not this search vocabulary/technical keys or actual target DB state. Publication/import-preparation states are not proof of live canonical records. No database was queried and no catalog/source file was changed.

Original16 draft cases were reviewed and remain byte-identical/unapproved. WH-1000XM5 occurs in an actual Product Name (B0C5ZC7FWR);USB-C occurs in30 raw titles. i7-1360P and C++ do not occur in this source title snapshot and remain labelled synthetic grammar cases,not invented catalog observations.

## Proposed recognition rules (not implementation)

1. Reuse D2 normalization/tokenization exactly. Any token with digit/retained connector/sign is non-fuzzy in query/candidate names. SKU and technical-specification sources never receive fuzzy matching,even an all-letter SKU/value. Do not rewrite punctuation,digits,16.0 or units.
2. Proposed conservative family vocabulary:the19 exact normalized terms below are query/candidate fuzzy exclusions. Match complete D2 tokens,never substrings,stems or typo aliases. This may suppress fuzzy recall for homonyms (e.g.Galaxy as an ordinary noun);that trade-off explicitly requires reviewer approval. Brand words such as Samsung/Sony/Apple are not added just because they are adjacent. No selected family token collides with a Brand Name token in this snapshot;future catalog/vocabulary changes require review.
3. Ambiguous terms are context-only:recognize the exact reviewed brand/family span independently in query and candidate Product Name/Brand context. A misspelled query cannot bypass a protected candidate token,even when the query token itself is unknown. Unanchored generic words are not automatically identifiers. Context/provenance must be available;do not infer missing Brand/model identity from units/category alone.
4. Exact rank7 MODEL promotion is a separate scope from fuzzy exclusion:only the six explicitly anchored Product/model pairs below are proposed. A family word,a number/unit token,a component-family term or an accessory compatibility target does not automatically become the owning Product model. No new model column is assumed.
5. Vocabulary/context exclusions belong to query-time final token verification. Immutable indexed functions stay structural and never read this proposal or mutable vocabulary. Unknown alphabetic models remain a bounded coverage limitation;no claim of universal recognition. No threshold,alias/synonym expansion,spell correction,translation or fuzzy model admission is proposed.

## Nineteen family candidates

| Normalized term | Product Names with exact token | Brand-name token collisions | Provenance anchor |
| --- | ---: | ---: | --- |
| galaxy | 253 | 0 | samsung |
| iphone | 209 | 0 | apple |
| ipad | 69 | 0 | apple |
| xperia | 5 | 0 | sony |
| redmi | 39 | 0 | xiaomi |
| thinkpad | 6 | 0 | lenovo |
| ideapad | 12 | 0 | lenovo |
| inspiron | 7 | 0 | dell |
| vivobook | 11 | 0 | asus |
| zenbook | 1 | 0 | asus |
| macbook | 18 | 0 | apple |
| chromebook | 63 | 0 | platform term;device-brand anchor required |
| elitebook | 2 | 0 | hp |
| celeron | 37 | 0 | intel |
| pentium | 7 | 0 | intel |
| ryzen | 18 | 0 | amd |
| snapdragon | 10 | 0 | qualcomm |
| geforce | 13 | 0 | nvidia |
| radeon | 10 | 0 | amd |

Each JSON entry includes up to3 concrete source examples. These counts do not certify that every occurrence is a device identity;compatibility references are deliberately separated from owning-model rank7.

## Six product-scoped model anchors proposed for review

| Model token | Source ASIN | Source line |
| --- | --- | --- |
| wh-1000xm5 | B0C5ZC7FWR | 384 |
| wh-1000xm4 | B08DBDSVBC | 377 |
| a515f | B08PPSRLQ5 | 1 |
| pro-i | B09L88FZ95 | 2 |
| l210 | B081V6W99V | 155 |
| be2026 | B08R7D4KZJ | 128 |

These six anchors are source-backed proposals only. At activation,bind to the actual canonical Product/eligible Variant and reconciled source identity;do not assume proposed UUIDs prove live ownership. No fuzzy equivalence between WH-1000XM5 and WH-1000XM4 is permitted.

## Ambiguous / excluded cases

| Term(s) | Proposed handling / false positive |
| --- | --- |
| Core | Intel Core or Samsung Galaxy A03 Core needs explicit span;generic processor/core descriptions do not establish model identity |
| Pro,Air,Note,Plus,Max | Only inside reviewed device spans;professional/audio,airflow,note-taking and ratings are ordinary uses |
| Surface | Microsoft Surface compatibility/device span vs observed Logitech “Any Surface”;never infer accessory owns the target model |
| Pavilion,Aspire,Envy,Spectre | HP/Acer family context only;homonyms must not be globally blacklisted |
| Pixel,Nord,Vega,Legion | Google/OnePlus/AMD/Lenovo context only;generic/image/other names ambiguous |
| Studio,Graphics | Current observations are descriptive/feature labels;do not add global model identifiers |
| HD,UHD,ROG,TUF | Short-token rule already disables fuzzy;context/family mention is not automatic rank7 |

Relevant specification observations are recorded separately:cpu_model100,cpu_family64,gpu_model137. Some extracted values are long/truncated or suspiciously labelled (e.g.Intel Celeron Processor N4000 in gpu_model). Extractor accepted=true is not search approval or correctness. Do not promote these raw values/keys;T003 remains untouched.

## Approval request and freeze procedure

Reviewer must explicitly approve or revise:(A)the19 exact family exclusions and stated homonym cost;(B)context-only rules/14 review cases;(C)six Product/model anchor bindings for rank7;(D)bounded source snapshot/version,limitations and ownership checks. Reject any term/rule that is too broad rather than silently weakening identifier protection.

Approval should identify reviewer,UTC,proposal version and hashes,accepted/excluded entries and constraints. Until received,approval remains null,T004 unchecked and CHK009 NEEDS VERIFICATION. This proposal does not sign T003 or approve specification filters. After actual sign-off,T005 core corpus may be frozen;T014 still requires T005 before calibration.

Reproduce source audit from repo root with existing Java test runtime reports:

```sh
python3 specs/007-product-search-filter/evidence/audit-identifier-vocabulary.py
```

The script reads source files and executes the existing normalizer via a temporary JVM probe;it writes proposal evidence only,not a production recognition algorithm. [identifier-review-cases.json](identifier-review-cases.json) distinguishes original16 draft cases and14 additional review scenarios;none are claimed executed classifier/fuzzy tests.

## Current complete reviewer matrix and executed checks

The user accepted the three-group classification as **candidate review scope**,not final vocabulary/context sign-off. The [complete46-row reviewer matrix](identifier-review-matrix.md) and [machine-readable matrix](identifier-review-matrix.json) contain all19 family terms,6 source-verified model anchors and21 context-dependent terms. Every row includes source evidence,recognition/context rule,expected ranking and positive/negative witness inputs. Family terms never automatically receive exact-model rank7;model ownership/eligibility and conservative fuzzy protection remain explicit.

[identifier-review-results.json](identifier-review-results.json) records checks for all30 draft/review cases:16 actual existing D2 primitive executions pass;14 additional cases pass source/proposal/primitive consistency checks. All92 matrix witness inputs were tokenized. These are **not**30 passing recognition/ranking tests:the contextual classifier,fuzzy matcher and ranker are not implemented or executed. Homonym,accessory ownership and missing-brand ambiguities remain review decisions. No observed specification is promoted from UNVERIFIED or approved for filtering.

Reproduce using existing Java test classpath metadata and unchanged sibling source package:

```sh
python3 specs/007-product-search-filter/evidence/review-identifiers.py
```

This compiles only a temporary context-free probe of the completed normalizer. Original16 test-resource cases remain unchanged. Final sign-off must identify proposal/version/hash,accepted/excluded entries,context and ownership constraints,reviewer and date. All approval fields remain null;T004 stays unchecked. After sign-off,T005 becomes dependency-ready;T014 additionally waits for T005. T003 is independent.

## Current scope preservation

Only selected task evidence,tasks.md and the user-authorized three-constraint feature OpenAPI restoration were changed. Temporary tooling stays outside the repository. No application/test source,migration,Catalog-Data record or baseline Catalog contract was modified by this execution. T006 now has actual semantic evidence;its previous blocked record is historical. No migrations,commit or push were run.


## Current owner-authorized execution revision

Vocabulary v1 is now authorized by the project owner MISSION. See vocabulary-approval-v1.json (AR2 hash) and mission-scope.md. Six source anchors remain disabled for model rank7;canonical SKU equality is separate. Original16 primitive cases and14 source/proposal cases are preserved;new runtime classification covers all19 family exclusions and every declared context span. This does not relabel source checks as runtime ranking tests.
