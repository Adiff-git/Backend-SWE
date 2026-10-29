# Catalog Model, Normalization and Enrichment Design

**Date:** 2026-10-29. **Status:** proposal for review, not implementation authorization.

## 1. Recommendation and verified baseline

Adopt a small relational Catalog core with a dedicated Brand entity, keep existing Product/Variant/Category identities and relationships, and add a separate typed JSONB document for variant configuration specifications. Govern technical keys, units, aliases and category applicability through versioned reviewed registry files. Add a source-link/provenance table for repeatable source matching and transactional enrichment receipts. Do not create relational rows for all 770 raw attributes or merge existing products.

**Final scope:** Implementation enriches only existing pilot Product/Variant identities. The original full-corpus/new-creation discussion below remains historical architectural evidence; it does not authorize Product/Variant creation or require full dataset import.

This refines the earlier report's lighter controlled-brand-text and external-provenance alternatives. Those alternatives remain workable for a one-off import. Stable brand identifiers, reviewed alias management, repeatable enrichment and restart recovery now make one Brand table and one source-link table worth their cost. There is still no demonstrated requirement for runtime attribute administration, so relational attribute-definition/value tables are deferred.

All five requested artifacts are available and were read: REPORT.md, profile.json, technical-attributes.csv (770 rows), brands.csv (424 rows), category-paths.csv (1,468 rows, including the unresolved bucket). There are 1,467 accepted source-qualified paths, not 1,468 usable categories. Current entities, migrations V1–V9, importer, Catalog HTTP contract, integration-test source and constitution were inspected. No live PostgreSQL audit or test execution was performed. Findings about 1,000 products are traced seed/source evidence, not current production counts.

Verified constraints remain:

- Product, Variant and Category use UUIDs; Product has exactly one assigned Category and DRAFT/PUBLISHED/ARCHIVED status.
- Variant owns SKU, whole finite NUMERIC selling price, and flat string-map JSONB options.
- SKU uniqueness ignores case. Option maps are unique per product; multi-variant products require nonempty maps; every product needs a variant.
- Existing schema does not require nonnegative prices; importer requires positive approved whole VND. Preserve this distinction.
- Brand, standardized technical specifications, inventory and Product creation timestamps are absent.
- Listing accepts only page/size, defaults 0/20, size 1–100, UUID-ascending order. Current response fields are exact-tested; detail behavior must remain unchanged.
- Public reads expose only PUBLISHED products, use batched relationship loading, and preserve publication/image rechecks and signed URLs. Existing image-read transactions need row-lock capability.

No application, migration, raw-data, seed, constitution or database changes were made. The interrupted earlier `specs/004-search-filter` scaffold and feature pointer were left untouched. This document is not a Spec Kit feature specification or plan command output.

## 2. Canonical ownership

| Owner | Authoritative fields | Rules |
|---|---|---|
| Product | Existing UUID, name, description, assigned category, optional brand reference, publication status | Customer-facing identity remains independent of retailer names/URLs. No price/stock copied here |
| Variant | Existing UUID, parent Product, globally unique SKU, approved selling price, selectable option combination, typed configuration specifications | Price and configuration facts always remain associated with the same variant |
| Category | Existing UUID, display name, optional parent | Preserve identifiers; taxonomy corrections are reviewed data changes |
| Brand | New UUID, immutable canonical code, canonical display name | Identifier survives display-name changes; unknown Product brand is null |
| Source link | Source item identity, mapped Variant UUID, accepted source hash and normalization/manifest receipt | Provenance is not customer product identity or inventory |
| Versioned normalization registry | Attribute definitions, category applicability, source aliases, types, units, parser versions, filter eligibility | Not a new customer-editable taxonomy or runtime administration system |

### Product-level versus variant-level specifications

A fact belongs to Product only if it describes the entire product and is invariant across every legitimate variant: e.g. a verified family-level feature. CPU, RAM, storage, display and OS can vary with configuration, so they must not be presumed product-level.

