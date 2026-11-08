# Data Model: Personal Product Wishlist

## WishlistItem / wishlist_items

| Field | Java / PostgreSQL | Rules |
| --- | --- | --- |
| id | Long / BIGINT identity | Private generated immutable primary key |
| user | LAZY ManyToOne User / user_id BIGINT | Required existing user; no ORM parent cascades |
| product | LAZY ManyToOne Product / product_id UUID | Required existing product; no ORM parent cascades |
| addedAt | Instant / added_at TIMESTAMPTZ | Required immutable UTC absolute timestamp, updatable=false |

Use safe getters/protected no-arg constructor, no entity @Data or relationship-based equality/toString. No updatedAt, variant, quantity, price, copied product/user data or persisted image URLs.

## Constraints and indexes

- Primary key id; nondeferrable uk_wishlist_items_user_id_product_id UNIQUE(user_id, product_id), targeted by atomic insertion.
- fk_wishlist_items_user_id references users(id) ON DELETE CASCADE; fk_wishlist_items_product_id references products(id) ON DELETE CASCADE. These delete dependents only; ORM membership-to-parent persist/remove cascades are forbidden.
- All four stored fields are NOT NULL.
- idx_wishlist_items_user_id_added_at_product_id supports owner filtering with added_at DESC, product_id ASC.
- idx_wishlist_items_product_id supports product-reference cleanup. Pair uniqueness supports owner/product lookup/delete.
- Existing unrelated FKs may independently block physical parent deletion; do not change other domains' lifecycle policies.

## Migration

Plan V10__create_wishlist_items.sql; recheck next available number during implementation. Create association/constraints/indexes, insert MANAGE_WISHLIST and grant it to USER via role_permissions by names, not hardcoded IDs. Preserve user_roles, custom roles and unrelated grants. Existing V6 establishes USER; unexpected absence is a migration error to investigate, not permission to reassign accounts. Existing and future USER assignments inherit the grant.

No V1–V9 edits, schema changes or migration execution are part of this planning phase. SQL keywords/types uppercase, identifiers snake_case.

## Lifecycle

Absent + eligible add -> present/new addedAt/201. Present + published duplicate -> unchanged/200 through targeted INSERT ... ON CONFLICT DO NOTHING, with no transaction failure and no new pessimistic duplicate-resolution lock. Hidden duplicate -> 404 while membership remains. Visible/hidden owned membership + removal -> absent/204. Absent + removal -> absent/204. Unpublishing hides without deleting; republishing restores original time. Physical parent deletion cleans dependents. Re-add after removal creates a new ID and samples a fresh addedAt; the timestamp need not be strictly greater than the removed value. Equal timestamps retain deterministic product UUID ASC ordering.

Timestamp tests advance a controlled Clock beyond PostgreSQL microsecond storage precision for deterministic fresh-sample assertions, and separately retain equal-time ordering coverage. Do not assume two calls in the same clock tick produce distinct persisted times or add a monotonic-timestamp invariant. Catalog owns product state; no duplicated membership visibility flag.

## Projections and DTOs

Repository page projects productId/addedAt only; matching count filters owner and PUBLISHED before pagination. WishlistItemResponse contains addedAt and prepared ProductSummary product. WishlistPage contains items/page/size/totalItems/totalPages. Internal AddWishlistResult contains created boolean and item for controller status selection, not a public response wrapper. Fully materialize inside services; do not expose entities or require OSIV.
