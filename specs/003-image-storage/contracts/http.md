# HTTP Contract

All backend routes use existing header `Authorization: Bearer <token>`, current account/session eligibility and ApiError/ProblemDetail. No JWT cookie/query/body authentication. Capability-bearing successful responses use `Cache-Control: no-store`, including private and limited-public profile responses containing image URLs.

## Media routes

| Method/path | Body | Success | Authorization |
| --- | --- | --- | --- |
| POST /api/image-uploads | Required JSON object with exactly contentType | 201: id (UUID string), uploadUrl (String), expiresAt (UTC absolute timestamp) | Authenticated eligible caller; no permission requirement |
| POST /api/image-uploads/{imageId}/complete | No body | 200: id, finalUrl | Authenticated owner only |
| GET /api/image-uploads/{imageId}/download-url | No body | 200: id, finalUrl | Authenticated owner of COMPLETED image only |

Creation accepts exact `image/jpeg`, `image/png`, `image/webp`. Missing/null/blank/other types and unknown fields return 400. Image IDs are canonical lowercase hyphenated UUID strings; malformed or noncanonical values returns 400. Authentication is checked before any resource details. A valid caller without MANAGE_USER_PROFILE can perform all otherwise valid owned-media operations.

Example creation request: `{"contentType":"image/png"}`. Response shape: `{"id":"00000000-0000-4000-8000-000000000001","uploadUrl":"<signed PUT>","expiresAt":"2026-10-27T10:00:00Z"}`. Complete/renew response shape: `{"id":"00000000-0000-4000-8000-000000000001","finalUrl":"<signed GET>"}`. Neither response exposes bucket/key/credentials/owner details. Upload expires 900 seconds after creation; each download capability is issued for 300 seconds and may cease to work sooner if signing credentials expire.

Browser PUT uses raw file bytes, the required Content-Type and the untouched URL. It does not send multipart form data or the application's Authorization header. Browser GET requires no extra private headers; configure/test presigner browser compatibility. Storage failures use provider responses directly, not the backend's ApiError. Backend completion remains necessary after a successful PUT.

## Profile integration

Existing PUT /api/me retains MANAGE_USER_PROFILE and selective-update semantics. `profileImageId` omitted preserves, null clears, canonical lowercase UUID string selects an owned completed image. Missing/foreign selection: 404; pending/expired: 409; malformed: 400; unavailable final object: 503. Entire update is atomic, including other profile fields.

GET /api/me retains its profile permission. GET /api/users/{id} remains authenticated-only with its existing limited privacy allowlist. Responses change profileImageId from null-only placeholder to UUID/null and add profileImageUrl as temporary URL/null. Both null when absent. An authenticated profile viewer may see another user's attached avatar but cannot invoke owner-only renewal for that image. Removing references never deletes objects. URL-generation failure during a profile write must roll back that write; retry is an ordinary repeat of the profile update.

## Backend failures

| Status | Conditions |
| --- | --- |
| 400 | Malformed requests, invalid ID representation, unsupported declaration, prohibited fields |
| 401 | Missing/invalid/revoked/expired credentials or account/session rejection |
| 403 | Existing profile permission denied; no new media permission denial |
| 404 | Missing or foreign media ID; identical detail and no state disclosure |
| 409 | Expired upload, absent staging, invalid lifecycle for attachment/renewal, replacement precondition failure, optimistic conflict |
| 422 | Empty, corrupt, non-image, undecodable or declared/stored/decoded MIME mismatch |
| 503 | Storage/read/copy/signing unavailable, missing final content/integrity failure |
| 500 | Unexpected database/internal failure, including commit failure after copy |

Use existing ProblemDetail fields plus timestamp, message, path and fieldErrors. Domain exceptions derive from ApplicationException, central advice maps application failures, security entrypoint/denied handler handles filter failures. Do not wrap every infrastructure exception merely for naming; map actual boundary conditions safely. Logs/errors omit presigned URLs, keys tied to private details, SQL, stack traces and credentials. Post-commit staging cleanup failure remains success; post-commit URL signing failure is 503 with committed state preserved and retryable through idempotent completion.
