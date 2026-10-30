# Feature Specification: User Profiles and Address Management

**Created**: 2026-10-26

**Status**: Business requirements confirmed; functionality remains unimplemented.

**Input**: English specification for user profiles and address management in the single-vendor store, based on the supplied request. Requirements and acceptance criteria only; no production code or tests are implemented.

## Clarifications

### Session 2026-10-26

- Q: When a user changes their username, should their existing authentication token continue to work? → A: Existing tokens remain valid; authentication identifies the account through its immutable user ID.
- Q: If two devices update the same profile or address at nearly the same time, how should conflicting changes be handled? → A: Later successful writes win, and omitted profile fields preserve the latest stored values. Coordinate competing changes for each account.
- Q: How many addresses may each user save? → A: Maximum 20 addresses per user; reject additional creates as invalid input. Apply the limit even when creates overlap.
- Q: What should happen if a write cannot proceed before the concurrent-update wait expires? → A: Report a conflict with a safe concurrent-update/retry message, roll back the operation, and do not retry automatically; the client decides whether to retry.

## Implementation Readiness and Future Integration

Profile and address business rules can be developed and verified independently of future account authentication and image management. No functionality is currently implemented. Independent verification does not establish readiness for customer access or image-reference operations.

- **FR-018 — Delivery sequencing and identity boundary**: Deliver independent profile/address behavior before the separate authentication and image-management features. Always determine ownership from a trusted, immutable account identity. Never use a customer-supplied ownership claim, simulated authentication or fixed production identity. Keep customer access unavailable until genuine authentication is integrated. Controlled identities are permitted only in verification, with all ownership rules retained.
- **FR-019 — Future image capability**: Until image management is available, support profiles without images; omitting the image preserves absence and explicitly clearing an already absent image changes nothing. A non-empty image selection is unavailable and must fail before any change, without claiming the image valid or invalid. Actual image validation, removal and replacement remain deferred. Never save an unvalidated image reference or invent an identifier policy. Image upload, storage and deletion belong to the separate image-management feature.

Future acceptance retains all authenticated-access and image scenarios below. Independent verification cannot satisfy those deferred scenarios. Technical delivery constraints are maintained in [plan.md](plan.md) and [engineering constraints](contracts/engineering.md).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Maintain my private profile (Priority: P1)

As an authenticated customer, I can view and selectively update my details so my account information stays accurate.

**Why this priority**: Accurate personal details support all account experiences.

**Independent Test**: With an authenticated, pre-existing user, retrieve details and change each editable field without requiring an address book.

**Acceptance Scenarios**:

1. **Given** an authenticated user, **When** they retrieve their details, **Then** only their private response fields are returned, never credentials or relationships.
2. **Given** existing details, **When** a valid selective update is submitted, **Then** supplied values change and omitted values remain unchanged according to the presence table; the response reflects persisted state.
3. **Given** existing username/email values, **When** the same values are submitted, **Then** the update succeeds; values conflicting with another user report a conflict and no changes persist.
4. **Given** an avatar reference, **When** its field is omitted, null, or a valid replacement, **Then** it is preserved, removed, or replaced respectively, without deleting stored media.
5. **Given** an invalid field, prohibited-field change, or unavailable/foreign image, **When** an update is attempted, **Then** it is rejected and all original details remain intact.

6. **Given** a valid authentication token issued before a username change, **When** the user successfully changes username and uses that token to view their profile or manage owned addresses, **Then** it continues to identify the same immutable user ID and the requests succeed while the token remains otherwise valid.

### User Story 2 - Maintain my address book (Priority: P1)

As an authenticated customer, I can save, view, change and remove delivery addresses while always having one default when addresses exist.

**Why this priority**: Customers need a reliable delivery destination.

**Independent Test**: With an authenticated user, create, list, retrieve, update and delete addresses without changing profile details.

**Acceptance Scenarios**:

