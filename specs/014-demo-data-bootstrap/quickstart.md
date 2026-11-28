# Quickstart and Validation Guide

**Implementation checkpoint, 2026-11-27.** Implementation and Windows/Docker full-profile verification passed: exact persisted data, twelve logins/103 API checks, preserved normal activity, confirmed fresh isolation/recovery, commit/report faults and healthy backend recovery. Native Linux/WSL2 host and native cross-checkout checks remain user-deferred. Frontend journeys are explicitly UNEXECUTED without a supplied frontend.

**R-40 confirmed volume**: The approved artifact contains R=83,561 reviews, C=175,538 completed orders, 178,538 total orders and 578,080 items. These derived volumes replace the old fixed totals; exact review/rating bands and uniform draws are unchanged. Independent persisted checks passed both exact distributions with zero invariant errors. First import took 1,761 seconds on the verified environment, including 1,477 seconds in enabled deferred constraints; allow substantial time for a fresh full import.

## Prerequisites

- Linux Bash or WSL2 and working local Docker Engine/Compose v2. Native Windows PowerShell/Git Bash is not the supported bootstrap runtime.
- Existing `data/Catalog-Data` source at the reviewed revision and intact image files; approved offline enriched artifact with exact digest/review record. No runtime AI account/API call is required.
- Approved standalone dependency lock and built helper/backend images. Implementation resolves and obtains approval for exact dependency versions before installation/introduction, then documents the build command/image digest. No Java/Maven dependency changes.
- `.env.local` following existing `.env.example`, configured for project backend-swe-demo, database backend_demo and bucket backend-demo-images; valid private database password/JWT secret and DEMO_BOOTSTRAP_PASSWORD for twelve login accounts. Never paste secrets into a command/report or commit this file.
- Successful actual PostgreSQL UTF8/ICU/pg_trgm compatibility probe. A failed V15 prerequisite is a reported blocker, not permission to edit migrations.

The frozen artifact includes reviewed catalog/variants, source/synthetic provenance, recipe/rules and hashes. Offline preparation is a separate maintainer activity described by [artifact contract](contracts/artifact.md); startup never creates missing AI output. F-39 scope is private non-commercial course use/submission with team/instructor access only. Preserve provenance; public redistribution verification is not applicable to current scope, permissions remain unverified, and public redistribution/commercial use are prohibited pending future permission verification and scope reassessment.

## First normal startup

From repository root after genuine artifact release approval and dedicated configuration:

```bash
bash scripts/start-dev.sh
```

Expected: preflight and exclusive lock; verified infrastructure readiness; safe unseeded-state recognition; unpublished temporary migration instance; assets/checkpoints verified; atomic business import/completion; ordinary backend ready. Result prints nonsecret connection information, twelve usernames and scenario/report paths. It never prints passwords/hashes/tokens.

Inspect the emitted report against [verification contract](contracts/verification.md). Initial expected counts: 1,500 products, artifact's final 1,500–9,000 variants, 500 users, 512 addresses, artifact-frozen C + 3,000 orders (C >= 9,000 completed/900 pending/750 confirmed/750 shipping/400 cancelled/200 failed), exactly R sampled eligible reviews under R-40, 300 populated carts, 3,500 wishlist entries and 360 recent behavior events. Confirm exact review-count bins 300/300/525/300/75, exact average-rating bins 60/120/300/420/300 among 1,200 reviewed products, eight voucher scenarios, 1,500 verified image mappings and zero invariant violations. Compare R/C/status/item totals with the manifest, never a fixed expected-review mean.

Verify source bytes/manifests remain unchanged. Operational validation must prove generated search columns and existing ICU/price functions remain active, not manually replaced. Import is not complete merely because tables/Flyway/roles/products exist.

## Repeated normal startup

Use one designated account through normal frontend login with the privately configured password. Make a benign wishlist/cart/profile change and record its identity privately. Run:

```bash
bash scripts/start-dev.sh
```

