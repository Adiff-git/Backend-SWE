# Inventory Integration HTTP Contracts

Binding design for [spec.md](../spec.md). These implemented deltas extend existing Cart/Catalog/Checkout/Order routes without new Inventory endpoints; see [execution evidence](../implementation-validation.md). Earlier feature contracts remain in force except the inventory/status extensions stated here.

## 1. No new route or customer permission

Public catalog reads remain public/PUBLISHED-only. Existing cart routes require current bearer identity and MANAGE_CART; checkout requires MANAGE_CHECKOUT without additional cart/address/order/inventory grant; owned order routes keep MANAGE_ORDER. ROLE_ADMIN alone does not bypass customer ownership. Protected unknown/foreign identifiers remain indistinguishable404. Private cart/order/checkout responses/errors retain private,no-store; Location/CORS behavior remains unchanged.

No inventory read/edit route, admin stock-loading interface, receipt-confirmation route, generic status setter, SHIPPING-to-FAILED action, refusal/payment callback or expiry scheduler is added. [Internal completion](internal-services.md) is not an externally callable HTTP operation and grants no new public ability.

## 2. Additive availability representation

Add an always-present nullable availability property only to:

- ProductListingItem, for its existing representativeVariantId;
- VariantSummary in product detail, for that variant;
- CartItemResponse, for its current row variant when catalog publication permits disclosure.

Do not add it to shared ProductSummary, wishlist entries, order summaries/details or durable checkout receipts.

```json
{
  "availability": {
    "availableQuantity": 7,
    "status": "IN_STOCK"
  }
}
```

| Case | availability |
| --- | --- |
| Published known balance with available>0 | Object; exact nonnegative JSON integer, status IN_STOCK |
| Published known balance with available=0 | Object;0, OUT_OF_STOCK |
| Published variant with missing balance | Object;0, INVENTORY_UNAVAILABLE; not a claim of known physical zero |
| Hidden/missing product or variant on retained cart row | null; use existing safe prior-public snapshots/PRODUCT_UNAVAILABLE behavior |
| Listing compatibility case with no representative variant | null; keep the existing null representative/display-price pair |

availableQuantity range0..9223372036854775807, represented exactly as a JSON integer, never rounded/narrowed. Consumers must not assume a JavaScript-safe integer range. No stock/reserved counters, allocation IDs, owner identities or editable availability are exposed. Every object uses one observed balance; no cross-request or cross-batch snapshot/future stock guarantee.

Out-of-stock published products/variants remain visible with approved public metadata/images. Stock never changes existing product membership, search ranking, filters/sorts, representative selection or pagination. A listing's chosen variant may be out of stock while another is in stock; do not substitute a different variant. Price/publication errors retain their current semantics.

## 3. Cart mutations and rendering

Existing routes and request shapes remain unchanged:

| Operation | Stock validation |
| --- | --- |
| POST /api/me/cart/items | Validate the widened accumulated final quantity, not just added quantity |
| PUT /api/me/cart/items/{id}/quantity | Validate full replacement quantity |
| PUT /api/me/cart/items/{id}/variant | Absent target quantity1; existing target unchanged quantity; same-variant unchanged quantity, then valid no-op |
| PUT /api/me/cart/items/{id}/selection with selected=true | Validate full stored quantity, even if already selected |
| PUT /api/me/cart/selection with selected=true | Select only fully eligible rows; preserve every ineligible stored preference |
| Individual/bulk selected=false or removal | No stock eligibility barrier; preserve existing ownership/input checks |

All actions are non-reserving. They never change stock/reserved or create allocations. An accepted operation checks current observed availability, not stock promised until checkout. Only add, quantity replacement, variant change and individual selected=true recheck the affected target's full resulting quantity in their final prepared response inside the cart transaction; a discovered shortage rejects/rolls back that mutation. This final stock409 recheck never applies to bulk true, deselection, removal or GET. Bulk true remains partial-success selection of eligible rows. A later independent stock change may still make a row unavailable; reads never silently lower quantity, remove rows or change selection.

Keep quantity1..999, owner checks and catalog privacy/reason precedence. Invalid direct quantity shape/range stays400; accumulated result>999 stays422 CART_QUANTITY_LIMIT; hidden/missing target404, invalid published price422 and cross-product switch422 remain their established outcomes. After these validations, missing balance or insufficient full stock yields409 CART_STOCK_CONFLICT with safe detail. Do not expose stock internals or turn that shortage into the existing price/unavailability422. A rejection changes neither row, target/source identity/time/snapshots nor balances.

Bulk true is a successful partial selection update, not whole-cart409 for a shortage; already-selected shortage preferences may remain true but are excluded from purchasable totals. False and removal remain usable for unavailable/hidden rows.

Row rendering preserves existing fields and adds availability. Row-specific eligibility is prepared before conversion:

