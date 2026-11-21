# Product Review HTTP Contract

Approved implementation contract. Q1–Q3 govern content, deletion exclusion and visibility. The user explicitly approved all remaining proposals on 2026-11-21, including P-01–P-05, MANAGE_ORDER, update/error semantics, media fallback and active-author summary. No policy approval gate remains.

## Common rules

Base: `/api`. JSON success bodies; RFC 9457 `application/problem+json` failures using existing error factory conventions. UUID parameters use canonical lower-case UUID form; numeric IDs are positive signed 64-bit integers. Absolute timestamps are UTC ISO-8601. No response exposes author ID, purchase IDs, private account data, object keys or upload credentials. No endpoint accepts authorId/productId/orderItemId as authoritative body fields; reject unknown properties.

Both public GETs accept requests without credentials. A supplied invalid/inactive Bearer token yields existing 401 behavior. Return `Cache-Control: no-store` on review/summary success responses and avoid caching identity-bearing results. Mutation requests require current ACTIVE account, approved MANAGE_ORDER permission and owned purchase/review; authentication/permission checks precede owned-resource lookup. No privileged ownership bypass.

## GET /products/{productId}/reviews

PUBLISHED product: 200; missing/DRAFT/ARCHIVED/removed: 404, including anonymous requests. Only these parameters are supported:

| Parameter | Default | Allowed |
| --- | --- | --- |
| page | 0 | Nonnegative integer |
| size | 20 | Integer 1–100 |
| sort | createdAt,desc | createdAt,asc; createdAt,desc; rating,asc; rating,desc |

One occurrence per parameter. Reject unknown parameters, malformed/overflowing values and repeated keys with 400. Always append review id DESC as final sort. Pagination stability applies only to an unchanged dataset. No rating filter or distribution. Out-of-range pages return 200 with empty items and actual totals. No-review totalPages is 0.

Example response shape (placeholder UUIDs illustrate types):

```json
{
  "items": [{
    "id": 71,
    "rating": 5,
    "comment": "Accurate description",
    "images": [{"id": "11111111-1111-4111-8111-111111111111", "finalUrl": null}],
    "author": {"displayName": "Former customer", "profileImage": null},
    "createdAt": "2026-11-20T03:00:00Z",
    "updatedAt": "2026-11-20T03:00:00Z",
    "verifiedPurchase": true
  }],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

`images` is always an ordered array, including []; unavailable entries retain ID and position with finalUrl null. Healthy finalUrl is an ephemeral current-media download URL; never durable input identity. Signing alone does not guarantee object existence. Author has only displayName and nullable profileImage `{id, finalUrl}`. Active-author display uses nonblank displayName, otherwise username. Inactive authors (INACTIVE/SUSPENDED/BANNED) always use “Former customer” and null profileImage, with no status or original identity fields. Missing author data defensively uses the same anonymous summary rather than exposing a raw ID.

## GET /products/{productId}/rating-summary

No query parameters. Same visibility/authentication rules as listing. 200:

```json
{"reviewCount": 3, "averageRating": 3.33}
```

No reviews: `{"reviewCount":0,"averageRating":null}`. Average is a JSON number rounded HALF_UP to two decimals; trailing zeros are not semantically required. All committed product reviews count, including repeated completed purchases and inactive authors, regardless of page/sort. No shop summary/distribution fields.

## POST /me/orders/{orderId}/items/{orderItemId}/reviews

Example body:

```json
{"rating": 5, "comment": "  Useful purchase  ", "imageIds": []}
```

- rating: required integer 1–5; missing/null/fractional/string/boolean invalid (400), including coercible strings.
- comment: optional plain-text string or null. Absent/null/blank becomes null. Apply Java strip semantics at both edges; count Unicode code points afterward; maximum 2,000. Reject longer content with 400, never truncate. Embedded markup remains plain text.
- imageIds: optional array of 0–10 distinct UUID strings; omission becomes []; explicit null, null elements, duplicate IDs or malformed UUIDs are 400. Submitted order is retained.
- Owned item must belong to the path order and current actor; only COMPLETED is eligible, even if quantity >1. PENDING/CONFIRMED/SHIPPING/CANCELLED/FAILED are 409. Another completed purchase of the same product can have its own review. No product-publication requirement on creation; missing historical product identity is 409.
- Every image must be completed and owned by the actor. Unknown/foreign 404; pending/expired 409; mutation storage unavailable 503. Validate the complete collection atomically.

Success: 201, review body as above (without page wrapper), `Location: /api/me/reviews/{reviewId}`. Location identifies the owned resource targeted by PATCH; no GET-by-ID endpoint is added. A duplicate request is 409 and does not replay 201; exactly one concurrent create may commit. No idempotency-key requirement.

## PATCH /me/reviews/{reviewId}

Only author may patch; missing/foreign review is indistinguishable 404. No editing time limit. Product unpublication/removal does not prevent an existing review edit. Use immutable purchase proof and validate its owner/completed status without requiring the deleted product link to be populated.

| Field | Omitted | null | Empty/blank | Explicit value |
| --- | --- | --- | --- | --- |
| rating | Preserve latest | 400 | Invalid | Replace, integer 1–5 |
| comment | Preserve latest | Clear | Clear | Normalize and replace, max 2,000 code points |
| imageIds | Preserve latest | 400 | [] clears references | Replace entire ordered collection |

Reject {} and unknown properties with 400. Effective changes advance updatedAt; no-op succeeds without changing it; createdAt/author/purchase/product immutable. 200 returns resulting review. All fields and reference changes commit together; invalid images/content preserve previous state/statistics. Concurrent patches serialize against latest state; later explicit same-field values win, omitted fields preserve latest values. A lock timeout/deadlock reports 409 without an automatic retry. No ETag/version precondition is added.

## Failures

| Status | Cases |
| --- | --- |
| 400 | Shape/query/identifier/normalization/length/rating/image-list validation; unknown fields; empty patch |
| 401 | Missing mutation credentials, invalid credentials, inactive-account credentials or in-transaction inactive actor |
| 403 | Authenticated actor lacks confirmed mutation permission |
| 404 | Unavailable public product, non-owned/missing order/item/review, mismatched path order/item, unknown/foreign image |
| 405 | Unsupported HTTP method if the request reaches MVC; no review DELETE mapping exists |
| 409 | Duplicate review, ineligible order state, irrecoverable product identity on creation, incomplete/expired image, lock conflict |
| 415 | Unsupported mutation request content type |
| 503 | Required attachment object storage unavailable; no review write occurs |

Separate upload completion retains existing 422 invalid-binary-content responses. Do not relabel that as review shape validation. Read-time unavailable media uses safe null URLs; unexpected database/programming failures still fail normally rather than being swallowed. Errors must not reveal SQL, stack traces, storage keys or foreign-owner identity.

Review deletion is excluded: unmatched DELETE requests remain denied by the existing security fallback (401 without valid credentials, 403 with valid credentials); do not add an allow rule to force MVC dispatch. No DELETE mapping, no account-deactivation cascade, and no deletion of objects on reference removal. Do not introduce an authenticated GET-by-ID solely for Location, or other new endpoints outside the four responsibilities.
