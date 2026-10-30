# Authentication Data Model

## Existing User (user domain)

Reuse users.id BIGINT/Long, existing username/display casing and database-generated username_key/email_key, encoded password TEXT, email VARCHAR(254), accountStatus string enum and joinedAt TIMESTAMPTZ. No second User or password column. Registration assigns ACTIVE/USER and joinedAt; profile fields remain absent as allowed. Add a new username CHECK prohibiting @, retaining trimmed/nonblank/max-50 Unicode-code-point and case-insensitive uniqueness rules. Registration/profile validation share the prohibition. No ASCII-only restriction, minimum-three rule or lowercase display-name storage.

Temporary email policy reads current nullable User.email as trusted, with no proof/verified flag. Replacing it immediately stops old-email lookup; new current email is provisionally trusted. Future real verification must explicitly replace that policy. No migration sets emailVerified=true or invents provider artifacts.

Password is exact BCrypt strength-10 output. Inputs require at least eight Unicode code points and at most 72 UTF-8 bytes without normalization/truncation. Synthetic fixture hashes must be replaced where real authentication is exercised; no production fallback encoder. Unknown real legacy password formats require separate approved remediation.

## User-owned authority tables

| Table | Fields / types | Constraints / ownership |
| --- | --- | --- |
| roles | id BIGINT identity PK; name VARCHAR(50) NOT NULL | uk_roles_name; nonblank; seed USER only |
| permissions | id BIGINT identity PK; name VARCHAR(100) NOT NULL | uk_permissions_name; nonblank; seed MANAGE_USER_PROFILE and MANAGE_ADDRESS |
| user_roles | user_id BIGINT; role_id BIGINT | composite PK, FK users/roles, no duplicate assignment |
| role_permissions | role_id BIGINT; permission_id BIGINT | composite PK, FK roles/permissions, no duplicate grant |

Use LAZY associations or use-case projections with explicit fetch plans. No global EAGER loading or converter queries. Role names persist unprefixed; authentication emits ROLE_USER exactly once, permissions unchanged/deduplicated. User service owns retrieval/provisioning/assignment APIs; auth cannot query these repositories. New migrations backfill existing users with USER only, never admin, preserving status/password/profile/address data. No role-management endpoint or additional capabilities are included.

Indexes for reverse joins: idx_user_roles_role_id and idx_role_permissions_permission_id where needed; composite PKs already index their leading columns. No duplicate unique-column indexes.

## AuthenticationSession (auth domain; authentication_sessions)

| Field | Type | Constraints |
| --- | --- | --- |
| id | UUID | PK; random session identity generated locally; feature-specific, not User ID policy |
| user_id | BIGINT | NOT NULL FK users.id; owner immutable; no cascade into User |
| created_at | Instant / TIMESTAMPTZ | NOT NULL; creation instant truncated consistently to token second precision |
| expires_at | Instant / TIMESTAMPTZ | NOT NULL; fixed created_at+7 days; immutable |
| revoked_at | Instant / TIMESTAMPTZ | Nullable; once set cannot be cleared through ordinary workflows |
| revocation_reason | string enum / VARCHAR(32) | Nullable iff revoked_at null; LOGOUT, REFRESH_REUSE, PASSWORD_CHANGED |

CHECK expires_at > created_at and equals created_at + fixed 604800-second interval (not a timezone/DST-dependent calendar-day calculation); revocation-field consistency. Index idx_authentication_sessions_user_id_id for owner coordination/all-session revocation; idx_authentication_sessions_expires_at for cleanup. Keep expired/revoked rows until cleanup; state plus current time determines validity without waiting for job execution. Auth session may map user as LAZY reference for FK identity, but must use User service for business access/eligibility rather than hidden traversal.

Session active means revoked_at IS NULL and now < expires_at and current User ACTIVE; owner must exist. Current authority eligibility is not frozen into the session. Revoking one session leaves others unchanged; password change revokes every existing session under the owning User coordination root.

## RefreshCredential (auth domain; refresh_credentials)

| Field | Type | Constraints |
| --- | --- | --- |
| id | BIGINT identity / Long | PK; internal record ID, never raw secret |
| session_id | UUID | NOT NULL FK authentication_sessions.id; immutable owner |
| token_hash | BYTEA | NOT NULL, SHA-256 digest; CHECK OCTET_LENGTH=32; uk_refresh_credentials_token_hash |
| issued_at | Instant / TIMESTAMPTZ | NOT NULL |
| consumed_at | Instant / TIMESTAMPTZ | Nullable; set once when rotated |

No plaintext secret, JWT refresh claims or independently extendable refresh expiry. Session.expires_at is the sole family deadline; replacing a credential cannot extend it. Retain old hashes associated with the same session; consumed_at distinguishes history from current, so separate successor pointers are unnecessary for reuse detection.

Unique partial index idx_refresh_credentials_session_id_current on session_id WHERE consumed_at IS NULL enforces at most one current credential. Index idx_refresh_credentials_session_id_id supports bounded ordered record access/cleanup. Service transactions establish exactly one on successful active issuance. A revoked session may still have one unconsumed row but it is not an active credential: session revocation invalidates the family without deleting evidence or resetting consumed state.

Use LAZY session association, no unsafe Lombok equality/toString/Data. Refresh state is derived: CURRENT if unconsumed and session active; CONSUMED if consumed; INVALID if session revoked/expired. Raw input is 43-character Base64URL output of 32 SecureRandom bytes; receive up to 1024 characters at the boundary, reject malformed/empty appropriately, hash exact input without trim/normalization and never persist/log it.

## State transitions and invariant enforcement

| Event | Coordinated effect |
| --- | --- |
| Register/login | User lock/provisioning; create session and one current credential; sign local access; publish only after commit |
| Refresh current | User -> session -> credential locks; recheck now/eligibility; set consumed_at and flush; insert one replacement and sign; commit |
| Reuse consumed | Same lock order; revoke session REFRESH_REUSE; normal rejected outcome commits, outer facade returns 401 |
| Logout recognized unexpired | Same coordination; revoke LOGOUT if active; already revoked remains idempotent 204 |
| Unknown/expired logout | Generic 401; no mutation; cleanup timing irrelevant |
| Password change | User lock; recheck authenticating session; replace encoded password and revoke all sessions PASSWORD_CHANGED atomically |
| Expiry | Time checks invalidate access/refresh immediately; no job dependency |
| Cleanup | Expired-session owner lock then child order; delete credential rows before sessions, batches; preserve active-session evidence |

## Migration compatibility and boundaries

Allocate new versions after inspecting actual migration history; never rewrite V1/V2. Suggested logical sequence: authority tables/seeds/backfill; no-@ constraint with explicit preflight failure on incompatible rows; sessions and credential history with constraints/indexes. No real shared database inspection occurs in planning. If @ usernames exist, report and obtain approved remediation rather than silently renaming/deleting. Existing session/role data must not be destroyed on retries. No Media FK/provider is added.

Secret/key/issuer are not database columns. JWT contains stable canonical User ID sub, UUID sid, iss, iat and exp; no stored password/hash/refresh secret or personal data. No access-token blacklist table is needed because session validity is checked on every protected authentication.
