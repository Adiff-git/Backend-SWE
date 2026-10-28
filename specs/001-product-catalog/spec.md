# Feature Specification: Backend Product Catalog

**Feature Branch**: `main` (no branch hook configured)
**Created**: 2026-10-26
**Status**: Draft — business decisions for this feature resolved
**Input**: Independent backend Product Catalog for home and listing pages and product details, including product images and a limited image-upload workflow.

## Clarifications

### Session 2026-10-27

- Q: Who should be allowed to read product listings and details, and which product statuses should those reads include? → A: Public access; only `PUBLISHED` products are visible.
- Q: Must each catalog product have at least one variant with its selling price on the variant, or may products exist without variants? → A: Every product has at least one variant; each variant owns its selling price.
- Q: Which fields should the Product Catalog return for listing cards and product details, including images? → A: Return customer-facing catalog fields and support multiple product images with one primary image; store image object keys and metadata in the database, store files in S3-compatible object storage, and expose accessible image URLs.
- Q: Should Feature 1 provide backend APIs that upload product images to S3, or only read images that another workflow has already uploaded? → A: Feature 1 includes authorized image-upload APIs using staged presigned upload and completion; product/category administration remains excluded.
- Q: Who may upload product images, and should images on unpublished products be accessible to the public? → A: Any authenticated user may upload to an existing product; images of published products are public, while images of unpublished products remain private. Upload permission was superseded by the later clarification below.

### Session 2026-10-27

- Q: Who should be allowed to prepare and complete product-image uploads? → A: Authenticated catalog administrators only; the user who prepared an upload must complete it.
- Q: Which file types, size and count limits, and image metadata should Feature 1 require for product images? → A: JPEG, PNG, or WebP only; maximum 10 MiB each and 10 completed images per product; require nonblank alt text and record verified type, byte size, dimensions, display order, and primary status. The first image is primary; later images append unless a unique order position or new primary is selected.
- Q: Which expiration and recovery policy should the staged product-image upload use? → A: Presigned upload URL and session expire after 15 minutes; states are PREPARED, VERIFIED, PUBLISHED, EXPIRED, and FAILED. Completion verifies the staged object and initiating administrator, publishes to a separate durable private key, and records metadata only after the durable object exists. Repeated completion returns the same result; abandoned, failed, and orphan objects are cleaned within 24 hours.
- Q: How should the backend give the frontend image URLs while keeping unpublished images private? → A: Keep S3 objects private and generate 5-minute signed read URLs dynamically for images of PUBLISHED products; store only durable object keys and metadata, never image URLs. Previously issued links may remain usable for up to five minutes after unpublishing.
- Q: Which identifier format and SKU uniqueness rule should the new Product Catalog records use? → A: Use UUID identifiers for Category, Product, ProductVariant, ProductImage, and ProductImageUpload in this feature; require nonblank SKU values unique across all variants ignoring case. Object keys are globally unique, image order is unique within a product, and image content is not deduplicated.

### Session 2026-10-27

- Q: Which currency and decimal precision should ProductVariant selling prices use? → A: All ProductVariant selling prices use VND in whole đồng, with zero fractional digits; the catalog response states the VND currency explicitly.
- Q: How should each ProductVariant describe its selectable options in the catalog response? → A: Use a flat map of nonblank option names to nonblank values. A single-variant product may use an empty map; products with multiple variants require distinct nonempty option maps.
- Q: Which product statuses should this catalog allow besides PUBLISHED, and how should a product become public? → A: Allow DRAFT, PUBLISHED, and ARCHIVED. DRAFT and ARCHIVED remain private; product data becomes public only when its status is explicitly set to PUBLISHED by trusted initial data or a separately approved ADMIN workflow. Image upload does not change product status.

### Session 2026-10-27 — publication deadline refinement

- Q: What is the enforceable boundary for the approved 15-minute upload session? → A: The final locked and claim-fenced database-side eligibility check must sample PostgreSQL `clock_timestamp()` strictly before expiry. Publication still requires a successful transaction commit, which may physically occur after expiry. A check at or after expiry cannot publish; a committed result can be replayed after expiry.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Browse products (Priority: P1)

