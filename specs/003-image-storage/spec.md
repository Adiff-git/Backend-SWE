# Feature Specification: Image Storage and Profile References

**Feature Branch**: Existing `feature/user-address`; no branch created.
**Created**: 2026-10-27
**Status**: Core implementation and automated verification complete; local persistence acceptance remains blocked (T044/T046).
**Input**: Supplied image-storage request; specification only, no implementation or detailed plan.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Upload and view an image (Priority: P1)

An authenticated eligible user requests upload permission, sends raw image bytes directly to private storage, completes validation and receives a stable image ID and temporary viewing access.

**Why this priority**: Publication is the foundation of every image consumer.
**Independent Test**: Upload and view each supported format without attaching to any resource.

**Acceptance Scenarios**:

1. **Given** an authorized user and JPEG, PNG or WebP declaration, **When** permission is requested, **Then** 201 returns a pending ID, signed PUT URL and absolute deadline.
2. **Given** that permission, **When** the browser PUTs raw bytes with required Content-Type and completes before expiry, **Then** 200 returns the ID and a temporary GET URL for validated final content.
3. **Given** a completed image, **When** its owner repeats completion even after the upload deadline, **Then** the original ID and fresh viewing access return without revalidation or copying.
4. **Given** a missing or foreign ID, **When** completion or owner renewal is requested, **Then** identical 404 responses disclose no ownership/state details.

### User Story 2 - Fail safely and recover (Priority: P1)

An uploader receives useful failures without unsafe images becoming attachable.

**Why this priority**: Accurate durable state and safe publication protect all users.
**Independent Test**: Seed pending records and controlled storage/database faults; inspect responses, records and objects.

**Acceptance Scenarios**:

1. **Given** empty, corrupt, undecodable, non-image or MIME-mismatched content, **When** completed, **Then** 422 rejects publication and the record stays pending until expiry.
2. **Given** a pending upload at or beyond its deadline, **When** completed, **Then** 409 returns and EXPIRED remains persisted despite the error.
3. **Given** absent staging content or content replaced with different bytes during validation/promotion, **When** completed, **Then** 409 returns without false completion; the caller may retry before expiry.
4. **Given** outage or failed copy, **When** completion fails, **Then** 503 returns without new committed completion.
5. **Given** successful copy then database failure, **When** completion fails, **Then** database changes roll back, staging remains, and a possible final orphan is reconciled separately.
6. **Given** committed completion then staging deletion failure, **When** responding, **Then** success remains 200 and sanitized operational evidence supports cleanup.

### User Story 3 - Manage my avatar (Priority: P2)

A user selects, replaces or clears a completed owned image; authorized profile viewers obtain temporary viewing access.

**Why this priority**: User profiles are the existing consuming domain.
**Independent Test**: Use completed image fixtures and exercise profile writes and private/public reads.

**Acceptance Scenarios**:

1. **Given** an owned completed image, **When** `PUT /api/me` supplies its ID, **Then** the reference persists and the response includes ID and temporary URL.
2. **Given** an avatar, **When** its update field is omitted, null or a valid explicit ID, **Then** preserve, clear or replace respectively without deleting objects.
3. **Given** a missing, foreign, pending, expired or malformed selection, **When** submitted with other profile edits, **Then** reject the entire update without changing any fields.
4. **Given** an attached avatar, **When** an authorized viewer reads the profile, **Then** temporary viewing access is returned without granting ownership or exposing other private fields.
5. **Given** an expired viewing URL, **When** the owner renews access or an authorized viewer rereads a profile, **Then** fresh access requires no reupload.

### User Story 4 - Run the same workflow locally (Priority: P3)

Developers can reproduce the browser workflow without production credentials.

**Why this priority**: Environment parity supports reproducible delivery.
**Independent Test**: Follow local setup and upload/view all three formats using disposable infrastructure.

**Acceptance Scenarios**:

1. **Given** initialized LocalStack and explicit origins, **When** a browser uploads and views an image, **Then** signed hosts are reachable and storage CORS permits the workflow.
2. **Given** production configuration, **When** access is prepared, **Then** objects remain private and credentials stay server-side.

### Edge Cases

