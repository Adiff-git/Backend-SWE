# Feature Specification: Product Search & Filter

> Implementation revision (owner MISSION,2026-11-09): see [mission-scope.md](mission-scope.md) for authorized approvals,local best-effort fact policy and explicit planning deltas. Four keys/five family bindings are approved;source model Rank7 and battery remain deferred. Runtime taxonomy uses actual Backend UUIDs and disables missing/ambiguous family bindings. Historical pending-approval observations below are retained as history,not current authorization. Actual execution evidence is recorded separately;this notice is not a test PASS.

**Feature Branch**: `features/SearchAndFilter`

**Created**: 2026-11-08

**Status**: Analyze findings remediated in documents — ready for repeat analyze; eight verification dependencies remain; implementation not started

**Input**: Extend the existing public GET /api/products with keyword and fuzzy search, relevance ranking, category/brand/price/approved specification filters, and representative variant pricing while preserving Product Catalog behavior.

## Clarifications

### Session 2026-11-08

- Q: How do multiple tokens and variant keyword matches combine? → A: Use AND across tokens, allowing matches across Product-level fields. Preserve model numbers and technical identifiers. Boost exact phrases. Every required variant-level keyword match and all active variant filters must be satisfied by one variant.
- Q: Where is fuzzy matching allowed? → A: Only suitable Product Name and Brand Name text tokens of at least four normalized characters; shorter tokens use exact/prefix only. Never fuzzy-match SKU, model numbers or technical specification values. Exact technical identifiers outrank approximate matches. Similarity thresholds remain configurable and must be validated against representative queries; Vietnamese accent-insensitive matching remains required.
- Q: How are specification filters approved and scoped? → A: Require categoryId and a category-aware registry of approved canonical keys derived from existing catalog documentation and typed data structures. Include descendants but reject ambiguous/incompatible definitions, unsupported keys/operators/units with HTTP 400. No automatic unit conversion.

### Design review resolution 2026-11-08

The seven gaps found by the design checklist are resolved in [design-resolutions.md](design-resolutions.md), decisions D1–D7. These amend observable input/error/nullable-field semantics explicitly; they do not approve registry facts or claim implementation. In particular, the former planning-only listing409 is removed after checking current source; existing detail409 stays unchanged. The eight verification dependencies remain open.

### Analyze remediation 2026-11-08

[analysis-remediation.md](analysis-remediation.md) records scoped technical/registry/local/deployment gates,corpus partitions,deterministic seven-tier ranking with explicit prior-precedence conflict,parallel test isolation,existing-endpoint readiness probe and independent regression/benchmark gates. All20 FR/6 SC remain mandatory. Foundation+US1.core is only a development increment;complete US1 and local feature acceptance still require approved technical specifications. No approval or implementation is implied.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Find the right electronics product (Priority: P1)

As a shopper, I want to find products by name, brand, SKU, description or approved specifications, even when I omit Vietnamese accents or make a common spelling mistake.

**Why this priority**: Search is the primary way to locate a known item without browsing the entire catalog.

**Independent Test**: Use a controlled catalog containing exact, prefix, token and typo candidates and check visibility, membership and ranking without any filters.

**Acceptance Scenarios**:

1. **Given** published products with distinguishable name, brand name/code, variant SKU, description and approved specification values, **when** each field is searched, **then** its owning product is discoverable; unpublished products and unapproved specification content never contribute matches.
2. **Given** a Vietnamese name containing `Điện thoại`, **when** searched with `DIEN THOAI` or canonically equivalent Unicode text, **then** case and accents, including đ/Đ, do not prevent matching.
3. **Given** a query and exact, prefix, partial/token and accepted fuzzy candidates, **when** default search sorting is used, **then** these classes appear in that order,with Exact refined by FR-008:whole-query SKU/Model,whole-query Product Name,phrase,then token-only. No exact SKU token in a scattered query may leapfrog a higher exact tier.
4. **Given** `Samsung` and `Samsnug` in the approved typo acceptance set, **when** the latter is searched, **then** Samsung products remain discoverable according to the agreed fuzzy policy; an exact competing identifier remains first.
5. **Given** a multi-word query whose words occur in different searchable fields, **when** searched, **then** every token must match in shared Product-level fields or the same eligible variant; a missing token excludes the product. Exact phrases occupy tier5 ahead of separated exact tokens in tier4,while complete-query SKU/Model and Product Name have tiers7/6;see RK01–RK10.

