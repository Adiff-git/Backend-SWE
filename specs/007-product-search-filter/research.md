# Research: Product Search & Filter

> Implementation revision (owner MISSION,2026-11-09): see [mission-scope.md](mission-scope.md) for authorized approvals,local best-effort fact policy and explicit planning deltas. Four keys/five family bindings are approved;source model Rank7 and battery remain deferred. Runtime taxonomy uses actual Backend UUIDs and disables missing/ambiguous family bindings. Historical pending-approval observations below are retained as history,not current authorization. Actual execution evidence is recorded separately;this notice is not a test PASS.

Date: 2026-11-08. Design decisions, not implementation or live-environment validation.

## Evidence and current limitations

Read current ProductCatalogController, ProductCatalogService/Impl, ProductRepository, ProductCatalogConverter, ProductSummary/ProductPage/SellingPrice/VariantSummary, entities and TechnicalSpecificationValidator; CatalogSecurityConfiguration and ApiExceptionHandler; migrations V1–V14; demo/pom.xml; current Product Catalog OpenAPI; CatalogQueryCountIT and CatalogPublicReadPerformanceIT; constitution v1.1.0, enhancement spec/architecture and sibling Catalog-Data metadata. Research included an independent read-only registry audit.

Current listing accepts only page/size, pages published UUIDs first, batches relations and rechecks publication before signing. ProductSummary has four fields, is shared beyond listing, and SellingPrice uses BigInteger/VND. Optional Brand and typed Variant specifications exist. Migrations currently name enhancement V11, wishlist V10 and latest V14; historical docs referring to V10 enhancement are not current migration authority. No database introspection, Maven run or benchmark was performed.

The setup script reports BRANCH=007-product-search-filter from feature-path resolution. Actual git branch remains features/SearchAndFilter; no branch switch is inferred from that JSON field. Existing .gitignore and application edits were present before planning and are preserved.

## R1 — PostgreSQL-native search

**Decision**: Native SQL fragment within Catalog, using pg_trgm and explicit typed predicates. No Elasticsearch or external service.