For this phase, store observed technical facts on the current single Variant, including facts that might eventually prove shared. Do not add Product technical-specification storage yet. There are no explicit variant groups in the observed dataset and no reliable basis for computing shared intersections. This avoids an inheritance/override system that nobody currently needs.

If verified grouped products later justify shared storage, introduce it separately with an explicit invariant: a shared value must agree across all variants; disagreement is a modeling conflict, not silent override. Filtering should then resolve an effective configuration fact per variant. Do not create two competing owners now.

### Product/variant grouping

Keep one source-specific configuration as one Product with its existing Variant unless an independently reviewed grouping proves otherwise. Repeated or similar names are not merge evidence. GTIN is absent; MPN mirrors SKU; no retained hasVariant/isVariantOf/ProductGroup links exist. Neither a nonempty specification map nor a price establishes a family relationship.

Existing one-variant products retain `{}` options. Do not populate options with every technical fact merely to make filters work. New multi-variant grouping requires a separately reviewed mapping of distinguishing choices, stable Variant IDs and nonempty distinct option maps. If the distinguishing choice is unknown, quarantine the proposed group.

## 3. Brand model and aliases

### Decision: dedicated Brand, without an alias table initially

Proposed `brands` fields: UUID `id`, immutable unique nonblank `code`, nonblank `name`. Product gains nullable `brand_id` referencing Brand. No delete cascade; deletion of referenced Brand is refused. Display name need not be a global identity key. Codes use a reviewed canonical form and uniqueness; do not generate identity from mutable display text on every import. Persist approved UUID/code mappings in a versioned manifest; this proposal does not assign IDs.

Why: 17,899 records contain brands, representing 424 raw labels and 405 lexical identities after NFC/whitespace/case normalization. ASUS/Asus and HP/Hp demonstrate repeated alias handling. A foreign key centralizes identity and permits display-name correction without rewriting all Products. Brand is still owned inside Catalog, not a new service/domain dependency.

Aliases belong initially to a reviewed versioned import registry keyed by source plus normalized raw label. Each approved key maps to exactly one Brand code. Ambiguous keys have no active mapping and must not be resolved by import order. Preserve raw label and rule ID in provenance. Add a `brand_aliases` table only if aliases need runtime administration or database-side lookup; neither is established now.

Unknown/missing/unapproved Brand: Product.brand_id remains null. Do not create a single fake Brand entity named Unknown or import `Hãng khác` as a manufacturer. Label an unresolved known-looking brand in the review output rather than creating entities automatically.

Verified lexical-equivalence candidates: ASUS/Asus, HP/Hp, MSI/Msi, Ecovacs/Ecovacs-space, Fujihome/Fujihome-space. Lexical normalization is automatic; approving the identity mapping is a one-time reviewed registry decision. `Apple Chính hãng` (352) versus Apple and `Samsung Chính hãng` (233) versus Samsung require explicit approval because the suffix adds a distribution claim. No global suffix-stripping rule or brand extraction from names.

Alternative: controlled Product brand-code/text field plus registry. Fewer joins/tables, but no foreign-key protection, more repeated-label governance and harder centralized lifecycle. Prefer it only if stable database-local Brand identity is deliberately rejected; do not implement both representations.

## 4. Category model and taxonomy

Existing Category structure is adequate; current assignments are not proof of a normalized electronics taxonomy. The inspected seed has 31 categories, whereas source paths include 1,467 accepted source-qualified paths and 1,796 unresolved records. Navigation mixes product type, brand, series, condition and marketing.

Preserve every existing Category UUID. Renaming a label does not require a new UUID if meaning is unchanged; semantic category changes require a reviewed mapping. Never generate replacement IDs from newly normalized category names for existing rows.

Maintain an explicit source-path-to-target-category-UUID registry. Key it by source and complete cleaned path/explicit category; retain original path. A source mapping cannot silently point to multiple target categories. Existing aliases may map to one reviewed UUID without deleting historical Category rows. Preserve existing Product.category_id until a per-product correction is approved; enrichment of brands/specs does not authorize recategorization.