A frontend consumer retrieves a bounded page of products so shoppers can browse the catalog without loading all records.

**Business objective**: Provide predictable product cards at catalog scale.
**Functional requirements**: FR-001 through FR-005, FR-010, FR-012.
**Expected API behavior**: `GET /api/products?page=0&size=20` returns HTTP 200 with `items`, `page`, `size`, `totalItems`, and `totalPages`. Page is zero-based; omitted page/size default to 0/20; size is 1–100. Empty collections and out-of-range pages return `items: []` with accurate totals. Invalid parameters return HTTP 400.
**Independent Test**: Seed multiple products and verify pages, metadata, stable ordering, and empty results without using the detail endpoint.

**Acceptance Scenarios**:
1. **Given** DRAFT, PUBLISHED, and ARCHIVED products, **when** an anonymous visitor requests page 0, **then** at most `size` PUBLISHED summaries and totals counting only PUBLISHED products are returned.
2. **Given** no published products, **when** page 0 is requested, **then** HTTP 200 contains `items: []`, `totalItems: 0`, and `totalPages: 0`.
3. **Given** a page beyond the final page, **when** it is requested, **then** HTTP 200 returns no items and unchanged totals.
4. **Given** a negative page, size outside 1–100, nonnumeric value, or duplicate parameter, **when** a page is requested, **then** HTTP 400 identifies the invalid input.

**Edge cases**: A product without a variant is invalid catalog data. Unpublished products do not appear or contribute to totals.
**Dependencies**: Persistent catalog records with a status value.
**Out-of-scope behavior**: Search, filters, caller-selected sort, inventory, and product edits other than the dedicated image workflow.

---

### User Story 2 - View a product (Priority: P1)

A frontend consumer opens a product by stable identifier to show its name, description, category, and choices.

**Business objective**: Let shoppers understand a product before later shopping workflows.
**Functional requirements**: FR-001, FR-006 through FR-010, FR-012, FR-015.
**Expected API behavior**: `GET /api/products/{id}` returns HTTP 200 with one product detail, including one category summary, associated variants, and ordered product images. A well-formed but unknown or ineligible ID returns 404; malformed ID returns 400.
**Independent Test**: Seed one product, category, and variants; verify the detail and unknown/malformed ID responses without using listing.

**Acceptance Scenarios**:
1. **Given** an eligible product, **when** its ID is requested, **then** its category and only its own variants are returned.
2. **Given** an unknown, DRAFT, or ARCHIVED ID, **when** an anonymous visitor requests detail, **then** HTTP 404 contains no internal database information.
3. **Given** an ID that is not a canonical UUID, **when** detail is requested, **then** HTTP 400 identifies invalid input.

**Edge cases**: A product without a variant is invalid catalog data; unpublished products return 404; unrelated variants never appear.
**Dependencies**: Catalog records with UUID identifiers and required relationships.
**Out-of-scope behavior**: Stock availability, cart actions, and product changes.

---

### User Story 3 - See category and variant information (Priority: P2)

A frontend consumer uses category and variant information embedded in product responses to label products and show choices.

**Business objective**: Provide necessary context without extra catalog requests.
**Functional requirements**: FR-006, FR-008, FR-009, FR-011 through FR-015.
**Expected API behavior**: A product summary contains `category: {id, name}` and a primary image URL when images exist; detail also contains a nonempty `variants: VariantSummary[]` and an ordered `images` array. Each variant has `id`, `sku`, `options` as a flat name-to-value map, and `sellingPrice`. Each image has an accessible URL and an indication of whether it is primary. No separate category endpoint is required for the stated pages.
**Independent Test**: Seed two categories and products with distinct variants and images; retrieve details and verify ownership, category labels, image order, and primary image.

