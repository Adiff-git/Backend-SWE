# Checkout HTTP Contract

Binding design for [spec.md](../spec.md), with approved Q1:A/Q2:A/Q3:A. Only new route: POST `/api/me/cart/checkout`.

Implemented contract: see [validation evidence](../implementation-validation.md) for strict MVC input checks, real bearer authorization/recovery, and real HTTP checkout-to-history/cancellation flow. Application-facing media errors remain415; receipt, cart/catalog lock and recognized database conflicts remain409 as specified below. No additional endpoint or workflow was introduced.

## Access and input

Existing Bearer/current-state identity applies before controller validation. Require MANAGE_CHECKOUT; existing/future USER role membership inherits the grant, custom roles need explicit grant. No additional MANAGE_CART/MANAGE_ADDRESS/MANAGE_ORDER required for checkout or receipt replay. Existing order read/cancel endpoints retain MANAGE_ORDER. ROLE_ADMIN alone grants no bypass.

Exactly one Idempotency-Key header value, regex `^[A-Za-z0-9_-]{16,128}$`, case-sensitive, no trimming; reject missing/empty/repeated/comma-combined/invalid ASCII/whitespace values400. Same exact key may be used independently by different customers. HTTP header name is case-insensitive; key value is not.

Required nonempty JSON object containing exactly one addressId: a JSON integer number1..9223372036854775807. Reject omitted/null/empty/string/boolean/array/fraction/exponent/nonpositive/overflow values400. Reject duplicate/unknown properties, trailing JSON, top-level null/nonobject, empty body and any query parameter400. No owner/cart ID/items/prices/totals/status/payment/voucher input. JSON syntax whitespace is permitted; key-value whitespace is not normalized. Accept application/json and compatible application/*+json; missing/incompatible Content-Type415 for a supplied body. Missing/zero-byte required body400 even without Content-Type. Nonempty whitespace under accepted JSON media type400.

## Success and receipt

First committed success and every successful replay return201 Created, Content-Type application/json, Location `/api/me/orders/<orderId>`, Cache-Control `private, no-store`. Location is exposed to cross-origin browser clients; preserve existing Authorization exposure. All errors also retain private,no-store, including security-filter failures (existing CartCacheFilter path coverage).

Top-level JSON has exactly order and skippedItems:

| Field | Type/meaning |
| --- | --- |
| order.id | Positive Long, created order ID |
| order.orderNumber | Immutable unique server number |
| order.placedAt | UTC ISO-8601 Instant captured at creation |
| order.status | Always original PENDING in this receipt |
| order.recipientNameSnapshot | Original saved-address recipient |
| order.recipientPhoneSnapshot | Original saved-address phone |
| order.deliveryAddressSnapshot | Original full saved address text |
| order.items | Complete nonempty list, generated item ID ascending |
| order.merchandiseTotal | Stored exact money object |
| order.payableTotal | Same exact amount as merchandiseTotal |
| skippedItems | Array sorted by cartItemId ascending, empty when none |

Each order.items entry has id (generated Long), productNameSnapshot, variantSkuSnapshot, variantOptionsSnapshot (flat string map, possibly empty), quantity(integer1–999), unitPriceSnapshot and lineTotal. No current product/variant references, image URL, cancelledAt/completedAt or mutable status metadata. Durable item/order identities and historical fields are sufficient to navigate current detail without storing live metadata. Each money object has amount (nonnegative JSON integer) and currency exactly VND. Never truncate to JavaScript-safe integer or long; clients must preserve exact JSON amounts.

Each skipped entry has cartItemId (positive Long) and reason. Codes, first-applicable precedence:

1. VARIANT_UNAVAILABLE: variant absent or cannot resolve parent mapping.
2. PRODUCT_UNAVAILABLE: known parent ID absent.
3. PRODUCT_NOT_FOR_SALE: parent exists but not PUBLISHED.
4. INVALID_QUANTITY: stored quantity not an integer1–999.
5. INVALID_PRICE: current price missing/nonfinite/negative/fractional.

One reason per skipped selected row; do not disclose hidden name/SKU/options/image. Unselected rows are neither ordered nor reported. No eligible selected rows return422 and no receipt; its ProblemDetail may contain skippedItems using exactly the same safe entries (empty for empty/no-selected cart). No failed outcome is saved for replay.

## Replay and processing

After current access and input validation, query the exact owned committed successful receipt first. At every receipt hit, compare canonical addressId and fingerprint: matching command returns original201/Location/immutable body; different command returns409 CHECKOUT_KEY_CONFLICT. If the first lookup misses, try the nonblocking scoped transaction fence. After acquisition, perform a fresh committed-receipt lookup before any checkout work; a hit resolves exactly as above. If acquisition fails, perform a fresh committed-receipt lookup before any processing response: return the found matching receipt as201, or the established command-mismatch409. Return409 CHECKOUT_IN_PROGRESS only after a failed fence attempt and no committed receipt visible on this recheck. Only a fence holder with no successful receipt may execute checkout. All lookups remain customer/operation/key scoped and precede mutable cart/address/catalog evaluation. Key/fingerprint never contain mutable cart state, address contents, prices or optional images.

Receipt lifetime equals order lifetime; no age-based expiry, recycling or replay repricing. After cancellation the receipt still says PENDING while current detail says CANCELLED. New cart rows, address edits/deletion and catalog disappearance do not invalidate replay. Failed validation/rollback reserves no key; later valid attempts may reuse it with a changed command. Concurrent completed replays return201 without taking a mutation fence. Pending requests receive processing409 only when the busy-path committed-receipt recheck is empty; a receipt committed before that recheck is returned as201. No repeated checkout, cart consumption, stock/voucher call or mutation occurs on replay; excluded stock/voucher implementations remain absent. Different keys still serialize cart consumption and cannot consume the same removed row twice. No automatic server retries or promised Retry-After interval.

## Errors

All errors use established application/problem+json ProblemDetail, safe message/code/path, no stack/SQL/credentials or foreign/hidden data. Access filtering precedes controller evaluation; within controller validate query/key/body before transaction; replay precedes current-address checks; first-time address ownership precedes eligibility.

| HTTP | Code / behavior |
| --- | --- |
| 400 | INVALID_CHECKOUT_REQUEST: missing/malformed header/query/body/address format; no mutation/key reservation |
| 401 | Existing invalid bearer/account/session/identity behavior, including replay |
| 403 | Existing insufficient grant behavior, including replay |
| 404 | Existing owned-address safe not-found behavior; unknown/foreign identical |
| 409 | CHECKOUT_IN_PROGRESS: keyed work busy; client may retry |
| 409 | CHECKOUT_KEY_CONFLICT: successful key/address command mismatch |
| 409 | CHECKOUT_CONFLICT: recognized lock timeout/deadlock/conditional consume conflict, catalog-parent race, receipt uniqueness or order-number collision; rollback and client-directed retry |
| 415 | Existing unsupported JSON request media type behavior on this cart-prefixed path |
| 422 | NO_ELIGIBLE_CHECKOUT_ITEMS: missing/empty cart, no selection or all selected ineligible; optional safe skippedItems |
| 422 | CHECKOUT_AMOUNT_LIMIT: finite integral computed amount exceeds supported representation/storage bound |
| 500 | Existing safe unexpected persistence/infrastructure behavior; never convert arbitrary failure into skip/conflict |

Existing more-specific dependency-unavailable statuses remain applicable where actually thrown; checkout performs no storage/network calls and invents no503 path. Do not map every unique violation to409: recognize only named feature constraints; unexpected integrity failures500. Security errors retain their existing code conventions rather than inventing checkout-specific authentication codes.

## Compatibility

Current GET `/api/me/orders`, PENDING filtering/detail and cancellation unchanged. Created orders are immediately readable after commit subject to their own current grant checks. No confirm-receipt, preview, creation override, stock, voucher, payment or shipping route. Cancellation changes neither cart nor receipt and performs no refund/release.
