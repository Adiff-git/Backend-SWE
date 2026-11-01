# Wishlist HTTP Contract

Require current valid Bearer authentication and MANAGE_WISHLIST for every operation. Resolve immutable ownership through CurrentUserIdProvider; no userId selector. Preserve current account/session checks and handlers.

| Method / path | Success |
| --- | --- |
| GET /api/me/wishlist?page=0&size=20 | 200 WishlistPage |
| POST /api/me/wishlist/{productId} | 201 new WishlistItem; 200 existing WishlistItem on duplicate |
| DELETE /api/me/wishlist/{productId} | 204 without body, including absent/foreign-only membership |

## Inputs

productId is canonical lowercase hyphenated UUID, as existing catalog validation requires. GET accepts page/size once each. Omission defaults to 0/20. Page is canonical unsigned decimal fitting Java int, >=0; size 1–100. Empty/text null/signed/whitespace/fractional/overflowing/duplicate values and unknown keys including sort/userId are 400. POST/DELETE accept no query keys and need no body; supplied JSON including null/{} or ownership claims is 400. HTTP shape validation belongs in the controller; applicable business/input rules belong in the domain validator. No request DTO is needed.

## Response bodies

```json
{
  "addedAt": "2026-10-30T08:30:00Z",
  "product": {
    "id": "00000000-0000-4000-8000-000000000001",
    "name": "Example product",
    "category": {"id": "00000000-0000-4000-8000-000000000002", "name": "Example category"},
    "primaryImageUrl": null
  }
}
```

WishlistPage is {items: WishlistItem[], page: integer, size: integer, totalItems: nonnegative long, totalPages: nonnegative integer}. Empty uses []/0/0; beyond-last is 200 with [] and actual totals. totalPages is ceiling(totalItems/size). Order is addedAt DESC/product UUID ASC. Count only visible owned products; totalItems is the established public field, not totalElements.

POST returns one item; duplicates preserve original time. Hidden additions return 404 even if previously saved. Re-add after removal samples a fresh addedAt, which need not be strictly greater than the removed value; equal timestamps use product UUID ASC as the deterministic tie-breaker. No rating/review, storage keys, private membership/user IDs or variant fields. Existing public catalog HTTP contracts remain unchanged.

On stable data, visibility is filtered before page/count, full pages except the final page and exact totals are required. Separate READ COMMITTED statements are not a cross-statement/cross-request snapshot. Late ineligibility of a selected product yields safe 409, never a post-filtered shortened success page or hidden disclosure.

Success bodies use application/json; errors application/problem+json. Private/capability responses use Cache-Control: no-store. Preserve existing five-minute signed catalog URL policy, including already-issued URL validity after unpublication.

## Errors

| Condition | Status |
| --- | --- |
| Missing/invalid credentials; ineligible current user/session; user disappears on insert | 401 |
| Valid eligible caller lacks MANAGE_WISHLIST | 403 |
| Invalid UUID/query/pagination/body | 400 |
| Missing/DRAFT/ARCHIVED product on add; product deletion race | 404 |
| Recognized concurrent state/lock conflict, including late page ineligibility | 409 |
| Signing/preparation unavailable | 503 |
| Unexpected persistence/infrastructure failure | 500 |

ApiError is RFC 9457 ProblemDetail: about:blank type, title/status/detail/instance plus timestamp/message/path/fieldErrors. Reuse safe common messages and security handlers; 401 retains WWW-Authenticate: Bearer. No SQL, constraint names, tokens, signed URLs or stack traces in errors. Duplicate-only races with a stable published product return 200, not 409/500. Failed operations do not partially mutate; response loss is not proof of rollback.

Existing/future USER accounts inherit MANAGE_WISHLIST; custom-role-only accounts need explicit grants even if profile/address permissions exist. Grants/revocations apply on subsequent requests using the same otherwise-valid token.
