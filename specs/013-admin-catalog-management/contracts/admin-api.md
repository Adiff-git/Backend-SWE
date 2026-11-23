# Admin Catalog REST Contract

**Proposed, not implemented**. Base `/api/admin/catalog`. Every operation requires a current eligible **JWT** with `ROLE_ADMIN`; generic authenticated or mock username/password Admin authentication is insufficient. Current account/session revocation and bearer validation remain. No actor/user/role input is accepted. JSON content type, `Cache-Control: no-store` for administrative responses, canonical lowercase UUIDs. New services use Lombok request/response classes, Jakarta validation and presence-aware readers consistent with the constitution.

## Common shapes and request rules

Revision tokens are JSON strings representing nonnegative BIGINTs: `"0"`, `"17"`; never lossy floating numbers. `expectedVersion` means Product version for ALL nested endpoints, and own entity version for Category/Brand. `expectedTaxonomyVersion` is additionally required for existing-Product and Category mutations. DELETE supplies tokens as single query parameters because it has no body. Optional brand selection carries expectedBrandVersion. No If-Match/ETag, new CORS header or global idempotency key.

Reject unknown/repeated JSON members, unknown query keys, repeated scalar query keys, null required revisions and empty PATCH (revision fields alone do not constitute a business edit) with400. Repeated status filters are the explicitly allowed exception; duplicate values deduplicate. Canonical IDs, exact integer money amounts, strict true/false and finite numerics; no string-to-number coercion for prices. BigInteger-backed money is parsed losslessly; use existing `{amount: <integer>, currency:"VND"}` shape. Fractional amounts/wrong currency are422; missing/null required request fields400. No arbitrary upper money bound, rounding/conversion or per-variant currency column.

Omitted editable fields preserve. Null clears **only** optional description/brand, or category parent to root. Required name/category/SKU/price/options/enabled cannot be null/blank. Product collection changes are not generic edit fields. Description empty text is a supplied value, null clears; brand null clears and does not require expectedBrandVersion. A changed nonnull Brand ID needs current expectedBrandVersion; an orphan version with omitted/null Brand is400. Specs omission preserves; `{"schema_version":1,"values":{}}` clears only that variant's facts. Null facts or null values are invalid; boolean false/numeric0 are data. Unknown but canonical generic fact keys stay supported structural facts, not newly searchable keys. No caller-submitted storage key/URL is accepted.

### Response DTO definitions

- `AdminProductPage`: `{items:[AdminProductRow],page,size,totalItems,totalPages,taxonomyVersion}`. Row `{id,name,status,version,category:{id,name,version},brand:null|{id,code,name,version},variantCount,enabledVariantCount}`. Counts do not filter historical variants out of admin visibility.
- `AdminProductDetail`: row fields plus `{description,lastRestoredAt:null|UTC-Instant,variants:[AdminVariant],images:[AdminImage],taxonomyVersion,publicationReadiness:{eligible,violations}}`. `violations` ordered deterministically by path/code; readiness can be false on Draft without preventing GET/save. No inventory quantity is required to calculate readiness. Admin gallery includes active associations only; detached records remain internal retained history, not current images.
- `AdminVariant`: `{id,productId,productVersion,sku,enabled,options,sellingPrice,technicalSpecifications}`; list wrapper `{items,productVersion,taxonomyVersion}`. Finite whole preparation prices may be nonpositive when parent context permits. No stock update field.
- `AdminImage`: `{id,productId,url,altText,width,height,displayOrder,isPrimary}`. Signed url is temporary; never persist/accept it as identity. Gallery wrapper `{items,productVersion,taxonomyVersion}`. For new Admin detail/gallery responses with active images, failure to prepare/sign required gallery URLs returns503 with no key disclosure; an empty gallery needs no signer. Signing does not HEAD every object or prove physical content presence. Existing customer/order optional-image fallbacks remain unchanged. Exercise these distinct contracts in storage tests.
- `AdminCategory`: `{id,name,parentCategoryId:null|UUID,version}`. Page adds taxonomyVersion and normal page fields. Mutation envelope `{category,taxonomyVersion,affectedBindings:[{family,state,reason}]}`; binding states ACTIVE/DISABLED and safe reasons MISSING/AMBIGUOUS/CHANGED_ANCESTRY; no private object data. Create/update use this envelope.
- `AdminBrand`: `{id,code,name,version}`; page uses normal page fields.
- `AdminProductMutation`: current AdminProductDetail for product edits/lifecycle. Variant write envelope `{variant,productVersion,taxonomyVersion}`. Gallery mutation wrapper uses gallery shape. Attachment additionally `{image,replay,productVersion,taxonomyVersion}`. Successful creating POST sets Location to the corresponding existing GET detail/list resource; it never claims a nonexistent GET route.

No public DTO gains Admin status/version fields. Public ProductDetail excludes Disabled variants; public images exclude inactive associations; all four state controls remain admin-only.

## Endpoint inventory

All listed endpoints are **new proposals**, except the explicitly identified legacy alias and existing upload routes. Parent ownership is checked for nested IDs; a variant/image under a different Product returns404. Missing target Product is404 even if the referenced nested ID exists elsewhere.