**Acceptance Scenarios**:
1. **Given** a product assigned to a category, **when** it is retrieved, **then** its immediate category is identified consistently.
2. **Given** multiple associated variants, **when** detail is retrieved, **then** each appears once with a whole-đồng VND selling price and variants of another product are absent.
3. **Given** a missing required relationship, **when** persistence is attempted, **then** integrity constraints reject it.
4. **Given** a product with no variants, **when** catalog data is validated for use, **then** it is rejected as invalid; no public response presents it as a valid catalog product.
5. **Given** a product with multiple images, **when** its summary and detail are retrieved, **then** its primary image appears on the summary and all associated images appear once in the documented order on detail.
6. **Given** a product with no images, **when** it is retrieved, **then** its summary has a null primary image URL and its detail has an empty images array.
7. **Given** variants in different products with SKUs differing only by letter case, **when** the second variant is persisted, **then** the catalog-wide uniqueness rule rejects it.
8. **Given** a variant selling price with fractional đồng, **when** catalog data is validated, **then** the value is rejected rather than rounded silently.
9. **Given** a product with one variant, **when** detail is retrieved, **then** that variant may have `options: {}`.
10. **Given** a product with multiple variants, **when** variant data is validated, **then** each variant has a nonempty option map and duplicate maps within that product are rejected.

**Edge cases**: Optional parent categories do not imply hierarchy navigation; variant data must exist before a product is valid for catalog use. A product with images must have exactly one primary image.
**Dependencies**: Catalog data satisfying the variant relationship, image object keys and metadata, and an approved accessible-URL policy.
**Out-of-scope behavior**: Category administration, inventory quantities, and SKU selection rules.

---

### User Story 4 - Add product images (Priority: P2)

A catalog administrator uploads one or more product images to an existing product so public catalog responses can show a primary image and an ordered gallery when that product is published.

**Business objective**: Keep product imagery in object storage while making its validated metadata available through the catalog.
**Functional requirements**: FR-015 through FR-018.
**Expected API behavior**: The backend prepares an upload for an existing product and returns a temporary presigned URL for staging. After the same catalog administrator uploads image bytes to staging, a completion request validates the object and finalizes it; the response identifies the new image. The administrator can mark the new image as primary and supply its display order. Authentication alone does not grant upload access. The image becomes publicly accessible only while its product is published.
**Independent Test**: Prepare an upload, place a valid image in staging, complete it, then retrieve the product and verify its new image URL, order, and primary selection. Test expired, missing, invalid, and unauthorized uploads separately.

**Acceptance Scenarios**:
1. **Given** a catalog administrator and an existing product, **when** a valid image upload is prepared and completed by that administrator, **then** the object is stored in the configured S3-compatible bucket and the database stores its durable key and metadata.
2. **Given** multiple completed images, **when** a new image is selected as primary, **then** exactly one image is primary and, while the product is published, its summary uses that image's accessible URL.
3. **Given** a staged upload that is missing, expired, or fails validation, **when** completion is requested, **then** no public image appears and a non-success error is returned.
4. **Given** an anonymous caller, **when** the caller tries to prepare or complete an upload, **then** HTTP 401 is returned without publishing an image.
5. **Given** an authenticated user without the catalog-administrator role, **when** that user tries to prepare or complete an upload, **then** HTTP 403 is returned without publishing an image.
6. **Given** one catalog administrator prepared an upload, **when** another catalog administrator tries to complete it, **then** HTTP 403 is returned without publishing an image.
7. **Given** an uploaded image belongs to an unpublished product, **when** an anonymous caller attempts to access it, **then** the object remains private and no public catalog URL is returned.
8. **Given** a file outside JPEG/PNG/WebP, larger than 10 MiB, missing alt text, or a product already containing 10 images, **when** upload preparation or completion is attempted, **then** the image is rejected without adding a gallery item.
9. **Given** a product with no images, **when** its first valid image is completed, **then** that image is primary at the first available display position.
10. **Given** an administrator requests an occupied display position, **when** completion is attempted, **then** the request is rejected without changing existing image order.
11. **Given** a prepared upload older than 15 minutes, **when** completion is attempted, **then** it is expired, no image is published, and its staging object is removed within 24 hours.
12. **Given** completion succeeded but the response was lost, **when** the same administrator repeats completion, **then** the existing image is returned without adding a duplicate.
13. **Given** S3 publication succeeds but the database write fails, **when** the operation fails, **then** no public image is returned and the private orphan object is reconciled or removed within 24 hours.
14. **Given** a published product image, **when** a visitor requests the product, **then** the response contains a signed image URL valid for no more than five minutes and a fresh product request can obtain a new URL.
15. **Given** only an image object key or an unpublished product, **when** an anonymous visitor requests the object or the product, **then** no new public image access is granted; an already issued signed URL may remain valid until its five-minute expiry.
16. **Given** an image or upload attempts to reuse an existing object key or an image attempts to reuse an occupied order in the same product, **when** persistence is attempted, **then** the conflicting record is rejected without changing the existing gallery.
17. **Given** an ADMIN completes an image upload for a DRAFT or ARCHIVED product, **when** the product is read through public catalog APIs, **then** the product remains hidden and no new public image URL is issued.
18. **Given** an expired upload or publication claim, **when** scheduled recovery runs alongside a completion request or after a worker crash, **then** it does not impersonate an administrator, publish an image, or disturb a live valid claim; it safely expires or fails abandoned work and cleans unreferenced private objects within 24 hours.
19. **Given** completion began before expiry, **when** its final database-side publication eligibility check occurs at or after expiry, **then** no new image is published; staged and incomplete durable objects remain private and enter the approved cleanup process.
20. **Given** the final database-side publication eligibility check occurs before expiry, **when** the transaction commits successfully after expiry, **then** the new image is published once; if that transaction rolls back, no image is published.

