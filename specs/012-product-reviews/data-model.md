# Data Model: Product Reviews

## product_reviews — new review aggregate

| Column | Type | Rules |
| --- | --- | --- |
| id | BIGINT identity | Primary key; positive public review identifier |
| order_item_id | BIGINT | NOT NULL; unique; FK order_items(id) ON DELETE RESTRICT |
| author_id | BIGINT | NOT NULL; FK users(id) ON DELETE RESTRICT; immutable |
| product_id | UUID | NOT NULL; immutable purchase-derived snapshot; intentionally no catalog FK |
| rating | SMALLINT | NOT NULL; CHECK between 1 and 5 |
| comment | TEXT | Nullable; normalized strip/blank-to-null; CHECK CHAR_LENGTH <= 2000 |
| created_at | TIMESTAMPTZ | NOT NULL; immutable UTC Instant |
| updated_at | TIMESTAMPTZ | NOT NULL; initially created_at; effective edits only |

Constraint names: `uk_product_reviews_order_item_id`, `fk_product_reviews_order_item_id`, `fk_product_reviews_author_id`, `ck_product_reviews_rating`, `ck_product_reviews_comment_length`, `ck_product_reviews_timestamps` (updated_at >= created_at). Index `idx_product_reviews_product_id_created_at_id` supports default listing; `idx_product_reviews_product_id_rating_id` supports rating sort. Product-only aggregation can use the leading product ID index. Use descending-compatible index ordering for default `(created_at DESC, id DESC)`; opposite rating direction still needs ID DESC tie-breaking, so inspect plans before claiming all sorts are index-only.

Safe JPA getters and protected no-arg constructor; scalar cross-domain IDs, no cascades to users, products, items or media. Use no Lombok Data/unsafe entity equality. A dedicated service transaction pessimistically locks an existing review for updates; no exposed version/optimistic precondition is introduced.

## product_review_images — aggregate-owned ordered references

| Column | Type | Rules |
| --- | --- | --- |
| id | BIGINT identity | Primary key |
| review_id | BIGINT | NOT NULL; FK product_reviews(id); aggregate-owned |
| image_id | UUID | NOT NULL; FK image_uploads(id) ON DELETE RESTRICT |
| display_order | INTEGER | NOT NULL; CHECK 0 <= value AND value < 10 |

Unique constraints `uk_product_review_images_review_id_display_order` and `uk_product_review_images_review_id_image_id`; FKs `fk_product_review_images_review_id` (ON DELETE CASCADE within the aggregate only), `fk_product_review_images_image_id`; check `ck_product_review_images_display_order`. Dense positions 0..n-1 are assigned from submission order. Unique positions in the ten-value range protect the maximum persistently; service validation enforces density. Reusing an image in another owned review is allowed; no global image uniqueness rule is introduced. Parent reference removal may delete reference rows; it never removes uploads or S3 objects.

Replace references atomically under the parent review lock, flush deletion before insertion, and avoid a bulk persistence operation that leaves managed children stale. Configure only aggregate-owned cascades/orphan removal, or explicit reference repository operations with the persistence context kept coherent.

## Existing entities and service-owned data

- Order and OrderItem: numeric IDs; owned order must be COMPLETED; all other current states, including FAILED, are rejected. Quantity does not multiply review entitlement. Eligibility DTO contains only actor/order/item/product identity and status, not delivery snapshots. Lock author → order → exact item → review → sorted media metadata.
- Product: UUID; PUBLISHED gates public reads only. Product deletion may null existing order-item product references. New reviews copy the product ID while holding the exact item lock; existing reviews remain preserved independently. A legacy item already missing product ID cannot create a review; an existing review can still be edited through its immutable associations.
- User: numeric author ID retained internally; safe bulk projection supplies account status and minimal display data. Inactive status never erases history; public mapping replaces all identity fields with “Former customer.” No lifecycle operation is added.
- ImageUpload: UUID ID, numeric owner, terminal COMPLETED attachment readiness. Pending expiry is not a post-completion TTL. New review references add deletion restriction rather than media deletion.

## State transitions and validation

No moderation/deletion state exists. Absent → Created occurs only after eligibility and image validation; exactly one review per item. Created → Edited has no age limit for an active owner. createdAt/author/item/product never change. Effective rating/comment/image-order edits advance updatedAt using injected Clock and database-compatible microsecond precision; use a value strictly greater than the previous updatedAt if the clock has not advanced. No-op edits preserve timestamps. Never accept timestamps from clients.

POST: absent/null comment becomes null; absent images becomes []; explicit null images is invalid. PATCH: absent preserves, null rating/images invalid, null/blank comment clears, [] images clears, explicit list replaces. Normalize before measuring Unicode code points; reject 2001 without truncation. Never use @Size alone as a UTF-16 comment-length implementation.

## Derived rating summary

No statistics table or mutable counters. One committed review contributes one count regardless of purchased quantity. Count all rows by product UUID, average rating rounded HALF_UP to two decimal places; count 0 returns averageRating null. Distribution is excluded. Summary is not affected by list page/sort and cannot include uncommitted writes.

## Migration and retention

Add a new migration under `demo/src/main/resources/db/migration`, provisionally `V27__product_reviews.sql` because V26 is currently the latest numeric version; recheck at implementation time. No edits to applied migrations, backfill from names, destructive data changes, or changed catalog/order delete rules. Tests must verify uniqueness, checks, FKs, aggregate ownership and history under catalog deletion and account status changes.