6. **Given** an ordinary token with three normalized characters, **when** searched, **then** only exact/prefix matches qualify; an internal substring or typo alone does not qualify. Four-character text tokens may use configured fuzzy matching only in Product Name/Brand Name.
7. **Given** a model identifier such as `WH-1000XM5`, **when** searched, **then** its digits and meaningful punctuation remain significant; a misspelled identifier cannot qualify through fuzzy matching in any field.

### User Story 2 - Narrow choices by category and brand (Priority: P1)

As a shopper, I want to browse a category subtree and select multiple brands so I see only relevant choices.

**Why this priority**: These filters reduce large catalogs without requiring a keyword.

**Independent Test**: Filter a catalog with a parent, child, grandchild and unrelated category and several brands without keyword or variant filters.

**Acceptance Scenarios**:

1. **Given** products assigned to a parent and its descendants, **when** the parent is selected, **then** all those products are eligible while unrelated categories are excluded.
2. **Given** brands A, B and C, **when** A and B are selected with a category, **then** products must belong to that subtree AND have brand A OR B; unknown brands do not match selected brands.
3. **Given** a valid category or brand selection with no published products, **when** listing, **then** a successful empty page and zero totals are returned.

### User Story 3 - Compare matching configurations and prices (Priority: P1)

As a shopper, I want price and specification filters to describe one purchasable configuration and the displayed price to belong to that configuration.

**Why this priority**: Combining cheap and powerful facts from different variants would mislead purchase decisions.

**Independent Test**: Use multi-variant products with intentionally conflicting prices and specifications and verify membership and displayed variant without keyword search.

**Acceptance Scenarios**:

1. **Given** variant A costs 10,000,000 VND with 8 GB RAM and B costs 15,000,000 VND with 16 GB RAM, **when** maximum price is 12,000,000 and RAM is at least 16 GB, **then** the product is excluded.
2. **Given** multiple variants satisfying every active variant filter, **when** listing, **then** the lowest-priced satisfying variant supplies both displayPrice and representativeVariantId; equal prices use variant UUID ascending.
3. **Given** no variant filters, **when** listing, **then** the lowest-priced variant supplies the display fields if keywords match only Product-level fields; if a keyword requires a variant SKU/specification hit, select the lowest-priced variant satisfying those keyword conditions.
4. **Given** approved numeric, quantity, text and boolean definitions, **when** valid operators and values are used, **then** comparison follows the operator table; false is distinct from missing.
5. **Given** a variant exactly on either price or specification range boundary, **when** an inclusive range is applied, **then** it matches.
6. **Given** SKU token X on variant A and an approved specification token Y only on variant B, **when** X and Y are searched together, **then** the product is excluded even without explicit filters.
7. **Given** keyword SKU X matching A and a price/specification filter satisfied only by B, **when** combined, **then** the product is excluded; if A also satisfies the filters, A supplies the display fields.
8. **Given** explicit specification filters without categoryId, or ambiguous/incompatible definitions in its subtree, **when** listing, **then** HTTP 400 is returned. A valid key applicable only to some descendant categories excludes inapplicable-category products without invalidating the whole request.
9. **Given** a request or stored quantity requiring conversion from its approved canonical unit, **when** filtering, **then** unsupported request units produce HTTP 400 and differently stored units do not silently match.

### User Story 4 - Browse reliable pages and choose ordering (Priority: P2)

As an existing catalog consumer, I want stable pages, accurate totals and explicit price/name sorting without changing established browsing behavior.

**Why this priority**: Existing integrations and comparisons depend on predictable results.