Expected: compatible completed state, no temporary migrator, import skipped, unchanged anchor/password hashes and preserved activity. No forced restoration of original counts, carts or expired promotion dates. Later normal authentication sessions/user uploads are legitimate activity, not seed corruption. If a required surviving owned image is missing, repair only that asset from trusted source/checkpoint evidence, without reseeding DB rows.

## Fresh startup

Fresh intentionally removes all data in the approved application database, including later demo activity. Non-owned uploaded objects/checkpoints are preserved even though their database references disappear; no automatic backup or undo is promised. PostgreSQL volume and unrelated projects/resources remain.

```bash
bash scripts/start-dev.sh --fresh
```

At the confirmation prompt type exactly:

```text
RESET backend_demo
```

Expected: validated local ownership, durable owned-asset reset intention, backend traffic stopped, infrastructure ready before DB reset, only backend_demo recreated via existing migrations, only tracked owned assets/checkpoints cleaned, import at a new recorded UTC anchor, all checks and backend readiness. Mismatched/absent confirmation causes nonzero exit without deletion.

Compare before/after resource inventory and private reports to prove volume/unrelated assets survived. Confirm stable catalog/scenario identities and original prices while time windows intentionally shift to the fresh anchor. Do not expect byte-identical password salts or wall-clock metadata.

## Named frontend journeys

Use the twelve usernames and product/order/cart references from the generated scenario summary; do not hardcode guessed IDs. Obtain actual frontend URL/build from the real frontend setup, because backend Compose does not run a frontend.

1. Login/profile/default address: authenticate normally; inspect both addresses and one default.
2. Browse/search/filter/pagination: exercise available category/brand/price/specification filters and relevance/price/name sorts. Additional AI option dimensions do not create new filters; Laptop/Tablet registry applicability remains.
3. Variants: select coherent product-specific options; report sparse/unavailable combinations without inventing product capabilities. Fallback products may remain single-variant.
4. Successful checkout: selected available items, retained unselected items and owned address; inspect immutable order price/voucher snapshots.
5. Stock failures: named insufficient-stock and out-of-stock cases fail through existing rules without negative balance/counter changes.
6. Vouchers: valid fixed/percentage successes and minimum-spend, expired, scheduled, disabled, global/customer-limit failures with existing eligibility calculations.
7. Order history/cancellation: inspect approved statuses and a currently cancellable owned order. FAILED reservations remain ACTIVE under existing rules.
8. Reviews/wishlist: inspect eligible history, edit an owned text review and create a review for a reserved unreviewed completed item; modify wishlist normally. No seeded avatar/review images are promised.
9. Behavior history: inspect recent PRODUCT_VIEW/SEARCH/PRODUCT_CLICK events; no recommendation-engine success claim.

Record each journey as PASS/FAIL/UNEXECUTED with actual frontend/backend/artifact identity. Verify sales/rating/popularity coverage through operational queries where no application endpoint exists; no new sales/rating sort or statistics feature is included.

## Operational interruption, storage and concurrency checks

During implementation verification only, use controlled fault points in the internal utility and exact owned-resource setup, not a new public operating mode. Record commands/results for the ten scenarios in [verification contract](contracts/verification.md): interrupted upload/checkpoint/transaction, lost commit response, concurrent starts from different checkouts, stale sentinel recovery, missing/conflicting owned object, LocalStack restoration, foreign/noncompatible state and post-commit readiness failure.

Do not delete any unrelated object/checkpoint to make a check pass. Restore controlled fixtures after each check. Root checkpoint restore currently overwrites from valid checkpoint bytes; prove live conflict detection occurs before it could mask an introduced conflict. Fresh cleanup must remove owned checkpoint sources so restart does not resurrect them.

## Recovery guide