**Edge cases**: A database failure after object upload must not leave an image publicly visible without a committed record; abandoned staging objects expire and are cleaned within 24 hours. Completion retries must not publish duplicates.
**Dependencies**: LocalStack S3 for local development, deployed AWS S3, the constitution's JWT sign-in and catalog-administrator authorization capability (not implemented in this checkout), and a defined storage lifecycle.
**Out-of-scope behavior**: Product/category CRUD, direct image bytes in the catalog database, variant-specific images, and general-purpose media administration.

### Edge Cases

- Empty catalog and empty later page are successful responses, not missing-resource errors.
- Unknown versus malformed identifiers yield 404 versus 400.
- Unpublished products are excluded from listing and return 404 on detail, including for signed-in callers of these catalog endpoints.
- Products with no variants are invalid catalog data and must be rejected before they can appear in a public response.
- A product may have no images; if images exist, exactly one is primary. Images from another product must never appear. Images on unpublished products must not be accessible anonymously.
- Large pages must not produce one related-data request per product or variant.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST read catalog data from persistent storage and return response DTOs rather than persistence entities or inventory quantities.
- **FR-002**: Listing MUST return a bounded collection and `page`, `size`, `totalItems`, and `totalPages`; empty results MUST be HTTP 200 with an empty `items` array.
- **FR-003**: Listing MUST accept zero-based `page` and positive `size`, default omitted values to 0 and 20, cap size at 100, and reject invalid or duplicate values with HTTP 400.
- **FR-004**: An unchanged catalog MUST yield stable page boundaries under a deterministic default order; no caller-controlled ordering is included.
- **FR-005**: A Product MUST have exactly one status from `DRAFT`, `PUBLISHED`, or `ARCHIVED`. Both catalog endpoints MUST allow anonymous reads and return only `PUBLISHED` products. `DRAFT` and `ARCHIVED` products MUST be excluded from list items and totals and MUST return 404 on detail; these endpoints do not provide a privileged preview. A product MUST NOT become `PUBLISHED` implicitly through image upload or catalog reads.
- **FR-006**: Detail MUST retrieve by stable identifier and include the product's immediate category and associated variants; unknown or ineligible IDs MUST return HTTP 404.
- **FR-007**: Category, Product, ProductVariant, ProductImage, and ProductImageUpload records introduced by this feature MUST use UUID identifiers. All catalog identifiers exposed in requests or responses MUST use the canonical hyphenated UUID string format. Malformed identifiers MUST return HTTP 400; a well-formed but unknown product identifier MUST return HTTP 404. This is a feature-specific ID choice and does not set a policy for unrelated domains.
- **FR-008**: Every product MUST have at least one variant, and every variant MUST have an exact selling price in VND whole đồng with zero fractional digits. Selling price belongs only to ProductVariant; the catalog MUST NOT store a competing product-level selling price or per-variant currency. Fractional-đồng values MUST be rejected rather than rounded silently. Catalog data with a variantless product MUST be rejected before public use. This feature does not decide whether zero-priced products are permitted.
- **FR-009**: Catalog responses MUST expose only customer-facing fields: product ID, name, category ID/name, primary image URL or null in summaries; details additionally expose description, variant ID and SKU, options as a flat name-to-value map, selling price as an exact whole-đồng amount with currency `VND`, and an ordered collection of product images with accessible URLs, alt text, dimensions, display order, and a primary indicator. Product status and variant weight MUST NOT be exposed by these endpoints.
- **FR-010**: Successful GETs MUST return 200. Invalid input MUST return 400; missing or unpublished detail MUST return 404. These catalog reads require no sign-in. Errors MUST use the constitution's structured problem format without sensitive internals.
- **FR-011**: Each product MUST belong to exactly one category and have at least one variant; each variant, image, and image upload MUST belong to exactly one product. A product MAY have no images and MUST have no more than ten completed images. Optional parent-category relationships do not require a navigation API.
- **FR-012**: SKU MUST be nonblank and unique across all ProductVariant records without regard to letter case. Durable and staging object keys MUST be globally unique; image display order MUST be unique within a product; and a product with images MUST have exactly one primary image. Different image records MAY contain identical bytes if their object keys differ; content deduplication is not required. Persistence MUST protect required relationships with foreign keys, required fields with non-null constraints, and approved uniqueness/range rules. It MUST NOT include Inventory persistence.
- **FR-013**: Related category and variant reads MUST avoid per-product/per-variant requests, including on a page of 100 products.
- **FR-014**: Listing, detail, category, and variant representations MUST be consistent for the same unchanged catalog state.
- **FR-015**: A product MAY have zero to ten completed images. The database MUST store each image's durable object key, owning product, nonblank alt text, unique display order within its product, primary indicator, verified content type, byte size, and positive pixel dimensions. If a product has images, exactly one MUST be primary; completing the first image MUST make it primary, and selecting a later image as primary MUST atomically replace the previous primary. Later images MUST append by default; an administrator MAY request an unoccupied display position, while an occupied position MUST be rejected without changing existing order. Images MUST be returned in ascending display order. The object itself MUST reside in private S3-compatible storage, not in the catalog database; responses for published products MUST provide frontend-accessible signed read URLs derived from stored object keys. If a product has no images, its summary primary-image URL MUST be null and detail images MUST be empty.
- **FR-016**: The backend MUST support a staged, presigned image-upload preparation step and a separate completion step for an existing product. Only JPEG, PNG, and WebP image content is allowed; a file MUST be no larger than 10 MiB (10,485,760 bytes). Preparation MUST require nonblank alt text and proposed type/size and issue a staging upload URL valid for at most 15 minutes. For a first successful completion, the final database-side publication eligibility check MUST occur strictly before that upload session expires, using PostgreSQL `clock_timestamp()` within the publication transaction under the required Product/upload locks and claim fence. The transaction MUST commit successfully before a new image is considered published, but its physical commit MAY occur after expiry if that final check passed before expiry. Starting the request before expiry alone confers no eligibility. Completion MUST verify the preparing administrator, expected staging key, object existence, actual image format, byte size, positive dimensions, and per-product count. Client-declared content type or filename alone MUST NOT establish actual format. A rejected upload MUST NOT create a gallery item. The temporary presigned URL MUST NOT be stored as the image identifier. Local development MUST use LocalStack S3 and deployed storage MUST use AWS S3, following the constitution.
- **FR-017**: Each upload MUST move through PREPARED (URL issued), VERIFIED (staged object validated), and PUBLISHED (durable private object exists and image metadata is committed), or terminate as EXPIRED or FAILED. The upload session MUST expire 15 minutes after preparation; a first completion whose final database-side eligibility check occurs at or after expiry MUST return a non-success response and MUST NOT create a gallery image. Invalid objects MUST enter FAILED without a gallery entry. Completion MUST copy a validated staging object to a distinct durable private key and verify that object before committing image metadata; failed storage or database publication MUST NOT expose an image publicly. Retriable incomplete publication MAY be retried only while a final eligibility check can still pass before session expiry. A repeated completion after a successfully committed publication, including a replay after expiry, MUST return the same image without duplication. Scheduled recovery MAY detect expired sessions/claims, transition abandoned work to EXPIRED or FAILED, and clean unreferenced private objects; it MUST NOT impersonate ADMIN, perform a new administrator-authorized completion, publish an incomplete image, or interfere with a live valid claim. Staging objects from EXPIRED/FAILED uploads and private durable objects without committed image records MUST be reconciled or deleted within 24 hours of expiration or failure.
- **FR-018**: Only an authenticated user with the existing ADMIN role MAY prepare an image upload for an existing product and complete an upload that they personally prepared. Anonymous callers MUST receive HTTP 401; CUSTOMER users and ADMIN users attempting to complete another user's upload MUST receive HTTP 403. Authentication alone MUST NOT grant image-upload permission. Images attached to published products MUST be accessible to anonymous frontend consumers through FR-019; images attached to unpublished products MUST remain private at the object-access layer and MUST NOT appear in public catalog responses. JWT authentication and ADMIN role enforcement are dependencies, not existing implementations.
- **FR-019**: The backend MUST keep staging and durable S3 objects private so an object key alone grants no anonymous access. It MUST generate signed GET URLs dynamically only for images of `PUBLISHED` products and set each URL's maximum lifetime to five minutes. The database MUST store object keys and metadata, never signed URLs. A client whose URL expires MUST fetch the product response again to obtain a fresh one. Unpublishing MUST stop new URL generation immediately; a URL issued beforehand MAY remain usable until its five-minute expiry.
- **FR-020**: Each ProductVariant MUST have an `options` map whose keys are nonblank option names and values are nonblank selected values. The map MAY be empty only when its product has exactly one variant. Within one product, no two variants MAY have the same option map regardless of key order. Option names and values are not restricted to a predefined business vocabulary in this feature; no option-definition management is included.
- **FR-021**: Initial catalog data MUST set each product status explicitly; a product becomes publicly visible only after trusted initial data or a separately approved ADMIN-controlled workflow explicitly sets `PUBLISHED`. This feature MUST NOT expose an endpoint to create products or change product status. A `DRAFT` product represents unpublished catalog data; an `ARCHIVED` product represents retired catalog data, and both remain private under FR-005 and FR-019.