- At exactly expiresAt a pending upload is expired; completed images remain usable after this deadline.
- Concurrent completion cannot publish unvalidated content or conflicting final references; losing requests may receive retryable 409.
- Reuse of a staging PUT URL after completion cannot alter final content.
- Missing final content/key on a completed record is an availability/integrity failure, not expiration; no successful attachment or renewal is reported.
- Invalid/nonpositive/out-of-range IDs return 400; valid missing/foreign IDs return indistinguishable 404.
- Empty strings, arrays, booleans and fractional avatar IDs are 400. Scalar avatars have no duplicate-ID or ordering semantics; lists are excluded.
- WebP must actually decode; an unavailable decoder never permits unvalidated acceptance.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Reuse current-state Bearer JWT authentication, account/session eligibility and trusted immutable principal identity. Creation requires authentication alone; completion and standalone URL renewal require authentication plus upload ownership, with no additional media permission or MANAGE_USER_PROFILE requirement. Profile attachment retains MANAGE_USER_PROFILE plus ownership.
- **FR-002**: Persist immutable required ownerUserId referencing the existing User, derived exclusively server-side. Client identity/key claims never establish ownership. Missing/foreign uploads are indistinguishable.
- **FR-003**: Accept exact image/jpeg, image/png and image/webp declarations only; missing/null/blank/unsupported types fail before creating records.
- **FR-004**: Generate unpredictable unique staging and distinct final keys server-side, for example staging/users/{userId}/{randomId} and images/users/{userId}/{randomId}. Clients cannot choose buckets or keys or write directly to final content.
- **FR-005**: Create PENDING metadata and a presigned PUT bound to staging key and Content-Type. Upload deadline equals the URL deadline. Signing/persistence failure cannot return successful creation.
- **FR-006**: Complete only authorized owned uploads strictly before their deadline; late attempts durably persist EXPIRED despite returning an error. EXPIRED is terminal and requires a new upload.
- **FR-007**: Before publication verify staging exists, nonzero bytes, stored Content-Type, successful actual image decoding and exact decoded-format agreement. Extensions or metadata alone are insufficient.
- **FR-008**: Published bytes must be the same content that was actually validated; object identity means byte-for-byte content equality, not the same storage object version or write event. Different bytes substituted during validation/promotion must not be accepted as validated content. Rewriting identical bytes is not a content change and need not be rejected solely because a write occurred. Final Content-Type and metadata must remain server-approved under FR-009, regardless of client metadata-only changes. Conditional reads/copies using observed ETag are an implementation suggestion; their assurance limits must remain explicit and must not be described as cryptographic content proof or immutable-version protection.
- **FR-009**: Create final content before committing COMPLETED/finalKey. Copy only approved Content-Type and server-approved metadata/headers, never arbitrary client metadata.
- **FR-010**: Protect completion's read/check/write sequence with an appropriate transactional concurrency safeguard. Selection of locking technique belongs to planning; no automatic application retry policy is implied. The simultaneous-completion integration test is deferred; the object-replacement integration test remains required. Concurrency safety is not deferred.
- **FR-011**: Delete staging only after database commit. Cleanup failure cannot turn committed success into failure. Repeated completed operations return the original ID and newly generated viewing access without processing/copying staging again.
- **FR-012**: Store stable IDs/keys, never presigned URLs. Owner renewal and authorized profile reads generate temporary viewing access without reuploading.
- **FR-013**: Attach only caller-owned COMPLETED images with valid nonempty finalKey and available final content. Reject the whole profile update on invalid selection.
- **FR-014**: Preserve existing profileImageId presence rules: omitted preserves, null clears, valid positive Long replaces. Preserve all unrelated profile update semantics. Reference removal/replacement does not delete objects.
- **FR-015**: Private and limited-public profile responses expose profileImageId as Long/null and add profileImageUrl as temporary URL/null; both null when absent. Existing authenticated public-profile visibility permits viewing the attached avatar, never generic foreign-image renewal or attachment.
- **FR-016**: Use domain-specific exceptions under the common application base and existing ApiError/ProblemDetail format and security handlers. Preserve timestamp/message/path/fieldErrors extensions and safe errors.
- **FR-017**: Enforce failure/retry behavior below. Database rollback never implies reversal of storage copies; failures must not falsely report publication.
- **FR-018**: Preserve header-only authentication. No cookie-specific CSRF token workflow applies under this transport; do not introduce global CSRF changes. Reassess protection if transport changes. Signed storage requests require no application JWT.
- **FR-019**: Keep credentials/tokens/signed URLs out of logs and errors. URLs are temporary bearer capabilities, not permanent public URLs. Capability responses use Cache-Control: no-store; possession permits access until expiry.
- **FR-020**: Support the same flow with LocalStack locally and private AWS S3 in production using environment configuration below.
- **FR-021**: Document operator-triggered reconciliation for abandoned staging, failed deletion and unreferenced final objects. Compare persisted lifecycle/references/deadlines, allow in-flight completion to finish and recheck before deletion; uncertain or referenced objects must not be deleted. No scheduled media cleanup is included.

