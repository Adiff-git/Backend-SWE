# HTTP and browser contracts

## Common transport and validation

JSON only on mutation/attempt/exchange requests; reject missing required fields, explicit null, blank, wrong types and unknown fields with 400 ProblemDetail. Email: strip, validate, lowercase, max 254; no Gmail alias rewriting. Password: no trimming, minimum eight Unicode code points, maximum 72 UTF-8 bytes. Code: six-character string matching ^[0-9]{6}$, preserving leading zeros. API errors use application/problem+json with safe stable code and no raw provider error/secret. All secret-bearing responses are Cache-Control:no-store. Bearer failures 401, permission denial 403, non-owned challenge 404, uniqueness conflict 409, infrastructure unavailable may return safe 503. Application access token only in Authorization response; refresh in existing TokenResponse JSON. Expose Authorization to exact allowlisted frontend origins. No application-token cookies.

## Public pending registration

These exact POST routes join the existing public credential chain and do not parse unrelated bearer credentials. Opaque registrationCredential is required for pending authority, never a public ID or email alone.

| Route | Request | Response |
| --- | --- | --- |
| /api/auth/register | {email,password} | 202 {registrationCredential,challengeId,expiresAt}; no Authorization/TokenResponse; pending+challenge/outbox only |
| /api/auth/registration-verifications/confirm | {registrationCredential,challengeId,code,password} | 201 existing TokenResponse and Authorization only after verified account/session creation commits |
| /api/auth/registration-verifications/resend | {registrationCredential,challengeId} | 202 replacement registrationCredential/challengeId/expiry metadata; old credential/code invalidated atomically |

Initial password is validated but not stored or activated. Confirmation password is authoritative and revalidated; it is never emailed. Server derives destination/attempt from hashed secret and current challenge, not client-supplied owner/destination. Wrong/missing/expired/superseded registration proof: generic 400 REGISTRATION_VERIFICATION_FAILED; replay yields no credentials. Official email already claimed: safe 409 REGISTRATION_CONFLICT without signing in/linking. Pending rows do not reserve official email/username or block another independent attempt. Pending secret/code lifetime ten minutes, resend establishes a new ten-minute generation. Missing rotated secret/lost response has no public-ID recovery: initiate a separate attempt. No automatic token reissue after successful confirmation replay.

## Existing account routes

POST /api/auth/login retains {identifier,password}, 200 TokenResponse/Authorization; email normal identifier, username legacy compatibility. Google-only password login generic 401 with comparable failure work. Refresh/logout request bodies and statuses retain existing behavior; PUT /api/auth/password retains existing current/new password and revoke-all contract.

GET /api/me adds emailVerified/hasLocalPassword. PUT /api/me preserves existing other-field semantics and omission; any supplied email, including null/blank, returns 400 EMAIL_VERIFICATION_REQUIRED. Historical email-less/unverified users keep approved access. No email-removal/unlink/password-reset endpoint.

## Verification and sensitive mutations

All following routes require current-state bearer and existing profile authority/ownership.

| Route | Request | Response |
| --- | --- | --- |
| POST /api/me/email-verification-challenges | {purpose:REGISTRATION or EMAIL_CHANGE,destination} | 201 {id,purpose,expiresAt,deliveryStatus} |
| GET /api/me/email-verification-challenges/{id} | none | 200 metadata/status, no code; non-owner/unknown 404 |
| POST /api/me/email-verification-challenges/{id}/confirm | {code} for historical REGISTRATION; {code,recentAuthProof} for EMAIL_CHANGE | 200 {email,emailVerified:true} |
| POST /api/me/reauthentications | {operation:EMAIL_CHANGE or CONNECT_GOOGLE,password} | 200 {recentAuthProof,expiresAt}; wrong/no local password generic 401 |
| POST /api/me/password | {newPassword,recentAuthProof} | 204 initial password only; no replacement credentials, all sessions revoked; already set 409 |
| GET /api/me/google-connection | none | 200 {connected,linkedAt}; nullable linkedAt when disconnected, no provider subject/email |

Authenticated REGISTRATION verifies only an existing historical user's current official email, not new pending signup. EMAIL_CHANGE destination must differ and be available; official email/evidence stays unchanged until proof and fresh account authentication commit. Same-purpose request is resend, superseding only the owner's previous challenge. Already-verified historical registration 409 EMAIL_ALREADY_VERIFIED; code failure generic 400 VERIFICATION_FAILED. Email-change confirmation must have a still-valid five-minute EMAIL_CHANGE proof, acquired afresh if necessary while ten-minute code remains valid. Recheck unique destination. SMTP outage never alters official email; queued delivery status is observable for owned challenges.

## Google attempt initiation and callback

Public POST /api/auth/google-login-attempts takes {codeChallenge,codeChallengeMethod:S256}; 201 {authorizationUrl,expiresAt}. Authenticated POST /api/me/google-connection-attempts takes {codeChallenge,codeChallengeMethod:S256,recentAuthProof}; requires fresh CONNECT_GOOGLE proof and binds reference/owner/sid to LINK attempt. Authenticated POST /api/me/google-reauthentication-attempts takes {codeChallenge,codeChallengeMethod:S256,operation:EMAIL_CHANGE or INITIAL_PASSWORD or CONNECT_GOOGLE}; REAUTH must prove already linked Google identity. Current password is required for local-account CONNECT_GOOGLE proof when password exists. New identity's provider proof cannot issue that account proof.

