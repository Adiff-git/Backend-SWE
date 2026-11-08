# Tasks: Email Verification and Google Login

Task IDs remain stable. Markers record implementation evidence, not reviewer checklist quality. T013-T019 cover the approved request-throttling removal; their validation is recorded in quickstart.md. No new dependencies, commit or deployment is authorized.

## Setup

- [X] T001 Inventory branch, feature.json and migration versions; preserve unrelated work and existing V12 without switching branches.

- [X] T002 Record explicit mail dependency approval before adding the Boot-managed mail starter and approved OAuth2 Client to demo/pom.xml; add no test dependencies.

- [X] T003 Add ControlledOidcProvider and GoogleIdentityTestData fixtures with real signed claims and controlled endpoints.

- [X] T004 Validate IdentityProperties and VerificationProperties: exact origins/destinations, Google/SMTP settings, independent HMAC/AEAD keys and production HTTPS.

## Foundation

- [X] T005 Add V13__email_google_auth.sql for pending/Google/proof/browser/challenge/outbox persistence and nullable local passwords; preserve historical users and applied migrations. V14 defines current schema after removal.

- [X] T006 Add populated legacy upgrade, ownership, unique-identity and timestamp constraints in IdentityMigrationIT; update CatalogMigrationsIT inventory.

- [X] T007 Extend user-owned entity/service persistence for local/social creation, official-email evidence and locked credential changes, preserving domain boundaries.

- [X] T008 Extend private user DTO/converter with emailVerified and hasLocalPassword; preserve legacy identifiers and reject direct profile email writes.

- [X] T009 Implement IdentityProofService/impl secure random secrets and six-digit strings, HMAC binding, constant-time compare and AES-GCM payloads; never log secrets.

- [X] T010 Map OAuthLoginAttempt, OAuthHandoff, RecentAuthProof and OAuthBrowserSlot plus repositories: operation enums, LINK proof FK, immutable original-session/owner binding, current generation and uniqueness.

- [X] T011 Implement IdentityCommandService/impl and IdentityCommandOutcome with READ_COMMITTED business transactions, separate pending/user locks, rollback-safe conflicts and no ambient AuthService facade calls.

- [X] T012 Test OAuth state persistence/expiry and pure cryptography in OAuthStateRepositoryIT and IdentityProofServiceImplTest, including tampering and key mismatch.

- [X] T013 Add V14__remove_request_rate_limiting.sql dropping historical accounting tables/reference columns and failed-code cap while preserving business state and immutable V13.

- [X] T014 Extend IdentityMigrationIT for populated V13-to-V14 upgrade preserving legacy user, pending/challenge/outbox; verify absent rate structures and update catalog version inventory.

- [X] T015 Test unrestricted repeated starts, failed exchanges, password/code failures followed by valid proof, immediate repeated sends/resends and absence of 429/Retry-After in GoogleSecurityConfigurationIT, EmailVerificationFlowIT and PendingRegistrationServiceIT.

- [X] T016 Remove rate-only entities/repositories/services/filter/scheduler/IP resolver/proxy properties and tests; remove rate keys and reservation persistence fields.

- [X] T017 Simplify pending/email/Google/reauthentication and mail orchestration to normal atomic business transactions; retain proof expiry/rotation, rollback, locks and one-use semantics.

- [X] T018 Remove cooldown DTO metadata, rate-specific errors and Retry-After handling; update converters, controller fixtures and HTTP contracts.

- [X] T019 Consolidate spec/plan/research/data-model/HTTP/quickstart/checklists for unrestricted requests; retain stable task IDs and requirement coverage.

- [X] T020 Implement GoogleAttemptService/impl and browser slot generation/start ticket persistence with S256 validation, supersession and safe current-cookie checks.

- [X] T021 Implement PostgresAuthorizationRequestRepository and GoogleOidcConfiguration with real OIDC validation/scopes/freshness and encrypted state; initiation alone writes the five-minute cookie, callbacks consume server state without cookie mutation.

