# Data Model: Customer Order Management

Design only; no migration/entity code is created in this phase. Relevant requirements: FR-004, FR-008–015, FR-017 and all historical-integrity scenarios.

## orders / Order

| Column | PostgreSQL / Java type | Nullability and rule |
| --- | --- | --- |
| id | BIGINT identity / Long | Primary key, immutable |
| customer_id | BIGINT / Long | Required FK users.id, ON DELETE RESTRICT; owner cannot be changed |
| order_number | TEXT / String | Required, nonblank, unique uk_orders_order_number; immutable |
| placed_at | TIMESTAMPTZ / Instant | Required, immutable absolute UTC timestamp |
| status | TEXT / OrderStatus | Required; PENDING, CONFIRMED, SHIPPING, COMPLETED, CANCELLED only; JPA STRING enum |
| recipient_name_snapshot | TEXT / String | Required nonblank historical recipient |
| recipient_phone_snapshot | TEXT / String | Required nonblank historical phone, not validated against current customer phone |
| delivery_address_snapshot | TEXT / String | Required nonblank complete historical address text; no live address dependency |
| merchandise_total | NUMERIC / BigDecimal | Required finite nonnegative integer VND; equals committed sum of item line totals |
| payable_total | NUMERIC / BigDecimal | Required finite nonnegative integer VND; independently stored authoritative total |
| cancelled_at | TIMESTAMPTZ / Instant | Required exactly when status=CANCELLED, otherwise null; >=placed_at |
| completed_at | TIMESTAMPTZ / Instant | Required exactly when status=COMPLETED, otherwise null; >=placed_at; fixture/history field, no customer writer |
| cancellation_reason | TEXT / String | Nullable; only CANCELLED may have it; trimmed nonblank when present, <=500 Unicode code points |

No payment/shipping/shop/voucher/checkout columns, version column or generic updatedAt is needed. No creation service or order-number generator is added. Test fixtures choose unique numbers directly.

## order_items / OrderItem

| Column | PostgreSQL / Java type | Nullability and rule |
| --- | --- | --- |
| id | BIGINT identity / Long | Primary key, immutable; defines stable item order |
| order_id | BIGINT / lazy Order | Required FK orders.id, ON DELETE RESTRICT; item cannot be reparented |
| product_id | UUID / UUID | Optional FK products.id ON DELETE SET NULL |
| product_variant_id | UUID / UUID | Optional FK product_variants.id ON DELETE SET NULL |
| product_name_snapshot | TEXT / String | Required nonblank; independent of current product |
| variant_sku_snapshot | TEXT / String | Required nonblank; independent of current variant |
| variant_options_snapshot | JSONB / Map<String,String> | Required object; keys/values strings; empty object valid when no options |
| quantity | INTEGER / Integer | Required 1..2147483647; no cart-specific 999 cap imposed on historical orders |
| unit_price_snapshot | NUMERIC / BigDecimal | Required finite nonnegative integer VND |
| line_total | NUMERIC / BigDecimal | Required finite nonnegative integer VND; exactly unit_price_snapshot*quantity |

Do not deduplicate historical rows by variant. Repeated product/variant references can legitimately exist as separate historical rows. References are optional even in fixtures representing deleted catalog records; snapshot fields remain mandatory. No image ID/key/URL is persisted: optional primaryImageUrl is current authorized presentation only.

## Relationships, lifecycle and mappings

One order belongs to one existing customer and has at least one item. Use scalar customer/catalog IDs so lazy ORM relationships cannot accidentally fetch current catalog/profile data. OrderItem.order and optional Order.items mapping are LAZY; expose no JPA entities. Use safe Lombok getters/setters/protected no-arg constructors, no @Data or association-based equals/hashCode/toString. No cascade/remove/orphanRemoval for independent aggregates or historical order items. Ordinary feature code never deletes orders/items or rewrites snapshots.

Catalog deletion nulls only live references, not snapshots. A variant deletion can leave productId present; a product deletion may null both references through existing catalog cascades. Do not require both references simultaneously non-null or query current variants to validate a historical read. User deletion is blocked while orders reference that user. Broader account deletion/anonymization policy is separate.

## Integrity enforcement

