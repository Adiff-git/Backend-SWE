# Implementation Plan: Email Verification and Google Login

**Feature**: 006-email-google-auth | **Consolidated**: 2026-11-05
**Input**: [spec.md](spec.md). Resolve directory from .specify/feature.json; do not infer it from Git branch or switch branches.

## Summary

Local email/password registration creates only a pending registration, destination challenge and mail outbox. No local user, official-email/username reservation, authentication session or credentials exists until confirmation proves the initiating credential and mailbox and supplies the authoritative password. Confirmation creates verified user, role, session and refresh atomically. Preserve legacy users' approved access.

Add Spring Security Google OIDC login/new registration, explicit one-to-one Connect Google with fresh existing-account proof, optional first-password setup with revoke-all, six-digit destination proof, and PostgreSQL-backed single-use browser handoffs. Keep application access tokens in Authorization responses and refresh tokens in JSON; no application-token cookies or email-based automatic linking.

## Technical Context

**Language/Version**: Java 25, Spring Boot 4.1.2, existing Maven wrapper.
**Dependencies**: Existing MVC, Security/resource server, JPA, PostgreSQL, Flyway, Lombok and approved test stack. Explicitly requested OAuth2 Client uses Boot-managed spring-boot-starter-security-oauth2-client. Boot-managed spring-boot-starter-mail was explicitly approved and added during implementation. No Redis, new test library or plugin.
**Storage**: PostgreSQL shared across instances; data-model.md is authoritative for fields, locks, expiry and retention. Existing S3/image ownership remains unchanged; Google picture ignored.
**Testing**: JUnit 5, Mockito, AssertJ, MVC/MockMvc and PostgreSQL Testcontainers. Controlled JDK HTTP OIDC fixture and capture-mail adapter; no live Google account. Clock abstraction for deadlines in integration tests.
**Platform/type**: Existing containerized JVM modular-monolith backend, production HTTPS; frontend is an external contract.
**Performance/scope**: Exact spec lifetimes; indexed bounded cleanup, no DB locks across SMTP/provider calls, no invented SLA. One Google identity per account; existing role/status/token lifecycle retained. Human walkthrough remains separate evidence.

## Constitution Check

| Gate | Pre-design | Post-design |
| --- | --- | --- |
| Domain boundaries/interfaces/DTOs | Pass | auth orchestrates via UserService; no user → auth repository dependency |
| Dependency approval | Explicit approval received | Boot-managed OAuth2 Client and mail starters added; no test library/plugin introduced |
| HTTP/security/current-state | Pass | Explicit pending public routes, protected account mutations, original-session proof and safe errors |
| PostgreSQL/Flyway/atomicity/locks | Pass | Additive migrations, user and pending lock protocols and outbox atomicity |
| Storage/timestamps | Pass | Existing image rules retained; UTC Instant fields |
| Meaningful tests/discovery | Pass for design | Concrete MVC/service/repository/e2e/race tasks; no unexecuted pass claims |

No constitution exception. Approval to edit specifications does not approve new dependencies or deployment. Unknown deployment credentials are required configuration, never hardcoded fallback values.

## Project Structure

Artifacts: spec.md business requirements; plan.md architecture/gates; research.md technical rationale; data-model.md schema/state/locks; contracts/http.md public transport/errors; quickstart.md validation guide; tasks.md executable work; review.md concise decision history; checklists/ reviewer criteria.

Production remains under demo/src/main/java/com/example/demo: auth/{controller,service,service/impl,repository,entity,dto/request,dto/response,validator,converter,security,config}; user domain retains user/credential/email persistence through UserService. New services pair interfaces and impl classes. Tests mirror domains/layers under demo/src/test/java/com/example/demo with actual cross-domain flows in e2e. Migrations use next unused version under demo/src/main/resources/db/migration.

## Phase 0 — Research conclusions

The project has no request throttling. Ordinary authentication, authorization, atomic business transitions, expiry and one-use proofs remain enforced. No limiter infrastructure or dependencies are required.

## Phase 1 — Architecture and flow

- PendingRegistrationService orchestrates public initiation/resend/confirmation and delegates actual account creation to UserService only after proof. No initial password persistence. All pending credentials are hashed; outbox code payload encrypted. Resend rotates current credential/challenge.
- IdentityCommandService uses READ_COMMITTED/REQUIRED and outcome-return boundaries. Existing AuthServiceImpl rejects ambient transactions; transactional workflows reuse lower-level session/token services, not that facade. Invalid OTP checks do not exhaust challenges; uniqueness exceptions rollback completely before fresh outcome handling.
- GoogleAttemptService and framework AuthorizationRequestRepository persist five-minute correlation. Exact public Google chain handles provider start/callback and anonymous LOGIN exchange. Separate exact registration and profile-mutation chains enforce credentialed allowlisted CORS without changing the legacy catalog/credential CORS behavior. LINK/REAUTH exchange still validates bound bearer owner/sid even on the shared endpoint. Framework OIDC validation stays enabled; no stored authenticated HTTP SecurityContext or Google authorized-client credentials.
- RecentAuthenticationService issues one-use EMAIL_CHANGE/INITIAL_PASSWORD/CONNECT_GOOGLE proofs. Password proves local ownership when present; otherwise only already linked Google identity can prove it. CONNECT_GOOGLE reference follows attempt→handoff and is consumed with association creation under original active session. Separate provider proof establishes the identity being added.
- First-password setup writes BCrypt, consumes proof and invokes existing revokeAll with PASSWORD_CHANGED in one transaction. Failed mutation changes neither credential nor sessions; no replacement tokens on success.
- Browser slot/generation rejects stale attempts, not out-of-order Set-Cookie writes. Frontend serializes every Google initiation until its complete response; callbacks never write/delete/refresh correlation cookies on success or failure. Consume relevant attempt server-side and let cookie expire after five minutes. Missing/stale correlation requires settled outstanding requests and a new serialized initiation. Do not hold slot locks while waiting for owner locks; completion rechecks generation. Cookie never authenticates APIs.
- Mail adapter and durable worker lease briefly, send outside locks, and respect encrypted-secret retention. IdentityCleanupService schedules bounded removal and startup overdue-payload purge before dispatch.

## Delivery order and dependencies

Setup/config/fixtures → shared schemas, proof crypto, transaction primitives → US1 pending/local verification MVP → US2 Google login → US4 linking/step-up/password → US3 adversarial browser/cleanup completion → cross-cutting acceptance. Shared OAuth transport foundations precede US2/US4; US1 password-bearing email replacement is independent of Google, with Google-only step-up completed in US4. tasks.md owns exact IDs/dependencies and full coverage matrix.

## Complexity Tracking

Persistent short-lived state supports multi-instance replay/expiry guarantees. SMTP outbox addresses external side-effect consistency. No broker or unrelated architecture change.

## Design validation status

Architecture and requirement review are recorded in review.md. Current build/test evidence belongs in quickstart.md and task completion in tasks.md. Dependency approval has been received; deployment configuration and the human walkthrough remain external prerequisites.

## Implementation workspace baseline

2026-11-05: actual Git branch feature/upload-image; active directory specs/006-email-google-auth from feature.json. Existing worktree has 103 status entries, principally catalog/media/Compose/import removals and edits; these are outside this feature and must be preserved. Migration inventory V1–V12, including existing untracked V12__product_image_attachments.sql; V13 implements the identity feature; V14 removes historical rate persistence without changing V13 or V12. Baseline Java 25.0.4.1 Maven compile passed before feature changes. Root Git ignore covers target/class/jar/environment files; Docker build context demo requires its own .dockerignore.