**Rationale**: Current scale and transactional same-variant requirements favor the authoritative relational store. PostgreSQL documents trigram GIN/GiST indexes, similarity operators and strict word similarity; trigrams ignore non-alphanumeric punctuation, so they cannot decide identifier equality. We propose GIN for boolean candidate matching rather than GiST nearest-neighbor top-K. [PostgreSQL 17 pg_trgm](https://www.postgresql.org/docs/17/pgtrgm.html).

**Alternatives considered**: Elasticsearch adds synchronization/hosting and eventual-consistency costs without a demonstrated requirement. Full-text language stemming alone cannot satisfy typo/identifier behavior. JPA Criteria alone makes recursive hierarchy/ranking harder to audit; in-memory filtering breaks counts/pagination. Never pre-limit fuzzy candidates before exact eligibility/counting.

## R2 — Normalization and identifier handling

**Decision**: Propose immutable, versioned SQL functions catalog_search_normalize_v1 and catalog_search_natural_name_v1. Apply NFD, remove Vietnamese combining marks (grave, acute, tilde, hook above, dot below, circumflex, breve, horn), map đ/Đ to d/D, lowercase with explicit deterministic collation, trim/collapse Unicode whitespace. Preserve digits and meaningful internal punctuation. Use the same SQL function for stored expressions and bound query values, with Java reference tests. UTF8 is mandatory. PostgreSQL supplies normalize/translate/string operations; locale-sensitive lowercase must be pinned and tested. [String functions](https://www.postgresql.org/docs/17/functions-string.html).

**Rationale**: PostgreSQL unaccent is a viable broader dictionary-based alternative; it is an installable extension with configurable rules. A narrowly versioned Vietnamese mapping avoids a second extension and dictionary-driven index drift. [unaccent](https://www.postgresql.org/docs/17/unaccent.html). Do not falsely declare dictionary lookups immutable just to permit an index. The proposed function must use only immutable deterministic primitives, verified from pg_proc during migration tests; otherwise STOP and report a separately reviewed design correction;maintained normalized columns are only an alternative considered,not an automatically approved fallback.

**Alternatives considered**: Java-only normalization plus live raw SQL lower() drifts; trigger-maintained denormalized search documents add a refresh lifecycle; unaccent expression wrappers hide rule changes. A function behavior change requires a new version and index rebuild. NFKC and punctuation removal are rejected for identifier collision risk.

Tokenization is fully specified by D2 in [design-resolutions.md](design-resolutions.md), including Unicode whitespace, allowed single internal connectors, signed decimals, C++ and separator runs. D4 rejects nonblank zero-token queries and duplicate JSON members, deduplicates normalized token membership/rank after raw limits, and preserves original phrase order. Exact means whole field or complete token, prefix means token start, partial means internal substring for tokens≥4; short tokens permit exact/prefix. D1 typed numeric/quantity/boolean branches intentionally use whole typed equality rather than fuzzy/prefix matching. Escape all literal wildcard characters in SQL patterns; no stemming, stopwords or client regex syntax.

Classify identifiers before fuzzy eligibility: any token with a digit or meaningful internal punctuation is protected; also protect exact canonical SKU/model/technical-identifier vocabulary available from approved definitions. Apply the classifier to query and name candidates. The immutable indexed natural-name function uses structural digit/punctuation rules only and NEVER reads registry tables, files or mutable vocabulary. Apply approved vocabulary exclusions at query time during final per-token verification; deployment registry changes cannot change an existing index expression. A future vocabulary embedded in a function would require a new function version and index rebuild. Do not claim the current model has a dedicated model-number column; models must be preserved from names/SKU or approved technical text. Unrecognized alphabetic model names are a documented coverage limitation (B3), requiring a reviewed representative vocabulary rather than a claim that heuristics identify every model.

## R3 — Fuzzy matching and deterministic rank

**Decision**: Only eligible natural text of ≥4 normalized characters in Product Name/Brand Name participates. Derive natural-name expressions with structurally protected identifiers excluded. Use indexed strict-word operator queryToken <<% naturalName only as a candidate predicate. Recheck individual extracted natural-name tokens after excluding approved identifier vocabulary, requiring similarity(queryToken,nameToken) > configured threshold. Use the maximum surviving per-token similarity as the tie score. A candidate index hit alone never establishes fuzzy eligibility; final per-token verification also rejects cross-word extents. No fuzzy Brand Code, SKU, description or technical fact. The strict-word candidate can span words, so the final single-token recheck is mandatory. Prove index candidate recall against the exhaustive per-token oracle; if it ever drops accepted matches, remove that prefilter rather than truncate results. Never broaden approved behavior to fit a score.

Use transaction-local pg_trgm.strict_word_similarity_threshold, set via bound set_config(...,true) on the SAME connection as the query. Proposed property catalog.search.fuzzy.threshold=0.30, valid 0<threshold≤1, and catalog.search.fuzzy.enabled=true. D3 defines the explicit diagnostic exact-only disabled mode, response header and503 capability-loss handling; disabled mode is not a complete-feature release. pg_trgm remains a schema/deployment prerequisite in either configuration. This value is a calibration starting point, not a measured good threshold. Read operator threshold semantics in official documentation; verify boundary-equal cases. No session-global SET or pool leakage. Transaction-local setup only applies when fuzzy candidates are considered; threshold changes do not alter exact ranking.

Use the normative seven-tier comparator in [analysis-remediation.md R3](analysis-remediation.md#r3--one-ranking-order-and-explicit-requirement-refinement-i1):whole-query SKU/Model7 > whole-query Product Name6 > exact phrase5 > exact token4 > prefix3 > partial2 > fuzzy1. All-token/same-variant eligibility is enforced first. Whole-query model promotion requires reviewed anchored model scope;protected numeric/unit syntax alone is not model evidence. Ties compare summed per-token classes,source preferences and six-decimal quantized fuzzy contributions,then Product UUID. Choose the best entire tuple over eligible variants independently from representative minimum(price,variant UUID). Explicit price/name sorting overrides relevance.

This replaces the former identifier-hit-count-before-phrase tuple. A scattered exact SKU token cannot outrank a complete Name/phrase;whole-query SKU/model still wins. Golden RK01–RK10 and partitioned corpus make the refinement testable. Numeric/model tokens are never fuzzified or rewritten.

**Alternatives considered**: Weighted additive scores alone can invert tiers; full-name similarity penalizes long names; fuzzy matching every text field admits specification/SKU errors. Client-provided thresholds or arbitrary sort expressions are rejected.

## R4 — Same-variant filters and batch loading

**Decision**: One reusable eligible-variant relation feeds product rank, representative selection, page and count, with the narrowly guarded orphan compatibility branch in D7. For each token use shared fields OR variant-specific fields; all tokens and filter groups hold on that same variant. Multiple values for a key OR; keys AND; price bounds AND. Recursive categories use UNION deduplication and stable UUID bindings. Choose representative by price ASC,id ASC; best rank and cheapest eligible variant need not be the same but never use an ineligible variant. With no owned variants at all, no price/spec conditions and all tokens shared-field matches, keep Product membership/count and project a null display pair. This explicit D7 correction removes the unsupported planning-only listing409; detail409 is unchanged. Null prices sort last for either price direction.

**Rationale**: This prevents independent EXISTS clauses from mixing 8GB cheap and 16GB expensive configurations. ID-first page plus projected representative avoids collection pagination and N+1. One statement count/page preserves a single candidate snapshot including empty later pages.

**Alternatives considered**: Independent per-key variant EXISTS violates requirements; joins without product reduction duplicate counts; fetch-all then paginate exhausts memory. A generic collection JOIN FETCH with Pageable is rejected.

## R5 — Registry, safe JSON and capabilities

**Decision**: Classpath versioned JSON registry owned by Catalog; schema is in data-model.md. No runtime authoring endpoint. Only APPROVED definitions with source/approval evidence and concrete category UUID bindings activate. Registry errors/duplicate conflicting bindings fail startup; invalid client selections produce400. Unknown key does not become approved merely because JSON contains it. Quantity units must exactly match canonical tokens, with no conversion.

**Rationale**: Existing validator checks only shape. Existing category mapping approval and import approval do not prove technical search/filter approval. Nine keys are observed but all 1,500 source specification records are UNVERIFIED. See registry-inventory.md; no production initial allowlist can honestly be activated yet.

Guard each stored JSON shape/version/type/unit with CASE expressions before casting; do not rely on SQL WHERE evaluation order. Strings that look numeric never become quantities. Finite NUMERIC comparisons, exact booleans and normalized text preserve false/missing and decimal semantics. Only scalar text/number/boolean/quantity are supported. Request ranges compare scalars; stored arrays/ranges remain unsupported. Search values only from approved searchable definitions using D1 canonical views; do not cast the entire technical document to text. D5 makes zero category support400 for known categories independently of product counts; supported no-match is200. This does not approve any observed key.

**Alternatives considered**: Arbitrary JSON paths and automatic source-key aliasing expose unapproved data; relational attribute administration adds unrequested scope; global key maps lose category semantics.

## R6 — Proposed migrations and indexes

**Decision**: Reserve next free versions at implementation, tentatively V15 search functions/pg_trgm and V16 search indexes (V14 currently latest). Never change applied files or assume live versions. Fresh and V14-upgrade tests must verify rows/constraints and migration checksums. PostgreSQL extension install privileges/availability on actual target remain unverified.

Baseline additive index candidates:

| Table | Proposed index | Purpose |
| --- | --- | --- |
| categories | idx_categories_parent_category_id | Recursive child lookup |
| product_variants | idx_product_variants_product_id_selling_price_id | Cheapest eligible variant and ownership traversal |
| products | idx_products_search_name | GIN gin_trgm_ops over normalized name, partial status=PUBLISHED |
| products | idx_products_search_natural_name | GIN over protected-token-free natural-name expression for fuzzy matching, partial published |
| products | idx_products_search_description | GIN normalized description, partial published; retain only if benchmark improves mixed search |
| products | idx_products_search_name_id | B-tree normalized name COLLATE C,id, partial published, for explicit name sort |
| brands | idx_brands_search_name | GIN normalized name for exact/prefix/partial |
| brands | idx_brands_search_natural_name | GIN natural-name expression for fuzzy matching |
| product_variants | idx_product_variants_search_sku | Nonunique B-tree normalized SKU text_pattern_ops; retain existing unique lower(sku) index unchanged |

Reuse existing products(status,id), products(category_id), products(brand_id) and variants(product_id). Do not drop apparently overlapping indexes in this feature. Brand Code is small/selective initially; measure before adding another index. Partial SKU search for long identifiers and short-prefix paths may scan candidate variants; measure those explicitly rather than claiming B-tree supports arbitrary contains.

Add per-key numeric/quantity expression indexes only after approved registry and EXPLAIN evidence; include the same safe CASE/type/unit guard as the query. A blanket JSONB GIN does not establish efficient range comparisons. No index for illustrative/unapproved keys. [Expression indexes](https://www.postgresql.org/docs/17/indexes-expressional.html).

**Trade-offs**: Function indexes avoid stale shadow fields but cost write work; many OR branches and short tokens may favor scans. Index usage is a measured outcome, not guaranteed. Prototype non-concurrent index creation on disposable DB; choose transactional normal builds at current scale. A larger populated deployment needs a separately reviewed concurrent-build/nontransactional migration strategy, not an automatic CONCURRENTLY inside Flyway transaction. D6 documents validation, partial-commit detection and recovery, including when safe retry must STOP instead of modifying immutable migration files or using repair.

## Readiness and unresolved evidence

- **B1 — blocking**: No verified approved canonical technical-key registry or search/filter approval matrix. Resolve with reviewed registry artifact, category UUID reconciliation and explicit approval receipts; do not guess from observed data.
- **B2 — blocking acceptance calibration**: 0.30 threshold, ranking tuple and strict-word false positives need a frozen representative corpus and disposable PostgreSQL measurements. Proposed starting design is explicit; results are unknown.
- **B3 — blocking identifier coverage sign-off**: Numeric/punctuated recognizer is specified, but current data has no authoritative model field/vocabulary for all alphabetic identifiers. Freeze representative protected cases and an approved identifier vocabulary/rule scope; if corpus cannot distinguish them from natural text, seek a bounded requirement clarification rather than claiming universal protection.
- **Deployment prerequisite**: Verify pg_trgm availability, migration permissions, UTF8, function volatility, live Flyway history, current data counts and extension versions before target execution. No target was started/changed for planning.

No unresolved alternative is silently treated as approved. The design is reviewable and supports checklist/preparatory tasks; the feature must not pass implementation readiness until blocking evidence is supplied. Do not automatically proceed to tasks or implementation.

## R7 — Reviewed contract corrections

D1–D7 in [design-resolutions.md](design-resolutions.md) close CHK003/007/025/029/030/031/040. The nullable orphan pair changes the previous new design, because source inspection shows the old listing does not assert variants whereas detail does. It preserves legacy membership/counts rather than extending detail409 to listing. Explicit disabled-mode response metadata and503 capability loss add documented operational semantics without silently turning unavailable fuzzy into a successful no-match. The prior artifacts are updated consistently; these decisions are not implemented.

The eight NEEDS VERIFICATION criteria remain in the resolution register with methods, required evidence and dependencies. No threshold/vocabulary/registry/SQL function or environment claim becomes verified solely by these edits.

## R8 — Analysis findings resolved without fabricated evidence

See analysis-remediation.md R1–R7. Registry approval is scoped;US1.core corpus/calibration does not require future filters/sorts or approved-key cases. The reviewed-core gate is T029;full US1 T031 additionally waits the approved contributor T030. Same count66 is retained,with oldT023→newT030 and oldT024–T030→newT023–T029. No business requirement is removed.

Default integration execution is serial,with per-instance isolation proofs required for concurrent destructive/failure fixtures. Existing-endpoint keyword health probing makes capability503 operationally visible without new dependencies. Regression and benchmark can collect results independently;local handoff does not wait for a deployment target,but rollout still does. All eight verification criteria remain open until their actual evidence exists.

## Final owner-authorized implementation evidence

See [mission-scope.md](mission-scope.md),[local verification](evidence/verification-summary.md),[coverage](evidence/implementation-coverage.md) and [handoff](evidence/handoff.md). New V16–V19 and generated description cache/conservative typo guard are reported design deltas;the original20FR/6SC and all RK expectations remain. Actual1500 performance is required;10k is exploratory under resumed owner policy. T059 optional index work is deferred with no-index dependency resolution;T063 deployment remains blocked. Historical proposal/pending notes above are retained as history,not current approval or execution state.