1. **Given** an empty address book, **When** listed, **Then** the result is an empty array; creating the first address with omitted, null, false or true default flag makes it default and confirms creation with a reference to the new address.
2. **Given** a default address, **When** another is created with omitted/null/false flag, **Then** the old default remains; true makes the new address the only default.
3. **Given** an owned address, **When** required recipient fields are valid and default is omitted/null, **Then** details change and its flag is preserved; true makes it the only default; false on a non-default succeeds; false on the default is rejected as invalid input with no changes.
4. **Given** several addresses, **When** a non-default is deleted, **Then** the default stays; deleting the default promotes the highest remaining ID; deleting the last leaves no default and confirms deletion without additional content.
5. **Given** a missing or another user's address ID, **When** read, updated or deleted, **Then** all operations return indistinguishable not-found errors and change nothing.
6. **Given** concurrent first creates, default switches or default deletion/create operations for one user, **When** operations finish, **Then** every saved non-empty book has exactly one default and failed operations leave no partial changes.
7. **Given** saved addresses, **When** listed, **Then** default is first and other addresses appear by descending ID, with no addresses owned by others.
8. **Given** 19 addresses, **When** a valid address is created, **Then** the book reaches 20; a further create returns invalid-input rejection with no changes. Two concurrent creates starting from 19 permit only one to succeed. Updates/deletes remain allowed at capacity, and deleting one permits another create.

### User Story 3 - View a limited profile (Priority: P2)

As a permitted viewer, I can identify another customer without seeing private account information.

**Why this priority**: Recognition must preserve customer privacy.

**Independent Test**: Retrieve another pre-existing user's public profile under the approved viewer policy and compare the exact response field set.

**Acceptance Scenarios**:

1. **Given** an existing user and an authorized viewer, **When** the public profile is requested, **Then** only id, username, displayName, profileImageId and joinedAt may appear.
2. **Given** a nonexistent user, **When** an authorized viewer requests the profile, **Then** not-found rejection is returned.
3. **Given** real Authentication has been integrated and no valid authentication is supplied, **When** a feature operation is requested, **Then** authentication-required rejection is returned without private information.
4. **Given** a valid authenticated viewer, **When** a limited public profile is requested, **Then** access needs no additional feature-specific permission or account-status eligibility. Existing access policy may deny a genuinely applicable operation without adding feature-specific restrictions. Customer access remains unavailable until genuine authentication exists.

### Edge Cases