- [X] T022 Validate attempt/exchange DTOs and GoogleAttemptValidator against missing/null/blank/wrong-type/unknown fields and forbidden owner/provider/destination overrides.

- [X] T023 Wire exact public Google and pending-registration routes, protected original-session LINK/REAUTH, allowlisted Origin/JSON/CORS and legacy credential/catalog chains.

## US1: Pending registration and email proof

- [X] T024 [US1] Add RegistrationVerificationControllerTest and registration controller/service/API contract coverage for pending-only initiation, authoritative confirmation password and credential transport.

- [X] T025 [US1] Cover leading zeros, expiry, repeated invalid-code checks, immediate resend rotation, unchanged official email and SMTP failures in PendingRegistrationBoundaryIT, EmailVerificationFlowIT and VerificationMailDeliveryIT.

- [X] T026 [US1] Test exclusive challenge ownership/active uniqueness in EmailVerificationRepositoryIT; retain outbox rollback/lease/delivery and competing confirmation coverage in their service/flow tests.

- [X] T027 [US1] Test PendingRegistrationRepositoryIT, PendingRegistrationServiceIT and boundary cases: no stored initial password/account/official reservation, current credential/generation, expiry/replay and confirmation password authority.

- [X] T028 [US1] Map PendingRegistration and repository with normalized nonunique destination, hashed credential, generation/state/expiry and parent locking.

- [X] T029 [US1] Define pending registration confirmation/resend request DTO validation, response converter and no authentication-token metadata before confirmation.

- [X] T030 [US1] Map EmailVerificationChallenge/VerificationMailOutbox and repositories with XOR ownership, one active purpose per owner, HMAC and encrypted delivery state; no failed-attempt counter.

- [X] T031 [US1] Implement EmailVerificationService/impl with ten-minute six-digit proof, immediately available resend, historical current-address verification and fresh EMAIL_CHANGE proof/uniqueness checks at confirmation.

- [X] T032 [US1] Implement VerificationMailService/impl and worker: transactional encrypted enqueue, lease/recovery, SMTP outside locks, 5/30/120-second retries (four total), superseded-message handling and payload erasure.

- [X] T033 [US1] Implement PendingRegistrationService/impl and validator: pending/challenge/outbox atomic initiation, immediate rotation, authoritative password and atomic verified account/session creation; pending then challenge then outbox locks.

- [X] T034 [US1] Implement RegistrationVerificationController confirm/resend and strict DTOs; issue credentials only after successful committed confirmation, never replay token responses.

- [X] T035 [US1] Route AuthController/RegisterRequest and AuthTransactionService through pending-only initiation and lower-level verified account/session creation; preserve legacy and Google sessions.

- [X] T036 [US1] Implement RecentAuthenticationService/impl and ReauthenticationController: current-password EMAIL_CHANGE/CONNECT_GOOGLE proof, actual-auth five-minute expiry, original active session/owner/operation binding and generic failure.

- [X] T037 [US1] Implement EmailVerificationController create/get/confirm, DTO/converter metadata, current-address historical verification, non-owner 404, verified 409 and safe failures.

- [X] T038 [US1] Preserve legacy username/email-less/unverified-user access and reject missing local password generically; test historical authenticated verification.

- [X] T039 [US1] Exercise simultaneous pending confirmations, separate same-email pending attempts and pending versus Google-registration conflicts with one winner and whole losing transaction rollback.

## US2: Google login

- [X] T040 [US2] Cover Google login transport/validation, access-header and refresh-body semantics, invalid bearer isolation and safe claim/eligibility errors.

- [X] T041 [US2] Test new/returning Google accounts, absent/unverified new email, canonical issuer/opaque subject, changed provider metadata and explicit existing-email collision recovery.