### Key Entities

**ImageUpload** owns lifecycle and publication metadata; it has no back-reference to each consuming domain.

| Field | Contract |
| --- | --- |
| id | Generated immutable Long identifier |
| ownerUserId | Required immutable trusted reference to existing user |
| stagingKey | Required unique, maximum 512 characters |
| finalKey | Nullable before completion; unique when present, maximum 512 characters |
| contentType | Required, maximum 100 characters |
| status | PENDING, COMPLETED, EXPIRED |
| expiresAt | Required absolute upload deadline |
| createdAt | Required immutable absolute creation timestamp |

**Profile image reference**: User owns one nullable reference to a completed image; URL strings are not persisted.

**Lifecycle**: create → PENDING; successful validation/publication and commit → COMPLETED; late completion attempt → EXPIRED. Failed pre-deadline attempts remain PENDING. COMPLETED and EXPIRED have no outgoing transitions. Time passage alone makes pending completion ineligible without requiring an automatic persisted transition.

### HTTP Contracts

| Operation | Request | Success |
| --- | --- | --- |
| POST /api/image-uploads | JSON {"contentType":"image/jpeg"} | 201 {"id":123,"uploadUrl":"temporary capability","expiresAt":"2026-10-27T10:00:00Z"} |
| Direct storage PUT | Raw bytes to uploadUrl with required Content-Type, no multipart wrapper or application JWT | Storage success permits completion; does not complete the record |
| POST /api/image-uploads/{imageId}/complete | No body; identity from Bearer token | 200 {"id":123,"finalUrl":"temporary capability"} |
| GET /api/image-uploads/{imageId}/download-url | No body; owned completed image | 200 {"id":123,"finalUrl":"temporary capability"} |
| Existing PUT /api/me | Existing contract plus omitted/null/positive Long profileImageId | Existing 200 response plus image ID/URL |
| Existing GET /api/me and GET /api/users/{id} | Existing permission/privacy rules | Existing 200 profile response plus image ID/URL |

Creation requires a JSON object; unknown fields including owner/key/bucket parameters are 400. Renewal is a proposed owner-only contract needed for standalone image reuse. Upload URLs expire 15 minutes after creation; download URLs expire 15 minutes after each issuance, potentially earlier if signing credentials cease to be valid.

### Failure and Retry Behavior

| Condition | Status | State and next action |
| --- | --- | --- |
| Malformed request, unsupported declaration, invalid ID shape | 400 | No partial changes; correct input |
| Missing/invalid authentication or ineligible account/session | 401 | Existing generic authentication behavior |
| Valid caller lacks applicable permission | 403 | No changes or upload-detail disclosure |
| Missing/foreign upload or image reference | 404 | Identical safe response |
| Pending deadline reached / EXPIRED | 409 | EXPIRED persists; create new upload |
| Staging not yet uploaded | 409 | PENDING retained before deadline; upload then retry |
| Pending/expired attachment or renewal | 409 | No profile changes; complete or create new upload |
| Empty/corrupt/non-image/undecodable/MIME mismatch | 422 | No publication; replace staging and retry before expiry |
| Different-content replacement or state/lock conflict | 409 | No false completion; caller decides whether to retry |
| Storage outage, failed read/copy/signing, missing final content | 503 | No false completion or attachment; retry/investigate |
| Unexpected database failure, including after copy | 500 | Database rollback; possible final orphan; reconcile |
| Staging deletion failed after commit | 200 | Completion persists; operator cleanup retries |

If completion committed but response signing fails, 503 does not undo completion; retry returns the completed ID and fresh URL without copying. An uncertain network response is not proof of rollback. Profile writes must not report partial changes as success if URL preparation fails; preserve atomic business edits and a clear retry outcome.

### Configuration and Delivery Constraints (Explicit User Requirements)

These requested engineering constraints are separate from business outcomes and do not constitute an implementation plan.