### Proposed Read API Contract

| Method and path | Request | Success | Errors |
|---|---|---|---|
| `GET /api/products` | Public; optional `page`, `size` per FR-003 | 200; `{items: ProductSummary[], page, size, totalItems, totalPages}`, published only | 400 invalid pagination |
| `GET /api/products/{id}` | Public; required canonical UUID product ID | 200; published `ProductDetail` | 400 malformed ID; 404 absent/unpublished |
| `POST /api/products/{id}/images/uploads` | ADMIN user; canonical UUID product ID, proposed JPEG/PNG/WebP type and size ≤10 MiB, nonblank alt text | 201; UUID upload identifier, staging upload URL, required upload fields, expiry ≤15 minutes | 400 invalid metadata or ID; 401 anonymous; 403 CUSTOMER; 404 product; 409 image-count limit reached |
| `POST /api/products/{id}/images/uploads/{uploadId}/complete` | Preparing ADMIN user; canonical UUID product/upload IDs, optional new-primary selection and unoccupied display order | 201 on first success; 200 with same image on replay | 400 malformed input or ID; 401 anonymous; 403 CUSTOMER or different preparer; 404 product/upload; 409 expired, count limit, or occupied order; 422 invalid object; 503 storage unavailable |

`ProductSummary` has `id`, `name`, `category: {id, name}`, and `primaryImageUrl` (nullable, signed, five-minute maximum lifetime). `ProductDetail` adds `description`, a nonempty `variants: VariantSummary[]`, and ordered `images: ProductImageSummary[]`. Each `VariantSummary` has `id`, `sku`, `options: {name: value}`, and `sellingPrice: {amount, currency: "VND"}`, where `amount` is an exact whole-đồng number with no fractional part. Each `ProductImageSummary` has `id`, `url` (signed, five-minute maximum lifetime), `altText`, `width`, `height`, `isPrimary`, and `displayOrder`. IDs are canonical UUID strings. A separate category listing endpoint is omitted because the stated pages can read category labels from product responses.

