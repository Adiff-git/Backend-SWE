# Feature Specification: Authentication and Session Tokens

**Feature Branch**: `feature/user-address` (existing branch; no branch hook configured)

**Created**: 2026-10-26

**Status**: Clarified and validated; ready for planning

**Input**: Registration, username/current-email login under the temporary trusted-email policy, access credentials, refresh rotation/reuse detection, session logout, password change and revocation. Specification only; no implementation.

Binding interface and engineering details are recorded separately in [contracts.md](contracts.md).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Register and sign in (Priority: P1)

As a customer, I create an account or sign in with username/password and obtain one independent device session.

**Why this priority**: Establishes account access without unrelated identity providers.

**Independent Test**: Register an account, sign in again and demonstrate two independent sessions without email or commerce workflows.

**Acceptance Scenarios**:

1. **Given** a unique valid username/password, **When** registration succeeds, **Then** exactly one account, encoded password, server-assigned default role/status and session are created and credentials are returned together. Failure leaves no partial initialization or successful credentials.
2. **Given** padded/case-equivalent usernames competing for registration, **When** requests complete, **Then** at most one account is created and duplicates receive conflict rejection.
3. **Given** an eligible account, **When** correct username credentials are supplied, **Then** a new independent session is created without revoking other devices.
4. **Given** missing identity, wrong password or ineligible account, **When** login is attempted, **Then** a generic authentication failure reveals no account existence/status detail or credential.
5. **Given** a password with surrounding whitespace, **When** registered or checked, **Then** its exact value is preserved without normalization or truncation.

### User Story 2 - Access protected capabilities (Priority: P1)

As a customer, I use short-lived access credentials while current account eligibility, permissions and ownership remain enforced.

**Why this priority**: All protected operations require valid current identity and session state.

**Independent Test**: With one session, exercise valid/tampered/expired credentials and change account/session/authority state between requests.

**Acceptance Scenarios**:

1. **Given** valid credentials, **When** a protected request is authenticated, **Then** current user status, session validity and database roles/permissions are checked, independently of ownership checks.
2. **Given** valid signature but missing user, unknown/revoked/expired session, missing identity or session-owner mismatch, **When** used, **Then** authentication fails.
3. **Given** a username change, **When** the same otherwise-valid access credential is used, **Then** stable account identity still resolves the same user.
4. **Given** committed status/permission changes, **When** subsequent authentication occurs, **Then** stale credential contents cannot preserve obsolete access. No cancellation of already-authenticated executing work is promised.
5. **Given** an unrelated stale access credential attached to registration/login/refresh/logout, **When** that exact operation is requested, **Then** its own supplied credentials determine the outcome; protected operations still reject invalid access credentials.

### User Story 3 - Renew one session safely (Priority: P1)

As a customer, I renew within the original seven-day deadline without leaving old refresh credentials usable.

**Why this priority**: Rotation and reuse handling are required security guarantees.

**Independent Test**: Rotate one session, reuse its consumed credential and verify revocation while another session remains usable.

**Acceptance Scenarios**:

1. **Given** a current refresh credential and eligible unexpired session, **When** refreshed, **Then** the old credential is consumed, one replacement becomes current and new access is issued for the same session and unchanged expiry.
2. **Given** consumed-credential reuse, **When** processed, **Then** refresh fails and the entire owning session is durably revoked, including its replacement refresh and every access credential; unrelated sessions remain active.
3. **Given** concurrent refreshes of one current credential, **When** completed, **Then** no two current replacements coexist. One may initially rotate, but detected competing reuse revokes the session, making even that response's credentials unusable afterward.
4. **Given** random invalid input, **When** refresh is attempted, **Then** no unrelated session is identified/revoked from caller-supplied IDs.
5. **Given** a lost successful refresh response, **When** the old credential is retried, **Then** strict reuse may revoke the session. Clients must serialize refresh attempts and avoid blind retries.
6. **Given** reuse-triggered revocation, **When** the authentication-error response finishes, **Then** revocation remains committed rather than rolling back with the error.

