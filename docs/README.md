# API documentation

[openapi.json](openapi.json) documents the current source at `feature/voucher` commit `f7d1f7e` as OpenAPI 3.1.0: **52 controller operations and 2 Google framework operations across 48 paths**, reviewed 2026-11-20. It includes auth/email/Google, profiles, addresses, catalog/search, product images, media, wishlist, behavior tracking, Inventory availability, Cart, Checkout, Order history/cancellation and owned Voucher wallet. Examples are synthetic.

## Use the contract

Import the JSON into an OpenAPI 3.1 client/editor. The server `/` means the backend origin; choose the actual API base URL in your client (local default `http://localhost:8080`, Docker host port may differ).

1. POST `/api/auth/register` with email/password returns 202 pending metadata, not tokens. Retain registrationCredential/challengeId and obtain the emailed code. Confirm at `/api/auth/registration-verifications/confirm` with the current proof and authoritative password to create the verified account and receive tokens. Immediate resend rotates credential/code.
2. Alternatively log in with email or a legacy username/password. Read the access JWT from the **Authorization response header**; refresh metadata is JSON. Supply bearer JWT on protected calls. Refresh rotates a single-use credential; replay can revoke its session. Logout uses the refresh body.
3. Google LOGIN starts an attempt with frontend S256 PKCE. Follow authorizationUrl in the same browser, retain its verifier, and exchange the 60-second application handoff. Existing-email matches require explicit authenticated Connect Google; never auto-link. LINK needs fresh CONNECT_GOOGLE ownership proof, original active bearer session and separate Google identity proof.
4. Serialize every Google initiation across purposes/tabs until the previous response settles. The five-minute google_handshake cookie only correlates browser state. Callbacks never modify/delete it; server consumption rejects replay. Missing/stale cookie requires settled requests and a fresh serialized start.
5. Upload an image by creating an upload, PUT raw bytes with the declared Content-Type to its signed storage URL without application Authorization, then call completion. Signed downloads last at most five minutes. Attach an owned completed image to a profile or, as ROLE_ADMIN, a product gallery.

6. Cart requests require MANAGE_CART. Add a canonical productVariantId and quantity 1..999; the server checks the complete resulting quantity against stock minus reserved. Select individual or eligible bulk rows. Reads/mutations calculate exact eligible subtotals; unavailable rows remain visible and do not partially contribute.
7. Browse `/api/me/vouchers` with any eligible authenticated identity. Each administrator-granted walletEntitlementId is one use; apply that ID through PUT `/api/me/cart/voucher`. No code claiming or public discovery exists. Preview spends nothing; invalid selections are cleared on a successful Cart evaluation with a reason. Wallet browsing itself never cleans the Cart.
8. Checkout with addressId and exactly one Idempotency-Key header matching `[A-Za-z0-9_-]{16,128}`. MANAGE_CHECKOUT alone suffices. New execution rechecks stock and selected Voucher after locks, reserves Inventory, spends only the selected grant, persists one order and immutable receipt, and clears only represented rows/selection atomically. Any failure rolls back all. A retry with the same address command recovers the original 201/Location/receipt, even after current state changes. A different address conflicts. New receipts include Voucher fields; legacy replies do not gain them.
9. Read your stored order history with MANAGE_ORDER. Summary previews show the first 3 distinct historical rows and remaining count; sorting uses stored payable totals. PENDING/CONFIRMED orders can cancel; repeated CANCELLED calls return 204. SHIPPING/COMPLETED/FAILED cancellation conflicts. No customer shipping-to-completed/received/failed API exists.

No endpoint request quotas, resend cooldowns or failed-proof lockouts apply. Authentication/authorization, expiry, supersession, one-use proof and business conflicts still apply. Optional initial-password setup for Google-only accounts requires fresh linked-Google proof and revokes every session; existing passwords change through PUT `/api/auth/password`.

## Operations

