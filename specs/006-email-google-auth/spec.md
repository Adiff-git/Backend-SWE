# Feature Specification: Email Verification and Google Login

**Feature**: 006-email-google-auth
**Created**: 2026-11-04 | **Consolidated**: 2026-11-05
**Status**: Current request policy consolidated; implementation/test evidence is tracked in tasks.md and quickstart.md. External acceptance remains incomplete.

## User Scenarios & Testing

### User Story 1 - Register and verify an application email (Priority: P1)

A new registrant proves mailbox ownership before an account exists. Existing users can verify a historical address or replace an address without losing approved access.

**Independent Test**: Initiate registration, receive a code, confirm with the initiating credential and chosen password, then receive the new verified account's credentials. Separately verify an existing user's email replacement through current-password reauthentication.

**Acceptance Scenarios**:

1. **Given** a valid email/password request, **When** registration starts, **Then** only pending state and mail delivery are created; no user, official-email/username reservation, session or credentials exist.
2. **Given** the initiating registration credential and six-digit code, **When** valid confirmation supplies a valid password, **Then** proof consumption, verified-user creation, default role/ACTIVE status, normal session and credential issuance succeed atomically. The confirmation password is authoritative; no earlier submitted password is activated.
3. **Given** missing, wrong, expired, replayed or superseded registration proof, **When** confirmation occurs, **Then** no account or credentials are created.
4. **Given** another pending attempt for the same destination, **When** a legitimate registrant starts an independent attempt, **Then** the earlier attempt does not reserve that email or supply the new account's password.
5. **Given** a pending attempt, **When** Google registration first claims that email, **Then** later local confirmation conflicts without signing into or linking the Google-created account. Concurrent valid confirmations create at most one official owner.
6. **Given** an eligible existing user, **When** they verify a replacement destination using fresh account authentication and a code, **Then** the old official address/evidence remains until atomic success; uniqueness conflicts leave it unchanged.
7. **Given** a historical unverified or email-less account, **When** its owner uses existing sign-in, **Then** approved access remains; no historical email is falsely marked verified. Direct profile email changes are rejected.

### User Story 2 - Sign in with Google (Priority: P1)

**Independent Test**: Controlled validated Google identities support new and returning account login without live Google consent.

**Acceptance Scenarios**:

1. A new validated Google identity with usable verified email creates exactly one local user/association with verified official email, default role/ACTIVE and no local password, only at successful application handoff exchange.
2. An existing association resolves by validated issuer/subject despite changed, missing or unverified provider email; managed fields, official email and pending local challenges are unchanged.
3. Matching an existing local email never authenticates or associates that local account. The visitor is directed to ordinary sign-in and authenticated Connect Google.
4. Missing/malformed/unverified email rejects new Google registration without partial account creation. Ineligible associated accounts receive no new sessions or credentials.
5. Callback completion alone, abandoned handoffs and failed exchanges create no application sessions. Existing expiry, refresh rotation/reuse, logout and current permissions govern issued sessions.

### User Story 3 - Complete or recover from browser login (Priority: P2)

**Independent Test**: Controlled success/cancellation/invalid identity/expiry/replay and outage journeys have safe, measurable outcomes.

**Acceptance Scenarios**:

1. Successful exchange returns existing application credential transport; no access/refresh secret appears in a redirect, error or log.
2. Cancellation, invalid correlation/identity or provider outage uses configured safe failure destinations and retry/sign-in guidance.
3. Invalid unrelated bearer credentials do not block public Google login; authenticated linking/reauthentication still requires the original active session.
4. Each browser has one active attempt. Frontend serializes all Google initiations while a previous initiation is in progress, including coordinated tabs. New completed initiation invalidates the old attempt; callback success/failure invalidates only relevant server state and never deletes, refreshes or overwrites the shared browser cookie, which expires after its existing five-minute lifetime.
5. Repeated initiations, failed exchanges and proof submissions continue through normal validation without throttling; a valid proof succeeds after repeated failures. Immediate resends rotate the current credential/code and invalidate the previous generation.
6. An older callback response arriving after a newer initiation cannot change the newer cookie. A stale/superseded or replayed callback remains invalid even while a cookie exists. Violating initiation serialization with out-of-order responses fails correlation safely rather than bypassing server generation checks; wait for outstanding initiations to settle and restart a serialized flow. Missing/expired correlation uses the same safe restart without issuing credentials.

### User Story 4 - Connect Google and enable password login (Priority: P1)

**Independent Test**: Local account owner connects Google using separate fresh account and provider proofs; a Google-created owner optionally sets a password and signs in afresh after all-session revocation.

**Acceptance Scenarios**:

1. Connect Google requires fresh authentication of the existing account within five minutes, its original active session, and separate proof of the identity being added. The new Google identity cannot prove ownership of the local account itself.
2. Linking atomically consumes a single-use CONNECT_GOOGLE proof with association creation. Expired/replayed/wrong-owner/wrong-session/wrong-operation/concurrently consumed proofs leave no partial association.
3. Only one Google identity belongs to an application account; only one application account owns a Google identity. Repeating the same association with fresh proofs is idempotent; replacement/reassignment is rejected.
4. Google-only accounts may optionally set a password in profile after fresh authentication of the already linked Google identity. Until then only Google login is available.
5. Successful first-password setup atomically revokes every session, including the initiating one, returns no replacement credentials and requires fresh Google or email/password sign-in. Failed setup neither changes password nor revokes sessions.

### Edge Cases

- Six-digit codes preserve leading zeros; exactly expired state is invalid even before physical deletion. Wrong codes do not exhaust a challenge; immediate resends supersede the prior generation.
- Resend rotates the pending registration credential and code for the same attempt; independent attempts never supersede one another solely by matching destination.
- Email replacement requires both destination proof and still-fresh account proof. A new five-minute account proof can be acquired while a ten-minute code remains valid.
- Mail outage does not create a new local user before proof or modify an existing official email. Duplicate or superseded delivery never makes an invalid proof valid.
- Omitted profile email preserves it; supplied email, including null/blank, cannot bypass verification. Registration/challenge/confirmation required fields reject omission, null or blank.

## Requirements

### Functional Requirements

- **FR-001**: Local registration accepts email/password and creates pending state only. No user, official-email or username reservation, application session or credentials exist before successful confirmation. Confirmation requires initiating credential, challenge context, emailed code and a newly supplied valid password; atomically create a verified account and normal session. Never use mailbox proof to authenticate an existing account. Derive username from email local part, suffix _2/_3 on case-insensitive collisions, truncate the base to fit 50 Unicode code points; do not rename after email change.
- **FR-002**: Existing owners must explicitly Connect Google from profile using single-use CONNECT_GOOGLE proof of account authentication within five minutes, original active session and separate Google proof. Use current password when present or the already linked Google identity when absent. Consume account proof atomically with successful linking. Verified Google email proves official-email ownership only for new Google registration; no redundant local challenge. Later provider-email changes are metadata only. New Google registration requires usable verified email; existing associations remain identified by validated issuer/subject.
- **FR-003**: Google-created users have no local password until optional profile setup. Require fresh already-linked Google authentication within five minutes, the existing password policy and atomic absent-password creation. Consume proof and revoke all sessions atomically; return no replacement credentials. Existing password changes retain current policy; failed setup preserves sessions and credentials.
- **FR-004**: Preserve access credentials in the Authorization response header, refresh credentials in JSON, explicit bearer/request-body submission and no-store responses. Google credentials never substitute for application credentials or become application-token cookies.
- **FR-005**: Validated issuer/subject identifies provider association, unique per provider identity and per local user/provider. New user/association creation is atomic. Same-owner/same-identity linking is idempotent with fresh proofs; replacement/reassignment is rejected.
- **FR-006**: Matching email never automatically links accounts, including verified provider email. Explicit unlinking is out of scope.
- **FR-007**: Apply existing default role/ACTIVE creation policy, seven-day absolute application sessions, access lifetime at most fifteen minutes and session expiry, refresh rotation/reuse detection, logout and current account/permission checks. Deny ineligible users before credential/session creation.
- **FR-008**: Keep official email/evidence until valid destination proof and fresh account authentication permit replacement. Bind challenges to intended owner, destination and purpose; prevent replay/bypass and recheck official-email uniqueness at confirmation.
- **FR-009**: Google access is identity/email/basic profile only; no offline access/refresh persistence. Initially copy usable display name and ignore picture. Never overwrite managed profile fields or bypass image ownership/storage rules.
- **FR-010**: Validate provider authenticity, issuer, intended recipient, expiry, request correlation, freshness where required and replay protection. Handshake state is distinct from application authentication.
- **FR-011**: Use configured frontend destinations only. Never put access/refresh credentials or provider secrets in redirect URLs, errors or logs. Distinguish safe browser failures from structured API errors.
- **FR-012**: Define safe outcomes for cancellation, invalid correlation/identity, missing claims, outage, ineligibility, collision, association conflict, invalid/replayed handoff and races. Repeated requests must not produce rate-limit denials.
- **FR-013**: Automated acceptance uses controlled provider/mail responses, not live accounts or consent, covering all stories, proof states, account eligibility, lifecycle integration and transaction/race outcomes.
- **FR-014**: Preserve legacy username access, email-less accounts, current credentials and approved access; historical addresses remain unverified. Pending attempts cannot reserve official email or prevent independent registration/Google signup through uniqueness constraints.
- **FR-015**: Application login handoffs last 60 seconds, prove initiating-client possession and are atomically single-use. Session creation occurs only at successful exchange after eligibility checks. Invalid/expired/replayed/misbound exchanges issue no credentials.
- **FR-016**: Handshakes last five minutes, one active attempt per browser, never authenticate APIs and resist interception/forgery. New attempts supersede old correlation; stale/uncorrelated callbacks cannot clear newer state. Callback responses never set/delete/refresh/overwrite shared correlation cookies, which expire after five minutes from initiation. Frontend serializes every Google initiation while one is in progress; generation checks reject stale flows but cannot control browser response ordering. Missing/stale/expired correlation requires safe restart after outstanding requests settle. Local transport exceptions are isolated; production correlation requirements and approved cross-route scope are defined in the technical contract.
- **FR-017**: Six-digit emailed codes last ten minutes. Resend is immediately available and rotates code and pending credential atomically. Wrong codes do not exhaust the current challenge. Pending confirmation/resend requires an initiating secret, never public ID alone; passwords are not stored in pending state or emailed. Delivery failure never replaces official email.
- **FR-018**: Email replacement requires account authentication within five minutes plus destination code: password when set, already linked Google when absent. Evidence is address-specific; successful change invalidates obsolete same-purpose challenges. Google login/linking never silently changes official email or its pending challenges.
- **FR-019**: Concurrent registration confirmation, pending versus Google registration, first Google login, linking/proof consumption, handoff exchange, email confirmation and password setup/refresh must preserve uniqueness and atomicity with no partial accounts/associations or unauthorized credentials.
- **FR-020**: Expired handshake/handoff/recent-auth/pending-registration secrets are deleted within 24 hours after expiry. Pending credentials expire with the ten-minute challenge. Encrypted mail payload is deleted at delivery/terminal processing; interruption cleanup occurs within 15 minutes after expiry while running, or before dispatch on recovery after downtime. Authorization rejects expired state regardless of cleanup.
- **FR-021**: Endpoints impose no request quotas, resend cooldowns or failed-attempt lockouts. Repeated requests continue through ordinary validation, authentication, authorization and business conflict checks. Expiry, supersession and single-use proof rules remain mandatory.