All starts require exact allowlisted Origin where applicable, JSON and no arbitrary destination/owner/provider overrides. LINK/REAUTH initiation uses explicit bearer, not cookie authentication. Frontend/backend are same schemeful site in production; exact credentialed CORS enables handshake cookies, including authenticated starts. Frontend retains PKCE verifier and serializes ALL initiation requests across LOGIN/LINK/REAUTH and coordinated tabs: await the complete previous initiation response and cookie processing before another request. UI cancellation must not start a replacement while a request is still in flight. One active browser attempt, new serialized initiation supersedes prior state and unused handoff. Generation checks reject stale flows but cannot control browser Set-Cookie response ordering. If initiation serialization is violated and responses arrive out of order, fail mismatched correlation safely; wait for all outstanding requests to settle, discard stale flow data and start a new serialized initiation. Missing/expired/stale cookie likewise requires restart, never callback cookie repair or credential issuance.

Browser goes to backend /oauth2/authorization/google?attempt=<opaque-ticket>, valid five minutes with matching correlation cookie. Cookie Path=/ covers both framework prefixes; Secure/HttpOnly/SameSite=Lax/Max-Age=300 in production. Cookie has no API authentication authority. Callback responses never emit Set-Cookie for this correlation cookie: no deletion, refresh, replacement or overwrite on success/failure. Consume/invalidate only the relevant server attempt; stale/uncorrelated callbacks mutate no newer state. Cookie naturally expires five minutes after its last initiation. A replay is still invalid because its server state is consumed.

Google callback is configured /login/oauth2/code/google. Framework validates state/nonce/signature/issuer/audience/expiry; REAUTH forces max_age=0 and validated auth_time freshness. Callback creates only a 60-second application handoff, no application session/credential or saved HTTP SecurityContext. Redirect 302 to fixed frontend callback ?code=<opaque-application-code>, no-store and Referrer-Policy:no-referrer. Frontend removes code from URL before external content. This code is not Google's authorization code. Only configured failure destination receives safe category: cancelled, invalid_request, identity_invalid, provider_unavailable, account_unavailable, account_connection_required or connection_conflict. Proxy/application logs redact query secrets on handshake/callback paths.

## Code exchange

POST /api/auth/google-code-exchanges {code,codeVerifier}; verifier 43–128 unreserved characters and S256 match. LOGIN exchange is public and ignores unrelated invalid bearer. LINK/REAUTH require explicit valid bearer matching original owner and exact session even though endpoint resides in public chain. Current profile permission is rechecked for protected completion; revoked permission returns 403 without consuming the account proof or creating an association. Cookies never suffice.

- LOGIN: 200 existing TokenResponse/Authorization after atomic handoff consumption/account resolution/session creation. New Google user requires verified usable email and gets null local password; pending local email attempts do not block it. Existing official-email collision 409 ACCOUNT_CONNECTION_REQUIRED with sign-in/Connect Google instruction. New identity missing/unverified email 400 GOOGLE_EMAIL_REQUIRED; existing associated identity remains independent of provider email changes. Ineligible owner generic 401.
- LINK: 200 {connected:true,linkedAt}; original active session, still-fresh referenced CONNECT_GOOGLE proof and separate validated provider identity required. Consume proof+handoff and create association atomically. Same association with a new valid proof is idempotent; different connection/owner 409 GOOGLE_CONNECTION_CONFLICT. Failure never leaves partial association and does not consume a valid proof as though linking succeeded. No application session/token issued.
- REAUTH: 200 {recentAuthProof,expiresAt}, owner/session/operation-bound and five minutes from actual validated provider authentication, not exchange time. Only already linked identity. Wrong identity generic 401; no application tokens.
- Invalid/expired/replayed/misbound handoff or local proof: generic 401 HANDOFF_INVALID or RECENT_AUTH_INVALID, no credentials/mutation. Malformed JSON/shape 400.

## Repeated requests

No request quota, resend cooldown, failed-code cap, 429 response or Retry-After contract. Repeated requests receive the same validation/authentication/authorization/conflict behavior as other requests. Expired, superseded or consumed proofs remain invalid. SMTP retries retain their bounded delivery schedule independently of endpoint request handling.

## Configuration and security chains

Exact public OIDC chain matches only provider routes and Google public attempt/exchange; existing public credential chain explicitly adds registration confirmation/resend. All /api/me mutations retain current-state bearer/profile policies. No blanket /api/auth/** permitAll. Google cookie checks plus state/ticket and exact Origin defend handshake; explicit bearer protects mutations; evaluate request-forgery policy per transport.

External Google client ID/secret/callback, fixed frontend destinations/origins, SMTP settings/TLS/timeouts, independent OTP-HMAC and AEAD key IDs/secrets, cleanup schedule and isolated local-cookie exception. Production fails closed for invalid enabled-feature secrets/HTTPS/origins. Identity scopes only; test issuer endpoint overrides are unavailable in production. Retention mechanics are defined in data-model.md under Cleanup and validation obligations; deployment guide must describe actual ingress redaction/sanitization.