| Method | Path | Access | Success |
| --- | --- | --- | --- |
| POST | `/api/auth/register` | Public | 202 |
| POST | `/api/auth/login` | Public | 200 |
| POST | `/api/auth/refresh` | Public | 200 |
| POST | `/api/auth/logout` | Public | 204 |
| PUT | `/api/auth/password` | Bearer | 204 |
| GET | `/api/me` | Bearer + MANAGE_USER_PROFILE | 200 |
| PUT | `/api/me` | Bearer + MANAGE_USER_PROFILE | 200 |
| GET | `/api/users/{id}` | Bearer | 200 |
| GET | `/api/me/addresses` | Bearer + MANAGE_ADDRESS | 200 |
| POST | `/api/me/addresses` | Bearer + MANAGE_ADDRESS | 201 |
| GET | `/api/me/addresses/{id}` | Bearer + MANAGE_ADDRESS | 200 |
| PUT | `/api/me/addresses/{id}` | Bearer + MANAGE_ADDRESS | 200 |
| DELETE | `/api/me/addresses/{id}` | Bearer + MANAGE_ADDRESS | 204 |
| POST | `/api/auth/registration-verifications/confirm` | Public | 201 |
| POST | `/api/auth/registration-verifications/resend` | Public | 202 |
| POST | `/api/me/email-verification-challenges` | Bearer + MANAGE_USER_PROFILE | 201 |
| GET | `/api/me/email-verification-challenges/{id}` | Bearer + MANAGE_USER_PROFILE | 200 |
| POST | `/api/me/email-verification-challenges/{id}/confirm` | Bearer + MANAGE_USER_PROFILE | 200 |
| POST | `/api/me/reauthentications` | Bearer + MANAGE_USER_PROFILE | 200 |
| POST | `/api/me/password` | Bearer + MANAGE_USER_PROFILE | 204 |
| GET | `/api/me/google-connection` | Bearer + MANAGE_USER_PROFILE | 200 |
| POST | `/api/auth/google-login-attempts` | Public | 201 |
| POST | `/api/me/google-connection-attempts` | Bearer + MANAGE_USER_PROFILE | 201 |
| POST | `/api/me/google-reauthentication-attempts` | Bearer + MANAGE_USER_PROFILE | 201 |
| POST | `/api/auth/google-code-exchanges` | LOGIN public; LINK/REAUTH original Bearer + MANAGE_USER_PROFILE | 200 |
| GET | `/oauth2/authorization/google` | Browser correlation cookie + Google provider flow | 302 |
| GET | `/login/oauth2/code/google` | Browser correlation cookie + Google provider flow | 302 |
| POST | `/api/image-uploads` | Bearer | 201 |
| POST | `/api/image-uploads/{imageId}/complete` | Bearer | 200 |
| GET | `/api/image-uploads/{imageId}/download-url` | Bearer | 200 |
| GET | `/api/products` | Public | 200 |
| GET | `/api/products/{id}` | Public | 200 |
| POST | `/api/products/{id}/images` | Bearer + ROLE_ADMIN | 201, 200 |
| GET | `/api/me/wishlist` | Bearer + MANAGE_WISHLIST | 200 |
| POST | `/api/me/wishlist/{productId}` | Bearer + MANAGE_WISHLIST | 201, 200 |
| DELETE | `/api/me/wishlist/{productId}` | Bearer + MANAGE_WISHLIST | 204 |
| POST | `/api/me/behavior/events` | Bearer + ROLE_USER; excludes ROLE_ADMIN | 201, 200 |
| GET | `/api/me/behavior` | Bearer + ROLE_USER; excludes ROLE_ADMIN | 200 |
| GET | `/api/admin/behavior/overview` | Bearer + ROLE_ADMIN | 200 |
| GET | `/api/me/cart` | Bearer + MANAGE_CART | 200 |
| POST | `/api/me/cart/items` | Bearer + MANAGE_CART | 200 |
| PUT | `/api/me/cart/items/{itemId}/quantity` | Bearer + MANAGE_CART | 200 |
| PUT | `/api/me/cart/items/{itemId}/variant` | Bearer + MANAGE_CART | 200 |
| PUT | `/api/me/cart/items/{itemId}/selection` | Bearer + MANAGE_CART | 200 |
| DELETE | `/api/me/cart/items/{itemId}` | Bearer + MANAGE_CART | 204 |
| PUT | `/api/me/cart/selection` | Bearer + MANAGE_CART | 200 |
| PUT | `/api/me/cart/voucher` | Bearer + MANAGE_CART | 200 |
| DELETE | `/api/me/cart/voucher` | Bearer + MANAGE_CART | 204 |
| GET | `/api/me/vouchers` | Bearer | 200 |
| GET | `/api/me/vouchers/{walletEntitlementId}` | Bearer | 200 |
| GET | `/api/me/orders` | Bearer + MANAGE_ORDER | 200 |
| GET | `/api/me/orders/{orderId}` | Bearer + MANAGE_ORDER | 200 |
| POST | `/api/me/orders/{orderId}/cancel` | Bearer + MANAGE_ORDER | 204 |
| POST | `/api/me/cart/checkout` | Bearer + MANAGE_CHECKOUT | 201 |