### User Story 4 - Sign out one device (Priority: P1)

As a customer, I end one session using its refresh credential even after access expires.

**Why this priority**: Logout must invalidate subsequent use of the device's session immediately.

**Independent Test**: Create two sessions, expire access for one and log it out through refresh credentials; only that session becomes unusable.

**Acceptance Scenarios**:

1. **Given** a recognized current or consumed refresh credential for an unexpired session, **When** logout succeeds, **Then** its entire session is revoked without requiring valid access; other sessions are preserved.
2. **Given** recognized credentials for an already-revoked but not absolutely expired session, **When** logout repeats, **Then** idempotent success is returned.
3. **Given** unknown or absolutely expired credentials, **When** logout is attempted, **Then** generic authentication failure is returned independently of cleanup timing.
4. **Given** logout racing with refresh, **When** logout commits, **Then** no resulting credential authenticates that session afterward.

### User Story 5 - Change password and end all sessions (Priority: P1)

As a customer, I prove my current password, replace it and require every device to sign in again.

**Why this priority**: Password changes must atomically revoke prior sessions.

**Independent Test**: Change password with multiple active sessions; reject every previous credential and accept a fresh login using the replacement password.

**Acceptance Scenarios**:

1. **Given** valid access/current password and a valid different replacement, **When** the change commits, **Then** only its encoded form is stored, all sessions including current are revoked, other profile fields remain unchanged and no replacement pair is issued.
2. **Given** incorrect current password, invalid/unchanged replacement or persistence failure, **When** the change fails, **Then** old password and existing session state remain unchanged.
3. **Given** concurrent login/refresh, **When** password change commits, **Then** no credentials based on pre-change password/session state bypass revocation. Login ordered after the change must check the replacement password.
4. **Given** already-authenticated executing work, **When** password changes, **Then** cancellation is not promised; subsequent authentication/refresh attempts enforce the change.

### User Story 6 - Sign in with the current email (Priority: P2)

As a customer with a current email, I can use it to sign in under the explicitly approved temporary trusted-email policy.

**Why this priority**: Current-email login is in scope now; real proof-based verification is separate future work.

**Independent Test**: Add a valid current email, sign in with it, replace it and demonstrate that the previous address cannot authenticate while the replacement follows the same temporary policy.

**Acceptance Scenarios**:

1. **Given** a valid current email and correct password, **When** email login is attempted, **Then** it may succeed without a delivered verification workflow under the temporary policy.
2. **Given** absent, previous or incorrect email credentials, **When** used, **Then** generic authentication failure reveals no account existence information.
3. **Given** email replacement, **When** committed, **Then** only the new current email is provisionally trusted; the old address no longer authenticates and no previous proof is reused.
4. **Given** no real verification or Google integration exists, **When** this feature is delivered, **Then** username and provisional current-email login remain usable and reports do not claim proof-based verification or Google login is implemented.

### Edge Cases