Use NOT NULL plus named CHECK constraints, including status allowlist, nonblank text, terminal timestamp equivalences, no reason outside CANCELLED, reason length, positive quantity, JSONB object/string structure, line arithmetic, and finite whole-VND values. Use NUMERIC without scale coercion; reject NaN/Infinity/-Infinity explicitly as well as negative/fractional values. Java fixture arithmetic uses exact BigDecimal/BigInteger conversion, never double.

Cross-row nonempty items and merchandise sum cannot be ordinary CHECK constraints. Use DEFERRABLE INITIALLY DEFERRED constraint triggers on orders INSERT/UPDATE and order_items INSERT/UPDATE/DELETE to validate affected existing parents against final transaction state; no snapshot recalculation. Parent insert before children is legal within one transaction, but committing an empty or mismatched order fails. Repeated checks within a bulk fixture may revisit the same aggregate; no global scans. Acquire parent locks consistently by ascending order ID where an operation affects several parents; reparenting is unsupported. This feature has no concurrent item writer. Any future creation/item-writer workflow must adopt the same parent coordination and is separately scoped. Test deletion/nulling catalog references still passes these integrity checks.

FK names: fk_orders_customer_id, fk_order_items_order_id, fk_order_items_product_id, fk_order_items_product_variant_id. Unique name: uk_orders_order_number. CHECK names use ck_orders_* / ck_order_items_*. No parent delete cascade; isolated fixture teardown explicitly deletes order_items then orders before users, or truncates dedicated test data with appropriate isolation.

## Indexes and access paths

- idx_orders_customer_id_placed_at_id: (customer_id, placed_at DESC, id ASC).
- idx_orders_customer_id_status_placed_at_id: (customer_id, status, placed_at DESC, id ASC).
- idx_orders_customer_id_payable_total_id: (customer_id, payable_total DESC, id ASC).
- idx_orders_customer_id_status_payable_total_id: (customer_id, status, payable_total DESC, id ASC).
- idx_order_items_order_id_id: (order_id, id ASC).
- idx_order_items_product_id and idx_order_items_product_variant_id: support FK deletion/nulling.

Ascending primary-sort variants may require a bounded database sort; indexes are not a latency guarantee. Review actual PostgreSQL plans during implementation before adding more indexes. Owner+status count comes first and exclusively from orders. Compute offset=(long)page*size; if offset >= totalElements, return accurate empty-page metadata without content/preview queries. For nonempty pages use normal JPA pagination only at offset <=Integer.MAX_VALUE; larger offsets use the custom order repository's bound long SQL OFFSET path with the same predicates/sort/id tie-breaker. Never narrow/clamp offsets or execute a second automatic count. Preview query restricts to selected page IDs before window counting/ranking; return at most 3 rows/order. Detail returns all owned-order rows, ID ascending. No schema change is needed for the pagination remedy.

## Customer state machine

| Current state | cancelOrder | Changes |
| --- | --- | --- |
| PENDING | 204 | CANCELLED, cancelledAt from injected Clock, normalized optional reason |
| CONFIRMED | 204 | Same |
| CANCELLED | 204 | None, including different valid supplied reason |
| SHIPPING | 409 | None |
| COMPLETED | 409 | None |

Unknown/foreign is 404 before state is exposed. No receipt-confirmation transition, status setter, automatic completion or other customer lifecycle API. Cancellation never changes completedAt, items, prices, totals, inventory or external systems. Valid fixtures set all five states and their matching timestamps directly.

For eligible PENDING/CONFIRMED first cancellation, a sampled Clock before placed_at yields HTTP 409 and changes nothing. Classify CANCELLED before Clock sampling so valid repeats remain 204 regardless of clock rollback. Unsupported/missing JSON media type on a nonempty cancellation body yields HTTP 415 before persistence access; absent/zero-byte body requires no Content-Type.

## Migration strategy

One additive migration creates tables, indexes, constraints/triggers, MANAGE_ORDER permission and its USER role grant. Provisionally V23__customer_order_management.sql given current V22 cart migration; resolve final available version at implementation. Do not edit older migrations or insert demo orders. Fail safely if mandatory USER role is absent, following existing permission migrations; no role reassignment. Verify clean migration, upgrade from prior schema, data preservation and existing/future USER access. Existing applied migration version and metadata must be checked before assigning the final number.