**Independent Test**: Request all pages across tied prices, names and relevance and compare unique IDs and totals to a manually enumerated expected set.

**Acceptance Scenarios**:

1. **Given** only existing page/size parameters or none, **when** listing, **then** existing membership, UUID-ascending order, pagination fields and existing summary values are preserved, with the two additive fields.
2. **Given** a keyword and explicit price ascending/descending or name ascending/descending sorting, **when** listing, **then** the explicit order overrides relevance; price uses displayPrice.
3. **Given** unchanged data with tied sort values, **when** repeated requests and adjacent pages are read, **then** product UUID ascending breaks final ties, with no duplicates or omissions and distinct-product totals.
4. **Given** an out-of-range page, **when** requested, **then** items are empty and totals still describe the complete filtered result set.
5. **Given** malformed, duplicate singleton or unsupported inputs, **when** requested, **then** HTTP 400 identifies the invalid input without leaking internal details.

### Edge Cases

- Empty/whitespace-only keywords, literal `null`, omitted filters and empty selections follow the input table below.
- Blank descriptions and absent brands contribute no text; they do not exclude otherwise matching products.
- Missing or unapproved specification facts do not satisfy a specification predicate, including equality to false or zero. Unknown data is never inferred.
- Multiple matching variants, overlapping descendant paths and repeated selections never duplicate a product or inflate totals.
- A parent category spanning several specification domains cannot make an inapplicable key valid for every child. Only applicable categories contribute matches; ambiguous/incompatible definitions produce HTTP 400 under FR-012.
- Equal prices/names/relevance remain deterministic. Name comparison uses case/accent-normalized text and final UUID tie-breaking.
- Identifier punctuation and digits must not be discarded in ways that conflate distinct SKUs/models; fuzzy name matching must not replace an exact identifier hit.
- SQL-like text and wildcard characters are user input, never executable instructions or implicit query syntax.
- Valid contradictory selections return no results; malformed ranges fail validation.
- Stable cross-request ordering is guaranteed for unchanged catalog data, not a frozen catalog across concurrent updates. Public responses and counts must exclude non-PUBLISHED data, retaining existing publication and image protections.
- Published products are expected to have a variant under source-defined V3 constraints. D7 explicitly preserves existing listing membership/counts for anomalous orphan data with a null display pair, excluding it when a real variant is required. No listing409 is added; existing detail409 is preserved. This neither repairs data nor weakens constraints.
- D2/D4 define punctuation-only, repeated-token, connector/decimal and strict duplicate-member outcomes. D3 defines disabled/unavailable fuzzy behavior; D5 separates unsupported category keys from valid no-match filters.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Extend GET /api/products for anonymous and authenticated catalog readers without requiring a new permission; only PUBLISHED products may contribute results, counts or searchable content exposed through this listing.
- **FR-002**: Preserve zero-based page default 0, size default 20, size range 1–100, current validation for page/size and response envelope `items`, `page`, `size`, `totalItems`, `totalPages`. Empty and beyond-last pages return HTTP 200, with accurate totals.
- **FR-003**: Preserve existing summary fields and their meaning, category projection and signed primary-image behavior. Add displayPrice in the existing sellingPrice shape `{amount, currency: "VND"}` using an exact whole-đồng amount, and representativeVariantId as the owning variant's canonical UUID. Both fields are present and nonnull for valid variant-backed products. A published legacy/corrupt product with no variants retains listing eligibility only without price/spec conditions and with all tokens satisfied by Product fields; both new fields are then null (D7), never a fabricated zero/UUID or new listing409. Price sorts place this null pair last in both directions. Do not change detail, wishlist or image workflows as an incidental effect of a shared summary type.
- **FR-004**: Search Product Name, canonical Brand Name/Code, variant SKU, Product Description and approved searchable technical specification values; raw/unmapped source metadata and arbitrary document keys MUST NOT become searchable by default. Approved scalar keyword views follow D1: canonical finite decimals, explicit amount+unit quantity views, literal true/false and typed text; no implicit unit conversion, fuzzy technical values or translated labels.
- **FR-005**: Keyword matching MUST ignore case, Vietnamese tone/letter accents including đ/Đ, canonical Unicode composition differences and redundant surrounding/inter-word whitespace. This does not imply translating English source content or adding Vietnamese synonyms.
- **FR-006**: Use AND semantics across normalized search tokens. Tokens may match different Product-level fields (name, brand name/code, description). A variant-backed product qualifies only if at least one of its variants, combined with those shared fields, satisfies every token and every active price/specification filter. The sole no-variant exception is D7/FR-003: no price/spec filters and every token matched by shared fields, with a null display pair. Tokens must never be assembled from different variants. Preserve complete technical identifiers and model numbers, including meaningful punctuation and digits. Exact phrase matches within a searchable field receive a relevance boost; phrase matching is not a requirement for inclusion. Apply deterministic D2 token grammar, D4 first-occurrence normalized-token deduplication and pre-dedup limits; a nonblank zero-token query is400, while whitespace-only remains omitted. Typed numeric equivalence never rewrites model/SKU text.
- **FR-007**: Fuzzy matching MUST apply only to suitable natural-language tokens of at least four normalized characters in Product Name and Brand Name. Tokens shorter than four characters MUST use exact/prefix matching only, regardless of field. Brand Code, SKU, model numbers and technical specification values MUST NOT use fuzzy matching, even when embedded in a product name. Similarity thresholds MUST be configurable and validated against a recorded representative positive/negative query set. All matching retains FR-005 normalization; identifier boundaries and meaningful punctuation MUST remain intact.
- **FR-008**: After publication,all-token AND and same-variant eligibility,default relevance MUST use Exact whole-query SKU/Model > Exact whole-query Product Name > Exact Phrase > Exact Token > Prefix > Partial > Fuzzy,with definitions and golden cases RK01–RK10 in analysis-remediation.md R3. Whole-query model equality requires reviewed anchored model scope,not a guessed column or automatic promotion of all numeric tokens. Exact Phrase requires at least two original token occurrences in one field;token-only matches may span allowed fields on the same eligible variant. Within a tier compare summed deduplicated token classes,source preferences,quantized fuzzy tie scores DESC,then Product UUID ASC. A fuzzy-dependent query never becomes exact through one identifier hit. Use the best whole tuple from eligible variants;explicit price/name sorts still override relevance. This explicitly refines the previous ambiguous identifier-count-before-phrase proposal without weakening Exact>Prefix>Partial>Fuzzy or identifier protection.
- **FR-009**: Support explicit ascending/descending price and name sorting, overriding keyword relevance. Price is the representative variant's displayPrice; names use the normalization in FR-005. Without keyword and without explicit sort, preserve UUID ascending. Explicit relevance without a keyword is invalid. Nullable orphan prices under D7 sort last for both price directions.
- **FR-010**: Category filtering MUST include the assigned category and every descendant depth. Brands MUST support multiple selected canonical identities with OR semantics. Category, brand, keyword, price and specification groups MUST combine with AND semantics.
- **FR-011**: Price bounds MUST be inclusive whole-đồng VND amounts, nonnegative and finite; either bound may be omitted. A reversed range or fractional price input MUST fail validation. No currency conversion is introduced.
- **FR-012**: Explicit specification filters MUST require categoryId. Resolve keys against a versioned category-aware registry of approved canonical definitions derived from existing catalog documentation and typed data structures; document source evidence and approval rather than inferring approval from stored data or example keys. Only category-applicable keys with approved types, units and search/filter eligibility may be used. Resolve descendant applicability within the selected subtree: a key may apply to a subset of categories, but applicable definitions must agree on meaning, type, unit and allowed operators; reject ambiguous/incompatible definitions with HTTP 400 rather than selecting one silently. For a known selected category, any requested key with no approved applicable definition in its self+descendant subtree is400; a multi-key combination unsupported by every category there is400. A valid supported filter with no matching products is200 empty. Products in partially inapplicable categories cannot match that predicate. The previously agreed unknown-category200 exception follows D5 global validation; no unsupported key bypass is allowed. Registry reconciliation must identify the actual supported matrix and version before approved technical-keyword/filter implementation or activation;registry-independent DTO/parser/normalization/basic listing/category/brand tasks may proceed under their actual technical gates; no invented key or unsupported stored value shape may be enabled.
- **FR-013**: Specification operators MUST follow the table below and the approved definition. Unsupported keys, units, type/operator combinations and any request requiring unit conversion MUST fail validation with HTTP 400; no automatic unit conversion is supported. Stored unknown/malformed facts MUST NOT match. Compare quantities only in the approved canonical unit; a differently stored unit cannot silently satisfy the predicate. Comparison MUST preserve decimal precision for numeric/quantity facts without treating booleans as numbers.
- **FR-014**: Selected alternatives within one brand or specification-key group MUST use OR. Different specification keys, price lower/upper bounds, and both bounds inside a single range MUST use AND. All price and specification groups MUST be satisfied by one and the same variant. A result requires at least one satisfying variant when variant filters are present.
- **FR-015**: Select the lowest-priced variant satisfying all active price/specification conditions and any variant-level keyword matches needed to satisfy FR-006. Break equal prices with variant UUID ascending; both new display fields MUST identify that same selection. Product-level keyword matches are shared by all variants. With no variant filters and no required variant-level keyword matches, use the lowest-priced variant overall. Thus a keyword matching only one variant SKU restricts the representative to that variant even when no explicit price/specification filter is present. If no owned variant exists, apply the D7/FR-003 paired-null compatibility exception rather than silently dropping legacy browse membership or adding409.
- **FR-016**: Apply filtering and sorting to the complete eligible product set before pagination. Totals MUST count distinct eligible products rather than variants or matches, and totalPages MUST reflect size. Acceptance uses unchanged data to verify full-page coverage and repeatability.
- **FR-017**: Validate inputs and return HTTP 400 using the existing ProblemDetail error contract for malformed values, unsupported parameters/filters/sorts/operators and invalid combinations. Valid selections of syntactically valid but unknown category/brand identities return an empty result rather than disclose resource existence through an error. Reject duplicate JSON members at every specFilters nesting level before binding (D4), including equal duplicate values. Never silently ignore an invalid filter.
- **FR-018**: Preserve canonical Product/Variant/Brand/Category identities, statuses, prices, specification values and existing Catalog functionality. Search is read-only; it does not publish, enrich, translate, recategorize or import catalog data.
- **FR-019**: Apply the owner-approved 2026-11-10 local MVP performance policy to the actual approximately 1,500-product fixture: p95 <500ms for browse, category+brand and price+specification; <1000ms for normal/selective keyword; <1500ms for broad `laptop` and fuzzy. Measure each of at least two independent serial JVM runs separately,20 warmups and100 measured size20 requests per scenario;record p50,p95,max,timeouts,distribution and SQL counts. No unexpected timeouts,N+1,unbounded loading or excess statements. This approved project-specific policy supersedes the previous uniform1s acceptance only for this MVP workload;unchanged unrelated synthetic/legacy assertions remain. See [performance policy](evidence/performance-policy.md).
- **FR-020**: pg_trgm availability is a deployment/readiness prerequisite. Normal enabled search must not silently degrade. Explicit operator-disabled fuzzy mode retains exact/prefix/partial and returns X-Catalog-Search-Mode: exact-only for nonblank q; it is not full-feature fuzzy acceptance. Required search capability lost after startup in either configured mode produces503 ProblemDetail for nonblank q; no-keyword paths do not invoke fuzzy and retain ordinary behavior while required DB objects are healthy. Startup capability failure blocks rollout. The operational HTTP healthcheck for a feature-capable backend MUST use the nonblank keyword probe defined in analysis-remediation.md R6,so the no-keyword path cannot mask missing capability. Coordinate the future healthcheck change with that backend version. See D3;no new endpoint/dependency,client feature toggle or extension installation in GET.