- Registration username is validated after trimming: empty/null/blank and more than fifty Unicode code points are rejected. Preserve username display casing and internal whitespace; do not impose ASCII-only, three-character minimum and reject @. Reject @ in registration and profile username updates. Login identifiers containing @ are email; all others are usernames.
- Required omitted/null values are invalid. Password minimum counts eight Unicode code points; whitespace is preserved; the upper bound is 72 UTF-8 bytes under the approved encoder contract; reject overflow, never truncate.
- At expiration equality credentials are invalid. Access is capped by the remaining session lifetime, even when less than fifteen minutes remain.
- Lost responses and concurrent refreshes can invalidate newly returned credentials through strict reuse. Cleanup cannot remove consumed evidence for a still-live session.
- Missing/deleted users, session-owner mismatch, stale role claims and current account-status changes cannot be bypassed by a valid signature.
- Signing/issuance failure cannot produce successful registration credentials or partial initialization. A lost successful response does not imply rollback.
- Refresh/reuse/logout/password/login races require real concurrent acceptance evidence, not only sequential unit cases.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001 — Scope**: Include registration, username/current-email login under the temporary trusted-email policy, access issuance/validation, refresh rotation/reuse, session logout, password change/all-session revocation, current status/authority enforcement and safe errors. Exclude social login, MFA, password reset, logout-all endpoint, frontend storage and unrelated commerce/media.
- **FR-002 — Registration identity**: Require only username/password. Reuse the original User policy: trim surrounding whitespace, preserve display casing/internal whitespace, require nonblank and at most 50 Unicode code points, compare lowercase case-insensitively with the same database-authoritative rules. Do not impose a three-character minimum or ASCII restriction; reject @. Enforce database uniqueness under concurrent writes. Never normalize passwords. Reject @ consistently at registration and profile username updates; other original User rules remain unchanged.
- **FR-003 — Atomic provisioning**: Create account, encoded password, server-default role/status and independent session atomically; automatically authenticate success and deliver credentials only on success. Clients cannot choose IDs/status/roles/permissions/verification/audit fields. Initial status is ACTIVE; assign default role USER and permissions MANAGE_USER_PROFILE / MANAGE_ADDRESS as defined in contracts.md, never client-selected.
- **FR-004 — Login**: Trim identifier, compare case-insensitively, treat any identifier containing @ as email and other identifiers as username; never fall back to a different account type after a failed password check. Authenticate exact passwords; email login uses only the current email, provisionally treated as verified under the confirmed temporary policy. Use generic credential failures. Each success creates an independent session without revoking others.
- **FR-005 — Verification dependency**: Bind the temporary trusted-email state to the actual current normalized email, not a reusable account-level proof. Replacement immediately removes old-address login and provisionally trusts only the new valid current address; clients cannot write a verification flag. No actual verification evidence is claimed. Reuse User email normalization/uniqueness. Confirmed temporary policy: any present valid current email is treated as verified without proof. This explicitly supersedes the earlier requirement to block email login until verification delivery. Username and current-email login are in scope now; real verification and possible future Google integration are deferred, with no provider/expiry workflow invented here.
- **FR-006 — Password policy**: Minimum eight Unicode code points without composition requirements; no trimming, normalization or truncation. Enforce encoder-compatible upper bounds, including the confirmed 72-byte UTF-8 maximum. Store encoded values only. Use the confirmed policy: minimum eight Unicode code points and maximum 72 UTF-8 bytes of the exact original password; encoding details are in contracts.md. Reject excess bytes before encoding/matching; never truncate.
- **FR-007 — Credential separation**: Access credentials use only the authorization response/request header, never auth cookies, query/body input or duplicate JSON output. Refresh credentials use designated JSON bodies only, never URLs, access claims, logs or unrelated responses. Token issuance prohibits caching; no frontend storage mandate.
- **FR-008 — Access lifetime/identity**: Access lasts at most fifteen minutes and never beyond session absolute expiry. Include stable user/session identity, issuer, issuance and expiration. Validate signature/allowed algorithm/issuer/expiry/required identities/session owner; audience only with an approved contract. Mutable username never determines ownership.
- **FR-009 — Current-state authentication**: Every protected authentication resolves current user, account eligibility, session validity and database roles/permissions. Valid signature alone is insufficient. Reject missing user/session, revocation or absolute expiry. Exclude passwords/refresh values/unnecessary personal data from access contents.
- **FR-010 — Session lifecycle**: Each registration/login creates one stable session with owner, creation, revocation state and fixed seven-day absolute expiry plus refresh history. Rotation never extends it. These are server-side security records, not browser-cookie login sessions; immediate revocation means the whole design is not fully stateless.
- **FR-011 — Refresh secrecy**: Use unpredictable opaque refresh secrets stored only as non-reversible hashes, each owned by one session. Never accept them as bearer access credentials. Exactly one current refresh secret exists per active session after successful issuance/rotation.
- **FR-012 — Atomic rotation**: Validate refresh secret, session state/ownership/expiry and current eligibility; atomically consume current secret and establish one replacement plus access for the same session/deadline. Failed rotation cannot leave multiple current credentials or partial family changes.
- **FR-013 — Strict reuse**: Consumed-secret reuse rejects refresh and durably revokes its entire session, replacement refresh and access credentials, preserving unrelated sessions. Keep consumed evidence through session lifetime. Unknown random input cannot revoke a session. Revocation commits despite the failure response. Concurrent/lost-response retries have no grace period; clients serialize refresh.
- **FR-014 — Logout**: Authenticate by supplied refresh credential, not arbitrary account/session IDs or access validity. Recognized current/consumed credentials revoke their unexpired session. Recognized revoked/unexpired repeats succeed idempotently; unknown/absolutely expired secrets fail generically independent of cleanup. Other sessions remain active. Client clears local credentials; clearing request context alone is insufficient.
- **FR-015 — Password change**: Require valid access, exact current password and valid different replacement, deriving owner from authenticated identity. Atomically persist encoded replacement and revoke all sessions including current. Failure preserves old password/session state; success preserves unrelated profile fields, requires all devices to sign in and issues no replacement pair.
- **FR-016 — Revocation races**: No login/refresh authorized with pre-change password/session state may bypass committed password-change revocation. Subsequent authentication after logout/reuse/password commit rejects affected credentials. Do not promise cancellation of already-authenticated executing requests; planning chooses coordination.
- **FR-017 — Eligibility/authorities**: ACTIVE eligible, INACTIVE disabled, SUSPENDED/BANNED locked unless a different approved policy exists. Map eligibility failures to generic authentication rejection. Use current approved role/permission names, deduplicate authorities/prefixes and keep ownership separate. Self-profile operations require the current MANAGE_USER_PROFILE permission and address operations require the current MANAGE_ADDRESS permission, in addition to ownership. Missing required permission is access denied even for the owner. Permission alone never grants access to another account or address. Limited-public-profile viewing requires authentication only.
- **FR-018 — Route isolation**: Registration/login/refresh/logout exact operations ignore unrelated attached access credentials and use their own credentials; public-access designation alone is insufficient if bearer processing still runs. Protected operations validate access. No form/Basic/cookie/servlet login. Explicit-origin CORS/header exposure/preflight and chain-specific CSRF rules are in contracts.md; preserve unrelated protections.
- **FR-019 — Errors/privacy**: Use existing safe error conventions and contracts.md status mappings for inputs/conflicts/credentials/eligibility/access/session/refresh/permissions/password failures. Never expose credentials except intended successful delivery, signing material, hashes, stack/SQL details or internal failures. Preserve appropriate bearer challenges and filter/application handling separation.
- **FR-020 — Retention/operations**: Preserve normalized uniqueness, owned session/token lifecycle, UTC time and atomic consumption/revocation. Keep consumed evidence until absolute expiry and enforce expiry independently of cleanup. Proposed cleanup bound is thirty days after expiry; never erase active-session evidence. Do not require another service merely for these guarantees. Externalize signing/deployment values without insecure fallback.
- **FR-021 — Existing-domain integration**: Reuse the single User, User-owned persistence/service boundary, immutable Long ID, current-user resolver and shared errors; own authentication behavior in auth. Explicitly add missing support without duplicate entities/security providers. Real authentication enables protected feature flows; Media remains separate and dependent operations remain blocked.
- **FR-022 — Acceptance evidence**: Provide appropriate unit/boundary/repository/service/API/end-to-end evidence including real concurrent rotation/reuse/logout/password/login outcomes and failure commit/rollback. Use disposable infrastructure only. Acceptance must distinguish provisional trusted-current-email login from future proof-based verification, which is not delivered here.

