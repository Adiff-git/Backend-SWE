# Data Model and Migration Design

**Status**: proposed, not applied. Requirements: FR-003–036, BR-01–20; decisions D01–D06 in [research](research.md). All existing UUIDs, source links, media references, Long commerce IDs and historical snapshots survive.

## Entities and ownership

| Existing entity/table | Proposed additive fields / retained structure | Validation and owner |
| --- | --- | --- |
| Product / products | status adds UNPUBLISHED; version BIGINT NOT NULL default0, CHECK>=0; last_restored_at TIMESTAMPTZ NULL; existing category/name, optional brand/description | Catalog Product aggregate; explicit transitions, required name/category, publication validator only for Published. No product-name uniqueness. |
| ProductVariant / product_variants | enabled BOOLEAN NOT NULL defaulttrue; existing mandatory SKU, NUMERIC selling_price, options JSONB, specifications JSONB | Product revision covers all mutations; SKU all-row lower(sku) unique, finite whole price, option uniqueness across Enabled/Disabled, version1 fact integrity. No nullable preparation fields or physical delete. |
| Category / categories | version BIGINT NOT NULL default0; existing parent/category identity | Category revision plus taxonomy revision; sibling/root normalized name indexes; FK, no cycles, reference-safe delete. |
| Brand / brands | version BIGINT NOT NULL default0; existing immutable unique code/name | Brand revision; normalized global name uniqueness and existing code grammar/identity remain. |
| ProductImage / product_images | active BOOLEAN NOT NULL defaulttrue; existing source image nullable and metadata retained | Product revision; active-gallery order/primary/count; inactive row retained for legacy FK and reconciliation. No direct entity delete via API. |
| New CatalogTaxonomyState / catalog_taxonomy_state | id SMALLINT PK restricted to1; version BIGINT NOT NULL default0 CHECK>=0; exactly one seeded row | Catalog-owned hierarchy gate/revision. Shared read lock for product admin changes, exclusive for category changes. No separate catalog database or distributed service. |

No independent child revisions are needed for variants/images because commands identify the parent aggregate version. JPA entities use safe Lombok/lazy relations and read-only version setters; the Catalog repository owns conditional version SQL. Do not add @Version alongside native revision management. The new hierarchy row's absence is an integrity failure; never silently recreate/reset a revision during requests.

### Retained relationships

Products keep required Category and optional Brand. Variants keep Product, source links, cart/wishlist/order references and Inventory identity. ProductImage rows keep Product and optional completed source upload; legacy product_image_uploads keeps its composite live FK. Inactive gallery rows remain addressable internally but are absent from current galleries, signing and count/primary predicates. No cascade delete introduced. Newly created variants use V25's existing zero-balance trigger; no Catalog-side stock insert/update.

## Lifecycle transition matrix

| Command | DRAFT | PUBLISHED | UNPUBLISHED | ARCHIVED |
| --- | --- | --- | --- | --- |
| publish | PUBLISHED after readiness | no-op after current readiness/version check | PUBLISHED after readiness |409 restore first |
| unpublish |409 never Published/invalid source | UNPUBLISHED | no-op after version check |409 invalid source |
| archive | ARCHIVED | ARCHIVED | ARCHIVED | no-op after version check |
| restore | no-op after version check; already desired Draft |409 invalid source |409 invalid source | DRAFT |
| information/variant/gallery edit | basic integrity | readiness must remain valid | basic integrity |409 restore first, except explicit re-enable below |
| explicit variant enable | validate basic, parent unchanged | validate whole proposed Published aggregate | validate basic, parent unchanged | permitted by BR-12/AC-37; validate basic, parent remains Archived |

Create always DRAFT, ignores no submitted status: status on create/edit is an unsupported field and400. State commands are explicit. Repeated target-state no-op uses current versions and leaves versions/history unchanged; it is not a stale request bypass. Publish no-op still checks readiness. Product.last_restored_at records the latest successful Restore time (Instant/UTC), nullable for never-restored records. Admin detail exposes lastRestoredAt to distinguish initial and restored Drafts under FR-002; preserve it through subsequent transitions. This is a minimal provenance field, not a publication-history ledger. No restore ever goes directly Published. Archive is POST business operation; no destructive DELETE Product/Variant endpoint.