- Empty/whitespace-only required strings, malformed email, future birth dates, invalid enum values, values of the wrong type and over-limit fields fail with invalid-input rejection and no mutation.
- After surrounding whitespace is trimmed, address phones must reject internal spaces, punctuation, non-ASCII digits, missing leading zero and lengths other than ten.
- Explicit null username/email is rejected even when email is currently absent; an absent email may remain absent when omitted.
- A date equal to today's UTC date is valid; tomorrow is invalid. Absolute times remain UTC and joinedAt never changes through profile updates.
- Empty optional strings are actual supplied values, not omission; they are rejected with invalid-input rejection after trimming. Null optional scalar fields preserve rather than clear.
- A uniqueness race returns conflict rejection with restoration of the previous state, including any simultaneously requested avatar change.
- Concurrent edits are coordinated: later successful supplied values win, omitted profile fields preserve the latest saved values, and default-address rules are rechecked against the latest coordinated state.
- **Concurrency acceptance**: Given two overlapping valid edits to the same field, when both succeed in a controlled successful-write order, then the later write is retained. Given edits to different profile fields, when the later request omits the earlier field, then both changes are retained. Verify the same ordering for complete address updates and verify every saved state preserves the default invariant.
- A failed default switch must restore the prior default. Concurrent creates into an empty book require coordination even when the address book is empty.
- Invalid or unavailable image replacement preserves the old reference; no upload or object deletion is performed here.
- **Concurrent-update wait acceptance**: Given a competing update prevents progress beyond the permitted wait, when a profile/address change cannot proceed, then it fails as a conflict with a safe retry explanation, performs no automatic retry and leaves every saved detail and default unchanged. Verify the complete failed operation.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001 — Scope**: Provide self-profile viewing/updating, limited other-user viewing and self-owned address CRUD for a single-vendor store. Exclude registration, login, password changes, account deletion, administration, seller/shop ownership, followers, shop ratings and image upload/storage implementation.
- **FR-002 — Identity**: Determine identity from the authenticated account; submitted user IDs never establish ownership. Every profile/address operation, including limited-profile viewing, requires authentication. No additional role, permission or account-status restrictions apply. Only the owner may change their profile or addresses. Identify accounts by immutable IDs so username changes do not invalidate an otherwise valid session or require signing in again; normal expiration and established authentication rules still apply.
- **FR-003 — Private profile**: Return id, displayName, username, email, phoneNumber, accountStatus, gender, dateOfBirth, joinedAt and profileImageId in a dedicated private response. Never return password/hash, roles, permissions or internal entity relationships. Nullable optional values may be represented as null.
- **FR-004 — Public privacy**: Public responses have the exact allowlist in Story 3. Exclude email, phone, addresses, birth date, gender, account status, authentication data, roles, permissions and unrelated identifiers. Apply this allowlist regardless of viewer privileges.
- **FR-005 — Updates**: Only displayName, username, email, phoneNumber, gender, dateOfBirth and profileImageId are editable. Attempts to modify id, password, accountStatus, joinedAt, roles or permissions must fail with invalid-input rejection (explicit contract assumption), never silently mutate protected values. Apply all requested changes atomically.
- **FR-006 — Uniqueness**: Username is nonblank and unique. Email may be absent but must be valid and unique when present. Reject blank updates. Same-user equivalent values succeed; another user's equivalent values fail as conflicts without changes, including simultaneous claims. Username/email comparisons are case-insensitive under the agreed normalization rules. Phone numbers are not unique.
- **FR-007 — Avatar**: Distinguish omission from explicit clearing. Use a durable image reference rather than an expiring link. Accept a replacement only when ownership and availability are verified. Reference changes never delete the independent image. Future image management must not block independent profile/address behavior; apply FR-019 until available.
- **FR-008 — Address ownership**: Each address belongs to exactly one user. Scope list/read/update/delete to that user. Missing and foreign IDs yield the same not-found rejection behavior; submitted ownership information must not override the authenticated identity.
- **FR-009 — Address validation**: recipientName and addressText are required and nonblank. recipientPhone is required and consists of exactly ten ASCII digits starting with zero. Create and update both require all three recipient fields. Every saved address has a definite default/non-default state. The address-phone rule does not apply to profile phoneNumber.
- **FR-010 — Defaults**: An empty book has no default; every non-empty book has exactly one. Apply all creation, update and deletion transitions in Story 2. The highest remaining address ID is the fallback.
- **FR-011 — Atomicity**: All changes to one owner's address book must be coordinated, including the first address. Checking capacity, changing defaults, saving and deleting form one indivisible operation. Failed operations leave the previous complete state intact, including failures after part of a default transition. Coordination for one owner must not prevent unrelated owners from making progress.
- **FR-012 — Responses**: Keep private-profile, limited-profile and address information separate. Address results contain only id, recipientName, recipientPhone, addressText and isDefault. A list contains all owned addresses, default first then descending ID; an empty book returns an empty list.
- **FR-013 — Errors**: Use the consistent, safe failure categories in Error Behavior. Distinguish invalid input, unavailable identity, applicable access denial, missing resources, conflicts and unexpected service failures without disclosing private information or internal diagnostics.
- **FR-014 — Data preservation**: Preserve existing accounts and saved data when introducing the feature or future integrations. Enforce agreed required values, lengths, uniqueness and single-owner relationships. Profile/address changes must never delete independent images. Verification must not alter customer or shared developer data.
- **FR-015 — Concurrent edits**: For overlapping profile/address updates, the later successful coordinated write wins for supplied fields. Omitted profile fields preserve the latest saved values. Address updates require all recipient fields and preserve the latest default flag when omitted or null. Do not reject a change solely because the customer viewed an older state. Recheck ownership, uniqueness, capacity and default rules against current state.
- **FR-016 — Address capacity**: Each user may save at most 20 addresses. Reject creates when the book already contains 20 with invalid-input rejection and a safe capacity message; leave all addresses and default flags unchanged. Check capacity as part of the coordinated change so simultaneous creates cannot exceed the limit. Existing-address updates and deletes remain allowed at capacity.
- **FR-017 — Concurrent-update wait failure**: When an operation cannot proceed because a competing change remains in progress beyond the permitted wait, reject it as a conflict with a safe message explaining that the customer may retry. Leave all profile/address/default values unchanged. Do not retry automatically; the customer decides whether to retry.