### Key Entities *(include if feature involves data)*

- **Category**: UUID ID, name, optional parent category; one category can classify many products. Global name uniqueness is not assumed.
- **Product**: UUID ID, name, description, required status from `DRAFT`, `PUBLISHED`, or `ARCHIVED`, exactly one category, and at least one variant. `PUBLISHED` is publicly visible; `DRAFT` is unpublished and `ARCHIVED` is retired, both private. Product has no selling-price field.
- **ProductVariant**: UUID ID, owning product, required exact VND whole-đồng selling price, nonblank catalog-wide case-insensitive unique SKU, and a required flat option map; the conceptual diagram also names weight. SKU, options, and selling price appear in product detail. The map may be empty only for a single-variant product; combinations must be distinct within a product. Weight unit remains unconfirmed, and weight is not exposed by this catalog API.
- **ProductImage**: UUID ID, owning product, globally unique durable object key, nonblank alt text, unique per-product display order, primary indicator, verified content type, byte size, and positive pixel dimensions. A product may have up to ten completed images; where present, one is primary. Image content is not deduplicated. This is new relative to the conceptual catalog diagram.
- **ProductImageUpload**: UUID prepared-upload ID, owning product, preparing administrator, globally unique staging key, optional durable key, preparation/expiry times, lifecycle state, and final image reference when published. It records enough information to reject expired or unauthorized completion and recover private orphans.
- **Inventory**: Related conceptual entity only; excluded from this feature.