- Use existing demo/ module and domain-oriented layers under com.example.demo. Separate storage infrastructure from media lifecycle/ownership. Controllers delegate; service implementations have interfaces; validators enforce business rules; repositories persist; converters map prepared data without queries/storage calls.
- Cross-domain consumers use service interfaces, never media repositories; keep dependencies acyclic. Avoid unnecessary factories, strategy layers, asynchronous jobs and extra infrastructure.
- PostgreSQL stores metadata/keys/references; S3 stores bytes. Use Instant and TIMESTAMPTZ for absolute timestamps and new Flyway migrations preserving existing data/IDs.
- Configure region, bucket, optional endpoint override, optional dummy local credentials, path-style access, and positive upload/download durations with explicit units. Upload lifetime is 15 minutes (900 seconds); download lifetime is 15 minutes (900 seconds). Each renewed download URL receives a fresh lifetime; renewal never extends a pending upload deadline.
- LocalStack uses Docker Compose, documented bucket initialization and optional data persistence. Explain Docker-internal versus browser-accessible hostnames. Sign for the intended client's reachable endpoint; never rewrite the hostname after signing.
- Production uses private AWS S3 and SDK default credentials, including IAM roles; no hardcoded credentials or secrets exposed to browsers.
- Storage bucket CORS separately permits required browser PUT/GET and Content-Type for explicit origins; retain backend security/CORS. Never make the bucket public to fix CORS.
- S3 SDK, WebP decoder and LocalStack test dependencies are not currently established; future dependency selections require constitution II approval. No dependencies are installed here.

### Acceptance Verification Requirements

- Service/validator/converter unit coverage without a Spring context: creation/mapping, all formats, ownership, exact deadlines, decoding/MIME mismatch, missing/changed objects, idempotence, fresh URLs and attachment rules.
- MVC coverage for HTTP contracts, authentication/current authority denial, ownership privacy and applicable header-only CSRF behavior; never weaken security for tests.
- PostgreSQL Testcontainers persistence coverage for constraints, avatar references, atomic updates, rollback and durable expiration; observe real commit/rollback boundaries rather than concealing them with test-level transactions.
- LocalStack integration coverage for real signed PUT/GET, declared Content-Type, object identity safeguards, copy/promotion and post-commit cleanup failure; no developer database or real AWS in automated tests.
- Reuse domain testdata fixture conventions and JUnit/Mockito/AssertJ. Test classes end Test for unit/MVC and IT for integration; methods use methodName_shouldExpectedBehavior_whenCondition. Reuse disposable PostgreSQL configuration and verify Maven discovery. Retain a deterministic integration test replacing the staging object between validation and promotion and asserting rejection with no false completion. Defer the simultaneous-completion integration test; retain sequential idempotence/state-conflict coverage and the required concurrency safeguard.

### Explicit Exclusions

No review/product domains, image lists/ordering, resizing, compression, thumbnails, application-defined size cap, multipart-to-backend uploads or public bucket. No automatic media expiry/abandoned-upload job, final deletion API or deletion on reference removal. Manual reconciliation is included; retention and automated deletion require a later explicit decision. No implementation, deployments or unrelated authentication changes.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All three supported formats complete upload and viewing with one stable image ID reusable without another upload.
- **SC-002**: Every invalid-content, foreign-owner and expired-pending acceptance case produces zero unauthorized attachments or upload-detail disclosures.
- **SC-003**: Every repeated successful completion retains the original ID and provides fresh viewing access, including after upload expiry, without duplicate publication caused by the repeat.
- **SC-004**: Every tested failure leaves accurate pending/expired/completed state; zero records falsely report publication and every expiry rejection preserves expired state.
- **SC-005**: Every avatar preserve/clear/replace case produces the requested result with zero unrequested edits or object deletions; authorized viewers regain access without reuploading.
- **SC-006**: A developer following local instructions can upload/view all supported formats without production credentials or manual database repair; the same user steps apply in production.

## Assumptions

### Repository Evidence and Compatibility

Inspected constitution, specifications 001/002, current authentication configuration and identity boundary, User entity/service/validator/converter/DTO/controller, ApiError/factory, application/test configuration, test inventory and Maven configuration. Current source supersedes older spec prose claiming profiles/authentication remain unimplemented.