| Method | Relative path | Request / purpose | Success | UC |
| --- | --- | --- | --- | --- |
| GET | `/products` | Admin query below; all states by default |200 AdminProductPage |031 |
| GET | `/products/{productId}` | Full admin detail, including zero-variant Draft and Disabled |200 AdminProductDetail |031 |
| POST | `/products` | `{name,categoryId,brandId?,expectedBrandVersion?,description?}`; only name/category required |201 detail, DRAFT |031 |
| PATCH | `/products/{productId}` | `{expectedVersion,expectedTaxonomyVersion,name?,categoryId?,brandId?,expectedBrandVersion?,description?}` |200 detail |031 |
| POST | `/products/{productId}/publish` | Product revision pair; validated explicit Publish/Republish |200 detail |031 |
| POST | `/products/{productId}/unpublish` | Product revision pair |200 detail |031 |
| POST | `/products/{productId}/archive` | Product revision pair; logical removal |200 detail |031 |
| POST | `/products/{productId}/restore` | Product revision pair; Archived→Draft |200 detail |031 |
| GET | `/categories` | Page/name/parent query below |200 category page |032 |
| GET | `/categories/{categoryId}` | Current category plus taxonomyVersion |200 `{category,taxonomyVersion}` |032 |
| POST | `/categories` | `{expectedTaxonomyVersion,name,parentCategoryId?}`; omitted/null parent=root |201 category envelope |032 |
| PATCH | `/categories/{categoryId}` | `{expectedVersion,expectedTaxonomyVersion,name?,parentCategoryId?}` |200 category envelope |032 |
| DELETE | `/categories/{categoryId}` | Query expectedVersion/expectedTaxonomyVersion; reject all product/child dependencies |204 |032 |
| GET | `/brands` | Page/name query below |200 brand page |033 |
| GET | `/brands/{brandId}` | Current brand/version |200 AdminBrand |033 |
| POST | `/brands` | `{code,name}`; existing code grammar/uniqueness |201 AdminBrand |033 |
| PATCH | `/brands/{brandId}` | `{expectedVersion,name}`; code not editable |200 AdminBrand |033 |
| DELETE | `/brands/{brandId}` | Query expectedVersion; reject any product reference |204 |033 |
| GET | `/products/{productId}/variants` | All owned variants, deterministic ID order |200 variant wrapper |034 |
| POST | `/products/{productId}/variants` | Revision pair + `{sku,sellingPrice,options?,technicalSpecifications?,enabled?}`; defaults options{}, empty version1 facts, enabledtrue |201 variant envelope |034 |
| PATCH | `/products/{productId}/variants/{variantId}` | Revision pair + `{sku?,sellingPrice?,options?,technicalSpecifications?}` |200 variant envelope |034/036 |
| POST | `/products/{productId}/variants/{variantId}/enable` | Revision pair; BR-12 exception allows Archived parent without restoring |200 variant envelope |034 |
| POST | `/products/{productId}/variants/{variantId}/disable` | Revision pair; preserve references, no stock changes |200 variant envelope |034 |
| GET | `/products/{productId}/variants/{variantId}/specifications` | Current version1 facts |200 `{technicalSpecifications,productVersion,taxonomyVersion}` |036 |
| PUT | `/products/{productId}/variants/{variantId}/specifications` | Revision pair + `{technicalSpecifications:<complete document>}` |200 variant envelope |036 |
| GET | `/products/{productId}/images` | Admin current gallery including nonpublic product |200 gallery wrapper |035 |
| POST | `/products/{productId}/images` | Revision pair + `{imageId,altText,isPrimary?,displayOrder?}`; imageId is owned completed source upload |201 new/reactivated;200 current-version exact replay |035 |
| PUT | `/products/{productId}/images/primary` | Revision pair + `{imageId:<active association id>}` |200 gallery wrapper |035 |
| PUT | `/products/{productId}/images/order` | Revision pair + `{imageIds:[<exact active permutation>]}` |200 gallery wrapper |035 |
| DELETE | `/products/{productId}/images/{imageId}` | Query revision pair and optional replacementImageId; association ID, not source upload ID |204 logical detach |035 |

`Revision pair` means body expectedVersion and expectedTaxonomyVersion. Creation of a second variant does not retroactively guess option values: if an existing empty option map makes multi-variant rules fail, return422; Admin first updates the first variant's options with a current version. Published mutations must preserve full readiness, including creating an Enabled variant; creation as disabled is available for basic-valid preparation. No physical variant DELETE, no stock endpoints.

`PUT specifications` completely replaces the one addressed document; `PATCH variant` replaces each supplied options/spec document as a whole, not recursive key merging. Both delegate to one variant service/validator. Selling price updates use PATCH variant, not a second price-calculation implementation. Product scalar changes cannot mutate gallery/variants.

### Administrative list/filter contract