### Persistence Boundaries

The minimum proposed tables are categories, products, product_variants, product_images, and product_image_uploads to track staged uploads across requests. Their new catalog primary keys and intra-catalog foreign keys use UUIDs, including `products.category_id`, `product_variants.product_id`, `product_images.product_id`, and `product_image_uploads.product_id`. The separate image table supports multiple images per product without storing image bytes in the database. Product status must be non-null and limited to `DRAFT`, `PUBLISHED`, or `ARCHIVED`; initial data sets it explicitly. Variant `product_id`, nonblank `sku`, `selling_price`, and `option_combination` must be non-null; `selling_price` must preserve exact whole-đồng VND values and reject fractional amounts, with no per-variant currency or product-level price column. The option combination stores one flat map; empty-map and per-product uniqueness rules follow FR-020, without extra option tables. SKU uniqueness applies across variants without regard to case. Image product link, globally unique object key, unique per-product display order, and primary indicator must be non-null; at most one primary per product requires an integrity rule, while the first-image/exactly-one rule also requires transaction-level validation. Upload records must retain product, initiating administrator, globally unique staging key, state, expiry, and final outcome. The at-least-one-variant rule requires validation of catalog data before public use because a child foreign key alone cannot enforce that minimum. Required names and ownership links should be non-null; other nullability, checks, and exact index definitions follow the selected schema/query. Index category-to-product, product-to-variant, and product-to-image links and support stable published-product pagination. The constitution requires PostgreSQL, Flyway migrations, plural snake_case tables, and named constraints. No applied schema or migration currently exists.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of approved acceptance cases for listing, empty listing, detail, not found, category, variant, pagination, and invalid input pass in an integrated environment.
- **SC-002**: A consumer can retrieve up to 100 summaries and open one product using two catalog requests, with no extra category, variant, or image-metadata request; returned image URLs can fetch image bytes during their five-minute validity window.
- **SC-003**: With at least 1,000 catalog products, 95% of 20-item page and detail requests finish within 1 second in an agreed, recorded test environment.
- **SC-004**: Repeated requests over unchanged data return identical page ID order and accurate counts in 100% of acceptance runs.
- **SC-005**: Review of the three stated frontend journeys confirms 100% of approved catalog fields are present, without inventory quantities or internal persistence data.
- **SC-006**: In acceptance tests, 100% of valid prepared-and-completed image uploads produce exactly one recorded image in the correct product gallery, while invalid, expired, and unauthorized attempts produce no publicly visible image.