Canonical browsing structure should separate product type from brand and used condition: Computers > Laptops/Monitors/Desktops/Components; Phones/Tablets; Cameras > Cameras/Lenses; Audio; Wearables; Accessories; TVs/Appliances as approved. These are proposed labels, not inserted categories. Services/buyback/SIM and non-device goods need a business inclusion decision.

Examples: verified TGDD Laptop and CellphoneS laptop paths may map to one Laptop UUID; `Hàng cũ > Máy ảnh cũ > Lens` may map to Lenses with condition retained separately in staging. Similar labels `Ốp lưng đ.thoại` and `Phụ kiện ốp lưng` are not automatically equivalent.

Validate cycles and self-parent references in approved taxonomy plans; current FK alone does not prevent cycles. No new closure table/materialized path is needed. Initial future filtering should use directly assigned category; descendant filtering can be a later explicit contract.

## 5. Technical specification design comparison

| Approach | Integrity | Filtering/performance | Import/maintenance | Migration cost |
|---|---|---|---|---|
| Typed JSONB plus reviewed file registry | Database checks document shape; service/import validator checks keys/types/units/category; weaker per-key SQL integrity | Suitable for allowlisted numeric/text/boolean fields; query/index choices require measurement | Small schema, no row explosion; registry versions and validation are essential | One additive Variant column; possible measured indexes later |
| Relational definitions and typed value rows | Strong FKs, category bindings and typed-value constraints; complex cardinality/unit constraints still need validation | Explicit typed filters, but multiple-attribute filtering adds joins/subqueries and count/dedup risks | More tables, mappings and value upserts; powerful for runtime administration | Largest migration/backfill effort, especially ranges/arrays/multivalues |
| Relational definitions/category bindings + JSONB values | Relational registry; JSON keys still cannot have ordinary per-key FKs | Flexible values and searchable metadata; validation/cache/version coordination needed | Good when users administer attributes dynamically; two representations must stay consistent | Moderate; not justified merely by 770 raw keys |

**Decision:** typed JSONB values with a versioned file registry. This is a relational Catalog with JSONB configuration documents, but not the relational-definition-table hybrid in the third row. Do not describe a registry file as an existing database capability.

Reasons: raw keys overlap; 43 lexical key-alias groups already exist. All observed values are strings, but reliable filters need typed values. An initial allowlist can cover high-value RAM/storage/display/CPU/OS/NFC without modeling every rare raw field. No runtime attribute-authoring requirement or performance result justifies full relational attributes now.

### Registry contract

Each canonical definition declares:

- Immutable key and human label; category UUID applicability and whether scope is Variant (initially all persisted technical facts).
- Value kind: number, text, boolean; explicit quantity, range or list structures only for approved definitions needing them.
- Canonical unit/dimension, accepted input units, conversion rules and precision; no implicit unit assumptions.
- Source/category-specific key aliases and parser versions.
- Cardinality and bounds only where justified; unknown and conflict behavior.
- Whether filtering is enabled and which comparisons are supported.
- Registry version; key/type/unit meaning cannot change silently. Breaking semantic changes require a new key or explicit conversion/backfill version.

Canonical keys should communicate semantics: `physical_ram`, `installed_storage`, `cpu_model`, `display_diagonal`, `display_resolution`, `refresh_rate`, `nfc_supported`. `maximum_supported_ram` must be distinct from physical RAM. Do not equate all `Độ phân giải` labels across cameras and displays.

### Value document

Proposed Variant column `technical_specifications` contains a versioned object, for example:

```json
{
  "schema_version": 1,
  "values": {
    "physical_ram": {"value": 8, "unit": "GB"},
    "display_diagonal": {"value": 15.6, "unit": "inch"},
    "cpu_model": "source-confirmed model",
    "nfc_supported": true
  }
}
```

This is illustrative syntax, not a source record. Attribute registry dictates exactly which shape applies to each key; do not permit arbitrary mixtures. Quantities use JSON numbers processed with exact decimal semantics; units are canonical tokens. Boolean false is a real observation. Unknown values are absent from `values`; never store zero/false/empty text to mean missing. Empty document defaults to `{"schema_version":1,"values":{}}` and means no accepted normalized facts, not “all features absent.” Separate staging disposition distinguishes unprocessed from processed-with-no-values.