### Input and comparison semantics

Parameter spelling, serialization, limits and error behavior are defined in contracts/products-search.md and contracts/openapi.yaml, with D1–D7 resolving the review gaps. These are proposed contracts pending implementation/semantic verification, not live endpoint claims.

| Input | Omitted | Empty or explicit value |
| --- | --- | --- |
| Keyword | No keyword constraint; existing default ordering | Trimmed empty means omitted; text `null` is ordinary keyword text; no null sentinel |
| Category | No category constraint | One canonical category identity named categoryId; required with explicit specification filters; empty/malformed invalid |
| Brands | No brand constraint | Nonempty selection of canonical brand identities; duplicate selections collapse; empty/malformed invalid |
| Price | Missing bound is unbounded | Nonnegative whole VND; empty, nonfinite, reversed or fractional invalid |
| Specifications | No specification constraint | Nonempty typed predicates; empty, missing operand, wrong type/key/unit/operator invalid; no null sentinel |
| Sort | Relevance with keyword, existing UUID order otherwise | One supported explicit order; empty/unsupported/duplicate singleton invalid |
| Page/size | Existing 0/20 defaults | Preserve existing unsigned canonical integer syntax and bounds; duplicate singleton invalid |

Unknown parameter names remain invalid. Multi-selection is intentional only for supported groups; repeated singleton parameters remain invalid. A predicate range is one alternative, not two OR-ed comparisons.