## Assumptions

- The application has only ADMIN and CUSTOMER roles. "Catalog administrator" in this specification means a user with the existing ADMIN role; it does not introduce another role. CUSTOMER users cannot upload product images.
- Only the stated home/list/detail journeys are in scope; they do not yet require a standalone category listing endpoint.
- Pagination defaults to page 0, size 20, maximum 100; these are proposed API defaults for review.
- Listing summaries omit variants; detail includes at least one variant. Listing summaries include the primary image URL where an image exists. No summary price is assumed.
- A product with no images remains valid; a product with images has exactly one primary. This is the minimum rule inferred from support for a primary image and can be revisited before planning.
- Stable pagination is required; the concrete ordering key is a planning choice using the approved UUID identifier and schema.
- A product has one immediate category, following the diagram. Category hierarchy is not exposed.

## Dependencies and Open Decisions

- **Confirmed access policy**: Only authenticated ADMIN users may add images to existing products, including published products, and select a new primary image; only the initiating ADMIN user may complete a prepared upload. CUSTOMER users cannot upload. Public reads and new image URLs remain limited to PUBLISHED products. JWT authentication and ADMIN role enforcement are required but not implemented in this checkout.
- **Storage decisions**: Private S3 objects, dynamically generated five-minute signed read URLs, upload states, 15-minute upload expiry, object verification/publication, 24-hour cleanup, failure recovery, image formats, size, count, metadata, order, and primary rules were approved in the current clarification session. The authentication system needed by the upload endpoints is not implemented in this checkout.
- **Confirmed business decisions**: Product statuses are DRAFT, PUBLISHED, and ARCHIVED; only PUBLISHED is public, and trusted initial data or a separately approved ADMIN workflow must explicitly set it. Selling prices use exact VND whole đồng on ProductVariant, and options use a flat map. The zero-price policy was not part of the price decision and can be deferred because this feature has no variant-write API.
- **Other design inputs**: SKU length/character rules, weight unit, category hierarchy use, and source of initial catalog data. These may be deferred if they do not affect the approved read/upload scope.
- **Repository baseline**: `demo/pom.xml` declares Spring Boot 4.1.1 and Java 25; the constitution names 4.1.2 as a target. Production Java contains only `com.example.demo.DemoApplication`. There are no catalog packages, controllers, services, repositories, entities, DTOs, migrations, security rules, exception handlers, or non-test datasource configuration. JPA, Flyway, PostgreSQL, Security, and test dependencies are declared; the test profile configures disposable PostgreSQL, and the only test checks context loading. Declared dependencies are not implemented behavior.
- **Reference limitation**: `specs/001-product-catalog-inventory-search/spec.md` is absent in this checkout, though `.specify/feature.json` pointed to it before this new spec. The conceptual diagram and constitution were used as available references; no missing draft contents were inferred.

## Scope Boundary

Backend Product Catalog only. Two public catalog read endpoints and the dedicated product-image upload/complete workflow are in scope. Frontend/UI, inventory persistence or quantities, stock reservation, product search, advanced filtering/sorting, cart/order/payment, general catalog administration/CRUD, and unrelated refactoring are excluded.
