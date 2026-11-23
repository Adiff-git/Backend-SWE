# Admin Catalog Management — implemented API quickstart

Feature013, UC-031–UC-036, branch `features/AdminCatalogManagement`. This implementation has passed the unrestricted verification recorded in [implementation-evidence.md](implementation-evidence.md). The [API contract](contracts/admin-api.md) remains the authoritative list of30 operations; its original planning wording is historical. This guide is a client/operator handoff, **not deployment authorization**.

## Runtime and environment boundary

Use Java25 and the existing Maven wrapper. Integration tests use isolated PostgreSQL17/Testcontainers, LocalStack and the genuine local AI HTTP runtime. Reuse [local verification procedures](local-verification-guide.md); do not recreate the frozen named reference/demo/copy environments or point tests at a developer database. The designated synthetic demo is still V27/read-only. Starting the current backend against it can invoke Flyway; **do not start it before separately approved G3 rollout**. Follow [rollout-readiness.md](rollout-readiness.md).

From `demo/`, the completed unrestricted command was:

```bash
UV_NO_SYNC=true bash ./mvnw clean verify
```

It passed957 Surefire tests and862 Failsafe integration tests,0 failures/errors/skips. `UV_NO_SYNC=true` preserves the existing compatible AI environment; it does not skip the genuine1500-candidate RecommendationHttpIT. See [final verification](final-verification.md) for exact current reports and artifact hashes. Re-run tests when changes justify it; the commands below are request examples for an authorized compatible environment, not requests executed during documentation finalization.

## Authentication and revision protocol

Obtain a current eligible Admin bearer through the existing login flow; server-side `ROLE_ADMIN` and current session/account state apply to reads and writes. Keep tokens/connection values in private runtime configuration; never paste them into documents or logs. All administrative responses use `Cache-Control: no-store`.

Treat revision values as **decimal strings**, not JavaScript numbers. Obtain `version` and `taxonomyVersion` from Admin Product detail. Every existing-Product mutation, including variant, price, specifications and gallery, uses the **Product's** version plus taxonomy version. Category writes use the category's version and taxonomy version; category creation needs taxonomy version. Brand updates/deletion use its own version; selecting a non-null Brand additionally requires its observed `expectedBrandVersion`.

On409 `CATALOG_STALE_VERSION`, reload the relevant Product/category/Brand and taxonomy, review the newer state, then explicitly resubmit. Do not silently merge, automatically retry, or use a child-specific revision. Current-token exact no-ops/replays do not consume revisions; stale no-ops/replays still fail. DELETE revisions are single query parameters.

## Product preparation and lifecycle

Requests below are relative to `/api/admin/catalog`, use `Content-Type: application/json`, canonical lowercase UUIDs and current tokens. Placeholder IDs/tokens must come from that authorized environment.

```http
POST /products
{"name":"Prepared laptop","categoryId":"<category-id>","description":""}
```

Expect201 and a variantless `DRAFT` with empty children. Omitted optional fields preserve existing values on PATCH; `description:null` and `brandId:null` explicitly clear them. Empty description is a supplied value. Non-null Brand selection requires its observed version; clearing Brand does not.

```http
PATCH /products/<product-id>
{"expectedVersion":"<product-version>","expectedTaxonomyVersion":"<taxonomy-version>","description":null}

POST /products/<product-id>/variants
{"expectedVersion":"<product-version>","expectedTaxonomyVersion":"<taxonomy-version>","sku":"DEMO-LAPTOP-BLACK","sellingPrice":{"amount":1000,"currency":"VND"},"options":{"color":"black"},"technicalSpecifications":{"schema_version":1,"values":{}},"enabled":true}

POST /products/<product-id>/publish
{"expectedVersion":"<product-version>","expectedTaxonomyVersion":"<taxonomy-version>"}
```

Reload/use each returned revision before the next request. Publish requires valid basic fields, applicable typed facts, at least one Enabled sellable variant and a valid active gallery with exactly one Primary Image. **Positive inventory is not a publication requirement**. Stock0 can publish but does not make checkout stock available. Existing stock operations remain outside Feature1.

The explicit lifecycle commands are `/publish`, `/unpublish`, `/archive`, `/restore`. Unpublish withdraws a Published Product; Archive is logical; Restore moves Archived→Draft and preserves history, never republishes or changes stock. Archived preparation requires Restore, except the approved explicit variant enable operation. Re-enable never changes the parent's lifecycle. Repeated commands still validate observed revisions.