- [X] T042 [US2] Implement GoogleHandoffService/impl for hashed 60-second application codes, S256 verifier, consumed-state checks, immutable flow bindings and concurrency-safe redemption.

- [X] T043 [US2] Implement GoogleExchangeService/impl dispatch, transaction outcome and safe conflicts; no credentials before successful redemption.

- [X] T044 [US2] Implement GoogleLoginService/impl: validated claims, atomic verified-email ACTIVE/default-role social account with null password/derived username, one association and no email auto-link/profile overwrite.

- [X] T045 [US2] Implement GoogleLoginController and converters; fixed redirect allowlist, no-store responses, application access header/refresh JSON, no application-token cookies.

- [X] T046 [US2] Complete controlled-provider GoogleLoginFlowIT through real security filters, login/refresh/logout and existing account eligibility/permissions.

- [X] T047 [US2] Race first login, handoff redemption and global official-email claims; enforce one account/session outcome and rollback losing state.

- [X] T048 [US2] Test unchanged historical login identifiers, Google-only local-password rejection and returning identity profile/email preservation.

## US4: Linking and first password

- [X] T049 [US4] Test CONNECT_GOOGLE fresh ownership proof at initiation and completion: expired/replay/wrong-owner/session/operation, revoked session and no partial association.

- [X] T050 [US4] Test idempotent same-link/rejected replacement, already-linked-only Google step-up, absent-password policy, setup rollback and all-session revocation.

- [X] T051 [US4] Implement GoogleConnectionService/impl: original active session, separate Google identity proof, atomic proof/handoff/association consumption and preserved official profile/email.

- [X] T052 [US4] Implement GoogleReauthenticationService/impl using already linked identity, provider auth_time/max_age=0 and five-minute actual-auth operation-specific proofs.

- [X] T053 [US4] Implement InitialPasswordService/impl with absent-password lock/recheck, fresh INITIAL_PASSWORD proof, BCrypt and atomic PASSWORD_CHANGED revoke-all; no replacement credentials.

- [X] T054 [US4] Implement GoogleConnectionController and account attempt DTOs for protected connection/status and operation-bound reauthentication starts.

- [X] T055 [US4] Implement InitialPasswordController/DTO with current password rules, 204 success and conflict on already-set password.

- [X] T056 [US4] Complete GoogleConnectionFlowIT: explicit linking, later Google login, optional password and fresh dual-method login, Google-only email change and proof reacquisition.

- [X] T057 [US4] Race subject ownership, concurrent proof consumption, first-password setup versus refresh; preserve failed mutation state and prevent partial links.

- [X] T058 [US4] Verify permissions/status/original active session again at completion and new provider identity cannot self-prove local ownership.

## US3: Browser recovery and cleanup

- [X] T059 [US3] Test exact security chains, origins, malformed/repeated requests and no request throttling in GoogleSecurityConfigurationIT.

- [X] T060 [US3] Cover delayed old callback after newer initiation, superseded/replayed callback with surviving cookie, missing/expired cookie, overlapping initiation response disorder and serialized safe restart.

- [X] T061 [US3] Test actual provider signature/issuer/audience/expiry/state/nonce/auth_time errors, cancellation and outage through real filters with safe responses and unchanged account/session state.

- [X] T062 [US3] Implement safe Google success/failure handlers, no callback correlation-cookie writes and fixed secret-safe redirects; server attempt invalidation controls replay.

- [X] T063 [US3] Implement IdentityCleanupService/repository/scheduler with bounded FK-safe cleanup and session-cleanup compatibility, 24-hour state retention and stricter outbox purge.

- [X] T064 [US3] Complete GoogleBrowserFlowIT and GoogleAttemptStateIT with generation checks, previous binding recognized only for initiation, current-only callback proof and no stale mutation.

- [X] T065 [US3] Test exact cleanup/expiry deadlines in IdentityCleanupIT/GoogleHandoffIT and dispatch retries/leases/supersession/recovery in VerificationMailDeliveryIT, using controlled clocks without sleeps.