### Field Presence and Validation

Profile changes are selective: only supplied editable values may change.

| Fields | Omitted | Explicit null | Supplied value |
| --- | --- | --- | --- |
| username, email | Preserve | Reject as invalid input | Validate and update; blank rejected |
| displayName, phoneNumber, gender, dateOfBirth | Preserve | Preserve | Validate and update |
| profileImageId | Preserve | Remove reference | Validate ownership/availability and replace |

An empty profile object is a successful no-op (assumption). Wrong types and unsupported enum values fail with invalid-input rejection. Birth dates are date-only, not future dates. Empty strings do not become null or trigger clearing. Apply the following confirmed constraints before validation, comparison and persistence; never truncate silently.

| Field | Maximum length after trimming | Rule |
| --- | --- | --- |
| displayName | 100 | Optional; supplied blank rejected |
| username | 50 | Required nonblank; preserve casing for display; lowercase comparison |
| email | 254 | Optional in storage; valid format; lowercase storage/comparison |
| phoneNumber | 20 | Optional; supplied blank rejected; not unique |
| recipientName | 100 | Required nonblank |
| recipientPhone | 10 | Required; exactly `0[0-9]{9}` after trimming; not unique |
| addressText | 1000 | Required nonblank |

All maximum string lengths count Unicode code points after trimming, with supplementary characters counting once and combining code points counted separately. Do not change accents or combine equivalent character sequences during normalization. Boundary criteria include supplementary characters at the maximum and one code point beyond. Trim leading and trailing whitespace from all supplied string fields in profile/address requests. Preserve internal whitespace. Reject blank strings for both required and optional fields. Optional null/omission remains governed by the operation-specific tables; it does not override the rejection of explicit null username/email. Preserve other text casing and case-sensitive comparisons. Password handling is outside this update contract.

Email is stored and compared lowercase. Username retains supplied display casing but is compared using its lowercase form. Case-insensitive uniqueness must hold even for simultaneous claims. Accepted and saved values must obey the same limits.

**Additional acceptance scenarios**:

1. **Given** padded valid text with internal whitespace, **When** saved, **Then** only surrounding whitespace is removed; internal whitespace and casing remain, except email is lowercased.
2. **Given** any required or optional string field, **When** empty or whitespace-only text is supplied, **Then** invalid-input rejection and a field error are returned without mutation.
3. **Given** each bounded field, **When** an otherwise valid trimmed value equals its maximum length, **Then** it succeeds; one character over fails with invalid-input rejection without truncation. Padded valid ten-digit address phones succeed; internal whitespace fails.
4. **Given** another user's username/email, **When** a padded or differently cased equivalent is supplied, **Then** conflict rejection is returned. Equivalent values belonging to the current user succeed, preserving submitted username casing and lowercasing email.
5. **Given** concurrent claims by different users for case variants of one username/email, **When** completed, **Then** at most one claim succeeds and duplicates receive conflict rejection with restoration of the previous state.
6. **Given** repeated valid phone numbers, **When** saved for different users/addresses, **Then** no uniqueness conflict occurs.
7. **Given** an authenticated non-owner, **When** they submit a profile update with another user's identity, **Then** it cannot alter that other user's profile; protected id fields fail with invalid-input rejection.
8. **Given** no authentication, **When** any feature operation is requested, **Then** authentication-required rejection is returned; an authenticated viewer can retrieve a limited profile without additional role, permission or account-status restrictions.

| Address isDefault | Omitted/null | true | false |
| --- | --- | --- | --- |
| First create | Default | Default | Default |
| Later create | Non-default | Sole default | Non-default |
| Update non-default | Preserve | Sole default | Remain non-default |
| Update default | Preserve | Remain sole default | Reject as invalid input |

### Error Behavior