Enabled variants on Published Products require positive whole-VND prices; last-Enabled disable and any edit invalidating Published readiness are rejected atomically. Basic-valid zero-price preparation does not qualify for publication. Prices are lossless integer amounts in the existing money object; no fractional/wrong-currency/string coercion or invented upper price bound. Global SKU ownership includes Disabled variants and Archived Products.

## Specifications and gallery

```http
PUT /products/<product-id>/variants/<variant-id>/specifications
{"expectedVersion":"<product-version>","expectedTaxonomyVersion":"<taxonomy-version>","technicalSpecifications":{"schema_version":1,"values":{"physical_ram":{"value":16,"unit":"GB"}}}}
```

This fully replaces only that variant's document, using the same validation/mutation path as variant PATCH. `values:{}` clears facts; omitted PATCH fields preserve them. Applicable registered types/units are validated; optional zero/false values are not erased. Generic facts do not automatically become searchable. No universal mandatory-fact policy is added.

Reuse existing owned presign→raw upload→completion services before attachment; do not invent another storage system.

```http
POST /products/<product-id>/images
{"expectedVersion":"<product-version>","expectedTaxonomyVersion":"<taxonomy-version>","imageId":"<owned-completed-upload-id>","altText":"Front view","isPrimary":true,"displayOrder":0}

PUT /products/<product-id>/images/primary
{"expectedVersion":"<product-version>","expectedTaxonomyVersion":"<taxonomy-version>","imageId":"<active-gallery-image-id>"}

PUT /products/<product-id>/images/order
{"expectedVersion":"<product-version>","expectedTaxonomyVersion":"<taxonomy-version>","imageIds":["<all-active-ids-in-desired-order>"]}

DELETE /products/<product-id>/images/<image-id>?expectedVersion=<product-version>&expectedTaxonomyVersion=<taxonomy-version>&replacementImageId=<replacement-id>
```

Gallery ordering is an exact active permutation; at most10 active images. Removing a Published primary requires a valid replacement; removing its last image is rejected. Detachment is logical and preserves shared storage/source references. Successful storage preparation is not represented as physically rolled back after a later DB failure; retained private objects require controlled reconciliation, never guessed deletion.

**Legacy-client upgrade:** existing `POST /api/products/{id}/images` now requires the same Product/taxonomy revisions as the Admin attachment route. Keep the existing image fields, add both revision strings, and consume returned revisions/replay information. Missing revisions400; stale replay409; current identical replay200; new/reactivated association201. There is no unversioned fallback. Upload ownership/current account checks remain.

## Categories, Brands and public consumers

Category root/sibling names ignore case and outer D05 whitespace; equal names under different parents are valid. Brand names use equivalent global equality; Brand code is stable. Reparenting preserves direct assignments, rejects cycles/destination collisions and validates affected Published applicability. Referenced children/Products, including Archived references, prevent unsafe deletion. Null category parent means root; omitted PATCH parent preserves it. Duplicate family roots may disable an existing search binding; they do not authorize merging categories or rewriting facts. Inspect `affectedBindings` and current taxonomy version.

Cart rows/quantity/selection survive withdrawal and disabling; eligibility is recalculated from current state, prices and inventory. Checkout revalidates independently and preserves receipt-first idempotency/reservations. Past order/receipt snapshots remain immutable. Public catalog/search/recommendations exclude ineligible states/variants; removal of gallery associations is rechecked after signing preparation and locking.

## Errors and trusted external writers

Malformed/unknown/repeated fields, missing tokens and invalid canonical IDs400; unsupported content type415; missing/foreign owned resources404; stale/dependency/uniqueness conflicts409; semantic/readiness failures422; dependency/signing/revision maintenance503. Unknown late transaction failures remain safe500, never success. Guest/invalid/currently invalidated bearer401; eligible Customer403. Preserve the existing structured error contract and reload guidance; do not expose storage keys or internals.

The historical `tools/catalog-import/output/` artifacts are not an import execution capability or dataset authorization. **UC-038 is not implemented here.** Before rollout, inventory/quiesce external trusted SQL/import/media writers and upgrade them to the same revision, lock, validation and storage-reference protocol or the approved Admin APIs. Do not let an old writer bypass Product aggregate protection or mix old/new schema assumptions. Hand off required changes to the owning importer project; this feature changes no other repository.

C01 remains OPEN/UNASSIGNED and production release BLOCKED. Full Maven success, G1/G2 and completed documentation do not authorize demo rollout or prove production CORS compliance.
