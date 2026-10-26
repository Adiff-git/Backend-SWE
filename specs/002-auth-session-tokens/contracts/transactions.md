# Transaction and Concurrency Protocol

## Entry and lock ordering

Production controller -> nontransactional AuthService facade -> distinct proxied AuthTransactionService mutation. Default REQUIRED / PostgreSQL READ COMMITTED. No outer request transaction, self-invocation assumption, noRollbackFor blanket, REQUIRES_NEW, SERIALIZABLE or automatic retries. Commit must complete before a success response or a security rejection whose state must persist.

Shared order for every cooperating writer: owning User row (ascending IDs for multi-owner batches), then AuthenticationSession rows by UUID stable order, then RefreshCredential rows by numeric ID. Reuse UserService.getUserEntityForUpdate; auth never directly accesses UserRepository. User lock is coordination, not an implicit child-row lock. Existing profile username/email updates follow the same owner lock. Cleanup also follows owner-first order in bounded batches and cannot delete active-session history.

Candidate identifier/hash lookup without locks only obtains routing IDs. Never use submitted userId/sessionId as proof. Acquire User lock, then reload/revalidate actual identifier/password/status/authorities/session/credential and current Clock time. If candidate ownership changed/deleted, reject generically; never authenticate a different unlocked principal. Login's AuthenticationManager check is inside this coordination, without stale provider/UserDetails caches.

## Workflows

| Operation | Inside coordinated transaction | After commit |
| --- | --- | --- |
| Registration | User service creates ACTIVE/USER account+assignments and flushes; create 7-day session/refresh; authenticate/encode/sign using real local providers; failures throw/rollback all initialization | Publish Authorization/header TokenResponse only on success |
| Login | Resolve/recheck same locked User and current identifier; AuthenticationManager verifies current exact password/eligibility; create independent session/current refresh; sign | Publish only successful committed pair |
| Refresh current | Find stored digest routing candidate; User -> session -> credential locks; check expiry/revocation/current User; consume old+flush; insert one replacement; sign capped access; success outcome | Publish pair for same sid/deadline |
| Refresh consumed | After expiry/session checks, recognize actual consumed digest; if active session, set revokedAt/reason REFRESH_REUSE; return rejection normally | Facade raises generic 401 after commit; replacement/all access unusable |
| Refresh unknown/revoked/expired | No unrelated session mutation; return rejection | Generic 401 |
| Logout recognized unexpired | Revoke active session LOGOUT; retained consumed credential identifies same family without refresh-reuse semantics; already revoked is no-op | 204, no credentials |
| Logout unknown/absolutely expired | No mutation; rejection | Generic 401, independent of cleanup |
| Password change | User lock; revalidate access sid and current status/session after lock; check current exact password and different acceptable new password; User service writes encoded replacement, auth revokes all account sessions; flush/commit atomically | 204; no new pair; client reauthenticates |

If a consumed token is presented for an active session belonging to an account that is now disabled, detected reuse still revokes that session before rejection. Revoked/expired sessions remain unusable regardless of token row state. Treat consumed history as evidence, never as a current secret. All mutations recheck time after waiting for locks.

Signing/persistence failure during register/login/rotation throws inside the transactional service and restores account/session/current-secret state. No credentials leave the process before commit. A committed success whose HTTP response is lost remains committed; retrying consumed refresh may revoke per the strict contract.

## Commit-before-401 boundary

RefreshOutcome is internal: IssuedPair or Rejected (including ReuseRevoked). For reuse, transaction updates revocation and returns normally; proxy commit occurs; the facade converts the returned outcome to a generic authentication exception. Never throw the auth exception from within that transaction, catch a rollback-only exception and continue, or return a rejection after partial registration/password writes. If commit fails, return safe infrastructure failure rather than pretending revocation succeeded. Tests must query persisted state from a new connection after the actual HTTP 401.

Service contract disallows an unexpected ambient transaction around facade invocation. Test and production entry paths must exercise the real proxy commit boundary. Detect/document violations instead of silently using unusual propagation to compensate.

## Race outcomes

- Same-token refresh A/B: first may rotate successfully; second observes consumed and durably revokes. At most one unconsumed row; final session revoked; A's returned pair fails subsequent authentication.
- Refresh/logout: whichever runs first, completed logout leaves the family revoked; a refresh ordered afterward fails. Retained consumed credential is valid for logout of an unexpired family.
- Password/login: login ordered first is included in all-session revocation. Password ordered first forces login's under-lock current-password check to use the replacement; old password cannot create surviving sessions afterward.
- Password/refresh: refresh first is later revoked; password first causes refresh to observe revoked state. No child-only lock gap.
- Profile email/username/login: same User lock orders changes; login re-resolves exact identifier under lock so an old email cannot authenticate after replacement or a candidate switch authenticate another account.
- Unknown random secret: no digest match means no target, no revocation and generic failure; IDs cannot be supplied to choose a victim.
- Ordinary bearer authentication reads current state per attempt without locking every query. Requests authenticated before revocation/role/status commit may continue; attempts beginning after commit must observe it. No stale authority/session cache.

Lock timeouts follow existing safe coordination-conflict handling, roll back the attempted mutation and do not automatically retry. Do not misclassify unexpected SQL failures as credential failures. BCrypt work under the User lock is intentional to avoid password races; signing is local and bounded, with no S3/mail/network calls while holding locks.

## Acceptance evidence

Independent PostgreSQL connections, real transactions and barriers/latches must prove all race pairs and fresh-state checks. Verify rollback after consumed flush/insertion/signing failure, password persistence failure and registration assignment failure. Verify 401 reuse persists revocation and 204 password change leaves no live prior session from concurrent operations. Tests must not default to class-level transactions hiding actual commit/rollback, fixed timing sleeps, mocked database locks or disabled security.
