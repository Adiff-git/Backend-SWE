# API documentation

[openapi.json](openapi.json) documents the current working-tree API as OpenAPI 3.1.0: **34 controller operations and 2 Google framework operations**, reviewed 2026-11-05. It includes auth/email/Google, profiles, addresses, catalog, product images, media and wishlist. Examples are synthetic.

## Use the contract

Import the JSON into an OpenAPI 3.1 client/editor. The server `/` means the backend origin; choose the actual API base URL in your client (local default `http://localhost:8080`, Docker host port may differ).

1. POST `/api/auth/register` with email/password returns 202 pending metadata, not tokens. Retain registrationCredential/challengeId and obtain the emailed code. Confirm at `/api/auth/registration-verifications/confirm` with the current proof and authoritative password to create the verified account and receive tokens. Immediate resend rotates credential/code.
2. Alternatively log in with email or a legacy username/password. Read the access JWT from the **Authorization response header**; refresh metadata is JSON. Supply bearer JWT on protected calls. Refresh rotates a single-use credential; replay can revoke its session. Logout uses the refresh body.
3. Google LOGIN starts an attempt with frontend S256 PKCE. Follow authorizationUrl in the same browser, retain its verifier, and exchange the 60-second application handoff. Existing-email matches require explicit authenticated Connect Google; never auto-link. LINK needs fresh CONNECT_GOOGLE ownership proof, original active bearer session and separate Google identity proof.
4. Serialize every Google initiation across purposes/tabs until the previous response settles. The five-minute google_handshake cookie only correlates browser state. Callbacks never modify/delete it; server consumption rejects replay. Missing/stale cookie requires settled requests and a fresh serialized start.
5. Upload an image by creating an upload, PUT raw bytes with the declared Content-Type to its signed storage URL without application Authorization, then call completion. Signed downloads last at most five minutes. Attach an owned completed image to a profile or, as ROLE_ADMIN, a product gallery.

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
| POST | `/api/auth/google-code-exchanges` | LOGIN public; LINK/REAUTH original bearer + MANAGE_USER_PROFILE | 200 |
| GET | `/oauth2/authorization/google` | Browser correlation cookie + provider flow | 302 |
| GET | `/login/oauth2/code/google` | Browser correlation cookie + provider flow | 302 |
| POST | `/api/image-uploads` | Bearer | 201 |
| POST | `/api/image-uploads/{imageId}/complete` | Bearer | 200 |
| GET | `/api/image-uploads/{imageId}/download-url` | Bearer | 200 |
| GET | `/api/products` | Public | 200 |
| GET | `/api/products/{id}` | Public | 200 |
| POST | `/api/products/{id}/images` | Bearer + ROLE_ADMIN | 201, 200 |
| GET | `/api/me/wishlist` | Bearer + MANAGE_WISHLIST | 200 |
| POST | `/api/me/wishlist/{productId}` | Bearer + MANAGE_WISHLIST | 201, 200 |
| DELETE | `/api/me/wishlist/{productId}` | Bearer + MANAGE_WISHLIST | 204 |

## Boundaries and availability

Permissions are current database authorities, not OAuth scopes. Media operations require an eligible owner, not MANAGE_USER_PROFILE. Address and wishlist operations require MANAGE_ADDRESS and MANAGE_WISHLIST respectively. Product attachment requires ROLE_ADMIN and ownership of its completed source upload. Catalog reads are public but a supplied bearer is still validated.

Verification controllers require `security.verification.enabled=true`; Google controllers/framework routes require `security.identity.enabled=true` and configured verification keys. Public registration routes return 503 if verification is disabled. Conditional routes can be rejected by fallback security while disabled; do not assume 404. Secrets, Google/SMTP credentials and real frontend origins/destinations remain deployment configuration.

Profile PUT preserves omitted fields. Any supplied email, including null/blank, fails EMAIL_VERIFICATION_REQUIRED; use destination proof and fresh account authentication. profileImageId omission preserves, null clears and canonical UUID selects owned completed media. Private profile adds emailVerified/hasLocalPassword; both profile shapes include nullable profileImageUrl.

Errors have actual domain shapes: catalog plain RFC 9457 ProblemDetail, identity ProblemDetail with stable code, and legacy ApiError with timestamp/message/path/fieldErrors. Do not require extensions across every handler. CORS/framework/storage responses may differ. Shared CORS allows wildcard origin/method/header without credentials; identity chains use exact configured origins with credentials. Authorization is exposed in both.

Springdoc is installed, but current deny-all fallback protects Swagger UI and `/v3/api-docs`; this standalone document does not alter runtime access. OPTIONS is infrastructure CORS handling, not another controller operation. Storage signed PUT/GET are external capability calls, not extra backend routes.

## Verification

Validation passed: exact coverage of 34 current controller operations plus 2 Google framework operations across 31 paths; 45 schemas; all 347 local references resolve; unique operation IDs; matching required path parameters; valid security scheme references; schema required/property consistency; 58 request/response/header examples satisfy the documented JSON Schema assertion subset. Registration, profile email rejection and removed cooldown metadata were checked explicitly. These are structural/source checks, not a full external OpenAPI conformance-validator run. No production code, runtime security policy or dependency was changed, and no application tests were run for this documentation-only update.