## Validation tiers

1. **Request**: canonical UUIDs, strict JSON members/duplicate-member rejection, actual JSON types, nonnegative integral revision tokens, mandatory supplied fields; presence-aware optional edits. No price rounding or Java long narrowing.
2. **Basic aggregate**: existing nonblank names/references, finite whole NUMERIC price (including current permitted zero/negative preparation values), SKU, option map/uniqueness and technical shape on ALL variants. Draft may have zero variants; Archived-from-Draft may too. Exact VND currency; price currency is a request constant and not a new column.
3. **Publication**: name/category valid, ≥1 Enabled and all Enabled valid for sale with price>0, current SKU uniqueness, active valid image count1..10 and exactly one primary; applicable supplied registered facts match type/unit. No universal required fact, brand or description, and no stock query.
4. **Purchase**: current Product PUBLISHED, variant enabled and finite whole positive price; then existing owned Cart/Checkout quantity and Inventory availability/reservation rules. Defensive price guards exclude incompatible legacy zero prices rather than accepting them as sale-ready. This is an explicit approved positive-price extension, not a historical snapshot rewrite.

Basic validity and publication eligibility are separate methods, not independent competing implementations. Validate proposed state before writes or within the transaction before flush; final deferred guards inspect committed proposed relations. Validate every existing variant's basic integrity; only Enabled need publication-specific sale validity.

## Proposed Flyway changes

Only design is supplied here. **No migration file has been created.** V28 is currently available; reserve final names/numbers only after rechecking the implementation branch.

### Migration A — additive catalog administration foundation (candidate V28)

In one normal transactional migration on PostgreSQL, after preflight and writer quiescence:

1. Add version fields/checks, nullable last_restored_at (no guessed legacy backfill), taxonomy gate and default enabled/active columns; preserve existing records as enabled/active and all previous lifecycle values.
2. Extend ck_products_status to include UNPUBLISHED without changing any stored status.
3. Define immutable name-key normalization under the confirmed database collation using the exact outer-whitespace character set in research D05. Add named unique expression indexes: `uk_brands_name_key`, `uk_categories_parent_name_key` for non-root `(parent_category_id,name_key)`, `uk_categories_root_name_key` for root `name_key`. Add nonempty normalized-name guards. Do not rewrite existing display names to build keys; future accepted edits store trimmed display text. Keep uk_brands_code and the existing lower(sku) unique index unchanged.
4. Change gallery position and primary unique indexes to active membership predicates; preserve object-key and product/source uniqueness across retained rows. Add active/primary integrity so inactive rows cannot remain primary. All existing rows active means this step itself causes no removals or reorder.
5. Replace V3 invariant function definitions in the NEW migration. Keep all-variant option rules; zero children allowed for non-Published states, Published needs ≥1 Enabled with every Enabled finite whole positive price. Gallery final guard requires active count≤10, nonempty exactly-one-primary and Published count≥1. Run the readiness guard on Product status updates as well as image/variant mutations; a Publish with zero images must not escape because no image row was touched. Both product/variant and gallery guards lock parent Products in UUID order and use fresh child snapshots. Keep basic typed JSON shape constraints; detailed applicable specification validation is centralized in application code, not a second SQL fact-rule engine.
6. Add final hierarchy cycle guard using recursive visited paths, explicit gate locking and named constraint errors; retain all FKs without destructive cascades. The service always takes the gate first. DB backstop also protects direct invalid DML; concurrent uncoordinated writers may abort, never commit cycles. They are unsupported while admin is active unless they follow the documented gate/revision protocol.
7. Explicitly validate all existing rows against new name/readiness/topology constraints before allowing migration commit; function replacement alone does not retroactively fire row triggers. Migration must fail with a safe diagnostic identifier on legacy incompatibility, not silently normalize names, unpublish, reprice, delete or generate images.

