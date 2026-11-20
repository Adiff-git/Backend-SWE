# Cart business-rule revision tasks

2026-11-17. These tasks implement the replacement brief; the earlier 46-task delivery used superseded policies. No placeholder features or new dependencies.

- [X] C001 Reconcile confirmed 999, variant-only, reset/retain and response requirements with spec.md and contracts.
- [X] C002 Introduce explicit request DTOs and strict parser; productVariantId input; remove combined update API.
- [X] C003 Update transactional quantity/variant operations while preserving common locking, ownership, selection, snapshots and rollback.
- [X] C004 Add totalItemCount and uniform business-action POST 200, with explicit security matchers.
- [X] C005 Add V22 without changing applied migrations; fail on incompatible legacy rows, add migration safety coverage, move conflicting disposable search fixture to V23.
- [X] C006 Pass quantity, controller, service, repository, migration, concurrency and authenticated flow regression tests under the new rules.
- [X] C007 Run full repository verification, inspect actual discovered reports and record results/limitations in quickstart.md.
- [X] C008 Final source/doc consistency and whitespace review; preserve unrelated changes and do not deploy/push without a separate request.
