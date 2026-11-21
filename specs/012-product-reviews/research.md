# Research: Product Reviews and Ratings

Date: 2026-11-20. Scope: local repository analysis and design, not application implementation. Constitution: 1.2.0.

## 1. Requirements and design authority

**Decision**: Preserve Q1–Q3 and implement all existing proposals as approved policies. On 2026-11-21 the user explicitly stated “all proposals are approved,” confirming P-01–P-05, MANAGE_ORDER plus ownership/ACTIVE checks, HTTP/update/error contracts, media fallback and active-author summary. No policy approval remains pending.

**Rationale**: The original planning invocation authorized design only; the later explicit approval resolves that distinction without inferring consent from a command. Existing exclusions remain unchanged.

**Alternatives considered**: Leaving proposal gates active after explicit approval would contradict the user's instruction. Introducing new scope or changing the constitution is unnecessary.
## 2. Domain ownership and dependency direction

**Decision**: Add `com.example.demo.review` with controllers, service interfaces/implementations, repositories, DTOs, converters, validators and exceptions. Dependency direction: review → order, catalog, user, media; order → catalog/inventory already exists. No upstream domain depends on review. Review persistence holds scalar cross-domain identifiers; fetch related data through service DTOs, not cross-domain repositories or lazy entity traversal.

Add `OrderReviewReadService`/`OrderReviewReadServiceImpl` in order: owned eligibility seed for `(actor, orderId, itemId)` and a current-state validation within a joining write transaction. Add `UserReviewReadService`/implementation for bulk safe author summaries. Extend MediaService with bulk attachment preparation, in-transaction metadata validation and bulk safe attached-image access; owning media implementation alone queries image uploads. Reuse `ProductCatalogService.getPublishedProductEntity` for public visibility checks without loading a full gallery.

**Rationale**: Existing `OrderReadService` produces customer read DTOs rather than review eligibility; `UserService.getUser` and `MediaService.getAttachedImage` are single-resource operations. Calling those per review would violate planned fetching. Existing catalog image attachment accesses media repositories directly; it is evidence, not a pattern to copy.

**Alternatives considered**: Cross-domain joins in review repositories and calling existing converters on lazy entities were rejected by constitution I/III. Adding review statistics to catalog detail now risks a reverse dependency and is unnecessary for dedicated summary reads.

## 3. Identity and historical purchases

**Decision**: New reviews use BIGINT identity IDs consistent with owned-order resources. Store immutable `order_item_id`, `author_id`, and `product_id` (UUID snapshot) in reviews. `product_id` deliberately has no live catalog foreign key; it remains queryable as historical identity when catalog records disappear. Restrict deletion of referenced users/order items; reviews own only their image-reference rows. No data is deleted or historical migration edited.

**Rationale**: `V23__customer_order_management.sql` sets order-item product/variant FKs to null on catalog deletion. An already-created review must retain its original product ID. Creation derives the ID from the current owned item; reject items whose ID is already irrecoverable. Do not invent identity from product-name snapshots or require archived products to be published for writes.

**Alternatives considered**: A mandatory product FK would block existing catalog deletion or destroy history; only traversing the order item's nullable FK loses the product identity. A new global ID policy is unnecessary.

## 4. Concurrency and transaction boundaries

**Decision**: Use short READ COMMITTED REQUIRED write transactions on a separate `ReviewWriteServiceImpl` bean, invoked by an orchestration service after storage preparation. Lock order: author user row first through `UserService.getUserEntityForUpdate`; verify ACTIVE; then owned order row through order service; then exact order item row (parent locks do not lock children); then existing review row for edit. Acquire media metadata rows with shared/read locks sorted by UUID after those locks if image references are being replaced. Create uniqueness is finally enforced by `uk_product_reviews_order_item_id` even if preliminary checks pass. No automatic retries. Lock failures and recognized uniqueness violations become 409.

Updates use `SELECT ... FOR UPDATE` on the owned review; apply presence-aware fields to the latest locked state. Concurrent edits serialize: both may succeed sequentially, with the later explicit value winning when the same field is replaced. Disjoint omitted fields preserve the newest values. No client version or If-Match contract is added. References are replaced only inside the same transaction as content. Delete old reference rows and flush before inserting replacement positions to avoid transient uniqueness conflicts. No-op normalized edits do not advance updatedAt.

**Rationale**: This protects accepted edits and image order without unrequested optimistic version input. The current JWT converter validates ACTIVE state before controller entry, but an in-flight write must recheck it under the existing user-row locking convention. No deactivation endpoint exists; future account-status mutation must use that same user lock. Completed orders are terminal in the current completion workflow. Exact item locking also protects identity capture against product-link nulling during concurrent catalog deletion.

**Alternatives considered**: Optimistic locking is viable but would add edit conflict behavior and require careful child-collection version coordination. Locking every product for statistics or raising isolation globally is unnecessary. Never assume a user/order lock locks children.

## 5. Storage and readiness