### Key Entities

- **User**: Existing immutable Long identity, username, encoded password, optional current email, accountStatus and joinedAt. Preserve existing profile/address data; add current-email verification support only through approved work.
- **Role / Permission assignments**: Current server-controlled authorities; only conceptual diagrams exist, so implement USER with MANAGE_USER_PROFILE / MANAGE_ADDRESS; no additional roles/permissions are introduced.
- **Authentication session**: Stable identity, owner, creation, fixed seven-day expiry, revocation state/time/reason and refresh family. Session-ID representation belongs to planning, not a new global account-ID policy.
- **Refresh credential record**: One session owner, non-reversible secret hash, issue/consume/revoke states and replacement history sufficient for reuse detection. Never persist raw secrets.
- **Email verification evidence**: Server-controlled proof for actual current email; actual verification is deferred; current valid email is provisionally trusted, not proven.

### Session and Credential Transitions

| Event | Result | Subsequent authentication |
| --- | --- | --- |
| Eligible registration/login | New session, one current refresh secret | Allowed until respective expiry/state changes |
| Valid current refresh | Consume old; one replacement; same session/deadline | New access accepted; consumed refresh reuse revokes |
| Consumed refresh reuse | Entire session revoked; error after durable security action | All session credentials rejected |
| Unknown refresh | No mutation; generic failure | No effect on other sessions |
| Logout, recognized unexpired-session secret | Revoke owning session | Access/refresh rejected; other sessions preserved |
| Repeated logout, recognized revoked/unexpired session | Idempotent success | Remains revoked |
| Absolute expiry | Unusable independent of cleanup | Access/refresh/logout authentication rejected |
| Successful password change | Password replaced, all account sessions revoked | All devices sign in again; no replacement pair |
| Eligibility/authority change | Current state enforced at next authentication | Reject ineligible account; use current authorities |

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Every successful registration/login journey creates exactly one session/pair; specified failures leave zero partially initialized accounts/sessions.
- **SC-002**: Across all expiry-boundary cases, zero access credentials work after fifteen minutes or the original seven-day session deadline, and zero rotations extend that deadline.
- **SC-003**: Every reuse/logout/password-change case rejects affected credentials on subsequent authentication and preserves unrelated sessions where specified, with zero secret disclosure in failures/logs.
- **SC-004**: Every controlled concurrent refresh case preserves at most one current refresh secret and durably revokes detected reuse despite an error response.
- **SC-005**: Every password-change concurrency/rollback case yields either unchanged prior state or replacement password with all prior sessions unusable; no mixed outcome is accepted.
- **SC-006**: Every provisional email-login case accepts only the current valid email with the exact correct password; zero previous-address logins succeed. No acceptance report represents provisional trust as completed verification.
- **SC-007**: All documented request/header/body/error contracts pass acceptance review, with zero authentication cookies, access-in-body responses or refresh credentials in unrelated delivery locations.
- **SC-008**: Customers complete registration/login, renewal, device logout and password change without manual data repair or retry behavior weakening documented revocation.