| Approved specification type | Allowed operators | Semantics |
| --- | --- | --- |
| Numeric | equal, greater than, greater than or equal, less than, less than or equal, inclusive range | Compare finite typed numeric values; range lower ≤ upper |
| Quantity | Same as numeric | Compare only in the key's approved canonical unit; no automatic conversion; never guess decimal/binary capacity conventions |
| Text | equal, prefix, contains | Case/accent-normalized comparison; no fuzzy specification filtering |
| Boolean | equal | Explicit true/false only; missing is neither value |

### Key Entities

- **Product**: Public catalog identity with name, description, publication status, one category and optional canonical brand; result and count unit.
- **Product Variant**: Product-owned configuration with UUID, SKU, exact VND price and typed specifications; unit of combined price/specification eligibility and displayed price.
- **Brand**: Stable canonical UUID, code and display name; selected values form one OR group.
- **Category**: Stable UUID, name and optional parent; filtering includes transitive descendants.
- **Approved specification definition**: Reviewed category applicability, key, type, unit, supported operators and search/filter eligibility; its authoritative version,evidence and approval scope must be recorded before specification-dependent work,not unrelated DTO/parser/normalization/basic browsing.
- **Listing summary**: Existing ProductSummary plus the selected variant's displayPrice and representativeVariantId; introduces no new stored price authority.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of approved field-search, Vietnamese normalization, typo, exact-identifier and relevance-order examples produce the expected membership and order. Freeze positive and negative examples before implementation, including 3/4-character boundaries, model/SKU non-fuzzy cases, exact phrase priority and cross-variant rejection; record thresholds and expected results with the corpus. Freeze/verify partitions per implementation increment;US1.core cannot require future US2/US3/US4 cases. Full US1 additionally requires its approved-specification partition,and final integration requires the complete manifest with no pending partitions.
- **SC-002**: 100% of combined-filter cases use a single satisfying variant; the deliberately split price/RAM example returns zero products. Every nonnull displayed price/variant pair belongs to the expected satisfying variant; the D7 orphan compatibility case has exactly two nulls and never matches a real-variant condition.
- **SC-003**: Across every page over unchanged acceptance data, 100% of expected products appear exactly once, in repeatable order, with exact distinct counts and page totals.
- **SC-004**: 100% of existing catalog browsing/detail/image acceptance scenarios retain their approved behavior, allowing only the explicitly additive listing fields; no DRAFT/ARCHIVED products or unapproved specification content appear through search.
- **SC-005**: 100% of the invalid-input matrix yields the specified error outcome, and every valid no-match query yields a successful empty page.
- **SC-006**: Each independent run satisfies every scenario-specific p95 budget in FR-019 on the approximately1,500-product fixture,with zero unexpected timeouts and preserved SQL/result-loading limits. No averaging p95 across runs;the optional10,000-product exploration is not an MVP gate.

