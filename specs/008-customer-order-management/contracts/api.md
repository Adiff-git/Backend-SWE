# HTTP Contract: Customer Order Management

All routes require current-state bearer authentication and MANAGE_ORDER. Identity comes from CurrentUserIdProvider; no owner query/body input is accepted. Responses are private with Cache-Control: no-store. JSON success DTOs are direct; no generic envelope. No admin, creation, receipt-confirmation or arbitrary-status route exists.

## GET /api/me/orders

No body. Only the following query parameters, each at most once:

| Parameter | Omitted | Accepted explicit value |
| --- | --- | --- |
| page | 0 | Canonical nonnegative decimal int, <=2147483647 |
| size | 20 | Canonical decimal int, 1..100 |
| status | All statuses | PENDING, CONFIRMED, SHIPPING, COMPLETED, CANCELLED, exact case |
| sort | placedAt,desc | placedAt,asc / placedAt,desc / payableTotal,asc / payableTotal,desc |

Append id ascending as unique tie-breaker. Filter by authenticated owner and optional status before sorting/counting/paging. Reject blanks, duplicate parameters even with equal values, null-like text, unknown parameters, malformed numbers, parameter range overflow and unsupported sorting with 400. Page/size grammar is 0 or a nonzero digit followed by digits, subject to range; no signs/whitespace/leading zeroes. Count the owned/status-filtered set first as totalElements (public totalItems), then compute offset=(long)page*size, widening before multiplication. If offset >= totalElements, return 200 with items=[] and accurate requested page/size, totalItems and totalPages without executing paginated content or preview queries. A large accepted page value is not invalid merely because its offset exceeds Integer.MAX_VALUE.

For offset < totalElements, fetch the requested content. At offset <= Integer.MAX_VALUE use the normal bounded JPA pagination path; at offset > Integer.MAX_VALUE use the explicit bound long SQL OFFSET repository path, never JPA setFirstResult or an int narrowing/wrap/clamp. Preserve the identical owner/status predicates, sort allowlist, ID tie-breaker and metadata on both paths. Example empty shortcut: page=2147483647,size=100,totalElements=125 gives exact offset=214748364700, items=[],totalItems=125,totalPages=2. Synthetic nonempty fallback: page=1073741824,size=2,totalElements=2147483649 gives offset=2147483648 and totalPages=1073741825; return the selected content through the long-offset path. Counts remain a snapshot of the count statement under READ COMMITTED; concurrent changes can shift content, as already documented.

200 response:

```json
{
  "items": [{
    "id": 41,
    "orderNumber": "TEST-ORDER-41",
    "placedAt": "2026-11-01T08:00:00Z",
    "status": "CONFIRMED",
    "payableTotal": {"amount": 350000, "currency": "VND"},
    "itemCount": 4,
    "itemPreview": [{
      "id": 101,
      "productNameSnapshot": "Historical product name",
      "variantSkuSnapshot": "SKU-OLD",
      "variantOptionsSnapshot": {"color": "Blue"},
      "quantity": 2,
      "primaryImageUrl": null
    }, {
      "id": 102,
      "productNameSnapshot": "Historical accessory",
      "variantSkuSnapshot": "ACC-OLD",
      "variantOptionsSnapshot": {},
      "quantity": 1,
      "primaryImageUrl": null
    }, {
      "id": 103,
      "productNameSnapshot": "Historical cable",
      "variantSkuSnapshot": "CABLE-OLD",
      "variantOptionsSnapshot": {},
      "quantity": 1,
      "primaryImageUrl": null
    }],
    "remainingItemCount": 1
  }],
  "page": 0,
  "size": 20,
  "totalItems": 1,
  "totalPages": 1
}
```

Counts are integers: itemCount counts distinct order rows, never purchased quantity; remainingItemCount=max(itemCount-3,0). Preview is exactly min(3,itemCount) rows ordered by item ID ascending and contains only the displayed preview fields. Summary has no recipient/address, unit/line prices, full item data or cancellation metadata. Empty result: items=[], totalItems=0, totalPages=0. Beyond-last: items=[] with matching totals preserved. totalPages=ceil(totalItems/size), computed without overflow; page metadata uses int and totalItems uses long consistent with existing page DTOs. If totalPages cannot be represented, fail safely rather than wrap; no practical capacity promise beyond the accepted fixture corpus.

## GET /api/me/orders/{orderId}

Canonical positive base-10 Long ID (1..9223372036854775807), no signs/leading zeroes. No body/query parameters. Invalid syntax/range is 400; valid but unknown/unowned is the same 404.