**Decision**: Keep initiation and completion separate from review writes. Before acquiring write locks, bulk-prepare explicitly submitted images through media: validate owner, COMPLETED state and physical final objects, and prepare access information. Inside the write transaction, revalidate owner/status/final-key identity using sorted metadata read locks; a changed identity causes 409. Image upload COMPLETED state is terminal in current code; expiry is a pending-upload deadline. No S3 copy/delete occurs in review persistence. Complete uploads that remain unattached follow existing retention rules.

For response reads, bulk-load media metadata and prepare one URL signer per response; resolve distinct images once. Unknown/unready metadata or a narrowly handled media/storage failure yields `{id, finalUrl:null}` in its original list position, not a failed review list. Do not catch arbitrary application/programming exceptions. Signing does not prove an S3 object exists: no per-image HEAD is performed on listing; a client may encounter an unavailable object and must display a placeholder. Removing a reference does not delete media.

**Rationale**: Existing `MediaServiceImpl.validateImageForAttachment` performs object verification, while attached reads sign without checking object existence. Moving physical checks before locks avoids holding database locks across network I/O. A physical deletion after validation is an external consistency race the existing workflow cannot atomically eliminate; safe reads cover it without claiming distributed atomicity.

**Alternatives considered**: Per-review single-image service calls violate fetch requirements; physical checks while holding locks increase lock duration; S3 deletion on detach changes established lifecycle rules.

## 6. Statistics and reads

**Decision**: Aggregate COUNT and numeric AVG directly from committed reviews by immutable product ID; round with BigDecimal HALF_UP to two decimals, null average for count zero. Do not maintain counters or cache. Page parent review projections with Pageable; load ordered references once for page IDs, authors once for distinct author IDs and media metadata once for distinct image IDs. Convert only fully materialized data. Use read-only REPEATABLE READ for a page's multi-query assembly and public visibility check to avoid mixed parent/child snapshots; summary uses one read-only transaction. A publication/account-status change committed after the read snapshot may be reflected on the next request. Set Cache-Control: no-store for both public responses so deactivation identity does not linger in application/CDN caches.

**Rationale**: Aggregation cannot drift on rating edits; no review count increment/decrement coordination is required. Snapshot reads give internally coherent pages without promising a snapshot across separate requests. At most a constant number of SQL reads per page independent of review count; no storage existence calls from converters.

**Alternatives considered**: Denormalized counters create unnecessary shared state. Collection join-fetch pagination and per-review author/media queries are unsuitable. An external performance SLA was not supplied, so no invented throughput commitment is made.

## 7. HTTP, validation and security routing

**Decision**: Use the spec's four approved endpoints, 201 create and 200 patch/list/summary. Reject unknown body/query properties and duplicate query keys. PATCH uses explicit presence flags via Jackson setters, like `UpdateUserRequest`, and validates normalized content. Reuse the existing `user/validator/CodePointLength` annotation/validator on normalized comment values (review already depends on user), rather than duplicate code-point counting. Unicode length uses `String.codePointCount` after `strip`, not Java UTF-16 length; PostgreSQL `CHAR_LENGTH` supplies the persistence bound. Required rating rejects fractional or string coercion locally rather than changing global binding. Image IDs are UUID strings, no null/duplicate elements.

Add the two GET rules to `CatalogSecurityConfiguration` (order 2), which already captures `/api/products/**` and otherwise denies them. Add owned mutation matchers to the bearer chain (order 3), using current actor and the approved MANAGE_ORDER mapping. Do not add them only to the fallback chain: it cannot see captured catalog paths. Preserve existing JWT handling: public reads without a Bearer header are accepted; supplied invalid/inactive credentials return 401 rather than being ignored. Anonymous unpublished reads return 404.

Map media exceptions as established: unknown/foreign images 404; pending/expired 409; invalid review input 400; unavailable mutation storage 503. Invalid binary content remains 422 in the separate existing upload completion flow. Translate only the exact new review uniqueness constraint to duplicate-review 409 via existing error factory conventions; unrelated integrity failures remain internal errors. Unsupported media type 415 and unsupported method 405 require review-controller-scoped handling where global advice would otherwise obscure them.

**Rationale**: Inspected `CatalogSecurityConfiguration`, `CurrentStateJwtConverter`, `GlobalExceptionHandler`, `ApiErrorFactory` and media exception classes establish actual routing and errors.

**Alternatives considered**: A separate public chain or global coercion/error changes would broaden scope. Reusing the entire PublicUserResponse exposes unnecessary identity data.

## Evidence and execution

Primary local evidence: `demo/pom.xml` (Java 25, Boot 4.1.2, existing test/S3 dependencies, Failsafe integration-test/verify goals); source paths cited above and in spec.md; order completion/integration fixtures; existing user presence-aware update DTO and media implementation. No dependency or plugin addition is required. Research-agent findings were consolidated with root inspection.

Setup: requested Bash script was attempted through installed Git Bash and failed with Windows NtCreateDirectoryObject access error; equivalent `.specify/scripts/powershell/setup-plan.ps1 -Json` succeeded. Setup reports BRANCH `012-product-reviews` as a feature-directory fallback; actual `git branch --show-current` is `feature/voucher`. No checkout was performed. No extension hooks are installed.