Index creation is normal transactional CREATE INDEX, not CONCURRENTLY inside Flyway's transaction. Planned maintenance quiescence and rehearsal determines lock/duration feasibility; no new migration framework, out-of-transaction schema half-state or automatic repair. Add indexes for necessary FK/affected-descendant queries only where existing indexes are missing and query plans justify them; do not presume a performance fix.

### Application/migration ordering

Old migrations V1–V27 remain byte-identical. Test fresh V1→new and restored V27→new. Stop old writers/readers before schema and compatible application startup; old binaries cannot safely deserialize UNPUBLISHED or respect enabled/active. Do not add default Published state or insert fake children to preserve old binaries. Seed/bootstrap fixtures may require valid Published images/positive Enabled prices; update prepared test data and documented bootstrap compatibility, never weaken constraints to keep legacy invalid fixtures.

Current importer source reconstruction and product-data import are UC-038/outside this feature. Inventory initialization trigger is retained and existing lineage rows are untouched. Deployment owner must verify external trusted import clients do not bypass gate/version/validity protocol before permitting them concurrently.

## Data preflight and compatibility gate

[contracts/data-preflight.sql](contracts/data-preflight.sql) is a read-only baseline query script, not a migration. It must run with a read-only account/transaction on the exact proposed target; retain target identity/commit/migration checksums and timestamp in a controlled report, never secrets. Review names with product/dependency identities rather than dumping arbitrary payloads.

Required outcomes:

- Unique normalized Brand names and sibling/root Category names; all collision identities and dependencies reviewed. Duplicate names under different parents and Product names are allowed.
- Existing SKU/all-row option constraints valid; no blank/null/nonfinite/fractional basic data; positive prices for ALL currently Published variants because they default Enabled.
- Supported statuses; Product/Variant relationships including empty aggregates; no category cycles; valid references and exact V27 expected history/checksums.
- Published galleries have1..10 valid metadata rows/exactly-one-primary; legacy source_image_id null is allowed, not a defect by itself. Record legacy upload pointers and private object keys only in controlled operator artifacts; do not publish keys in API/report.
- Typed facts pass the existing Java validator and new applicable-type/unit checks on a restored target copy. SQL envelope checks alone cannot prove every typed value valid. Run a future read-only Catalog preflight runner or equivalent service invocation on the copy; this runner has no write/repair flag in this feature.
- Storage validation on that copy verifies bounded metadata/content for referenced product objects using existing reader/content validator when execution credentials are authorized. SQL cannot prove object existence or content hash; already valid legacy images need not have shared source IDs.
- Inventory balance presence, reserved/allocation attribution, source links and order/receipt integrity are stable. Capture row counts and deterministic hashes of order_item snapshot fields, immutable receipt JSON and inventory reservation identity/state before/after upgrade rehearsal, without publishing customer address/receipt payloads.

**Observed result**: actual target data/schema/migration history NOT VERIFIED; no successful DB connection, no SQL executed. Local prerequisites are unavailable. All new uniqueness/readiness rollout checks therefore remain pending. Produce collision/readiness reports and obtain specific remediation authorization if failures arise. No silent merge, rename, detach, bulk Unpublish, price correction, stock provision or deletion.

## Rollback / recovery

A failed transactional migration rolls back DDL/data as one transaction; inspect Flyway and actual schema before retry. Never edit an applied migration or run repair to conceal an unreviewed checksum drift. Correct implementation defects with a new forward migration and compatible binary. Preserve a verified backup plus restore rehearsal before execution. After new states/variantless drafts/detached galleries exist, dropping columns or running the old binary is unsafe. Emergency rollback requires writer stop and an authorized verified backup restore or explicitly reviewed data-preserving compatibility conversion; no automatic destructive downgrade is designed. Private copied objects may remain across DB rollback and must retain existing reference-safe reconciliation.