## Assumptions

### Repository Evidence and Compatibility

Inspected constitution, feature 001 business/engineering contracts, current User/service/repository/DTOs, current-user boundary, ApiError/factory/advice, migrations V1/V2, Maven build and disposable-test infrastructure. User/Address core exists on feature/user-address. No configured AuthenticationManager/provider, UserDetailsService/CustomUserDetails, PasswordEncoder bean, JWT encoder/decoder/resource-server chain, session/refresh models, persisted role/permission model or email verification exists. Request-listed framework components are intended patterns, not existing implementations.

Constitution VI permits refresh/revocation when explicitly required; this request supplies that requirement, so no amendment is needed. The new registration policy now reuses the approved original User rules. The latest user decision prohibits @ in usernames, resolving identifier dispatch. This is an explicit narrow amendment to the prior User contract, not a new ASCII-only or lowercase-storage policy. Current authentication eligibility is checked before authorization. The confirmed permission-and-ownership decision below intentionally supersedes the earlier authentication-plus-ownership-only profile/address policy. No code, tests, earlier migrations, build or prior feature documents are changed here; branch creation and commit/push are not part of this request.

### Explicit Provisional Defaults

Password minimum counts Unicode code points consistently with feature 001. Access expires at min(issuance plus fifteen minutes, session expiry), without extension beyond that session deadline. Cleanup target is thirty days after expiry. Recognized revoked/unexpired logout repeats succeed; unknown/absolutely expired logout is 401. Incorrect current password and invalid/unchanged replacement are 400; credential/account failures are generic 401. These defaults are explicit, not changes to confirmed requirements.