## Cross-cutting validation

- [X] T066 Complete IdentityException/API error mapping for 400/401/403/404/409/503 safe codes, no raw provider/secret data and no success responses on failure.

- [X] T067 Update .env.example and local/prod properties with external Google/SMTP/HMAC/AEAD settings and fail-closed enabled production validation; no secrets or rate-only configuration.

- [X] T068 Verify audit/log redaction and after-commit events for proof/link/session changes, no OTP/token/provider payload disclosure.

- [X] T069 Run meaningful existing auth/user/catalog/media/wishlist regressions and controlled feature tests; record actual discovery/exit codes in quickstart.md. Rerun for the removal under T015.

- [ ] T070 Complete external frontend serialization/same-site integration, real Google/SMTP and production deployment checks plus five-person SC-006 walkthrough. External environment/participants unavailable; do not claim backend tests certify these.

- [X] T071 Reconcile implemented task markers, paths, dependency and FR/SC coverage against primary artifacts; preserve unrelated work and do not commit/push.

## Dependencies and execution order

T001 precedes migrations; T005 precedes mapping/repository execution. T007-T008 modify the same user files sequentially. Crypto interfaces precede service integration. T013 precedes T014; T016-T018 precede removal verification T015 and final reconciliation T019. Preserve V13 checksums and apply V14 on upgrade.

US1 depends on persistence/proof/business transaction foundations. US2 and US4 share browser/handoff foundations; password-backed US1 email change is independent of Google, while Google-only step-up depends on US4. T049-T058 require original-session linking proof enums/FKs already defined in T010. Cleanup must retain the same owner/leaf lock graph. No DB lock spans SMTP or provider network calls. Frontend serialization is an external T070 obligation; server generation tests cannot certify browser implementation.

## Requirement-to-task coverage

| Requirement | Tasks |
| --- | --- |
| FR-001 | 024-039 |
| FR-002 | 020-023, 040-058 |
| FR-003 | 049-058 |
| FR-004 | 021-023, 040, 045-046, 066 |
| FR-005 | 010, 041-044, 049-058 |
| FR-006 | 041-048 |
| FR-007 | 007-008, 027-039, 044 |
| FR-008 | 024-039, 052, 056 |
| FR-009 | 004, 021, 044, 067 |
| FR-010 | 020-023, 060-064 |
| FR-011 | 021-023, 045, 060-062, 068 |
| FR-012 | 015, 040, 046, 059-062, 066 |
| FR-013 | 003, 006, 012, 014-015, 024-028, 039-041, 046-050, 056-065, 069-070 |
| FR-014 | 005-008, 027-028, 035, 038-039, 048 |
| FR-015 | 010-012, 020-022, 042-043, 060-065 |
| FR-016 | 020-023, 060-064, 070 |
| FR-017 | 009, 015, 024-039, 065 |
| FR-018 | 010, 036, 049-058 |
| FR-019 | 014-015, 026-028, 039, 047, 049-050, 057, 060-065 |
| FR-020 | 032, 063, 065, 067, 070 |
| FR-021 | 013-019, 025, 033, 036, 059, 067, 069 |
| SC-001 | 024-039, 049-058, 069 |
| SC-002 | 041, 046-050, 057-058 |
| SC-003 | 023, 040-048, 058 |
| SC-004 | 024-039 |
| SC-005 | 060-062, 064, 068, 070 |
| SC-006 | 070 |
| SC-007 | 049-058 |
| SC-008 | 013-015, 025-028, 039, 059, 063-065, 069 |

Coverage numbers denote stable task IDs (e.g. 013-019 means T013 through T019). Source files follow demo/src/main/java/com/example/demo/auth and mirrored test layers unless a task names another domain; persistence mechanics are in data-model.md and transport details in contracts/http.md. Tasks are executable work, not a second policy definition.