### Key Entities

- **Pending registration**: Unauthenticated attempt with destination, initiating secret, challenge and bounded lifecycle; no account or official-email reservation and no stored password.
- **Application user**: Stable account identity, unique username/official email when present, address-specific verification, optional local credential, role/status and managed profile.
- **Provider association**: One-to-one Google/local relationship keyed by validated issuer/subject with link/login metadata.
- **Ownership challenge**: Six-digit destination proof for exactly one pending registration or existing user and purpose.
- **Recent account proof**: One-use owner/session/operation-bound evidence for EMAIL_CHANGE, INITIAL_PASSWORD or CONNECT_GOOGLE.
- **Browser attempt/handoff**: Short-lived client/purpose correlation separate from application sessions.

## Success Criteria

- **SC-001**: Every accepted email replacement has matching owner/destination/purpose proof; zero failed confirmations alter official email.
- **SC-002**: All first/returning Google scenarios resolve the correct local identity; zero matching-email cases silently link unrelated accounts.
- **SC-003**: All ineligible-account scenarios produce zero new sessions/credentials.
- **SC-004**: Every pending-registration failure creates zero users/sessions/credentials; successful confirmation creates exactly one verified account/session atomically. Legacy access/credential lifecycle regression scenarios retain approved behavior.
- **SC-005**: Every defined browser failure offers safe retry/sign-in guidance; zero redirect/error/log acceptance examples expose access/refresh credentials or provider secrets.
- **SC-006**: At least four of five walkthrough participants complete approved proof and Google journeys without assistance. Separate automated deadline and unrestricted-request evidence from human completion evidence.
- **SC-007**: Every successful first-password setup rejects all old access/refresh credentials and permits fresh Google/email-password login; failed setup preserves previous sessions.
- **SC-008**: Repeated initiation, resend, failed exchange, wrong password and wrong-code requests remain subject to normal validation without throttling. A valid proof can succeed after repeated failures; cleanup cases meet FR-020 deadlines.

## Assumptions and Scope

Existing auth/profile/image contracts remain dependencies except explicitly changed registration/email/linking behavior here. Legacy username support, derived suffix/truncation and absent-provider-email behavior are documented defaults. Unlinking, password recovery, Google offline access and picture import are excluded. The standalone proposal and ProjectCV source were unavailable; no contents were inferred. Technical design belongs in plan/research/data-model/contracts. Concise decision history is maintained only in review.md. These documents do not certify implementation, automated tests or human walkthrough completion.