## Assumptions

- Existing public read access, UUID identities and whole-đồng VND representation remain authoritative. Additive listing fields are the intended compatibility change; no new endpoint, role or catalog-editing workflow is assumed.
- No translation or bilingual enrichment is implied by accent-insensitive search. Vietnamese test records can establish normalization independently of the English-heavy source package.
- Lowest-priced eligible variant (including required variant keyword matches) and UUID tie-breakers are agreed rules that make display price, sort and pagination testable. Brand/category filter values use stable identities rather than mutable display labels.
- Approval of specification definitions is separate from document shape validity; the current validator alone cannot establish filter approval. A registry/approval workflow is a dependency, not an assumed new administration feature.
- The scoring formula, threshold values and representative validation corpus are design/calibration work constrained by FR-006–FR-008, not unresolved search semantics. No synonym dictionary, spelling suggestions or relevance personalization is assumed.
- The clarification supersedes the earlier “keyword alone never changes representative selection” assumption. It also narrows the earlier conversion wording to no automatic conversion and names categoryId as mandatory for explicit specification filters. Other parameter serialization remains contract-design work.
- Existing published-product/variant invariants are prerequisites. Test fixture approval and performance environment recording are required before acceptance; no live database state is asserted by this specification. Review verification register in design-resolutions.md preserves all eight dependencies.