Ranges retain min/max with inclusive meaning when sourced; a 48–120 Hz claim must not silently become a scalar 120. Storage arrays distinguish installed devices from expansion capacity; initial filters may support only a documented unambiguous installed-capacity subset. Do not advertise generic filtering over every JSON path.

Raw/unrecognized/conflicting values remain in immutable source observations and normalization manifests, not the filterable values document. Record per-attribute source pointer, raw key/value, parser/rule version and normalized value there. No technical specification becomes a selectable option automatically.

For filter correctness, an active technical condition, price condition and option condition must all be satisfied by one Variant. Missing normalized facts fail a requested filter; omitting that filter imposes no restriction. Product-shared fields such as Brand and Category apply to Product. Returned Products and totals count unique Product IDs.

## 6. Normalization and quarantine policy

| Area | Safe automatic | Conditional validation | Review/quarantine |
|---|---|---|---|
| Brand | NFC/trim/collapsed whitespace; preserve raw | Apply approved source alias to Brand code | Uncertain aliases, distribution claims, unknown labels; null target brand |
| Category | Clean whitespace while retaining complete path | Approved source-path mapping to existing UUID | Missing/ambiguous path, conflicting type, cycle, proposed correction |
| Attribute keys | NFC/trim/case comparison for alias discovery | Category-specific reviewed alias mapping | Different semantics under same label; unequal collision values |
| Values | Unambiguous formatting cleanup | Typed field parser with validated unit/context | Invalid quantity, unknown placeholder, inconsistent claims |
| Units | `inch`/`inches`; `60Hz`/`60 Hz` under known dimension | Explicit mm/cm/m or g/kg conversion with exact decimal arithmetic | Ambiguous MB/GB convention; mAh versus Wh without voltage |
| Identity | Verify existing UUID/source mapping | Approved source URL alias change using explicit mapping | Name-only/ambiguous match; changed identity inputs |
| SKU | Validate source string, trim staging copy, retain original | Check catalog-wide case-insensitive uniqueness | Missing, `0`, placeholder, conflict; no invented retailer SKU |
| Price | Parse exact numeric VND string with decimal arithmetic | Require positive whole approved amount for import | Zero/missing/negative/fractional/nonfinite, aggregate-only price |
| Options | Validate flat nonblank string map | Approved actual choices, distinct combinations per Product | Invented choices or collision caused by normalization |

Actual value rules:

- `8GB` and `8 GB` normalize to declared 8 GB. Physical `6GB + Mở rộng 6GB` is not 12 GB physical RAM; the extended component is separately represented only if its meaning is explicit.
- `Ổ cứng` includes installed SSD capacity and supported expansion in the same text. Expansion is never installed storage.
- Source-labeled GB/TB/MB are preserved as quantities initially. Numeric cross-unit capacity filtering requires an approved per-dimension conversion convention; do not assume RAM and storage share one byte convention. This does not block same-unit initial filters.
- Screen `15.6 inches` becomes 15.6 inch. Explicit 1920×1080 dimensions can normalize; bare labels such as 2K cannot be assigned guessed dimensions.
- `Có`/`Không` under `Công nghệ NFC` map to true/false. Coverage is 1,464 records, so this is an actual boolean use case. A blanket string-to-boolean conversion is forbidden.
- CPU/OS labels start as reviewed text. `Hệ điều hành khi ra mắt` differs from installed OS.
- Colliding alias keys with equal normalized values may coalesce while retaining all provenance; unequal values remain unresolved. Do not overwrite with the last key.

Disposition classes: NORMALIZED_VALID (mandatory import criteria satisfied); NORMALIZED_PARTIAL (optional facts missing); REVIEW_REQUIRED; QUARANTINED; NON_PRODUCT_OBSERVATION. Every raw record receives one exclusive overall disposition and zero or more issue codes, plus independent per-field resolution status. The disposition names are proposed staging states, not new Product publication statuses.

