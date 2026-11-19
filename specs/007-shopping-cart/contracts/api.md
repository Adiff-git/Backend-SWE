# Shopping Cart HTTP contract

Updated 2026-11-17. Authenticated eligible identity plus MANAGE_CART required on every route. Owner is resolved server-side; clients never send owner, price or totals. Responses/errors use private, no-store; errors use existing ProblemDetail. Missing/invalid bearer is 401, denied permission/unsupported methods are 403 before MVC. Authorized supported routes can return 415 for wrong media type.

| Method/path | Exact JSON body | Success |
| --- | --- | --- |
| GET /api/me/cart | none | 200 CartResponse; absent cart does not insert |
| POST /api/me/cart/items | {productVariantId,quantity} | 200 CartResponse for both new and existing rows; no Location |
| PUT /api/me/cart/items/{itemId}/quantity | {quantity} | 200 CartResponse |
| PUT /api/me/cart/items/{itemId}/variant | {productVariantId} | 200 CartResponse |
| DELETE /api/me/cart/items/{itemId} | none | 204, no body |
| PUT /api/me/cart/items/{itemId}/selection | {selected} | 200 CartResponse |
| PUT /api/me/cart/selection | {selected} | 200 CartResponse; missing cart remains absent |

POST is a documented business-action exception to the constitution's resource-creating POST default: it creates OR increments, always returning 200. It is not idempotent and no automatic client retry/idempotency-key protocol is implied. The former combined PUT /items/{itemId} is removed and denied by the route allowlist. Legacy input variantId is not an alias for productVariantId.

Bodies must be objects with exactly the listed fields, all required and nonnull. productVariantId is a canonical lowercase UUID string; quantity is a literal integer token 1..999 (no strings/fractions/exponents); selected is literal true/false. Reject duplicate/unknown fields, owner/price overrides, trailing JSON and malformed/missing bodies. GET/DELETE reject nonempty bodies; all routes reject query parameters. Malformed Long path IDs return 400; nonpositive/unmatched/foreign owned IDs return the same safe 404 CART_ITEM_NOT_FOUND.

Quantity PUT replaces only quantity and retains variant/selection. Variant PUT has no quantity: absent target resets source quantity to 1 while retaining identity/selection/time; existing target remains entirely unchanged and source is removed; current variant is a no-op. Validate eligible same-product target before mutation. Example A=8 selected, B=4 unselected: A to B leaves only B=4 unselected. Cross-product is 422; hidden/missing target 404; invalid published price 422.

## CartResponse

items is the complete collection, addedAt DESC then id ASC, with no pagination/cap. Each item includes id, productId, variantId, productName, sku, options, primaryImageUrl, unitPrice, quantity, selected, purchasable, availabilityReason and subtotal. Output variantId remains the item identifier field; request productVariantId is explicit in the request schemas above.

totalItemCount counts every row. selectedItemCount counts selected purchasable rows; selectedTotalQuantity sums their units. selectedSubtotal sums their line amounts; finalTotal equals it. Money is {amount,currency:"VND"}, with exact arbitrary-precision whole amounts. Empty cart has items=[] and all counts/amounts zero. A response with two rows may have totalItemCount=2 and selectedItemCount=1.

Published descriptions/prices are current. Hidden rows use prior-public name/SKU/options snapshots and no image; stored selected can remain true while ineligible. availabilityReason is PRODUCT_UNAVAILABLE before PRICE_UNAVAILABLE; eligible reason is null. Invalid price yields null unitPrice; ineligible subtotal is null. No stock guarantee, fees or locked future prices. Explicit unavailable selection is 422; deselect/delete allowed; bulk true preserves unavailable preferences and selects only eligible rows; bulk false clears all.

Expected errors: 400 shape/direct quantity; 401 authentication/identity; 403 access; 404 safe owned source or hidden/missing target; 409 actual lock/deadlock/known persistence conflict after rollback; 422 final added quantity overflow, cross-product, invalid-price/unavailable selection; 503 preparation/signing dependency; unexpected faults remain safe 500. Every rejected mutation leaves state unchanged. Image credentials prepare outside locks; local signing/conversion finish before commit and actual failures roll back.