- Existing header Bearer JWT authentication checks current sessions/accounts/authorities. MANAGE_USER_PROFILE and MANAGE_ADDRESS exist; no media permission exists. Unlisted endpoints are denied, so media routes require explicit authorization.
- Existing security chains disable CSRF under stateless header-only transport; this feature introduces no global security weakening or cookie transport.
- User has Long ID but no avatar association. UpdateUserRequest rejects non-null profileImageId with MediaUnavailableException; responses use null-only Void placeholders. This feature replaces that deliberate unavailable boundary with Long references while preserving feature 001 omitted/null semantics.
- Only user profiles are existing consumers. No media/storage domain, S3 dependencies, LocalStack setup or established WebP decoder was found. These are future delivery dependencies, not completed capabilities.
- ApiError extends ProblemDetail, satisfying the requested existing error format and constitution. Tests use disposable PostgreSQL JDBC Testcontainers; Maven has Failsafe configuration. No application tests were run for document-only work.
- Attached-avatar visibility follows existing authenticated limited-public profiles. Standalone renewal remains owner-only; recipients of signed capabilities can use them until expiry.
- Manual reconciliation is intentionally proportionate. No automatic cleanup or object deletion policy is assumed. Operational resource protection must be considered in planning without silently introducing an application size cap.

### Confirmed Decisions (2026-10-27)

- **Q1 — Media authorization**: Authentication alone permits upload creation; authentication plus ownership permits completion and standalone download-URL renewal. No media permission is introduced, and MANAGE_USER_PROFILE is not required for those operations. Existing profile read/edit permissions remain unchanged.
- **Q2 — URL lifetimes**: Upload and download URLs each have a configured lifetime of 15 minutes (900 seconds). The upload deadline equals creation plus that lifetime; completion at that instant is expired. Download renewal starts a fresh 15-minute lifetime without changing lifecycle or upload deadlines.
- **Q3 — Test scope**: Postpone the simultaneous-completion integration test. Retain the object-replacement-during-validation/promotion integration test. Implementation concurrency safeguards remain mandatory; test deferral does not establish verified simultaneous-completion behavior.

### Additional Acceptance Scenarios

1. **Given** an eligible authenticated user lacking MANAGE_USER_PROFILE, **When** creating, completing or renewing their own upload, **Then** the operation is allowed if otherwise valid; editing their profile still returns 403 without that permission.
2. **Given** a pending upload created at time T, **When** completion is attempted at T plus 900 seconds, **Then** 409 is returned and EXPIRED persists; completion strictly before that instant may succeed.
3. **Given** a completed image, **When** download access is issued at time D, **Then** its configured expiry is D plus 900 seconds; a later renewal provides a new expiry without reuploading.
4. **Given** staging content replaced with different bytes between validation and promotion, **When** the retained integration test runs, **Then** completion returns 409, does not commit COMPLETED and does not publish replacement bytes as validated content.

### Content Identity Decision (2026-10-27)

Confirmed by the user: "same object identity" means the same content that was checked. Byte-for-byte identical content may be published even if it was rewritten; there is no requirement to reject every write or bind an immutable object version. A conservative source-precondition conflict may still return retryable 409 rather than bypass the safeguard. A metadata-only rewrite must not transfer arbitrary metadata to the final image: approved Content-Type/headers remain server-controlled. ETag is an opaque conditional-operation safeguard, not independent cryptographic evidence of content equality.

Additional acceptance: rewriting identical bytes with an unchanged source condition does not itself invalidate the content and may complete if all other checks pass. A failed source condition remains 409, including an identical-content rewrite that changes that condition. Metadata-only changes cannot alter approved final metadata. Different replacement bytes must never be published as the bytes already validated. Retain the deterministic different-content replacement integration test; simultaneous-completion integration testing remains deferred.

### Unresolved Decisions

Original feature decisions are resolved. Implementation found a local-environment decision: approve a persistence-capable LocalStack image/account configuration, or explicitly defer local persistence. The currently approved community image failed native persistence restart acceptance. Simultaneous-completion integration testing remains explicitly deferred.


### Latest user override ? 2026-10-27

Allow-all API CORS (origins/methods/headers), retaining Bearer JWT authorization and allowCredentials=false, supersedes earlier explicit-origin restrictions. Bucket initialization is deferred; Compose must not reference ignored docker/ files or require an initialized bucket for health. docker/ and the fixture media README are excluded from Git. Future bucket CORS allows all origins/headers and all S3-supported methods while the bucket remains private. See research.md for the decision and fixture attribution. Existing persistence work remains deferred/open.