- Products: page default0, size20/max100; optional repeated `status` in DRAFT/PUBLISHED/UNPUBLISHED/ARCHIVED, optional single categoryId, includeDescendants boolean defaultfalse (valid only with categoryId), brandId, `q` nonblank case-insensitive literal substring over Product name or any owned SKU, and sort `id,asc|desc` / `name,asc|desc`, default `id,asc`. Name sorting uses display database collation then id same direction. Escape LIKE metacharacters; bind all values. Filter membership/counts include Disabled variants and all requested lifecycle states. Unknown category/brand gives empty page. Search relevance/customer fuzzy filtering is not silently reused for Admin search.
- Categories: same page bounds, optional `parentCategoryId=<UUID>|root` (omitted=all), q over name, sort id/name; all direct parent IDs in rows so a client can reconstruct hierarchy from bounded pages. No recursive dump or hidden page cap. Product assignments do not change during reparent.
- Brands: same bounds, q over code/name, sort id/name/code; stable ID tie. Exact repeated scalar/unknown keys400. Optional q is an escaped literal, never SQL or a new fuzzy vocabulary.
- Empty/out-of-range page200 with accurate statement-snapshot totals. Use Pageable and custom DTO, never PageImpl as the contract. Same selection relation supplies count/results. Concurrent changes may alter later pages; no frozen cross-request snapshot promised.

## Existing upload and attachment integration

`POST /api/image-uploads`, `POST /api/image-uploads/{id}/complete`, `GET /api/image-uploads/{id}/download-url` retain their existing current-owner behavior; signed raw storage PUT remains outside backend Admin routes. No product-specific upload architecture or new upload permission. Attachment additionally requires ROLE_ADMIN and the source owner's identity from JWT.

Existing **POST `/api/products/{id}/images`** remains available behind the existing Admin JWT guard, delegates to the exact same command, and accepts the existing four fields plus required expectedVersion/expectedTaxonomyVersion. This is an explicitly required administrative contract change under BR-15: missing revisions400, stale409 even for replay. Add current revisions/replay in the attachment response while retaining its existing image metadata fields; adapt old controller/API tests/docs. No unversioned fallback. Old clients must obtain revisions from Admin detail; public customer contracts remain unchanged.

## Errors and safe examples

Use nonoverlapping advice applicability: old bare catalog advice applies only to ProductCatalogController by assignableTypes; new code-preserving admin advice applies to the Admin controllers and legacy ProductImageController, never a parent-package selector that also matches subpackages. New Admin errors use RFC9457 shared ApiError shape (`type,title,status,detail,instance` plus established timestamp/message/path/fieldErrors) and safe `code`; optional ordered `violations:[{path,code,message}]`, resourceId, expectedVersion/currentVersion or expectedTaxonomyVersion/currentTaxonomyVersion. Never SQL/constraint text, raw object keys, tokens or customer data. Constraint identifiers are translated to stable external codes.

| Status | Code / condition | Caller action |
| --- | --- | --- |
|400 | CATALOG_INVALID_REQUEST; malformed/unknown fields, invalid/missing revision, null required field, invalid UUID |Correct request |
|401 /403 | Existing authentication/denial format |Authenticate / obtain already-approved Admin authority; no new RBAC |
|404 | CATALOG_RESOURCE_NOT_FOUND; target/nested ownership/reference absent |Reload valid reference |
|409 | CATALOG_STALE_VERSION; Product/Category/Brand/taxonomy expected revision mismatch |Reload current detail/tokens, reapply intent explicitly |
|409 | CATALOG_INVALID_TRANSITION / CATALOG_DEPENDENCY_CONFLICT / CATALOG_NAME_CONFLICT / CATALOG_SKU_CONFLICT / CATALOG_OPTION_CONFLICT / CATALOG_GALLERY_CONFLICT |Resolve named state/reference/identity condition; never auto-merge |
|409 | CATALOG_CONCURRENT_CONFLICT; recognized lock timeout/deadlock |Reload; no blind automatic retry |
|415 | Unsupported request media type |Use application/json |
|422 | CATALOG_PUBLICATION_INVALID / CATALOG_VALIDATION_FAILED; semantic price/fact/options/readiness issues |Fix identified fields; explicitly Unpublish first if needed |
|503 | Existing storage/dependency unavailability or safe revision-exhaustion maintenance error |Resolve dependency; no success reported |
|500 | Safe unexpected error |No internal detail disclosed; investigate |

Example conflict: `{"type":"about:blank","title":"Conflict","status":409,"detail":"Catalog changed; reload before retrying","code":"CATALOG_STALE_VERSION","expectedVersion":"7","currentVersion":"8"}` (shared ApiError extensions may add established fields).

Example Publish rejection: status422, code CATALOG_PUBLICATION_INVALID, violations `variants:ENABLED_VARIANT_REQUIRED` and `images:PRIMARY_IMAGE_REQUIRED`; state/version stay unchanged. Accumulate safe readiness failures deterministically; a missing Product is404 rather than a fake readiness report. For a valid Published product, setting an Enabled price0 produces422 and preserves its previous price/state/revision. Dependency DELETE409, not204.

Current-version repeated lifecycle target commands return200 with unchanged data/revisions, subject to readiness for Publish; invalid source transitions409. Repeating DELETE on an already detached or missing association returns404 after authorization/current parent-version validation; no generic delete replay guarantee. Full transition matrix: [data model](../data-model.md).
