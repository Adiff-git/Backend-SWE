# Technical research and decisions

## 1. Framework/dependencies

**Decision**: Keep Java 25/Boot 4.1.2 and Boot-managed Security OIDC Client; use the explicitly approved Boot-managed mail starter. Reuse existing tests plus JDK HTTP server.
**Rationale**: Native framework validation and SMTP adapter avoid handwritten protocols and extra auth frameworks.
**Alternatives**: Manual Google JWT trust, custom SMTP, new mock-server dependency rejected.
Sources: [Boot migration guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide), [OIDC configuration](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/advanced.html); local demo/pom.xml.

## 2. Domain/transactions

**Decision**: auth owns pending/proof/provider/session orchestration; UserService owns user mutations. READ_COMMITTED/REQUIRED, separate nontransactional facade and expected-outcome records. No calls to AuthServiceImpl inside new ambient transactions.
**Rationale**: Existing facade rejects ambient transactions; business state must remain atomic. Pending owners have no user row; their lock protocol must differ from existing users.
**Alternatives**: auth accessing UserRepository, user importing auth repositories, catch-and-continue after JPA integrity failure rejected. data-model.md defines separate lock graphs and conflict outcomes.

## 3. Pending account creation

**Decision**: No password persistence or official identity reservation before proof. Authoritative password is explicitly supplied on confirmation. Rotate initiating secret/code together on resend.
**Rationale**: Prevents unverified email reservation and activation of a password selected by another registrant. Same-destination pending attempts cannot authenticate or overwrite existing users. Global email uniqueness arbitrates pending versus Google signup atomically.
**Alternatives**: Persisting an attacker-selected initial password, unique pending destination, public-ID-only resend and replaying token responses rejected. Recovery from missing current initiating secret starts an independent attempt.

## 4. Google identity/handoff

**Decision**: Validated canonical Google issuer plus opaque case-sensitive subject; dual provider/user uniqueness. New social users have null local password, verified official email, default role/ACTIVE. Later provider email is metadata only. Frontend S256 PKCE binds 60-second hashed application codes; provider state/nonce remain separate. Framework AuthorizationRequestRepository uses constrained encrypted state in PostgreSQL, no Java native deserialization or retained Google tokens.
**Rationale**: Maintains account identity independent of mutable claims and preserves existing app token transport across redirects.
**Alternatives**: Email auto-link, usable placeholder passwords, tokens in URLs/cookies and memory-only state rejected. Username suffix allocation uses explicit conflict-safe inserts, not general transaction retries.

## 5. Linking/fresh proof

**Decision**: Three operation types: EMAIL_CHANGE, INITIAL_PASSWORD, CONNECT_GOOGLE. Link proof issued from current password or already linked Google identity, bound to original owner/session and actual authentication time. Link attempt/handoff stores proof reference; association/proof/handoff commit atomically. Google REAUTH forces fresh provider authentication and validates auth_time. First password consumes Google proof and uses existing PASSWORD_CHANGED revoke-all reason.
**Rationale**: Merely possessing a long-lived session or authenticating the newly added identity cannot establish fresh ownership of the existing account.
**Alternatives**: Self-proving first Google identity, JWT issuance timestamp as provider freshness and retaining sessions after initial password setup rejected.

## 6. Proof secrecy and external mail

**Decision**: Six-digit strings generated securely, HMAC bound to challenge/owner/destination/purpose, constant-time compare, independent external secret. AES-GCM outbox stores delivery payload only until terminal processing/expiry. Worker leases in short transactions and sends outside locks; at-least-once same-code delivery, bounded retry schedule and cleanup in data-model.md.
**Rationale**: Six-digit unkeyed hashes are cheaply guessed offline; database rollback cannot undo email. Pending registration and existing-user email workflows retain their own atomicity even during SMTP outage.
**Alternatives**: Plaintext OTP/logging, SMTP under DB locks, claiming exactly-once delivery rejected.

## 7. Request handling

**Decision**: No request rate limiting, resend delay or failed-proof lockout. Remove counters, admission reservations, reconciliation and rate-only client-IP configuration. PostgreSQL continues to provide business-state atomicity. Apply V14 after immutable V13 to remove accounting tables/columns while preserving existing accepted outbox/challenge/account state.
**Rationale**: The approved scope permits unrestricted endpoint requests; validation, proof expiry, one-use consumption and authorization still apply. SMTP retry delays are delivery behavior and remain unchanged.

## 8. Browser concurrency and cleanup

**Decision**: Callback responses never set/delete/replace/refresh the correlation cookie, on success or failure. Consume/invalidate the relevant server attempt and let the cookie expire at its existing five-minute lifetime. Keep slot generations and one-active-attempt checks. Frontend serializes all Google initiation requests, across purposes and coordinated tabs, until the complete response has settled. Server checks cannot control browser Set-Cookie response ordering; a client violating serialization may strand correlation, but cannot authorize stale attempts. Wait for every outstanding initiation response, discard stale flow data, then restart serially when cookie is missing/stale/expired.
**Rationale**: A server-validated old callback's delayed cookie deletion could erase a newer cookie after the database check. Emitting no callback cookie write removes that response-order race; browser initiation serialization addresses the separate initiation-response race.
**Alternatives**: Conditional callback deletion based only on server generation rejected because the browser has no conditional Set-Cookie compare-and-swap. Serializing only no-cookie/first initiation is insufficient. Cookie remains Path=/, Secure/HttpOnly/SameSite=Lax/300s and never authenticates APIs. Replay is rejected by consumed server state even while cookie survives.
**Cleanup**: Delete expired server rows under data-model.md deadlines; physical cookie expires naturally. The worker invokes the tested overdue-payload purge before every dispatch, including startup; actual production ingress and human evidence remain external validation.