| Row condition | purchasable | availabilityReason | unitPrice | subtotal | Selected totals |
| --- | --- | --- | --- | --- | --- |
| Catalog valid; full quantity<=available | true | null | Current valid VND price | Exact unitPrice x quantity | Include iff stored selected=true |
| Catalog unavailable/hidden | false | Existing PRODUCT_UNAVAILABLE | Existing safe behavior | null | Exclude |
| Published invalid price | false | Existing PRICE_UNAVAILABLE | Existing safe behavior | null | Exclude |
| Catalog/price valid; balance missing | false | INVENTORY_UNAVAILABLE | Keep valid current price | null | Exclude |
| Catalog/price valid; quantity>available, including0 stock | false | INSUFFICIENT_STOCK | Keep valid current price | null | Exclude |

IN_STOCK merely means positive observed units; a row requesting5 can have availability.status=IN_STOCK at3 but purchasable=false/INSUFFICIENT_STOCK. totalItemCount still counts all retained rows. Selected counts, selected quantity and subtotal/finalTotal include only fully purchasable selected rows; finalTotal remains selectedSubtotal, no fees/discounts. No partial subtotal for a partially fulfillable row.

## 4. Checkout

POST /api/me/cart/checkout retains required one strict Idempotency-Key and JSON addressId-only command, existing validation/media/current identity policy and exact VND snapshot rules. No new owner/items/stock/prices/status/payment input.

On a genuinely new attempt: current catalog eligibility precedes stock eligibility; include a selected row only if its entire quantity can be atomically reserved. Increase reserved only; physical stock remains unchanged. One managed PENDING order and complete owned item allocations commit with included cart-row removal and immutable successful receipt. A failure commits none of them.

Extend existing safe skipped reason enum with INVENTORY_UNAVAILABLE and INSUFFICIENT_STOCK after variant/product/publication/quantity/price causes. One first-applicable reason per skipped row, sorted by cart-item ID as before. Preserve skipped/unselected stored fields. No eligible/fully reservable rows yields422 with safe skippedItems and no successful key/empty order.

First success and successful replay remain201, same Location and immutable original version1 body, including PENDING and original skips even after cancellation/completion. No availability/counters/allocation history is stored in the receipt. Existing stock-free receipts remain decodable and are never rewritten. Receipt-first lookup, acquired-fence fresh recheck and busy-fence fresh recheck remain mandatory before any mutable-domain/Inventory work on receipt hits. Changed accepted command remains409 CHECKOUT_KEY_CONFLICT; truly unfinished original remains409 CHECKOUT_IN_PROGRESS.

A normal stock shortage is a row skip, not whole-checkout409 when another row can be reserved. After locked classification, an inconsistent mutation, lock/deadlock or known integrity conflict aborts all work with409 CHECKOUT_CONFLICT under existing error adaptation. Unknown SQL/dependency faults are not skips; preserve safe500 or existing more-specific unavailable status. Existing400/401/403/404/415/422 distinctions remain.

## 5. Order reads and cancellation

Existing owned list/detail/cancel routes retain their response shapes, pagination/sorts/preview counts/snapshots/images and current grants. Exact uppercase FAILED becomes a valid status filter/value alongside PENDING, CONFIRMED, SHIPPING, COMPLETED and CANCELLED. Invalid/repeated/blank status inputs still fail400. Reads return only owned matching rows and do not invent failedAt/reason or stock fields.

First cancellation from PENDING/CONFIRMED remains204 with optional existing reason. Managed orders release only exact ACTIVE owned allocations in the same terminal transaction; physical stock unchanged. Legacy orders cancel without Inventory mutation. Already-CANCELLED valid repeat remains204 before any second release or Clock sample, preserving the first reason/time. Authentication/ownership/input validation still applies to repeats.

SHIPPING, COMPLETED and FAILED cancellation return409 unchanged. A first pre-placement time conflict retains409. Foreign/missing remains404. Expected allocation mismatch is409 INVENTORY_CONFLICT without partial mutation; preserve existing generic recognized concurrency handling. These codes are safe and do not expose allocation ownership or SQL.

FAILED is vocabulary/read/filter compatibility only. No SHIPPING-to-FAILED transition, automatic release, failed timestamp or refusal-processing behavior follows merely from the enum addition. Future expiry/terminal-failure workflow requirements are recorded, not exposed as current routes or verified payment behavior.

## 6. Contract verification

Existing MVC/request/security and real bearer integration tests cover all route deltas. New assertions prove exact shortage vs invalid-catalog precedence, null/missing/zero availability, no source/target mutation on stock409, full-row totals, six-status ownership, unchanged old/new receipts and absence of excluded routes. Pure request parsing cannot be relaxed for wider stock quantities; cart requests remain strict1..999. Actual execution is recorded in [implementation-validation.md](../implementation-validation.md); [quickstart.md](../quickstart.md) lists reproducible gates.


## Voucher integration follow-up — feature011

Feature011 composes existing Inventory reservation with atomic Voucher purchase; it does not deduct stock early, change reservation lifecycle or add payment/admin/confirmation/FAILED transition producers. Cart estimated payable now subtracts the order-level Voucher discount from the existing eligible selected subtotal, with item and stock rules unchanged. Verified targeted deltas and the separately tracked unrestricted completion gate are recorded in [Voucher execution evidence](../../011-voucher-support/implementation-validation.md). Earlier completed tasks and their historical execution records are preserved; this follow-up adds no admin/code/public-discovery/cancellation-quota contract.
