# Checkout Data Model

Design only; implements [spec.md](spec.md) through [internal-services.md](contracts/internal-services.md). Provisional new migration V24; verify latest version before writing it. Existing applied migrations remain unchanged.

## checkout_receipts — successful outcome only

| Column | Type | Invariant |
| --- | --- | --- |
| id | BIGINT identity | Local internal primary key, not public API |
| customer_id | BIGINT NOT NULL | Existing users.id, ON DELETE RESTRICT |
| operation | TEXT NOT NULL | Exactly CHECKOUT; explicit key scope |
| idempotency_key | VARCHAR(128) COLLATE C NOT NULL | Case-sensitive ASCII regex `^[A-Za-z0-9_-]{16,128}$`, never trimmed |
| request_address_id | BIGINT NOT NULL | Positive original validated command; intentionally no addresses FK, so deletion does not invalidate replay |
| request_fingerprint | TEXT NOT NULL | SHA-256 lowercase hex of UTF-8 `checkout:v1:addressId=<canonical positive decimal>`; compare addressId too, never hash-only equality |
| order_id | BIGINT NOT NULL | orders.id, ON DELETE RESTRICT; unique one receipt/order |
| receipt_version | INTEGER NOT NULL | 1; explicit compatible decoding version |
| response_status | INTEGER NOT NULL | Exactly201 |
| location | TEXT NOT NULL | Exactly `/api/me/orders/<order_id>` |
| receipt_body | JSONB NOT NULL | Version1 immutable API body, object with order and skippedItems arrays/objects as contracted |
| created_at | TIMESTAMPTZ NOT NULL | Same Clock sample as order placedAt |

Named constraints: uk_checkout_receipts_customer_id_operation_idempotency_key; uk_checkout_receipts_order_id; fk_checkout_receipts_customer_id; fk_checkout_receipts_order_id; ck_checkout_receipts_key, _operation, _address, _fingerprint, _version, _status, _location, _body. Unique scoped key index provides exact lookup; order unique index provides inverse lookup. No redundant secondary lookup index or TTL index.

Use a composite reference to orders(id, customer_id), backed by additive uk_orders_id_customer_id, so a receipt cannot reference a different customer's order even under direct SQL. Name the composite FK fk_checkout_receipts_order_id_customer_id. This replaces the single-column order FK, not an additional duplicate association. Restrictive FKs preserve history and do not introduce account/order deletion workflows.

For version1, a migration-owned validation function/check validates JSON structure, field types, original PENDING status, nonempty item list, currency VND, finite nonnegative integral amounts, positive IDs, quantity1–999, line/aggregate/payable equality and unique sorted item/skipped IDs. Validate receipt header orderId/location/time against columns; a deferred receipt integrity trigger also verifies original persisted order ownership, snapshots/items/totals and initial state at insertion/commit, without rewriting data. Updates of receipt semantic columns are rejected by a receipt immutability trigger; no application update/delete method or cleanup task. Later order cancellation/live-reference nulling does not revalidate or rewrite the frozen receipt. Do not add triggers that prevent legitimate order cancellation or catalog deletion.

JPA CheckoutReceipt uses safe getters/protected constructor, immutable fields, scalar customer/order IDs (no eager aggregate graphs), JSON mapping or explicit repository JSON conversion. No @Data, entity equality traversal or cascading. Conversion produces a detached response before commit but cannot return it to HTTP until transaction completion.

## Existing orders and order_items

Retain V23 schema constraints, terminal timestamps, ownership and deletion semantics. Add only composite uniqueness needed above; no global identifier/precision change. Creation writes one PENDING order, null terminal fields/reason and complete immutable recipient snapshots. Each included cart row creates one item; distinct row count stays distinct-row semantics. Quantity validated1–999 by checkout/order creation, without tightening historical V23 quantities globally. Product/variant FKs retain ON DELETE SET NULL.