Price partition: 15,401 positive exact observations, 2,510 zero exact observations and 2,747 aggregate-only observations reconcile to 20,658. Only the first class is even a price-eligible candidate; it still needs identity, category, SKU and approval validation. The 139 SKU-zero records overlap other classes and must not be added to price totals as a separate partition. Likewise brand/specification coverage overlaps every disposition.

Creation gates and enrichment gates are distinct. An already mapped existing Variant can receive independently approved brand/specification metadata even if its current source observation lacks an acceptable new price or SKU, provided identity is unambiguous and existing approved price/SKU remain unchanged. Identity conflicts block enrichment. Optional specification conflicts block only those fields when the reviewed plan explicitly permits partial enrichment; they must remain visible in the report.

Zero prices are held until independently verified; never replace with one đồng, range minimum, average or another variant's amount. Aggregate category/family records stay traceable outside concrete import. A syntactically valid nonblank name like `undefined` is not accepted as a usable product name.

## 7. Source matching and preservation of the existing 1,000

Current stable import identity is UUIDv5(namespace `8f776a17-4fe9-4b13-82af-3e134ba6e55d`, exact `source|source_url`). Variant identity is UUIDv5(existing Product UUID, `variant|single`). These formulas are verified in current importer/normalizer. They are stable for these inputs, not guarantees that retailer URLs never change. Preserve exact identity inputs; URL redirects/new canonical URLs require an explicit alias map, not automatic re-keying.

The crawler's own source-record hash includes SKU or alternate JSON-LD identity. It can change when that input changes and is secondary provenance, not the sole backfill key. GTIN is absent and MPN is not independent. Never fallback to closest product name.

### Proposed source-link table

`catalog_source_links`:

- UUID `id`.
- `source` and immutable `source_key`, both nonblank; unique `(source, source_key)`.
- `source_url` and optional `source_record_id` retained as observation metadata.
- Required `product_variant_id` FK to Variant; derive Product through Variant instead of duplicating an unconstrained Product FK.
- Accepted source payload SHA-256, applied normalized-content SHA-256, normalization version and approved-manifest SHA-256, with required format/nonblank constraints for applied records.
- `updated_at` as TIMESTAMPTZ/Instant; this is provenance time, not newest-product time.

For the existing corpus, source_key is the exact source URL because the importer and snapshot identify one concrete record per URL. Future multiple source variants per URL require a reviewed composite source-item identity and persisted mapping before support; do not reinterpret old keys. A Variant can have multiple source links only by explicit reviewed matching. No cascading deletes.

This table is one accepted source mapping/receipt per item, not an immutable history store. Immutable reviewed manifests retain before/after values and prior receipts; updates never erase the retained raw observations. Hashes need a documented canonical serialization for normalized content. A hash alone does not prove source freshness or approval.

### Backfill algorithm (design only)

1. Pin raw snapshot, reviewed legacy manifests/seed mapping and normalization registry hashes.
2. Enumerate the actual target's Product/Variant IDs and current fields in a future explicitly targeted read-only preflight. Do not assume the seed's old publication split equals current state.
3. Match each existing Product UUID to exactly one source identity using approved provenance and deterministic derivation. Verify Variant UUID, ownership and source evidence. An unexpected additional Variant, missing mapping, duplicate source key, or conflicting SKU is a review case.
4. Generate an enrichment-only plan: optional brand assignment, approved technical values, source link, expected-before hashes. Do not plan inserts of replacement Products/Variants.
5. Preserve Product/Variant IDs, names, descriptions, category assignments, status, SKUs, prices, options, ProductImage IDs/keys/orders/primary flags and upload relationships. Category/price/option correction is a separate approval/action.
6. For each enrichment item: empty target + accepted source => proposed fill; equal accepted target => unchanged; different nonempty target or ambiguous source => conflict. Missing source field means leave target untouched, never clear it.
7. In an approved future transaction, verify expected-before values, write only allowed metadata, and update source receipt atomically. This prevents concurrent editor changes being silently overwritten. Preserve existing lock ordering when coordinating related rows; no new isolation default.
8. Commit bounded batches without splitting one Product group. After a failed batch, roll it back completely and retain the failure report; already committed batches remain committed.
9. Re-run the exact reviewed plan to classify committed-equal items as no-op. If a commit outcome was uncertain, inspect target/receipt before retry. Same raw snapshot with a new normalization rule is a new plan/version, not automatically the same import.
10. Reconcile all 1,000 expected records: enriched, unchanged, unmatched or conflict; separately count unresolved fields. Do not promise 1,000 brand assignments or 998 normalized spec documents: source presence does not mean approved alias or parsable facts.

