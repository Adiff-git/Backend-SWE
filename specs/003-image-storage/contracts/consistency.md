# Lifecycle, Transactions and Recovery

## Completion sequence

1. Nontransactional MediaService facade obtains trusted actor identity and invokes proxied MediaCommandService. Command uses REQUIRED/READ COMMITTED and owner-scoped lookup. Missing/foreign is 404.
2. COMPLETED returns persisted ID/key without staging read or copy. EXPIRED returns expired outcome. PENDING at/after deadline becomes EXPIRED, flushes and returns expired outcome normally.
3. For eligible PENDING, capture entity version, inspect staging and conditional GET on observed ETag. Validate Content-Type on the read response, nonempty bytes, actual decoded format/pixels and declaration agreement. Download into a temporary file with bounded infrastructure calls and reliable disposal.
4. Generate a new final key unique to this attempt. Conditional copy uses copySourceIfMatch with the validated source ETag; never use a destination-only condition to protect source. Explicitly replace metadata and await the fully parsed SDK result. Source 412 becomes 409; missing staging is 409; copy failure is 503.
5. Recheck pending completion deadline immediately before state transition. If exceeded, persist EXPIRED outcome; any copy remains unreferenced for reconciliation. Otherwise set COMPLETED/finalKey and flush, checking @Version. Conflicting attempts fail 409; a lost response is safe to retry.
6. Register staging deletion after successful completion commit only. Catch/sanitize cleanup failures; never let them turn success into failure. Commit failure does not run successful cleanup. Close temporary content on every path.
7. After command commit, facade turns EXPIRED outcome into 409, or verifies final existence and signs a fresh GET for COMPLETED. A signing failure returns 503 while completion stays committed. A retry repeats only existence/signing for a completed record.

Each competing attempt has its own final destination, so a loser cannot overwrite the winning destination even after successful copy. Losing copies and rolled-back copies may remain orphaned; do not delete an uncertain final object automatically. No new PROCESSING state, lease, global retry or idempotency-key mechanism is needed.

## Profile transaction

Retain existing account update coordination and transaction. Validate the image selection and prepare the final response URL before returning from the proxied transactional service; any invalid selection or URL failure rolls back all profile edits. Explicit null needs no attachment lookup and never deletes media. Prepared mapping data avoids converter I/O. Existing terminal completed state and no deletion policy keep validated references stable; operator deletion outside policy is surfaced as 503, not silently cleared.

## Failure matrix

| Failure point | Database | Storage | Response/retry |
| --- | --- | --- | --- |
| Validation rejects | PENDING unchanged | Staging retained | 422; replace and retry before deadline |
| Source missing/changed | PENDING unchanged | No accepted final copy | 409; upload/retry |
| Deadline observed | EXPIRED committed | Staging/orphan may remain | 409; create new upload |
| Copy fails or is ambiguous | No completion commit | Possible attempt orphan | 503; client retry; reconcile |
| DB fails after copy | Rollback | Copy not reversed; staging retained | 500 or optimistic 409; retry |
| Completion commit succeeds | COMPLETED | Final exists | Subsequent staging cleanup is best effort |
| Staging cleanup fails | COMPLETED unchanged | Staging leftover | 200 if response signing succeeds |
| Post-commit signing fails | COMPLETED unchanged | Final retained | 503; repeat completion/renewal |

## Manual reconciliation

Provide an operator runbook, no scheduler/API deletion feature. Inventory staging and attempt final objects against persisted keys, statuses and references. Coordinate with in-flight requests (maintenance window or verified quiescence), wait for active operations to finish, then re-read database state immediately before deletion. Never delete referenced or completed final objects, or objects whose status/commit is uncertain. Abandoned expired staging is eligible only after the signed PUT deadline and no in-flight completion. Log sanitized counts/failure categories and retry failed deletions manually. A simple age heuristic alone is not safe final-object reconciliation.

## Required verification

Retain deterministic object-replacement test: pause at storage seam after actual decode, overwrite staging with different bytes, resume conditional copy and assert 409, no COMPLETED commit and no replacement bytes promoted. Also test a change before conditional GET. Use real LocalStack conditional operations, not just mocks.

Observe real transaction boundaries for persisted expiry despite 409, database failure after successful copy, and caught post-commit cleanup failure. Test sequential repeated completion, completed lifetime independence, profile atomicity and new URL issuance with a controllable Clock. Simultaneous-completion integration test is explicitly deferred by user choice; required @Version/unique-attempt-key safeguards remain, and no report may claim that deferred interleaving was verified.


## Confirmed content identity clarification (2026-10-27)

Authoritative content-identity requirements and acceptance outcomes are defined in [spec.md FR-008 and Content Identity Decision](../spec.md#content-identity-decision-2026-10-27). This document references that definition rather than restating it.