### Confirmed Decisions (2026-10-26)

- Username registration reuses the original User policy: trimmed/nonblank, maximum 50 Unicode code points, preserved display casing, internal whitespace retained and case-insensitive uniqueness. No ASCII-only restriction or three-character minimum; @ is prohibited by the latest confirmed decision. Existing users are neither renamed nor deleted.
- Email is provisionally treated as verified whenever a valid current email is present. This explicitly overrides the original requirement to block email login until proof exists. It is an assumption of trust, not verification evidence; account-existence privacy remains mandatory. Real verification and possible Google integration are future work, with no Google/social login implemented here.
- Default role USER; initial status ACTIVE; initial permissions MANAGE_USER_PROFILE and MANAGE_ADDRESS, limited to self-profile and owned-address actions. Future capabilities may add roles/permissions. These permissions do not grant administration or foreign-account access; authenticated limited-public-profile viewing adds no permission restriction. Authority role is ROLE_USER, with no doubled prefix; permission names have no ROLE_ prefix.
- Approved password encoding is recorded in contracts.md; minimum eight Unicode code points, maximum 72 UTF-8 bytes; exact original passwords with no normalization/truncation. Required password fields containing fewer than eight code points or more than 72 bytes are rejected before encoding; matching must not rely on truncation.
- The approved signing/configuration choice is recorded in contracts.md, requiring an externally supplied secret of at least 32 UTF-8 bytes without fallback. Invalid configuration fails closed; session/issuer/current-state requirements remain unchanged.

### Identifier Dispatch Decision (2026-10-26)

- Username must not contain @. Registration and profile username updates apply this rule consistently; trim, nonblank, maximum 50 Unicode code points, preserved display casing/internal whitespace and case-insensitive database uniqueness remain unchanged.
- Login trims the identifier; containing @ selects current-email authentication, otherwise username authentication. Do not introduce identifierType or cross-type fallback.
- Planning must check whether persisted usernames containing @ exist. If found, report the compatibility blocker and obtain a separate approved remediation; never silently rename/delete accounts, reinterpret those values as usernames during email lookup or claim those legacy username logins are supported. Existing sessions continue to identify owners by immutable ID. No existing data was inspected or modified during specification.
- Acceptance: registration/profile updates containing @ fail validation atomically without changing the account. Existing case-preserving/non-ASCII usernames without @ remain allowed. Email-shaped login selects only the current-email path, and username-shaped login selects only the username path; failures remain generic.

No clarification questions remain. Ready for `$speckit-plan`; all technical choices and future verification boundaries remain as confirmed in contracts.md.


### Permission and Ownership Decision (2026-10-26)

Confirmed by the user: enforce both the relevant current permission and ownership for self-profile/address operations. Check MANAGE_USER_PROFILE for viewing/updating one's private profile and MANAGE_ADDRESS for all five owned-address operations. Missing permission denies the operation even when the caller is the owner; having permission never overrides ownership. Limited-public-profile viewing remains authentication-only, with its exact privacy allowlist. Technical authority checks are specified in contracts.md.

Acceptance: an authenticated eligible owner with the required permission can perform an otherwise valid operation; an owner without it receives access denial and no changes. A caller with the permission cannot access another owner's address: missing and foreign addresses remain indistinguishable not-found outcomes. Changing current permissions affects subsequent authorization. Submitted owner IDs cannot redirect self-profile actions. This decision explicitly amends the earlier feature 001 rule; it does not broaden account or address ownership.
