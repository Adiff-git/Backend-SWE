# Data model and query model

> Implementation revision (owner MISSION,2026-11-09): see [mission-scope.md](mission-scope.md) for authorized approvals,local best-effort fact policy and explicit planning deltas. Four keys/five family bindings are approved;source model Rank7 and battery remain deferred. Runtime taxonomy uses actual Backend UUIDs and disables missing/ambiguous family bindings. Historical pending-approval observations below are retained as history,not current authorization. Actual execution evidence is recorded separately;this notice is not a test PASS.

## Existing authoritative entities

Product: UUID id, required name/category/status, nullable description/Brand. Category: UUID id,name,parentCategory. Brand: UUID id,immutable code,name. ProductVariant: UUID id/productId,SKU,optionCombination,whole finite NUMERIC sellingPrice,typed technicalSpecifications. ProductImage and private signing lifecycle remain unchanged. New code must not change statuses, prices, taxonomy, media or source links.

Technical document shape: schema_version=1 and values map; scalar text, finite number, boolean, or quantity with exactly value/unit. The DB V11 envelope constraint is weaker than key approval and per-value typing, so reads must guard legacy/malformed facts. SQL CASE guards must precede casts; no implicit strings-to-number or boolean-to-number conversion.

No new persistence entity/table is required. New versioned immutable normalization functions and expression indexes are additive database objects. No search history or denormalized replica is stored.

## Registry model (proposed runtime configuration, not created here)

Location: demo/src/main/resources/catalog/specification-filter-registry.json. Load once per deployment; no client-selected registry version or hot reload. Deployment version changes are coordinated with query/index assumptions.

| Field | Constraint |
| --- | --- |
| registryVersion | Required stable version string |
| documentSchemaVersion | Exactly1 for current technical documents |
| definitions[].key | Existing canonical snake_case key; immutable meaning |
| definitions[].categoryIds | Nonempty canonical UUID bindings; explicit categories, no inferred inheritance |
| definitions[].type | number,quantity,text,boolean; supported scalar shapes only |
| definitions[].unit | Required canonical token for quantity; absent for other types |
| definitions[].operators | Nonempty subset of the type's allowed contract operators |
| definitions[].searchable / filterable | Explicit booleans; false never inferred true |
| definitions[].identifier | Explicit flag for technical text requiring protected identifier treatment |
| definitions[].approval | APPROVED to activate; pending candidates remain documentation-only |
| definitions[].evidence | Source reference/hash, approver,approval reference required for activation |

Registry validation rejects duplicate/conflicting key/category bindings, unknown operators/types, absent quantity units and unsupported schema versions at startup. Resolve one request across categoryId subtree: agree on key meaning/type/unit/operators among applicable definitions; otherwise400. For a known subtree, zero applicable definitions for any key means400; no category supporting the complete multi-key combination also means400. Partial-subtree applicability excludes inapplicable products. A supported filter matching no products is200. See D5 for the unknown-category exception and global validation order. Incompatible definitions cannot be hidden merely because current products do not use them. Unknown category with otherwise globally valid approved operands returns an empty result; an unknown/unapproved key is400 even on an empty subtree. Only explicitly approved searchable definitions contribute to keyword search; explicit specFilters require categoryId, keyword-only search does not.

Registry-independent core development does not instantiate/activate the unfinished approved-key module;it uses no technical facts and is not full US1/feature acceptance. The generic contributor interface is not an empty approved registry. No complete production-approved matrix was found: registry-inventory.md distinguishes observed keys from activation. Implementation gate B1 must resolve before an active runtime registry is shipped. Startup failure for invalid configuration is an operator/configuration problem; it must not be misreported as a user's unsupported-filter400.

## Request and internal query types

ProductSearchRequest is a Lombok request DTO with q,page,size,categoryId,brandIds,minPrice,maxPrice,sort and typed specification groups. Use exact BigInteger/BigDecimal amounts, bounded collections and nested Bean Validation. Parsed transport shape and semantic validation are distinct; explicit null is not a missing value. Criteria contains normalized tokens with protected/natural classification, approved category/key definitions, bound operands and an enum sort.