| Failure | Required response |
|---|---|
| Config/dependency/artifact mismatch | Correct the documented prerequisite; do not reset or change source checksums |
| ICU/extension/Flyway failure | Keep backend stopped, preserve exact error/history; report blocker without migration rewrite |
| Incomplete matching import | Reread completion; when rolled back, retry normal and reuse trusted objects/anchor |
| Lost commit response | Read authoritative metadata; completed means skip, unknown means stop rather than duplicate |
| Foreign data/changed artifact/importer | Preserve data; use matching compatible tooling/artifact or intentionally confirmed fresh |
| Missing owned object | Normal repair from trusted surviving reference/source/checkpoint; no database reseed |
| Conflicting object/corrupt checkpoint | Stop and report exact sanitized identity; never silently overwrite/delete non-owned content |
| Lock occupied/stale | Let active invocation finish; before targeted recovery prove original orchestrator/helpers/migrator are gone and match sentinel labels/token |
| Failed backend after commit | Dataset may be initialized; fix readiness then retry normal, no automatic reset |

## Regression and delivery evidence

After affected operational checks and cross-domain checkpoints, near implementation completion run the existing unrestricted verification from `demo/`:

```bash
./mvnw verify
```

No skip flags, selective test exclusions or new test classes/suites. Inspect current discovered test/package reports. Use targeted reruns after isolated fixes; repeat full verification for broad/shared-infrastructure changes or remaining regression uncertainty. Record environment blockers and unexecuted checks accurately. Actual execution status is recorded below; unexecuted journeys/reset/import are never treated as passed.

Completion delivery includes actual source/artifact/importer revisions, import anchor, sanitized counts/assets/invariant results, operational recovery/concurrency evidence, frontend outcomes and remaining limitations. F-39 educational scope is complete; public redistribution verification is not applicable to this scope. Retain source provenance and UNVERIFIED copyright/reuse-permission status. Any future public redistribution or commercial use requires permission verification and explicit scope reassessment, regardless of successful local execution.

## Approved release and verification status

The user approved the concrete review package and continued implementation. All 711 required decisions bind payload `a884af94a3b96e9a39e747dd50bb69ac8d820208dfee901df9e5001612854e08`. Actual database option/specification/price validation passed. Private release `.private/demo-bootstrap/artifacts/demo-v3` has digest `a393fc633883079bc4795a72a490ce3e6b9d63588f588447ef43a86cc1b0058c` and detached approval binding the unchanged approved payload. It includes two localized reset-recovery fixes; all approved payload files are byte-identical to the verified `demo-v1` release. It contains 1,500 products, 2,257 variants (757 enriched/743 single-variant fallbacks), 83,561 reviews, 175,538 completed orders plus 3,000 other-status orders, and 578,080 items. Independent full persisted checks passed. Rule-assisted analysis is identified honestly; no separate model invocation is invented.

The complete offline graph passed two replays excluding intentional BCrypt salt differences. Actual V1?V30 Flyway/ICU/extension checks, every candidate canonical option map, empty staging SQL/constraint flush/rollback, isolated signed upload/checkpoint recovery and exact owned cleanup/preservation checks passed. The required unrestricted `mvnw.cmd verify` passed 724 unit/slice and 758 integration tests, zero failures/errors/skips. Current reports are under `.private/demo-bootstrap/verification/` and `.private/demo-bootstrap/reports/final/`.

`.env.local` is configured for the dedicated target, reviewed release and private demo password. Its original bytes are preserved at `.private/demo-bootstrap/backups/env.local.before-bootstrap`, and existing secret values are unchanged. Controlled verification also retains `.private/demo-bootstrap/verification/demo.env`; secret values are never printed. The matching non-root helper is tagged `backend-swe-demo-bootstrap:local`. Effective public Compose validation passed. Full import, normal preservation, reset isolation/interruption, recovered commit acknowledgement, report failure and final healthy readiness all passed through Windows/Docker operational checks. Final normal startup imported zero rows and preserved all public-table digests and sequences. Native Linux/WSL2 host lifecycle is user-deferred. No frontend project/build/URL is available; frontend journeys are explicitly UNEXECUTED. Follow `tools/demo-bootstrap/README.md` for maintainer preparation and recovery instructions.