## Boundaries and availability

Permissions are current database authorities, not OAuth scopes. Media operations require an eligible owner, not MANAGE_USER_PROFILE. Address and wishlist operations require MANAGE_ADDRESS and MANAGE_WISHLIST respectively. Product attachment requires ROLE_ADMIN and ownership of its completed source upload. Catalog reads are public but a supplied bearer is still validated.

Verification controllers require `security.verification.enabled=true`; Google controllers/framework routes require `security.identity.enabled=true` and configured verification keys. Public registration routes return 503 if verification is disabled. Conditional routes can be rejected by fallback security while disabled; do not assume 404. Secrets, Google/SMTP credentials and real frontend origins/destinations remain deployment configuration.

Profile PUT preserves omitted fields. Any supplied email, including null/blank, fails EMAIL_VERIFICATION_REQUIRED; use destination proof and fresh account authentication. profileImageId omission preserves, null clears and canonical UUID selects owned completed media. Private profile adds emailVerified/hasLocalPassword; both profile shapes include nullable profileImageUrl.

Errors have actual domain shapes: catalog plain RFC 9457 ProblemDetail, identity ProblemDetail with stable code, and legacy ApiError with timestamp/message/path/fieldErrors. Do not require extensions across every handler. CORS/framework/storage responses may differ. Shared CORS allows wildcard origin/method/header without credentials; identity chains use exact configured origins with credentials. Authorization is exposed in both; shared CORS also exposes Checkout Location.

Springdoc is installed, but current deny-all fallback protects Swagger UI and `/v3/api-docs`; this standalone document does not alter runtime access. OPTIONS is infrastructure CORS handling, not another controller operation. Storage signed PUT/GET are external capability calls, not extra backend routes.

## Inventory, discount and history semantics

Catalog variant availability exposes availableQuantity and IN_STOCK / OUT_OF_STOCK / INVENTORY_UNAVAILABLE. Listing availability belongs to the deterministic representative variant; it is null together with a null representative/display price for retained orphan results. Inventory is observational outside Checkout and never means unlimited stock. No new public stock-admin/reservation endpoint exists.

Cart finalTotal is eligible selectedSubtotal minus voucherDiscount. Voucher minimum spend uses the pre-discount included subtotal. Fixed/percentage discounts apply once at order level, with exact whole-VND floor/caps and no item repricing. Positive base may round to 0 and still consume the chosen grant; zero base cannot redeem. Definition→usage→grant locks precede the authoritative purchase event, including exclusive-end expiry checks after a lock wait.

Order history explains immutable original terms without fetching current Voucher definitions. Legacy orders expose 0/null added fields while retaining original amounts; old successful receipt bodies remain unchanged. Cancellation releases managed Inventory exactly once and does not rewrite the receipt. Voucher cancellation-quota restoration/reissue policy, admin granting/stock supply, payment and new shipping/completion/FAILED producers remain deferred.

## Verification

Run `python docs/validate_openapi.py` from the repository root. [openapi-validation.json](openapi-validation.json) records the current source/structure check: **52 controller + 2 framework operations, 48 paths, 91 schemas, 480 resolved local references, 68 current Java DTO shapes and 83 checked examples**, with 0 errors. It also checks unique operation IDs, path parameters, security scheme references, required/property consistency and bodyless 204 responses. DTO comparison excludes deliberate unwritable profile presence flags and the always-rejected email setter.

These are standard-library source/structural checks and a documented JSON Schema assertion subset, not external OpenAPI conformance validation. Existing SnakeYAML 2.6 from the Maven cache was used only to read the established search/behavior contracts; no dependency or tool was installed. No application source, runtime security, build configuration or application tests were changed/run for this documentation-only update. Historical feature contracts/evidence are preserved. Only these API documentation/validation files are allowlisted under the otherwise ignored docs directory. No commit or push is performed by this documentation update.