Each specification group has one key and alternatives. Numeric/quantity alternatives are one comparator operand or an inclusive range with two bounds; text alternatives are eq/prefix/contains; boolean alternatives are eq true/false. Different keys AND, alternatives OR. An array containing two separate gt/lt alternatives means OR; clients must send between for an AND range. No automatic unit conversion, arrays or stored ranges. D4 requires duplicate-member rejection before DTO binding, and duplicate group keys remain invalid. D1 defines canonical typed keyword views independently of specFilters predicates; D2/D4 define token boundaries and deduplication.

## Query projections and selection

EligibleVariant: productId,variantId,sellingPrice, per-token best class, relevance tuple. Membership combines Product fields and that exact Variant. One row per owned variant before product reduction. D7 adds an orphan sentinel ONLY when NOT EXISTS(any owned variant), no price/spec group is active and every token matches shared fields; it must not represent real-but-nonmatching variants.

ProductCandidate: productId,bestEligibleRank,representativeVariantId,displayAmount,normalizedName. bestEligibleRank uses the seven-tier R3 comparator in analysis-remediation.md,not identifier-count-before-phrase;the highest rank must come from an eligible variant. Representative is lowest price then UUID among eligible variants. Ranking can use another eligible variant, never one excluded by filters. Do not expose raw rank fields or registry provenance in public response.

ProductCandidatePage: ordered bounded candidates plus distinct total from one SQL statement snapshot. An empty later page still carries the full count. Existing envelope semantics and Pageable inputs remain.

ProductListingItem: id,name,category,primaryImageUrl,displayPrice,representativeVariantId. Use this listing-only DTO to isolate shared ProductSummary consumers; ProductPage.items changes its Java element type while existing JSON fields retain their meaning. SellingPrice remains amount exact whole number,currency VND. Both fields are present/non-null for valid variant-backed products. For the explicitly preserved legacy orphan branch (D7), both fields are present and null, never a fake zero/UUID. Price sort uses NULLS LAST in either direction. No missing-variant409 is added to listing; source detail409 remains unchanged. Do not inner-join orphan products out of the legacy count/page path.

## Transaction and lifecycle

No new business lifecycle. Catalog read orchestration remains service-owned. Native criteria query and transaction-local threshold must use the same transaction-bound connection, e.g. EntityManager native queries for both. Preserve current write-capable read transaction where FOR SHARE publication checks are used; do not add blanket readOnly=true or REPEATABLE READ. Signer credentials resolve before locks. Publication locks remain short and ordered; no network call while locked. No retry loops introduced.

Count/page metadata reflects the eligibility statement snapshot. Later publication rechecks may suppress a row under concurrent changes; existing fail-closed safety takes precedence. Full-page/count equality and cross-request stability are tested under unchanged data, while race tests separately verify no hidden data/URL exposure.

## Operational capability and recovery models

D3 governs enabled/explicitly disabled/unavailable fuzzy states; readiness fails on missing required startup capabilities, and nonblank keyword requests return503 if required search capability is lost after startup in either configured mode. Diagnostic exact-only responses identify mode with X-Catalog-Search-Mode. No-keyword paths omit fuzzy evaluation; missing schema/DB is still an infrastructure error. D6 governs immutable migration history and partial-rollout recovery. All eight pending verification dependencies remain in design-resolutions.md; no runtime flags or recovery procedures are implemented here.

## Contributor and readiness boundaries

Core repository criteria accept a technical-keyword contributor interface with no approved-field provider in the incomplete core increment. T030 later registers its approval-backed implementation after T008;missing/invalid configuration when that full module is installed fails its startup validation. Core/basic filter work does not depend on registry approval and must not claim approved-key support. No production key,model column or runtime administration feature is invented.

Corpus partitions and local-vs-target readiness follow analysis-remediation.md. Per-worker instance/database/storage identity must be proven before parallel ITs;otherwise execute serially. The existing GET keyword HTTP probe,not ApplicationAvailability alone,makes a missing required capability visible to the operator healthcheck.

## Final owner-authorized implementation evidence

See [mission-scope.md](mission-scope.md),[local verification](evidence/verification-summary.md),[coverage](evidence/implementation-coverage.md) and [handoff](evidence/handoff.md). New V16–V19 and generated description cache/conservative typo guard are reported design deltas;the original20FR/6SC and all RK expectations remain. Actual1500 performance is required;10k is exploratory under resumed owner policy. T059 optional index work is deferred with no-index dependency resolution;T063 deployment remains blocked. Historical proposal/pending notes above are retained as history,not current approval or execution state.