Immutable manifests stored durably with restricted write access, content hashes and batch reports are sufficient for first-release audit; they must not live only under `/tmp`. No cloud service is required. If a durable run-history database is later required, introduce it as an explicit requirement rather than adding multiple job tables now.

## 8. Database change inventory and migration design

All names below are proposed, not existing. Do not reserve migration versions now; inspect actual target Flyway history and team changes first. Applied V1–V9 must remain immutable.

| Change | Limitation/evidence | Relationships/constraints | Alternative | Compatibility and migration |
|---|---|---|---|---|
| New `brands` | 17,899 populated brands; 424 raw labels; identity currently lost | UUID PK, unique immutable nonblank canonical code, nonblank name | Controlled Product text/code and file registry | Additive empty table; populate only approved canonical registry entries; no auto-created aliases |
| Nullable `products.brand_id` | Product has no brand | FK to brands, no cascade; index justified by FK/filter use | Brand text | Existing rows remain null and valid; backfill separately; preserve exact response DTOs |
| `product_variants.technical_specifications` JSONB | 17,728 source records have specifications, currently discarded | Non-null object envelope with schema version and object values; default empty version-1 document; precise key/type/unit validation at service/import boundary | Relational typed values or relational definitions + JSONB | Additive; old variants have no normalized values; verify database shape constraints and application mapping; no option/price rewrite |
| New `catalog_source_links` | Current provenance external; enrichment/retry must distinguish applied state | UUID PK; unique source/key; required Variant FK; accepted hashes/version/manifest metadata; TIMESTAMPTZ; no cascade | Durable external mapping/receipts only | Additive; backfill only exact matches. Initially empty; no requirement that legacy Products immediately have links |
| Reviewed taxonomy data plan, no new hierarchy structure | Source paths mix types/brands/condition; existing hierarchy usable | Existing parent FK plus cycle validation in taxonomy workflow | Automatic retailer-path import, rejected | Preserve Category IDs and Product FKs; approved corrections separate from brand/spec enrichment |

JSONB shape constraints must reject non-object values, malformed envelopes and invalid schema-version shape; they do not enforce the registry's entire per-key ontology. Application/service validation and importer validation must agree with the same versioned contract; direct authorized writers must follow it. If SQL-enforced per-attribute typing becomes mandatory, revisit relational typed values rather than claiming JSONB alone provides it.

No Product shared-spec column, 770 individual columns, runtime attribute tables, brand-alias table, inventory table, price constraint change, creation timestamp or external search infrastructure is required in this proposal. Model names/families remain source observations or explicitly defined technical keys until a stronger product-identity requirement exists.

### Migration sequence

1. Review schema/registry contracts and obtain authorization for implementation. Freeze expected existing schema/data baseline.
2. Build/test additive migrations on a disposable database restored from an authorized representative dataset. Verify fresh-install and upgrade paths; do not edit applied migrations.
3. Deploy schema additions without changing existing DTO fields, visibility, pagination, sorting or image behavior. Check upgrade compatibility with the actual previous app; do not claim downgrade compatibility without testing.
4. Deploy metadata-aware validators/importer. Update its exact Flyway allowlist/checksums for the approved new schema version. Current importer rejects any history beyond exact V1–V9; leaving it unchanged is a known blocker, not a reason to bypass validation.
5. Backfill approved Brand dictionary and exact source links/metadata in reviewable batches. Keep unverified legacy metadata null/empty.
6. Validate counts, foreign keys, preserved identifiers/media and no-op replay. Measure performance before adding any technical-key index.
7. Leave Search & Filter API additions for its separately approved specification and implementation.