| Failure category | Trigger | Observable outcome |
| --- | --- | --- |
| Invalid input | Invalid field, protected-field change, invalid default change, capacity exceeded or invalid image selection | Safe explanation and relevant field feedback; no partial changes |
| Authentication required | Missing, invalid or expired authentication | No protected information is disclosed |
| Access denied | Genuine applicable denial under established policy | Safe denial; no new feature-specific role/status restrictions |
| Not found | Missing user or missing/foreign address | Missing and foreign addresses are indistinguishable |
| Conflict | Username/email conflict or a concurrent-update wait failure | Safe explanation and no changes; for concurrent updates the customer may retry, without automatic retry |
| Unexpected service failure | An unexpected failure prevents completion | Safe failure; never presented as valid input or a successful change |

All failures use a consistent representation identifying the category, safe explanation, affected operation, time and relevant field feedback. Never expose credentials, authentication secrets, internal diagnostics or another customer's private data. Technical status mappings and representation are defined in [HTTP contract](contracts/http.md).

### Key Entities

- **User**: immutable account identifier; displayName; unique nonblank username; protected credentials; optional unique valid email; phoneNumber; accountStatus (ACTIVE, INACTIVE, SUSPENDED, BANNED); optional gender (MALE, FEMALE, OTHER); optional dateOfBirth; immutable joinedAt assigned at creation; optional profileImage reference. No new account-creation workflow is included. Account-status initialization and lifecycle belong to the identity dependency.
- **Address**: address identifier; required recipientName, recipientPhone and addressText; a definite default/non-default state; exactly one owning User. Owned by the user's address book.
- **Media reference**: Optional link to the existing/future independent image through an approved durable identifier. Its storage, upload lifecycle and deletion are independent.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All documented valid profile and address journeys complete with the specified outcome and persisted values in acceptance verification.
- **SC-002**: Across all public-profile and address ownership acceptance cases, zero private fields or foreign addresses are disclosed.
- **SC-003**: Every documented omitted/null/value combination produces its exact specified outcome, with zero unauthorized protected-field changes.
- **SC-004**: Every completed concurrent address-write scenario leaves exactly one default for a non-empty book, including repeated two-client first-create, switch and delete/create scenarios; every failed operation leaves zero partial changes.
- **SC-005**: All invalid-input and conflict scenarios return the specified failure category and a safe explanation; no case exposes credentials or internal diagnostics.
- **SC-006**: A customer can save an address, make it default, retrieve it and remove it in one complete acceptance journey without support or manual data repair.

## Assumptions

### Repository Evidence and Dependencies

The existing application is a scaffold; no profile/address functionality is implemented. Technical inventory and constraints are recorded in [engineering constraints](contracts/engineering.md).

Authentication and image management are separate future features. They must share the same account identity and preserve account lifecycle and existing data. Independent profile/address behavior proceeds first; genuine authentication is required before customer access, and real image management before image-dependent operations. No account-creation or image-storage workflow is added here.

UTC defines the birth-date boundary. A change with no supplied fields succeeds without changes. Protected-field edits are rejected rather than silently ignored. No latency, throughput or uptime target is approved; address-list size is bounded by the 20-address limit.

### Confirmed Clarification Decisions (2026-10-26)

- **Q1 - Confirmed A**: All feature operations require authentication. Limited public profiles have no additional restrictions and exclude sensitive data. Only the owner can modify their profile.
- **Q2 - Confirmed custom rules**: Approved limits, trimming, blank rejection, operation-specific null/omission, lowercase email storage, preserved username display casing, case-insensitive identity uniqueness and non-unique phones are specified above.
- **Q3 - Confirmed behavior**: Failures have a consistent safe explanation and category for every operation, including authentication/access failures. The approved technical representation is preserved in [engineering constraints](contracts/engineering.md) and [HTTP contract](contracts/http.md).

The earlier Q1-Q3 decisions remain confirmed. This clarification session resolved token continuity, concurrent edits, address capacity and concurrent-update wait behavior. Authentication, media and lifecycle integration remain explicit planning dependencies. The new implementation-readiness decisions supersede prior broad external gates. Independent work proceeds first; future-dependent operations remain explicitly blocked.