### Repository evidence and governance

Inspected on 2026-11-08: `.specify/memory/constitution.md` v1.1.0; `specs/001-product-catalog/spec.md`; `specs/005-catalog-data-enhancement/spec.md`; `docs/catalog-enhancement/architecture.md`; current catalog controller/service/repository, ProductSummary/ProductPage, Product/Variant/Brand/Category and TechnicalSpecificationValidator.

The controller currently accepts only page/size; the repository orders published UUIDs ascending. Brand is optional, category has a parent, and technical facts belong to Variant. The enhancement architecture document contains historical/proposed statements; current models establish the present source baseline. Existing uncommitted detail/brand changes are outside this documentation task and do not establish an approved listing contract.

The empty historical `specs/004-search-filter` directory is not reused: sequential directory numbering yields `007-product-search-filter`. The active template was resolved using the repository resolver. No `.specify/extensions.yml` exists, so no pre/post hooks or branch creation hook ran. The existing branch `features/SearchAndFilter` is retained.

Planning must follow the constitution's domain ownership, contract/error, data-preservation and meaningful-test requirements. This specification does not approve dependencies, populated-database changes, deployment or application implementation.

Clarification evidence: the architecture registry contract documents canonical keys, category applicability, types, units and eligibility, but its example keys/value document are expressly illustrative. The current TechnicalSpecificationValidator accepts text, finite number, boolean and scalar quantity shapes; it does not establish per-key approval or support stored ranges/lists. Range filter operands compare supported scalar values and do not authorize stored range/list capabilities. Registry file/version and the reviewed matrix were not verified in this checkout; planning must trace existing sources, record gaps and fail closed rather than invent approvals.

Clarification validation on 2026-11-08: three supplied decisions integrated, zero additional questions asked, zero unresolved clarification markers. Sections updated: Clarifications, User Scenarios & Testing, Edge Cases, Functional Requirements, input/operator semantics, Key Entities, Success Criteria and Assumptions. Checklist notes retain the original pass-1 history under the skill's marker-only update rule; the current checkbox states and this validation record supersede those historical notes. No pre/post clarification hooks exist. No plan or application changes were made.

### Scope boundaries

Included: the existing product listing's search, filters, sorting, selected-variant display price and compatibility validation. Excluded: search history, user behavior tracking, inventory filtering, AI recommendation, personalized ranking, frontend construction, new taxonomy/specification administration, catalog import/translation and unrelated Catalog changes.