Recovery: retain per-item before/after values. An approved compensation may restore only fields that still equal the enrichment's after-value, preserving later edits and source receipts/history. Do not drop new tables/columns or delete/recreate Products as a routine rollback. Do not roll back an entire database snapshot over unrelated concurrent changes.

## 9. Required normalizer/importer changes

### Normalization stage

Stream source records or use bounded staging; retain every source observation, original brand/path/specification value and hash. Generate reviewed brand/category/attribute mappings. Produce canonical typed fields, issues, dispositions, registry versions and source pointers. Keep raw archival storage outside the API. Do not publish all source keys as supported filters.

The current normalizer proposes empty options for eligible single-source items and does not preserve brand/specifications in its candidate projection. Extend that projection in future work; do not change the meaning of existing identity algorithms.

### Import planning stage

Separate operations: CREATE_CONCRETE_ITEM, ENRICH_METADATA, UNCHANGED, CONFLICT, QUARANTINE. Source normalization eligibility and target mutation approval are separate decisions. Publication remains independently approved. Enrichment plans never recompute old publication state from an old manifest or replace existing images.

Retain existing target-confirmation, exact schema-history checks, source-fact binding, case-insensitive SKU checks and deferred-invariant rollback behavior. The current `inspect_existing` compares complete fields and rejects drift; add an explicit narrowly scoped enrichment path rather than turning it into blanket upsert.

Preflight the entire approved manifest for duplicate keys, duplicate proposed UUIDs, SKU ownership, category/brand applicability and cross-row conflicts before any batch writes. Bound transaction size by measured workload; deterministic Product order and complete group atomicity matter more than a guessed batch number. Use batched lookups where warranted; do not promise a throughput rate without benchmark evidence.

Retry by replaying reviewed items after state reconciliation. No blanket automatic retry policy for deadlocks, serialization failures or integrity conflicts. This preserves the constitution's concurrency rule while supporting an operator restarting a failed batch. Changed raw data, registry versions or target metadata require a new reviewed plan.

## 10. Verification and remaining decisions

Before any real backfill, require tests for:

- Exact-match legacy mapping, unknown/ambiguous matches, renamed source URLs, extra target variants and no name-only fallback.
- No-op replay, batch rollback, uncertain commit reconciliation and concurrent metadata edits without lost updates.
- Unchanged Product/Variant/category/image identities and unchanged name/status/SKU/price/options fields.
- Brand alias ambiguity, null brands, stable identity after display-name correction.
- JSON document shape/version/type/unit validation, false versus missing, equal/unequal aliases, physical versus virtual RAM, installed versus supported capacity, and ranges.
- Existing CatalogContractIT exact response fields, default listing ordering/pages/totals, hidden-status exclusion, signed-image publication race and bounded-query tests. Existing test source was inspected; these tests were not run in this design turn.
- Importer fresh/upgrade Flyway validation, unchanged old migration checksums, malformed manifest rejection and no automatic publication.

Decisions needing review rather than invention:

1. Accept dedicated Brand/source links versus earlier controlled-text/external-only alternatives. Recommended: accept the small tables for stable identities and repeatable enrichment.
2. Approve the initial category and attribute allowlist. Recommended: laptops/monitors first because explicit TGDD cohorts have strong storage/display/CPU coverage; expand after category mapping.
3. Approve capacity conversion conventions before cross-unit range filters. Recommended: preserve declared units first; no implicit conversions for ambiguous GB/MB conventions.
4. Approve uncertain brand aliases and inclusion of used/services/buyback goods. Recommended: keep unresolved until reviewed, without blocking independently valid unrelated records.
5. Confirm actual target and provenance artifacts before enrichment. Recommended: read-only live reconciliation first; seed evidence alone is insufficient.

Completing design does not grant permission for database changes, import execution or Search & Filter specification generation. No such operations were performed.