200 response fields:

| Field | Type / rule |
| --- | --- |
| id | Positive integer order ID |
| orderNumber | Immutable unique string |
| placedAt | UTC absolute timestamp |
| status | One of the five statuses |
| recipientNameSnapshot, recipientPhoneSnapshot, deliveryAddressSnapshot | Required historical strings |
| merchandiseTotal, payableTotal | Stored money objects, amount exact whole VND integer, currency VND |
| cancelledAt, completedAt | Timestamp or explicit null according to lifecycle |
| cancellationReason | Stored string or explicit null |
| items | All complete historical rows, ID ascending |

Complete item fields: id; productId/productVariantId (UUID strings or null); productNameSnapshot; variantSkuSnapshot; variantOptionsSnapshot (string map); quantity (positive integer); unitPriceSnapshot/lineTotal (money objects); primaryImageUrl (string or null). Detail excludes preview/count fields and all payment/shipping/shop/voucher/review metadata. productId and productVariantId can independently be null. No current price/name/address value replaces a snapshot. Image absence does not omit a row.

## POST /api/me/orders/{orderId}/cancel

Same identifier grammar, no query. Success returns 204 and zero response body. Cancellation is valid from PENDING and CONFIRMED, recording CANCELLED/cancelledAt/optional reason atomically. If the Clock sample precedes placedAt on an otherwise eligible first cancellation, return 409 ORDER_CONFLICT with every field unchanged. Already-CANCELLED is classified before the clock check and returns 204 without sampling the current clock or changing anything, even if a different valid reason was submitted. SHIPPING/COMPLETED return 409. Access and shape validation apply on repeats.

| Body/field | Meaning/result |
| --- | --- |
| Absent or zero-byte body | No reason |
| {} or omitted reason | No reason |
| {"reason":null} | No reason |
| String reason | Trim with Java String.strip semantics; whitespace-only becomes no reason; count Unicode code points after stripping; maximum 500 |
| Nonblank normalized reason within 500 code points | Store once on the successful first cancellation |
| Overlong reason | 400 |
| Non-string reason except null | 400 |
| Duplicate/unknown fields, owner/status overrides | 400 |
| Top-level null/array/scalar, whitespace-only bytes, malformed/trailing JSON | 400 |
| Nonempty body with incompatible/missing JSON Content-Type | 415 |

Accept application/json and compatible application/*+json for nonempty bodies; empty body needs no Content-Type. Reject unsupported content types through the existing MVC/media-type error pipeline with safe ProblemDetail; do not silently parse arbitrary payloads.

## Errors, precedence and privacy

| Condition | Status / safe code |
| --- | --- |
| Missing/invalid token; unavailable/inactive customer/session | 401; existing authentication ProblemDetail and WWW-Authenticate: Bearer |
| Valid identity without MANAGE_ORDER | 403; existing access-denied contract |
| Invalid route input/query/body | 400 INVALID_ORDER_REQUEST |
| Unknown or foreign order after valid input | 404 ORDER_NOT_FOUND, identical message/shape |
| Owned SHIPPING/COMPLETED cancellation | 409 ORDER_CONFLICT |
| Current clock precedes placedAt on an eligible first cancellation | 409 ORDER_CONFLICT; no changes; valid CANCELLED repeats bypass this clock check |
| Lock timeout/deadlock recognized by existing infrastructure mapping | 409 safe concurrent-update outcome |
| Nonempty cancellation body with unsupported/missing JSON media type | 415 safe ProblemDetail; empty body needs no Content-Type |
| Unexpected database/infrastructure failure | 500 safe established message |

Authentication/permission checks precede controller/business access. After access checks, validate shape/identifier before owned lookup; unknown/foreign requests with valid shape always match. A malformed repeat is 400/415, not unconditional success. Errors follow existing ApiError ProblemDetail fields type/title/status/detail/instance and timestamp/message/path/fieldErrors extensions; do not introduce a mandatory code on pre-existing security/infrastructure responses. Where current global handling would incorrectly turn 415 into 500, add only the applicable safe MVC exception mapping rather than replacing the error contract. No error returns another owner's fields, SQL details or tokens.

Receipt-confirmation URLs remain unsupported by route/security configuration; do not add a special placeholder endpoint or promise a new status for unsupported URLs. Regression tests verify no transition/data changes occur. Normal offset pages may shift under concurrent changes; unchanged-data stability is the guarantee. Signed image access remains subject to existing expiry, with no immediate revocation promise.