Order number design: reserve next orders identity ID in the order repository, form `ORD-<id>`, native insert explicit ID and RETURNING projection. Never entity-merge a preset generated ID. Insert items in ascending source cart-row ID, return their actual generated IDs; existing detail ascending item ID remains stable. Number collision or sequence exhaustion is safe conflict/failure with full rollback; no automatic retry. No user-controlled number/status/totals. Existing arbitrary historical numbers unchanged.

Use a single injected Clock instant truncated to microseconds for order placedAt, receipt createdAt and response, no sampling to reconstruct replay. Exact recipientName, recipientPhone and complete addressText come from one coherent owned saved address. Full name/SKU/options and current prices come from protected catalog state. All computed money uses BigInteger; exact integral BigDecimal persistence only. Validate decimal integer digit count <=131072 for each amount/line/total before writes; overflow422. No fixed precision/scale rounding or long narrowing. Receipt mapper handles valid large integers with a receipt-only number-length bound131072; keep public request parser strict Long and do not weaken the global mapper.

## Existing cart and addresses

No new cart/address columns or Checkout aggregate. Under existing cart parent lock capture immutable selected rows: cartId, cartItemId, productId, productVariantId, quantity, selected. Consume only included rows matching captured owner/cart/id/variant/quantity/selected, and verify affected count equals included count. Any mismatch409 rolls back everything. Skipped/unselected rows remain byte-for-byte equivalent in stored business fields; no entire-cart clear, reselection, snapshot refresh or cart removal.

Address snapshot DTO: recipientName, recipientPhone, deliveryAddress; addressId belongs to command, not a mutable address reference in order history. User serialization coordinates existing address update/delete, without assuming a user-row lock locks address children automatically: safety depends on all existing address writers participating in the same protocol.

## Frozen receipt shape

See [API](contracts/api.md). Header/order/item snapshots are immutable and do not contain live product references/images; generated order/item IDs remain. skippedItems records only cartItemId/reason sorted ascending, including only skipped selected rows. Receipt does not reference live cart rows by FK, so later deletion/change cannot break it. No selected-empty successful receipt.

## Lifecycle

1. After current authorization/input validation, look up the exact committed customer/operation/key receipt; matching command returns original201 before any fence. On a miss, try the transaction fence and perform a fresh committed receipt lookup both after acquisition and after busy failure. Every hit applies addressId/fingerprint equality; mismatch409, matching result201. Only acquired fence plus continued receipt absence enters checkout; busy fence plus no committed receipt visible returns CHECKOUT_IN_PROGRESS409. READ_COMMITTED rechecks use new SQL statements, not a cached initial miss. Work in progress remains transaction-local, not durable entity state.
2. Validation/no-eligible/conflict/infrastructure failure -> transaction rollback, no receipt/order/deletion; key remains reusable, sequence gaps acceptable.
3. Successful commit -> durable version1 receipt and order/cart change together.
4. Replay same scoped command -> original stored201/Location/body, no domain re-evaluation.
5. Replay different scoped command ->409 CHECKOUT_KEY_CONFLICT, unchanged.
6. Order cancellation or catalog/address change -> receipt unchanged; current detail separately changes under its existing contract.

No time-based expiry, key recycling, processing cleanup, payment/stock state or excluded side effects.

## Migration validation

Implemented in V24__customer_checkout.sql. OrderCreationRepositoryImpl reserves the existing identity sequence, inserts ORD-<id> as the unique order number, and obtains generated item IDs with INSERT RETURNING in captured row order. It does not merge preassigned IDs or refresh live catalog state. Receipt JSON uses ISO Instant text at microsecond precision; its private decoder permits 131072-digit integers without changing public request parsing. Integrity tests validate deferred failures at actual transaction commit.

Verify clean apply and upgrade from V23 with existing user/cart/order data, exact USER grant inheritance, custom-role non-grant, receipt foreign-owner rejection, duplicate scoped key/order rejection, immutable receipt updates rejected, deleted address replay viable, malformed JSON/amount/totals rejected and deferred constraints at actual commit. Scope changes to latest-version assertions and isolated successor migration only; never rewrite historical V21/V22/V23 migration tests merely to reach current version.
